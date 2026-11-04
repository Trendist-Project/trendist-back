package com.trendist.keyword.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.country.service.CountryService;
import com.trendist.keyword.ai.GeminiClient;
import com.trendist.keyword.ai.NewsSearchClient;
import com.trendist.keyword.ai.TrendNewsContext;
import com.trendist.keyword.dto.AiCommentDtos;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.entity.KeywordAiComment;
import com.trendist.keyword.entity.KeywordAiRequest;
import com.trendist.keyword.repository.KeywordAiCommentRepository;
import com.trendist.keyword.repository.KeywordAiRequestRepository;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.player.entity.Player;
import com.trendist.player.service.PlayerService;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AiCommentServiceImpl implements AiCommentService {
    private static final Logger log = LoggerFactory.getLogger(AiCommentServiceImpl.class);
    private static final String BY_AI = "AI", BY_ADMIN = "ADMIN";
    /** 한 키워드를 이만큼 시도해도 못 받으면(실패·UNKNOWN) 자동 요청을 멈춘다. 관리자가 [AI 재요청]으로 다시 시도할 수 있다 */
    private static final long MAX_ATTEMPTS = 3;
    private static final int PAGE_SIZE = 30;
    /** 제미나이가 한도(429)를 알려 오면 이 시간 동안 자동 요청을 쉰다 */
    private static final Duration COOL_DOWN = Duration.ofMinutes(10);

    private final KeywordRepository keywords;
    private final KeywordAiRequestRepository requests;
    private final KeywordAiCommentRepository aiRows;
    private final GeminiClient gemini;
    private final TrendNewsContext newsContext;
    private final NewsSearchClient newsSearch;
    private final SettingService settings;
    private final CodeTables codes;
    private final CountryService countries;
    private final PlayerService playerService;
    private final RateLimiter rateLimiter;
    private final TransactionTemplate tx;
    private final Clock clock;

    // 서버 1대 메모리: 하루 요청 수와 쉬는 시각. 재시작하면 비워지는데, 그래도 분당 한도는 지켜진다
    private LocalDate day = LocalDate.MIN;
    private int usedToday = 0;
    private Instant pausedUntil = Instant.MIN;

    // ------------------------------------------------------------------ 자동 생성

    @Override
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public synchronized int generatePending() {
        if (!gemini.enabled() || settings.getInt(SettingKeys.AI_COMMENT_ENABLED) != 1) return 0;
        Instant now = Instant.now(clock);
        if (now.isBefore(pausedUntil)) return 0;
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) { day = today; usedToday = 0; }
        if (usedToday >= settings.getInt(SettingKeys.AI_COMMENT_DAILY_MAX)) return 0;
        boolean grounding = settings.getInt(SettingKeys.AI_COMMENT_GROUNDING) == 1;
        // 요청 한 번에 여러 키워드를 묶어 보낸다: 요청 수(무료 한도의 기준)·토큰·시간이 키워드마다 따로 보낼 때보다 훨씬 적다(10.4)
        List<Long> ids = keywords.findIdsNeedingAiComment(codes.keywordStateId(CodeTables.KW_BANNED), MAX_ATTEMPTS,
                PageRequest.of(0, settings.getInt(SettingKeys.AI_COMMENT_BATCH_SIZE)));
        if (ids.isEmpty()) return 0;
        // 나라마다 설명 언어가 다르다(KR=한국어, GLOBAL=영어): 언어별로 묶어 요청 한 번씩 보낸다. 저장되는 설명도 그 언어다
        Map<String, Map<Long, String>> byLang = new LinkedHashMap<>();
        for (Long id : ids) {
            tx.executeWithoutResult(s -> keywords.findById(id).ifPresent(k ->
                    byLang.computeIfAbsent(langOf(k), l -> new LinkedHashMap<>()).put(id, k.getText())));
        }
        int made = 0;
        for (var group : byLang.entrySet()) {
            if (usedToday >= settings.getInt(SettingKeys.AI_COMMENT_DAILY_MAX)) break;
            Integer r = generateGroup(group.getKey(), group.getValue(), grounding);
            if (r == null) return made; // 한도(429): 쉰다
            made += r;
        }
        return made;
    }

    /** 키워드가 속한 나라의 설명 언어: 나라 기본 언어가 영어면 en, 아니면 ko. 트랜잭션 안에서 불러야 한다. */
    private static String langOf(Keyword k) {
        String l = k.getCountry().getDefaultLocale();
        return l != null && l.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "ko";
    }

    /** 한 언어 묶음을 요청해 저장한다. 만든 수를 돌려주고, 한도(429)에 걸려 쉬게 됐으면 null. */
    private Integer generateGroup(String lang, Map<Long, String> texts, boolean grounding) {
        usedToday++;
        Map<String, Optional<String>> answers;
        try {
            answers = gemini.describeAll(new ArrayList<>(texts.values()), newsOf(texts.values(), lang), grounding, lang);
        } catch (GeminiClient.RateLimitedException e) {
            pausedUntil = Instant.now(clock).plus(COOL_DOWN);
            log.warn("gemini rate limited: pause auto comments until {}", pausedUntil);
            return null;
        } catch (Exception e) {
            log.warn("ai comments failed for {} keywords: {}", texts.size(), e.toString());
            tx.executeWithoutResult(s -> texts.keySet().forEach(id -> keywords.findById(id).ifPresent(k -> aiRow(k).incAttempts())));
            return 0;
        }
        int made = 0, unknown = 0;
        for (var e : texts.entrySet()) {
            long id = e.getKey();
            Optional<String> a = answers.get(e.getValue());
            if (a == null) { // 이 키워드의 답이 빠졌다: 다음 분에 다시
                tx.executeWithoutResult(s -> keywords.findById(id).ifPresent(k -> aiRow(k).incAttempts()));
            } else if (a.isPresent()) {
                save(id, a.get(), BY_AI); made++;
            } else { // 모른다고 했다: 다시 물어도 같다
                tx.executeWithoutResult(s -> keywords.findById(id).ifPresent(x -> aiRow(x).giveUp(MAX_ATTEMPTS)));
                unknown++;
            }
        }
        log.info("ai comments [{}]: {} made, {} unknown, {} requested in one request", lang, made, unknown, texts.size());
        return made;
    }

    private Map<String, List<String>> newsOf(Collection<String> texts, String lang) {
        Map<String, List<String>> m = new HashMap<>();
        for (String t : texts) {
            List<String> h = newsContext.get(lang, t);
            if (h.isEmpty()) { // RSS가 제목을 주지 않은 키워드(사용자가 등록한 유행어 등): 구글 뉴스에서 찾아 본다
                h = newsSearch.search(t, lang);
                newsContext.put(lang, t, h);
            }
            if (!h.isEmpty()) m.put(t, h);
        }
        return m;
    }

    private void save(long keywordId, String comment, String by) {
        tx.executeWithoutResult(s -> {
            Keyword k = keywords.findByIdForUpdate(keywordId).orElseThrow();
            aiRow(k).write(comment, by, Instant.now(clock));
        });
    }

    // ------------------------------------------------------------------ 사용자 갱신 요청

    @Override
    @Transactional
    public void request(long userId, long keywordId) {
        Player p = playerService.requireCurrent(userId);
        Keyword k = keywords.findById(keywordId)
                .filter(x -> x.getCountry().getId().equals(p.getCountry().getId()) && !x.getKeywordState().getId().equals(codes.keywordStateId(CodeTables.KW_BANNED)))
                .orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        if (k.getAiComment() == null || k.getAiComment().getComment() == null) throw ApiException.conflict("AI_COMMENT_NONE", "아직 AI 설명이 없어요");
        if (requests.existsByKeywordIdAndPlayerIdAndHandledAtIsNull(keywordId, p.getId())) return; // 이미 접수됨
        if (!rateLimiter.tryAcquire("aireq:" + p.getId(), 10, Duration.ofHours(1))) {
            throw ApiException.tooMany("AI_REQUEST_RATE", "요청이 너무 많아요. 잠시 뒤에 다시 해 주세요");
        }
        requests.save(new KeywordAiRequest(keywordId, p.getId()));
    }

    // ------------------------------------------------------------------ 관리자

    @Override
    @Transactional(readOnly = true)
    public AiCommentDtos.Page list(String countryCode, String filter, int page) {
        var country = countries.requireOpen(countryCode);
        long bannedId = codes.keywordStateId(CodeTables.KW_BANNED);
        int pg = Math.max(0, page);
        List<Keyword> list;
        long total;
        if ("missing".equals(filter) || "all".equals(filter)) {
            var r = keywords.findForAdminAi(country.getId(), bannedId, "all".equals(filter),
                    PageRequest.of(pg, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "activatedAt")));
            list = r.getContent(); total = r.getTotalElements();
        } else { // requests: 열린 요청이 있는 키워드(요청 많은 순). 열린 요청은 많지 않아 메모리에서 나눈다
            List<Object[]> open = requests.openByKeyword();
            total = open.size();
            List<Long> ids = open.stream().skip((long) pg * PAGE_SIZE).limit(PAGE_SIZE).map(o -> (Long) o[0]).toList();
            Map<Long, Keyword> byId = new HashMap<>();
            keywords.findAllWithAiByIds(ids).forEach(x -> byId.put(x.getId(), x));
            list = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        }
        Map<Long, Object[]> counts = openCounts(list.stream().map(Keyword::getId).toList());
        List<AiCommentDtos.Row> rows = list.stream().map(k -> row(k, counts.get(k.getId()))).toList();
        return new AiCommentDtos.Page(rows, total, pg, PAGE_SIZE, gemini.enabled());
    }

    @Override
    public AiCommentDtos.Row regenerate(long keywordId) {
        if (!gemini.enabled()) throw ApiException.conflict("AI_DISABLED", "AI 연결(제미나이 API 키)이 설정되지 않았어요");
        Keyword k = keywords.findById(keywordId).orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        Optional<String> text;
        String lang = tx.execute(st -> langOf(keywords.findById(keywordId).orElseThrow())); // 키워드가 속한 나라의 언어로
        try {
            Map<String, Optional<String>> r = gemini.describeAll(List.of(k.getText()), newsOf(List.of(k.getText()), lang),
                    settings.getInt(SettingKeys.AI_COMMENT_GROUNDING) == 1, lang);
            if (!r.containsKey(k.getText())) throw new IllegalStateException("AI 답에 이 키워드가 없어요");
            text = r.get(k.getText());
        } catch (GeminiClient.RateLimitedException e) {
            throw ApiException.tooMany("AI_RATE_LIMITED", "AI 요청 한도에 걸렸어요. 잠시 뒤에 다시 해 주세요");
        } catch (Exception e) {
            log.warn("ai regenerate failed for keyword {}: {}", keywordId, e.toString());
            throw ApiException.conflict("AI_FAILED", "AI에게서 설명을 받지 못했어요. 잠시 뒤에 다시 해 주세요");
        }
        if (text.isEmpty()) throw ApiException.conflict("AI_UNKNOWN", "AI가 이 키워드를 설명하지 못했어요. 직접 써 주세요");
        return tx.execute(s -> {
            Keyword x = keywords.findByIdForUpdate(keywordId).orElseThrow();
            aiRow(x).write(text.get(), BY_AI, Instant.now(clock));
            requests.closeAll(keywordId, Instant.now(clock));
            return row(x, null);
        });
    }

    @Override
    @Transactional
    public AiCommentDtos.Row edit(long keywordId, String comment) {
        String c = comment == null ? "" : comment.strip().replaceAll("\\s+", " ");
        if (c.isEmpty() || c.length() > 200) throw ApiException.badRequest("AI_COMMENT_LENGTH", "설명은 1~200자로 써 주세요");
        Keyword k = keywords.findByIdForUpdate(keywordId).orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        aiRow(k).write(c, BY_ADMIN, Instant.now(clock));
        requests.closeAll(keywordId, Instant.now(clock));
        return row(k, null);
    }

    @Override
    @Transactional
    public AiCommentDtos.Row dismiss(long keywordId) {
        Keyword k = keywords.findById(keywordId).orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        requests.closeAll(keywordId, Instant.now(clock));
        return row(k, null);
    }

    @Override
    @Transactional(readOnly = true)
    public long openRequestCount() { return requests.countByHandledAtIsNull(); }

    private Map<Long, Object[]> openCounts(Collection<Long> ids) {
        Map<Long, Object[]> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        for (Object[] o : requests.openCounts(ids)) m.put((Long) o[0], o);
        return m;
    }

    /** 키워드의 AI 설명 행. 아직 없으면 만들어 키워드에 잇는다(처음 시도할 때). 트랜잭션 안에서 불러야 한다. */
    private KeywordAiComment aiRow(Keyword k) {
        if (k.getAiComment() == null) k.attachAiComment(aiRows.save(KeywordAiComment.create()));
        return k.getAiComment();
    }

    private AiCommentDtos.Row row(Keyword k, Object[] open) {
        KeywordAiComment a = k.getAiComment();
        return new AiCommentDtos.Row(k.getId(), k.getText(), k.getKeywordState().getName(),
                a == null ? null : a.getComment(), a == null ? null : a.getWrittenBy(), a == null ? null : a.getCommentedAt(), a == null ? 0 : a.getAttempts(),
                open == null ? 0 : (Long) open[1], open == null ? null : (Instant) open[2]);
    }
}
