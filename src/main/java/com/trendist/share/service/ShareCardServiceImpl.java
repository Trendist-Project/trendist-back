package com.trendist.share.service;

import com.trendist.profile.dto.ProfileDtos;
import com.trendist.profile.service.ProfileService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.config.AppProperties;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.service.KeywordService;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import com.trendist.player.service.PlayerService;
import com.trendist.player.service.ScoreHistoryService;
import com.trendist.ranking.dto.RankingEntry;
import com.trendist.ranking.service.RankingService;
import com.trendist.share.dto.ShareDtos;
import com.trendist.share.entity.ShareCard;
import com.trendist.share.repository.ShareCardRepository;
import com.trendist.share.support.ShareCardRenderer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ShareCardServiceImpl implements ShareCardService {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 10; // 62^10 ≈ 8×10^17이라 추측으로 남의 카드를 찾을 수 없다
    private static final int CREATE_LIMIT = 10; // 분당(9.5)

    private final ShareCardRepository cards;
    private final com.trendist.player.repository.PlayerRepository playerRepo;
    private final PlayerService players;
    private final ScoreHistoryService scoreHistory;
    private final RankingService rankings;
    private final KeywordService keywords;
    private final RateLimiter limiter;
    private final ShareCardRenderer renderer;
    private final com.trendist.share.support.ShareViewCounter counter;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.trendist.stats.support.ActivityCounter activity;
    private final ProfileService profiles;
    private final String baseUrl;
    private final String site;
    private final SecureRandom random = new SecureRandom();
    /** 그린 PNG. 같은 코드의 그림은 바뀌지 않으므로 만료 없이 크기(최대 64MB)로만 내보낸다 */
    private final Cache<String, byte[]> images = Caffeine.newBuilder()
            .maximumWeight(64L * 1024 * 1024).weigher((String k, byte[] v) -> v.length).build();
    /** 공유 페이지가 이미지와 같은 값을 다시 읽지 않게. 코드 → (카드 id, 값) */
    private final Cache<String, Stored> data = Caffeine.newBuilder().maximumSize(10_000).build();

    private record Stored(long id, ShareDtos.CardData card) {}

    public ShareCardServiceImpl(ShareCardRepository cards, com.trendist.player.repository.PlayerRepository playerRepo, PlayerService players, ScoreHistoryService scoreHistory,
                                RankingService rankings, KeywordService keywords, RateLimiter limiter,
                                ShareCardRenderer renderer, com.trendist.share.support.ShareViewCounter counter, ObjectMapper json, Clock clock, AppProperties props,
                                com.trendist.stats.support.ActivityCounter activity, ProfileService profiles) {
        this.cards = cards; this.playerRepo = playerRepo; this.players = players; this.scoreHistory = scoreHistory; this.rankings = rankings;
        this.keywords = keywords; this.limiter = limiter; this.renderer = renderer; this.counter = counter; this.json = json; this.clock = clock;
        this.activity = activity;
        this.profiles = profiles;
        this.baseUrl = props.share() == null ? new AppProperties.Share(null).baseUrl() : props.share().baseUrl();
        String host = URI.create(baseUrl).getHost();
        int port = URI.create(baseUrl).getPort();
        this.site = host == null ? "trendist.kr" : port > 0 ? host + ":" + port : host;
    }

    @Override
    public String baseUrl() { return baseUrl; }

    @Override
    @Transactional
    public ShareDtos.Created create(long userId) {
        if (!limiter.tryAcquire("share:" + userId, CREATE_LIMIT, Duration.ofMinutes(1)))
            throw ApiException.tooMany("SHARE_TOO_FAST", "잠시 후 다시 만들어 주세요");
        Player p = players.requireCurrent(userId);
        ShareDtos.CardData d = collect(userId, p);
        String body = json.writeValueAsString(d);
        String hash = sha256(body);
        // 같은 카드를 동시에 두 번 만들면(더블 클릭, 두 탭, 개발 모드의 effect 두 번 실행) 둘 다 "없음"으로 보고 넣다가
        // 늦은 쪽이 고유 키에 걸린다. 플레이어 행을 잠가 한 사람의 만들기를 한 줄로 세운다(잠금 순서: 플레이어 먼저, 9.4)
        playerRepo.findByIdForUpdate(p.getId());
        ShareCard card = cards.findByPlayerIdAndContentHashForUpdate(p.getId(), hash).orElseGet(() -> insert(p, hash, body));
        return created(card.getCode(), userId);
    }

    private ShareCard insert(Player p, String hash, String body) {
        // 코드가 겹칠 확률은 거의 없지만 겹치면 다시 뽑는다
        String code;
        do code = newCode(); while (cards.findByCode(code).isPresent());
        activity.add(p.getCountry().getId(), com.trendist.stats.support.ActivityCounter.Kind.SHARE); // 사이트 통계(13.9)
        return cards.save(new ShareCard(p, code, hash, body, Instant.now(clock)));
    }

    /** 카드 값은 서버가 지금 DB·방 값으로 정한다. 클라이언트가 보낸 값은 쓰지 않는다(4.24) */
    private ShareDtos.CardData collect(long userId, Player p) {
        PlayerService.JoinInfo info = players.joinInfo(userId).orElseThrow();
        PlayerDtos.PlayerSummary s = players.summary(p);
        RankingEntry mine = safe(() -> rankings.top(s.countryCode()), List.<RankingEntry>of()).stream()
                .filter(r -> r.playerId() == p.getId()).findFirst().orElse(null);
        List<Long> week = safe(() -> scoreHistory.weekOf(p.getId()).points().stream().map(PlayerDtos.ScorePoint::score).toList(), List.of());
        // 💤 키워드는 "곧 뜰 것"을 알려 줄 수 있어 넣지 않는다(3.3)
        List<String> hot = keywords.myKeywords(userId).stream()
                .filter(k -> CodeTables.KW_ACTIVE.equals(k.state()))
                .sorted(Comparator.comparingLong(KeywordDtos.MyKeyword::trendValue).reversed())
                .limit(3).map(KeywordDtos.MyKeyword::text).toList();
        List<ShareDtos.Record> records = List.of(
                new ShareDtos.Record("누적 추가", String.format("%,d회", s.totalPlusCount())),
                new ShareDtos.Record("빼앗기", String.format("%,d회", s.stealCount())),
                new ShareDtos.Record("봇 사냥", String.format("%,d회", s.botWinCount())),
                new ShareDtos.Record("활동한 날", String.format("%,d일", s.activeDays())));
        long score = mine != null ? mine.score() : s.currentScore(); // 접속 중이면 방의 실시간 값
        Optional<ProfileDtos.Art> art = profiles.equippedArt(p.getId()); // 픽셀 그림(4.9)
        return new ShareDtos.CardData(info.nickname(), info.level(), info.title(), score, mine == null ? null : mine.rank(),
                week, hot, records, info.skin().background(), info.skin().borderColor(), info.skin().borderWidth(), info.trail(),
                art.map(a -> String.join(",", a.palette())).orElse(null), art.map(ProfileDtos.Art::pixels).orElse(null));
    }

    /** 순위·점수 추이는 카드의 한 칸이라 실패해도 그 칸만 비운다(닫힌 나라 등) */
    private static <T> T safe(java.util.function.Supplier<T> f, T fallback) {
        try {
            return f.get();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @Override
    public Optional<ShareDtos.CardData> find(String code) {
        return stored(code).map(Stored::card);
    }

    private Optional<Stored> stored(String code) {
        if (!validCode(code)) return Optional.empty();
        Stored hit = data.getIfPresent(code);
        if (hit != null) return Optional.of(hit);
        Optional<Stored> d = cards.findByCode(code).map(c -> new Stored(c.getId(), parse(c.getData())));
        d.ifPresent(v -> data.put(code, v));
        return d;
    }

    @Override
    public void count(String code, com.trendist.share.support.ShareViewCounter.Kind kind, String ip, String userAgent) {
        stored(code).ifPresent(st -> counter.hit(st.id(), kind, ip, userAgent));
    }

    @Override
    @Transactional(readOnly = true)
    public ShareDtos.AdminStats adminStats(String sort, int page) {
        int size = 30;
        var order = switch (sort == null ? "" : sort) {
            case "plays" -> org.springframework.data.domain.Sort.by("playCount").descending();
            case "createdAt" -> org.springframework.data.domain.Sort.by("createdAt").descending();
            default -> org.springframework.data.domain.Sort.by("viewCount").descending();
        };
        var pg = cards.findPage(org.springframework.data.domain.PageRequest.of(Math.max(0, page), size,
                order.and(org.springframework.data.domain.Sort.by("id").descending())));
        Object[] t = cards.totals().getFirst();
        long[] pend = counter.pendingTotal();
        Instant todayKst = java.time.LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul")))
                .atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        var items = pg.getContent().stream().map(c -> {
            long[] p = counter.pendingOf(c.getId());
            return new ShareDtos.AdminItem(c.getCode(), baseUrl + "/share/" + c.getCode(), parse(c.getData()).nickname(),
                    c.getViewCount() + p[0], c.getPlayCount() + p[1], c.getCreatedAt());
        }).toList();
        return new ShareDtos.AdminStats(((Number) t[0]).longValue(), cards.countByCreatedAtGreaterThanEqual(todayKst),
                ((Number) t[1]).longValue() + pend[0], ((Number) t[2]).longValue() + pend[1], items, pg.getTotalElements(), page, size);
    }

    @Override
    public Optional<byte[]> image(String code) {
        byte[] hit = images.getIfPresent(code);
        if (hit != null) return Optional.of(hit);
        return find(code).map(d -> images.get(code, k -> renderer.png(d, site)));
    }

    /** MySQL·MariaDB는 JSON 열을 글자 그대로 돌려주고, 테스트 H2는 글자를 JSON 문자열로 감싸 돌려준다. 둘 다 읽는다 */
    private ShareDtos.CardData parse(String raw) {
        var node = json.readTree(raw);
        return json.treeToValue(node.isString() ? json.readTree(node.asString()) : node, ShareDtos.CardData.class);
    }

    private ShareDtos.Created created(String code, long userId) {
        String url = baseUrl + "/share/" + code;
        Object[] t = cards.totalsOfUser(userId).getFirst();
        long views = ((Number) t[0]).longValue(), plays = ((Number) t[1]).longValue();
        for (long id : cards.idsOfUser(userId)) { long[] p = counter.pendingOf(id); views += p[0]; plays += p[1]; }
        return new ShareDtos.Created(code, url, url + "/card.png", views, plays);
    }

    private String newCode() {
        StringBuilder b = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return b.toString();
    }

    private static boolean validCode(String code) {
        return code != null && code.length() == CODE_LENGTH && code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
