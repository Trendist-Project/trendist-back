package com.trendist.keyword.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.game.event.GameEvents;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.entity.KeywordState;
import com.trendist.keyword.entity.PlayerKeyword;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.keyword.source.ExternalTrendSource;
import com.trendist.keyword.support.ActivePopulation;
import com.trendist.keyword.support.BannedWordFilter;
import com.trendist.keyword.support.KeywordNormalizer;
import com.trendist.keyword.support.SearchCounter;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import com.trendist.keyword.repository.KeywordAiRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 검색, +, 보관함, 만료, 충돌로 잃기(4.4~4.8, 9.4). */
@Service
@RequiredArgsConstructor
public class KeywordServiceImpl implements KeywordService {
    private static final Logger log = LoggerFactory.getLogger(KeywordServiceImpl.class);
    private final KeywordRepository keywords;
    private final BannedWordFilter bannedWords;
    private final KeywordAiRequestRepository aiRequests;
    private final PlayerKeywordRepository playerKeywords;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final TrendFormula formula;
    private final ActivePopulation population;
    private final SettingService settings;
    private final CodeTables codes;
    private final List<ExternalTrendSource> sources;
    private final SearchCounter searchCounter;
    private final com.trendist.keyword.support.KeywordViewCounter viewCounter;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final CountryService countries;
    private final Clock clock;
    private final com.trendist.stats.support.ActivityCounter activity;

    /** 트렌드 값이 바뀐 키워드. 1분 잡이 보유자 점수를 DB에서 다시 합산한다(9.4). */
    private final Set<Long> dirtyKeywords = ConcurrentHashMap.newKeySet();

    // ---------------------------------------------------------------- 검색

    @Override
    @Transactional
    public KeywordDtos.SearchResult search(long userId, String q) {
        Player p = playerService.requireCurrent(userId);
        String text = KeywordNormalizer.display(q);
        String normalized = KeywordNormalizer.validate(text);
        // 검색할 때마다 플레이어의 검색수 +1(찾았는지·금지인지와 관계없이). 형식에 맞지 않아 거절된 입력은 세지 않는다
        players.incSearchCount(p.getId());
        activity.add(p.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.SEARCH); // 사이트 통계(13.9)
        events.publishEvent(new GameEvents.TitleCheck(p.getId())); // 검색 칭호(4.10)
        Country country = p.getCountry();
        Instant now = Instant.now(clock);

        if (bannedWords.contains(country.getCode(), normalized)) return new KeywordDtos.SearchResult("BANNED", null); // 금지어가 들어 있으면 막는다(4.6)
        Optional<Keyword> found = keywords.findByCountryIdAndNormalized(country.getId(), normalized);
        if (found.isPresent()) {
            Keyword k = found.get();
            if (isBanned(k)) return new KeywordDtos.SearchResult("BANNED", null);
            if (isState(k, CodeTables.KW_INACTIVE)) tryExternalActivate(k, now); // 💤는 외부를 다시 조회한다
            searchCounter.count(userId, k.getId());
            return new KeywordDtos.SearchResult("FOUND", card(k, p, now));
        }
        // DB에 없음: 외부에서 확인되면 🔥로 등록, 아니면 등록하지 않고 💤 카드만 보여 준다(첫 + 때 만든다)
        Optional<Confirmed> ext = externalConfirm(country.getCode(), normalized);
        if (ext.isPresent()) {
            Keyword k = createKeyword(country, text, normalized, ext, now);
            searchCounter.count(userId, k.getId());
            return new KeywordDtos.SearchResult("FOUND", card(k, p, now));
        }
        var preview = new KeywordDtos.KeywordCard(null, text, CodeTables.KW_INACTIVE, 0,
                0, 0, 0, 0, false, null, null, false);
        return new KeywordDtos.SearchResult("NEW", preview);
    }

    // ---------------------------------------------------------------- +

    @Override
    @Transactional
    public KeywordDtos.PlusResult plus(long userId, String rawText) {
        Player found = playerService.requireCurrent(userId);
        Player p = players.findByIdForUpdate(found.getId()).orElseThrow(); // 잠금 순서: 플레이어 → 키워드
        String text = KeywordNormalizer.display(rawText);
        String normalized = KeywordNormalizer.validate(text);
        Instant now = Instant.now(clock);
        Country country = p.getCountry();
        if (bannedWords.contains(country.getCode(), normalized)) throw ApiException.badRequest("KEYWORD_BANNED", "검색할 수 없는 키워드예요"); // 금지어가 들어 있으면 만들지도 않는다

        Keyword k = keywords.findByCountryIdAndNormalized(country.getId(), normalized)
                .orElseGet(() -> createKeyword(country, text, normalized, externalConfirm(country.getCode(), normalized), now));
        k = keywords.findByIdForUpdate(k.getId()).orElseThrow();
        if (isBanned(k)) throw ApiException.badRequest("KEYWORD_BANNED", "검색할 수 없는 키워드예요");

        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId());
        if (existing.isPresent() && !existing.get().isExpired(now)) throw ApiException.conflict("ALREADY_PLUSED", "이미 보유중인 키워드예요");
        activity.add(country.getId(), com.trendist.stats.support.ActivityCounter.Kind.PLUS); // 사이트 통계(13.9)
        return addHolding(p, k, existing, now);
    }

    /** +의 본체: 보관함에 넣고 인원·EXP·트렌드 값·점수를 고친다. 플레이어와 키워드 행을 잠근 상태에서 부른다. */
    private KeywordDtos.PlusResult addHolding(Player p, Keyword k, Optional<PlayerKeyword> existing, Instant now) {
        Instant expires = now.plus(Duration.ofHours(settings.getInt(SettingKeys.PLUS_TTL_HOURS)));
        Country country = p.getCountry();
        long oldTrend = k.getTrendValue();
        long oldScore = p.getCurrentScore() - p.getLevel(); // 레벨은 유행도에 1:1로 더해지는데 "얻은 점수"에는 키워드 몫만 센다(레벨이 올라도 늘어난 것으로 보이지 않게)
        boolean newHolder;
        if (existing.isPresent()) {
            existing.get().renew(now, expires); // 만료 행 재사용: 가진 사람 수는 그대로
            newHolder = false;
        } else {
            playerKeywords.save(new PlayerKeyword(p, k, now, expires));
            k.incHolder();
            p.incHold();
            newHolder = true;
        }
        k.incAdd();
        p.incAdd();
        p.addExp(settings.getInt(SettingKeys.EXP_PER_PLUS)); // EXP는 💤 키워드에도 바로 준다(4.9)
        p.touchPlayed(now, ZoneId.of(p.getCountry().getTimezone())); // 활동한 날 칭호(4.10)

        if (isState(k, CodeTables.KW_INACTIVE) && k.getHolderCount() >= settings.getInt(SettingKeys.ACTIVATE_HOLDERS)) {
            k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now); // 내부 확산으로 활성(4.6)
        }
        long newTrend = updateTrend(k);
        keywords.flush();
        playerKeywords.flush();
        long newScore = playerService.recompute(p);
        long gainedScore = newScore - p.getLevel() - oldScore;
        publishTrendChange(k, oldTrend, newTrend, p.getId());
        if (newHolder) {
            events.publishEvent(new GameEvents.HoldingChanged(country.getId(), p.getId(), k.getId(), true));
            // 체력 회복(4.36): 새로 얻은 키워드의 점수와 얻은 뒤의 내 유행도(💤 키워드는 점수 0이라 회복도 없다)
            events.publishEvent(new GameEvents.KeywordGained(country.getId(), p.getId(), isState(k, CodeTables.KW_ACTIVE) ? newTrend : 0, newScore));
        }
        return new KeywordDtos.PlusResult(card(k, p, now), gainedScore, playerService.summary(p));
    }

    /** 이미 가진 키워드의 만료 시각만 지금부터 다시 `plus_ttl_hours`(기본 24시간)로 늘린다. 점수·EXP는 그대로(4.3, 4.20, 4.21). 새 만료 시각을 돌려준다 */
    private Instant renewHolding(PlayerKeyword pk, Instant now) {
        Instant expires = now.plus(Duration.ofHours(settings.getInt(SettingKeys.PLUS_TTL_HOURS)));
        pk.renew(now, expires);
        playerKeywords.flush();
        return expires;
    }

    // ---------------------------------------------------------------- 구슬(4.20)

    @Override
    @Transactional
    public KeywordDtos.OrbResult openOrb(long playerId) {
        return openOrb(playerId, false, true);
    }

    @Override
    @Transactional
    public KeywordDtos.OrbResult openPrizeOrb(long playerId) {
        return openOrb(playerId, true, true);
    }

    @Override
    @Transactional
    public KeywordDtos.OrbResult grantRewardKeyword(long playerId) {
        return openOrb(playerId, true, false);
    }

    private KeywordDtos.OrbResult openOrb(long playerId, boolean prize, boolean countOrb) {
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null) return new KeywordDtos.OrbResult("NONE", null, null, null, 0, null, null);
        if (countOrb) {
            activity.add(p.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.ORB); // 사이트 통계(13.9)
            p.incOrb(); // 꽝이어도 먹은 횟수는 센다(칭호, 4.10). 보스 보상 키워드는 구슬이 아니라 세지 않는다
        }
        events.publishEvent(new GameEvents.TitleCheck(p.getId()));
        // ? 구슬: 설정 확률(기본 60%)로 🔥활성 키워드를 얻고, 나머지는 내 키워드 하나를 잃는다(없으면 꽝). ! 구슬(prize)은 항상 얻는다
        if (!prize && ThreadLocalRandom.current().nextDouble(100) >= settings.getDouble(SettingKeys.ORB_ACTIVE_PERCENT)) {
            return loseFromOrb(p);
        }
        Instant now = Instant.now(clock);
        List<Long> ids = keywords.findIdsByCountryAndState(p.getCountry().getId(), codes.keywordStateId(CodeTables.KW_ACTIVE));
        if (prize && ids.size() > 1) {
            // 꽝 없이 "얻는" 구슬이므로, 가능하면 내 보관함에 없는 키워드에서 고른다(이미 가진 키워드면 시간만 갱신돼 얻은 느낌이 없다)
            Set<Long> held = new HashSet<>(activeKeywordIds(p.getId()));
            List<Long> fresh = ids.stream().filter(id -> !held.contains(id)).toList();
            if (!fresh.isEmpty()) ids = fresh;
        }
        if (ids.isEmpty()) return new KeywordDtos.OrbResult("NONE", null, null, null, 0, null, null);
        Keyword k = keywords.findByIdForUpdate(ids.get(ThreadLocalRandom.current().nextInt(ids.size()))).orElseThrow();
        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId());
        if (existing.isPresent() && !existing.get().isExpired(now)) {
            // 이미 보관함에 있는 키워드가 나오면 남은 시간만 지금부터 다시 24시간(사람에게서 빼앗을 때(4.3)와 같게). 점수·EXP는 그대로
            return new KeywordDtos.OrbResult("RENEWED", k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                    k.getHolderCount(), 0L, renewHolding(existing.get(), now));
        }
        KeywordDtos.PlusResult r = addHolding(p, k, existing, now);
        return new KeywordDtos.OrbResult("GAINED", k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                k.getHolderCount(), r.gained(), r.keyword().myExpiresAt());
    }

    /** 구슬에서 잃기(4.20): 가진 키워드(🔥·💤 가리지 않고, 금지 제외) 중 하나를 무작위로 지운다. 잃을 것이 없으면 꽝. 충돌 기록(잃은 횟수·통계)은 올리지 않는다 */
    private KeywordDtos.OrbResult loseFromOrb(Player p) {
        Instant now = Instant.now(clock);
        List<PlayerKeyword> candidates = playerKeywords.findLosableByPlayer(p.getId(), now, codes.keywordStateId(CodeTables.KW_BANNED));
        if (candidates.isEmpty()) return new KeywordDtos.OrbResult("MISS", null, null, null, 0, null, null);
        PlayerKeyword pk = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Long keywordId = pk.getKeyword().getId();
        String text = pk.getKeyword().getText();
        String state = pk.getKeyword().getKeywordState().getName();
        long score = isState(pk.getKeyword(), CodeTables.KW_ACTIVE) ? pk.getKeyword().getTrendValue() : 0; // 💤는 점수에 더해 주지 않았다
        playerKeywords.delete(pk);
        playerKeywords.flush();
        removeHolding(p, keywordId, now);
        Keyword k = keywords.findById(keywordId).orElseThrow();
        return new KeywordDtos.OrbResult("LOST", text, state, k.getTrendValue(), k.getHolderCount(), score, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BotKeyword> botCatalog(long countryId) {
        List<BotKeyword> out = new ArrayList<>();
        for (Object[] r : keywords.findCatalogRows(countryId, codes.keywordStateId(CodeTables.KW_ACTIVE))) {
            out.add(new BotKeyword(((Number) r[0]).longValue(), (String) r[1], ((Number) r[2]).longValue()));
        }
        return out;
    }

    @Override
    @Transactional
    public KeywordDtos.OrbResult gainFromBot(long playerId) { return gainFromBot(playerId, null); }

    @Override
    @Transactional
    public KeywordDtos.OrbResult gainFromBot(long playerId, long keywordId) { return gainFromBot(playerId, Long.valueOf(keywordId)); }

    private KeywordDtos.OrbResult gainFromBot(long playerId, Long botKeywordId) {
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null) return new KeywordDtos.OrbResult("NONE", null, null, null, 0, null, null);
        p.incBotWin(); // 칭호(4.10)
        playerService.awardAttack(p); // 봇도 빼앗기 대상이다: 공격에 성공했으니 빼앗은 횟수 +1과 공격 성공 EXP(4.3)
        events.publishEvent(new GameEvents.TitleCheck(p.getId()));
        Instant now = Instant.now(clock);
        // 구슬과 달리 꽝이 없다. 🔥 키워드 전체에서 고른다
        List<Long> ids = keywords.findIdsByCountryAndState(p.getCountry().getId(), codes.keywordStateId(CodeTables.KW_ACTIVE));
        if (ids.isEmpty()) return new KeywordDtos.OrbResult("NONE", null, null, null, 0, null, null);
        // 봇이 잃은 그 키워드를 준다. 그 사이 🔥가 아니게 됐으면(비활성·금지) 무작위 🔥 키워드로 대신한다
        Long pick = botKeywordId != null && ids.contains(botKeywordId) ? botKeywordId : ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
        Keyword k = keywords.findByIdForUpdate(pick).orElseThrow();
        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId()); // 만료됐지만 아직 안 지운 행일 수도 있다
        if (existing.isPresent() && !existing.get().isExpired(now)) {
            // 이미 가진 키워드: 사람에게서 빼앗을 때와 같이 남은 시간만 지금부터 다시 24시간(4.3)
            return new KeywordDtos.OrbResult("RENEWED", k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                    k.getHolderCount(), 0L, renewHolding(existing.get(), now));
        }
        KeywordDtos.PlusResult r = addHolding(p, k, existing, now);
        return new KeywordDtos.OrbResult("GAINED", k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                k.getHolderCount(), r.gained(), r.keyword().myExpiresAt());
    }

    // ---------------------------------------------------------------- 목록형 외부 소스(10.2)

    @Override
    public int applyExternalTitles(String countryCode, String sourceName, List<String> titles) {
        int held = 0;
        for (String title : titles) {
            String text = KeywordNormalizer.fromExternal(title);
            String normalized;
            try {
                normalized = KeywordNormalizer.validate(text); // 4.6 등록 조건을 통과하지 못하면 버린다
            } catch (ApiException e) {
                log.debug("skip external title '{}': {}", title, e.code());
                continue;
            }
            try {
                Boolean r = tx.execute(st -> holdFromList(countryCode, sourceName, text, normalized));
                if (Boolean.TRUE.equals(r)) held++;
            } catch (RuntimeException e) {
                log.warn("external title '{}' failed", title, e);
            }
        }
        return held;
    }

    /** 목록에 오른 단어 하나: 없으면 🔥로 만들고, 있으면 그 소스로 지금부터 유지 기간(기본 7일)을 다시 센다. 새로 유지를 시작했으면 true. */
    private boolean holdFromList(String countryCode, String sourceName, String text, String normalized) {
        Instant now = Instant.now(clock);
        DataSource source = codes.dataSource(sourceName);
        if (source.getWeight().signum() == 0) return false; // 가중치 0이면 쓰지 않는다
        if (bannedWords.contains(countryCode, normalized)) return false; // 금지어가 들어 있는 외부 제목은 버린다
        Optional<Keyword> found = keywords.findByCountryIdAndNormalized(countryOf(countryCode).getId(), normalized);
        if (found.isEmpty()) {
            createKeyword(countryOf(countryCode), text, normalized, Optional.of(new Confirmed(source, 100)), now);
            return true;
        }
        Keyword k = keywords.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (isBanned(k)) return false;
        boolean holding = k.getDataSource().getId().equals(source.getId()) && k.getExternalUntil() != null
                && k.getExternalUntil().isAfter(now);
        k.holdExternal(source, externalHoldUntil(now)); // 목록에 보일 때마다 지금부터 다시 7일
        if (holding) return false;
        long old = k.getTrendValue();
        k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now);
        long nw = updateTrend(k);
        publishTrendChange(k, old, nw, null);
        return true;
    }

    // ---------------------------------------------------------------- 관리자 키워드(13.7)

    @Override
    @Transactional
    public AdminKeyword adminAdd(String countryCode, String text) {
        String display = KeywordNormalizer.display(text);
        String normalized = KeywordNormalizer.validate(display);
        Country country = countryOf(countryCode);
        DataSource source = codes.dataSource(DataSource.ADMIN);
        if (source.getWeight().signum() == 0) throw ApiException.badRequest("SOURCE_DISABLED", "admin 소스의 가중치가 0이라 쓸 수 없어요");
        Instant now = Instant.now(clock);
        Optional<Keyword> found = keywords.findByCountryIdAndNormalized(country.getId(), normalized);
        if (found.map(k -> !isBanned(k)).orElse(true) && bannedWords.contains(country.getCode(), normalized))
            throw ApiException.conflict("KEYWORD_BANNED", "금지된 키워드가 들어 있어요. 금지를 먼저 풀어 주세요");
        if (found.isEmpty()) {
            // 새 키워드: 🔥활성, 반영 소스 admin, 지금부터 유지 기간(createKeyword가 유지 기간형 소스로 처리)
            Keyword k = createKeyword(country, display, normalized, Optional.of(new Confirmed(source, 100)), now);
            return adminView(k, true);
        }
        Keyword k = keywords.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (isBanned(k)) throw ApiException.conflict("KEYWORD_BANNED", "금지된 키워드예요. 금지를 먼저 풀어 주세요");
        long old = k.getTrendValue();
        k.holdExternal(source, externalHoldUntil(now)); // 다른 소스였어도 admin으로 바꾸고 지금부터 다시 7일
        k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now);
        long nw = updateTrend(k);
        keywords.flush();
        publishTrendChange(k, old, nw, null);
        return adminView(k, false);
    }

    private static final Set<String> ADMIN_SORTS = Set.of("trendValue", "holderCount", "addCount", "searchCount", "viewCount",
            "createdAt", "activatedAt", "externalUntil");

    @Override
    @Transactional(readOnly = true)
    public AdminKeywordLookup adminLookup(String countryCode, String text) {
        Country country = countryOf(countryCode);
        String display = KeywordNormalizer.display(text);
        String normalized;
        try {
            normalized = KeywordNormalizer.validate(display);
        } catch (ApiException e) {
            return new AdminKeywordLookup(display, "", false, e.getMessage(), null);
        }
        AdminKeywordRow row = keywords.findByCountryIdAndNormalized(country.getId(), normalized).map(this::adminRow).orElse(null);
        return new AdminKeywordLookup(display, normalized, true, null, row);
    }

    @Override
    @Transactional
    public AdminKeywordRow adminBanText(String countryCode, String text) {
        String display = KeywordNormalizer.display(text);
        String normalized = KeywordNormalizer.validate(display);
        Country country = countryOf(countryCode);
        Instant now = Instant.now(clock);
        // 등록 전이면 💤비활성으로 만든 뒤 같은 트랜잭션에서 금지한다(공개 페이지·사이트맵 캐시도 adminBan이 비운다)
        Keyword k = keywords.findByCountryIdAndNormalized(country.getId(), normalized)
                .orElseGet(() -> createKeyword(country, display, normalized, Optional.empty(), now));
        return adminBan(k.getId(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminKeywordPage adminSearch(String countryCode, String state, String source, String q, String sort, String dir,
                                        int page, int size) {
        Country country = countryOf(countryCode);
        // 상태·소스는 쉼표로 여러 개를 받는다(비면 전체). 예: "활성,금지"
        List<Long> stateIds = csv(state).stream().map(codes::keywordStateId).toList();
        List<Long> sourceIds = csv(source).stream().map(n -> codes.dataSource(n).getId()).toList();
        String like = q == null || q.isBlank() ? null : "%" + KeywordNormalizer.normalize(q) + "%";
        String by = ADMIN_SORTS.contains(sort) ? sort : "createdAt";
        Sort.Direction d = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int sz = Math.max(1, Math.min(size, 100));
        Sort order = Sort.by(d, by).and(Sort.by(Sort.Direction.DESC, "id"));
        var result = keywords.searchForAdmin(country.getId(), stateIds.isEmpty(), stateIds.isEmpty() ? List.of(-1L) : stateIds,
                sourceIds.isEmpty(), sourceIds.isEmpty() ? List.of(-1L) : sourceIds, like, PageRequest.of(Math.max(0, page), sz, order));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : keywords.countByStateForAdmin(country.getId(), sourceIds.isEmpty(), sourceIds.isEmpty() ? List.of(-1L) : sourceIds, like)) counts.put((String) row[0], (Long) row[1]);
        return new AdminKeywordPage(result.getContent().stream().map(this::adminRow).toList(), result.getTotalElements(),
                result.getNumber(), sz, counts);
    }

    /** 쉼표로 이은 값을 나눈다. 비었으면 빈 목록 */
    private static List<String> csv(String v) {
        if (v == null || v.isBlank()) return List.of();
        return java.util.Arrays.stream(v.split(",")).map(String::trim).filter(x -> !x.isEmpty()).distinct().toList();
    }

    @Override
    @Transactional
    public AdminKeywordRow adminBan(long keywordId, boolean banned) {
        Keyword k = keywords.findByIdForUpdate(keywordId)
                .orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        Instant now = Instant.now(clock);
        if (banned == isBanned(k)) return adminRow(k);
        long old = k.getTrendValue();
        if (banned) k.ban(codes.keywordState(CodeTables.KW_BANNED), now);
        else k.unban(codes.keywordState(CodeTables.KW_INACTIVE), now);
        long nw = updateTrend(k); // 금지·💤면 0
        keywords.flush();
        events.publishEvent(new com.trendist.keyword.support.KeywordPageEvents.Changed(k.getCountry().getCode(), k.getNormalized())); // 공개 페이지·사이트맵 캐시를 커밋 뒤 비운다(4.28)
        if (banned) events.publishEvent(new com.trendist.keyword.support.KeywordPageEvents.Banned(k.getCountry().getCode(), k.getNormalized())); // 커밋 뒤 모든 플레이어의 보관함에서 지운다
        publishTrendChange(k, old, nw, null); // 접속 중인 보유자는 바로, 아닌 보유자는 1분 재합산에서 점수가 빠진다
        return adminRow(k);
    }

    private AdminKeywordRow adminRow(Keyword k) {
        return new AdminKeywordRow(k.getId(), k.getText(), k.getNormalized(), k.getKeywordState().getName(), k.getDataSource().getName(),
                k.getTrendValue(), k.getHolderCount(), k.getAddCount(), k.getSearchCount() + searchCounter.pending(k.getId()),
                k.getViewCount() + viewCounter.pendingOf(k.getId()), k.getExternalUntil(), k.getActivatedAt(), k.getBannedAt(), k.getCreatedAt());
    }

    private AdminKeyword adminView(Keyword k, boolean created) {
        return new AdminKeyword(k.getId(), k.getText(), k.getKeywordState().getName(), k.getTrendValue(), k.getHolderCount(),
                k.getExternalUntil(), created);
    }

    @Override
    public int expireExternal(String sourceName, Set<String> currentNormalized) {
        DataSource source = codes.dataSource(sourceName);
        return expireStale(source, keywords.findByDataSourceIdAndExternalUntilBefore(source.getId(), Instant.now(clock)), currentNormalized);
    }

    @Override
    public int expireExternal(String countryCode, String sourceName, Set<String> currentNormalized) {
        DataSource source = codes.dataSource(sourceName);
        return expireStale(source, keywords.findByDataSourceIdAndCountryCodeAndExternalUntilBefore(source.getId(), countryCode, Instant.now(clock)),
                currentNormalized);
    }

    private int expireStale(DataSource source, List<Keyword> stales, Set<String> currentNormalized) {
        int dropped = 0;
        for (Keyword stale : stales) {
            try {
                Boolean r = tx.execute(st -> expireOneExternal(stale.getId(), source, currentNormalized));
                if (Boolean.TRUE.equals(r)) dropped++;
            } catch (RuntimeException e) {
                log.warn("expire external keyword {} failed", stale.getId(), e);
            }
        }
        return dropped;
    }

    private boolean expireOneExternal(long keywordId, DataSource source, Set<String> currentNormalized) {
        Instant now = Instant.now(clock);
        Keyword k = keywords.findByIdForUpdate(keywordId).orElse(null);
        if (k == null || !k.getDataSource().getId().equals(source.getId())
                || k.getExternalUntil() == null || k.getExternalUntil().isAfter(now)) return false;
        if (currentNormalized.contains(k.getNormalized())) {
            k.holdExternal(source, externalHoldUntil(now)); // 유지 기간이 끝나도 아직 목록에 있으면 지금부터 다시
            return false;
        }
        long old = k.getTrendValue();
        k.dropExternal(codes.dataSource(DataSource.INTERNAL));
        // 내부 데이터만으로는 가진 사람이 3명 이상이어야 🔥(4.7)
        if (isState(k, CodeTables.KW_ACTIVE) && k.getHolderCount() < settings.getInt(SettingKeys.ACTIVATE_HOLDERS)) {
            k.deactivate(codes.keywordState(CodeTables.KW_INACTIVE), now);
        }
        long nw = updateTrend(k);
        publishTrendChange(k, old, nw, null);
        return true;
    }

    // ---------------------------------------------------------------- 조회

    @Override
    @Transactional(readOnly = true)
    public List<KeywordDtos.MyKeyword> myKeywords(long userId) {
        return myKeywordsOfPlayer(playerService.requireCurrent(userId).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeywordDtos.MyKeyword> myKeywordsOfPlayer(long playerId) {
        Instant now = Instant.now(clock);
        return playerKeywords.findActiveByPlayer(playerId, now, codes.keywordStateId(CodeTables.KW_BANNED)).stream()
                .map(pk -> {
                    Keyword k = pk.getKeyword();
                    return new KeywordDtos.MyKeyword(k.getId(), k.getText(), k.getKeywordState().getName(),
                            k.getTrendValue(), pk.getCreatedAt(), pk.getExpiresAt());
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> activeKeywordIds(long playerId) {
        return playerKeywords.findActiveByPlayer(playerId, Instant.now(clock), codes.keywordStateId(CodeTables.KW_BANNED))
                .stream().map(pk -> pk.getKeyword().getId()).toList();
    }

    // ---------------------------------------------------------------- 만료, 잃기, 재합산

    /** 만료된 + 하나를 지우고 인원·트렌드 값·점수를 고친다. 지웠으면 true. */
    @Override
    @Transactional
    public boolean expireOne(long playerKeywordId) {
        Instant now = Instant.now(clock);
        PlayerKeyword pk = playerKeywords.findById(playerKeywordId).orElse(null);
        if (pk == null) return false;
        Player p = players.findByIdForUpdate(pk.getPlayer().getId()).orElseThrow();
        Long keywordId = pk.getKeyword().getId();
        if (playerKeywords.deleteIfExpired(playerKeywordId, now) == 0) return false; // 방금 재사용된 행
        removeHolding(p, keywordId, now);
        return true;
    }

    /** 충돌로 잃기(4.3): 금지되지 않은 키워드 중 하나를 무작위로 지운다. 잃은 키워드 표시명을 돌려준다. */
    @Override
    @Transactional
    public Optional<LostKeyword> loseRandom(long playerId) {
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null || p.getHoldCount() == 0) return Optional.empty();
        Instant now = Instant.now(clock);
        List<PlayerKeyword> candidates = playerKeywords.findLosableByPlayer(playerId, now, codes.keywordStateId(CodeTables.KW_BANNED));
        if (candidates.isEmpty()) return Optional.empty();
        PlayerKeyword pk = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        String text = pk.getKeyword().getText();
        Long keywordId = pk.getKeyword().getId();
        // 잃는 순간 이 키워드가 내 유행도에 더해 주던 점수(🔥만, 💤는 0)
        long score = isState(pk.getKeyword(), CodeTables.KW_ACTIVE) ? pk.getKeyword().getTrendValue() : 0;
        playerKeywords.delete(pk);
        playerKeywords.flush();
        activity.add(p.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.COLLISION); // 사이트 통계(13.9)
        p.incLose(); // 칭호(4.10). removeHolding이 재합산하며 칭호를 검사한다
        removeHolding(p, keywordId, now);
        return Optional.of(new LostKeyword(keywordId, text, score));
    }

    @Override
    @Transactional
    public Optional<LostAll> loseAll(long playerId) {
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null || p.getHoldCount() == 0) return Optional.empty();
        Instant now = Instant.now(clock);
        List<PlayerKeyword> candidates = playerKeywords.findLosableByPlayer(playerId, now, codes.keywordStateId(CodeTables.KW_BANNED));
        if (candidates.isEmpty()) return Optional.empty();
        long score = 0;
        int activeCount = 0;
        List<Long> keywordIds = new ArrayList<>();
        for (PlayerKeyword pk : candidates) {
            if (isState(pk.getKeyword(), CodeTables.KW_ACTIVE)) { score += pk.getKeyword().getTrendValue(); activeCount++; } // 💤는 점수에 더해 주지 않았다
            keywordIds.add(pk.getKeyword().getId());
        }
        playerKeywords.deleteAll(candidates);
        playerKeywords.flush();
        p.incLose(); // 칭호(4.10): 한 번 부딪혀 잃은 것이므로 한 번으로 센다
        activity.add(p.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.COLLISION); // 사이트 통계(13.9)
        for (Long keywordId : keywordIds) removeHolding(p, keywordId, now); // 인원·트렌드 값·점수를 키워드마다 고친다
        return Optional.of(new LostAll(keywordIds.size(), activeCount, score));
    }

    @Override
    @Transactional
    public Optional<LostAll> loseFraction(long playerId, int percent) {
        if (percent >= 100) return loseAll(playerId);
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null || p.getHoldCount() == 0 || percent <= 0) return Optional.empty();
        Instant now = Instant.now(clock);
        List<PlayerKeyword> candidates = new ArrayList<>(playerKeywords.findLosableByPlayer(playerId, now, codes.keywordStateId(CodeTables.KW_BANNED)));
        if (candidates.isEmpty()) return Optional.empty();
        int count = Math.max(1, candidates.size() * percent / 100); // 내림, 최소 1개(정수 계산이라 소수점이 생기지 않는다)
        java.util.Collections.shuffle(candidates, ThreadLocalRandom.current());
        List<PlayerKeyword> lost = candidates.subList(0, Math.min(count, candidates.size()));
        long score = 0;
        int activeCount = 0;
        List<Long> keywordIds = new ArrayList<>();
        for (PlayerKeyword pk : lost) {
            if (isState(pk.getKeyword(), CodeTables.KW_ACTIVE)) { score += pk.getKeyword().getTrendValue(); activeCount++; }
            keywordIds.add(pk.getKeyword().getId());
        }
        playerKeywords.deleteAll(lost);
        playerKeywords.flush();
        p.incLose(); // 칭호(4.10): 한 번 부딪혀 잃은 것이므로 한 번으로 센다
        activity.add(p.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.COLLISION);
        for (Long keywordId : keywordIds) removeHolding(p, keywordId, now);
        return Optional.of(new LostAll(keywordIds.size(), activeCount, score));
    }

    @Override
    @Transactional
    public Optional<StolenKeyword> stealRandom(long loserId, long winnerId) {
        // 두 플레이어 행을 번호 순서로 잠근다(두 사람이 동시에 서로에게서 빼앗아도 교착하지 않게). 그다음 키워드(플레이어 → 키워드 순서)
        Player first = players.findByIdForUpdate(Math.min(loserId, winnerId)).orElse(null);
        Player second = players.findByIdForUpdate(Math.max(loserId, winnerId)).orElse(null);
        if (first == null || second == null || loserId == winnerId) return Optional.empty();
        Player loser = first.getId() == loserId ? first : second, winner = loser == first ? second : first;
        if (loser.getHoldCount() == 0 || !loser.getCountry().getId().equals(winner.getCountry().getId())) return Optional.empty();
        Instant now = Instant.now(clock);
        List<PlayerKeyword> candidates = playerKeywords.findLosableByPlayer(loserId, now, codes.keywordStateId(CodeTables.KW_BANNED));
        if (candidates.isEmpty()) return Optional.empty();
        PlayerKeyword pk = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        String text = pk.getKeyword().getText();
        Long keywordId = pk.getKeyword().getId();
        long score = isState(pk.getKeyword(), CodeTables.KW_ACTIVE) ? pk.getKeyword().getTrendValue() : 0;
        playerKeywords.delete(pk);
        playerKeywords.flush();
        loser.incLose(); // 칭호(4.10)
        activity.add(loser.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.COLLISION); // 사이트 통계(13.9)
        playerService.awardAttack(winner); // 빼앗은 횟수 +1과 공격 성공 EXP(4.3). 점수·레벨을 다시 정하고 칭호를 확인한다
        removeHolding(loser, keywordId, now);

        Keyword k = keywords.findByIdForUpdate(keywordId).orElseThrow();
        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(winnerId, keywordId);
        if (existing.isPresent() && !existing.get().isExpired(now)) {
            // 이미 가진 키워드: 남은 시간만 지금부터 다시 24시간. 점수·EXP는 그대로
            return Optional.of(new StolenKeyword(text, score, "RENEWED", 0, renewHolding(existing.get(), now)));
        }
        // 없던 키워드: 일반 추가와 같이 보관함에 넣는다(24시간, EXP +1, 가진 사람 수 +1, 점수 반영). 구슬(4.20)과 같은 방식
        KeywordDtos.PlusResult r = addHolding(winner, k, existing, now);
        return Optional.of(new StolenKeyword(text, score, "ADDED", r.gained(), r.keyword().myExpiresAt()));
    }

    /**
     * 금지가 커밋되면 모든 플레이어의 보관함에서 그 키워드와 금지어가 들어 있는 키워드를 바로 지운다(4.7).
     * 금지 트랜잭션이 끝난 뒤에, 보관함 행마다 따로 새 트랜잭션으로 한다(플레이어 → 키워드 잠금 순서를 지키고, 한 건이 실패해도 나머지는 계속한다).
     * 접속 중인 사람의 보관함·점수는 `HoldingChanged`로 바로 고쳐진다.
     */
    @org.springframework.transaction.event.TransactionalEventListener
    public void onBanned(com.trendist.keyword.support.KeywordPageEvents.Banned e) { purgeBanned(e.countryCode(), e.normalized()); }

    int purgeBanned(String countryCode, String normalized) {
        TransactionTemplate fresh = new TransactionTemplate(tx.getTransactionManager());
        fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Long> ids = fresh.execute(st -> playerKeywords.findIdsHoldingBanned(countryCode, normalized, normalized.length() >= BannedWordFilter.MIN_WORD_LENGTH));
        int removed = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            try {
                Boolean r = fresh.execute(st -> {
                    PlayerKeyword pk = playerKeywords.findById(id).orElse(null);
                    if (pk == null) return false;
                    Player p = players.findByIdForUpdate(pk.getPlayer().getId()).orElseThrow(); // 잠금 순서: 플레이어 → 키워드
                    Long keywordId = pk.getKeyword().getId();
                    playerKeywords.delete(pk);
                    playerKeywords.flush();
                    removeHolding(p, keywordId, Instant.now(clock));
                    return true;
                });
                if (Boolean.TRUE.equals(r)) removed++;
            } catch (RuntimeException ex) {
                log.warn("purge banned holding {} failed", id, ex);
            }
        }
        return removed;
    }

    private void removeHolding(Player p, Long keywordId, Instant now) {
        Keyword k = keywords.findByIdForUpdate(keywordId).orElseThrow();
        long oldTrend = k.getTrendValue();
        k.decHolder();
        p.decHold();
        // 외부 데이터가 없고 가진 사람이 3명 미만이면 💤(4.7)
        if (isState(k, CodeTables.KW_ACTIVE) && k.getDataSource().isInternal()
                && k.getHolderCount() < settings.getInt(SettingKeys.ACTIVATE_HOLDERS)) {
            k.deactivate(codes.keywordState(CodeTables.KW_INACTIVE), now);
        }
        long newTrend = updateTrend(k);
        keywords.flush();
        playerService.recompute(p);
        publishTrendChange(k, oldTrend, newTrend, p.getId());
        events.publishEvent(new GameEvents.HoldingChanged(p.getCountry().getId(), p.getId(), keywordId, false));
    }

    @Override
    public int refreshTrendValues() {
        population.refresh();
        Long activeId = codes.keywordStateId(CodeTables.KW_ACTIVE);
        int changed = 0;
        for (Keyword k : keywords.findAllByStateWithSource(activeId)) {
            long v = formula.trendValue(true, k.getHolderCount(), k.getDataSource().getWeight().doubleValue(),
                    population.get(k.getCountry().getId()));
            if (v == k.getTrendValue()) continue;
            try {
                // 그 사이 +·삭제로 바뀌었을 수 있으니 행을 잠그고 다시 계산한다. 키워드 행만 잠그므로 플레이어 → 키워드 잠금 순서와 부딪히지 않는다
                Boolean r = tx.execute(st -> keywords.findByIdForUpdate(k.getId()).map(locked -> {
                    long old = locked.getTrendValue();
                    long nw = updateTrend(locked);
                    publishTrendChange(locked, old, nw, null);
                    return old != nw;
                }).orElse(false));
                if (Boolean.TRUE.equals(r)) changed++;
            } catch (RuntimeException e) {
                log.warn("trend refresh {} failed", k.getId(), e);
            }
        }
        return changed;
    }

    /** 1분 잡: 트렌드 값이 바뀐 키워드의 보유자 점수를 DB 기준으로 다시 합산한다. */
    @Override
    public Set<Long> drainDirtyKeywords() {
        Set<Long> out = new HashSet<>(dirtyKeywords);
        dirtyKeywords.removeAll(out);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> holderPlayerIds(long keywordId) {
        return playerKeywords.findHolderPlayerIds(keywordId, Instant.now(clock));
    }

    // ---------------------------------------------------------------- 내부

    private record Confirmed(DataSource source, double heat) {}

    /** 그 나라의 외부 소스 중 관심도가 기준 이상인 것, 여럿이면 가중치가 가장 큰 것(10.3). */
    private Optional<Confirmed> externalConfirm(String countryCode, String normalized) {
        double threshold = settings.getDouble(SettingKeys.EXTERNAL_CONFIRM_HEAT);
        Confirmed best = null;
        for (ExternalTrendSource s : sources) {
            if (!s.supports(countryCode)) continue;
            OptionalDouble h = s.heat(countryCode, normalized);
            if (h.isEmpty() || h.getAsDouble() < threshold) continue;
            DataSource ds = codes.dataSource(s.sourceName());
            if (ds.getWeight().signum() == 0) continue; // 가중치 0이면 쓰지 않는다
            if (best == null || ds.getWeight().compareTo(best.source().getWeight()) > 0) best = new Confirmed(ds, h.getAsDouble());
        }
        return Optional.ofNullable(best);
    }

    private void tryExternalActivate(Keyword k, Instant now) {
        externalConfirm(k.getCountry().getCode(), k.getNormalized()).ifPresent(c -> {
            long old = k.getTrendValue();
            if (isListSource(c.source())) k.holdExternal(c.source(), externalHoldUntil(now));
            else k.setDataSource(c.source());
            k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now);
            long nw = updateTrend(k);
            publishTrendChange(k, old, nw, null);
        });
    }

    private Keyword createKeyword(Country country, String text, String normalized, Optional<Confirmed> ext, Instant now) {
        DataSource source = ext.map(Confirmed::source).orElseGet(() -> codes.dataSource(DataSource.INTERNAL));
        KeywordState state = codes.keywordState(ext.isPresent() ? CodeTables.KW_ACTIVE : CodeTables.KW_INACTIVE);
        Keyword k = new Keyword(country, source, state, text, normalized);
        if (ext.isPresent()) {
            k.activate(state, now);
            if (isListSource(source)) k.holdExternal(source, externalHoldUntil(now));
        }
        try {
            k = keywords.saveAndFlush(k);
        } catch (DataIntegrityViolationException e) {
            // 같은 단어를 동시에 만들었다. 이 트랜잭션은 되돌리고 클라이언트가 다시 시도한다(다음 시도는 기존 행을 쓴다)
            throw ApiException.conflict("RETRY", "잠시 후 다시 시도해 주세요");
        }
        updateTrend(k);
        return k;
    }

    private long updateTrend(Keyword k) {
        long v = formula.trendValue(isState(k, CodeTables.KW_ACTIVE), k.getHolderCount(), k.getDataSource().getWeight().doubleValue(),
                population.get(k.getCountry().getId()));
        k.setTrendValue(v);
        return v;
    }

    private void publishTrendChange(Keyword k, long oldTrend, long newTrend, Long excludePlayerId) {
        if (oldTrend == newTrend) return;
        dirtyKeywords.add(k.getId());
        events.publishEvent(new GameEvents.KeywordTrendChanged(k.getCountry().getId(), k.getId(),
                newTrend - oldTrend, excludePlayerId));
    }

    @Override
    @Transactional(readOnly = true)
    public KeywordDtos.KeywordCard detail(long userId, long keywordId, String ip, String userAgent) {
        Player p = playerService.requireCurrent(userId);
        Keyword k = keywords.findWithAiById(keywordId)
                .filter(x -> x.getCountry().getId().equals(p.getCountry().getId()) && !isBanned(x) && !bannedWords.contains(p.getCountry().getCode(), x.getNormalized()))
                .orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        viewCounter.hit(k.getId(), ip, userAgent); // 볼 수 있는 키워드일 때만 센다(없거나 금지면 위에서 이미 404)
        return card(k, p, Instant.now(clock));
    }

    private KeywordDtos.KeywordCard card(Keyword k, Player p, Instant now) {
        Optional<PlayerKeyword> mine = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId()).filter(pk -> !pk.isExpired(now));
        String ai = k.getAiComment() == null ? null : k.getAiComment().getComment();
        return new KeywordDtos.KeywordCard(k.getId(), k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                k.getHolderCount(), k.getAddCount(),
                k.getSearchCount() + searchCounter.pending(k.getId()),
                k.getViewCount() + viewCounter.pendingOf(k.getId()), mine.isPresent(),
                mine.map(PlayerKeyword::getExpiresAt).orElse(null),
                ai, ai != null && aiRequests.existsByKeywordIdAndPlayerIdAndHandledAtIsNull(k.getId(), p.getId()));
    }

    /** 목록형 외부 소스 유지 끝 시각: 지금 + 설정 시간(기본 168시간 = 7일). */
    private Instant externalHoldUntil(Instant now) {
        return now.plus(Duration.ofHours(settings.getInt(SettingKeys.EXTERNAL_HOLD_HOURS)));
    }

    /** 유지 기간(기본 7일)을 두고 끝나면 다시 보는 소스: 목록에 오른 동안만 확인되는 google, 관리자가 넣은 admin. */
    private boolean isListSource(DataSource s) { return DataSource.GOOGLE.equals(s.getName()) || DataSource.ADMIN.equals(s.getName()); }

    private Country countryOf(String code) {
        return countries.requireOpen(code);
    }

    private boolean isBanned(Keyword k) { return isState(k, CodeTables.KW_BANNED); }

    private boolean isState(Keyword k, String name) {
        return k.getKeywordState().getId().equals(codes.keywordStateId(name));
    }
}
