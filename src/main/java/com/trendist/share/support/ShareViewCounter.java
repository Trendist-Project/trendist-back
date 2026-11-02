package com.trendist.share.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.share.repository.ShareCardRepository;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자랑하기 카드 조회·클릭 수(4.24). 요청마다 DB에 쓰지 않고 메모리에 모았다가 1분마다 카드별 UPDATE 한 번으로 더한다.
 * 같은 방문자(IP + User-Agent)는 카드마다 하루(한국 시간) 1번만 센다. 방문자 값은 해시로만, 메모리에만 둔다(저장하지 않음).
 * 메신저·검색 크롤러는 세지 않는다. 서버가 갑자기 꺼지면 저장 전 최대 1분치가 사라진다(정상 종료 때는 저장한다).
 */
@Slf4j
@Component
public class ShareViewCounter {
    public enum Kind { VIEW, PLAY }

    /**
     * 링크 미리보기를 만드는 크롤러. 카카오톡 앱 안 브라우저(UA에 "KAKAOTALK")는 사람이라 세고, 미리보기 수집기("kakaotalk-scrap")만 뺀다.
     * 그 밖의 봇·자동화 도구도 "…bot/"·crawler·spider 등이 들어가면 뺀다("bot" 한 단어는 CUBOT 같은 휴대폰 이름과 겹쳐 쓰지 않는다).
     */
    private static final Pattern CRAWLER = Pattern.compile(
            "kakaotalk-scrap|facebookexternalhit|facebot|twitterbot|discordbot|slackbot|slack-imgproxy|telegrambot|whatsapp"
                    + "|linkedinbot|pinterest|skypeuripreview|embedly|yeti|daum(oa|-?bot)|googlebot|bingbot|applebot|duckduckbot"
                    + "|bot/|crawler|spider|preview|headless|curl/|wget/|python-|java/|okhttp|go-http-client",
            Pattern.CASE_INSENSITIVE);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ShareCardRepository cards;
    private final TransactionTemplate tx;
    private final Clock clock;
    /** 하루 1번 중복 제거: (방문자, 카드, 종류, 날짜)의 해시. 하루가 지나면 날짜가 바뀌어 다시 센다 */
    private final Cache<String, Boolean> seen = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(25)).maximumSize(500_000).build();
    /** 아직 저장하지 않은 증가분: 카드 id → [조회, 클릭] */
    private final Map<Long, AtomicLongArray> pending = new ConcurrentHashMap<>();

    public ShareViewCounter(ShareCardRepository cards, TransactionTemplate tx, Clock clock) {
        this.cards = cards; this.tx = tx; this.clock = clock;
    }

    public static boolean isCrawler(String userAgent) {
        return userAgent == null || userAgent.isBlank() || CRAWLER.matcher(userAgent).find();
    }

    /** 세었으면 true(크롤러이거나 오늘 이미 센 방문자면 false) */
    public boolean hit(long cardId, Kind kind, String ip, String userAgent) {
        if (isCrawler(userAgent)) return false;
        String key = sha256(ip + '|' + userAgent + '|' + cardId + '|' + kind + '|' + LocalDate.now(clock.withZone(KST)));
        if (seen.asMap().putIfAbsent(key, Boolean.TRUE) != null) return false;
        pending.computeIfAbsent(cardId, k -> new AtomicLongArray(2)).incrementAndGet(kind.ordinal());
        return true;
    }

    /** 아직 저장하지 않은 증가분 [조회, 클릭]. 관리자·내 카드 화면이 저장 전 값도 더해 보여 주려고 쓴다 */
    public long[] pendingOf(long cardId) {
        AtomicLongArray a = pending.get(cardId);
        return a == null ? new long[]{0, 0} : new long[]{a.get(0), a.get(1)};
    }

    public long[] pendingTotal() {
        long v = 0, p = 0;
        for (AtomicLongArray a : pending.values()) { v += a.get(0); p += a.get(1); }
        return new long[]{v, p};
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flush() {
        if (pending.isEmpty()) return;
        Instant now = Instant.now(clock);
        for (Long id : pending.keySet()) {
            AtomicLongArray a = pending.remove(id);
            if (a == null) continue;
            long v = a.get(0), p = a.get(1);
            if (v == 0 && p == 0) continue;
            try {
                tx.executeWithoutResult(s -> cards.addCounts(id, v, p, now));
            } catch (RuntimeException e) {
                // 저장에 실패하면 다음 주기에 다시 더한다
                pending.computeIfAbsent(id, k -> new AtomicLongArray(2)).addAndGet(0, v);
                pending.get(id).addAndGet(1, p);
                log.warn("share card count flush failed id={}", id, e);
            }
        }
    }

    @PreDestroy
    public void flushOnShutdown() { flush(); }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
