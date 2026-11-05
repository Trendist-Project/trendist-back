package com.trendist.keyword.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.share.support.ShareViewCounter;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 키워드 조회수(4.28): 공개 키워드 페이지와 게임 안 상세 팝업을 연 횟수. 요청마다 DB에 쓰지 않고 메모리에 모았다가 1분마다 키워드별 UPDATE 한 번으로 더한다.
 * 같은 방문자(IP + User-Agent)는 키워드마다 하루(한국 시간) 1번만 센다. 방문자 값은 해시로만, 메모리에만 둔다.
 * 검색엔진·메신저 크롤러는 세지 않는다(판별은 자랑하기 카드와 같은 {@link ShareViewCounter#isCrawler}).
 * 서버가 갑자기 꺼지면 저장 전 최대 1분치가 사라진다(정상 종료 때는 저장한다).
 */
@Slf4j
@Component
public class KeywordViewCounter {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KeywordRepository keywords;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Cache<String, Boolean> seen = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(25)).maximumSize(500_000).build();
    /** 아직 저장하지 않은 증가분: 키워드 번호 → 수 */
    private final Map<Long, AtomicLong> pending = new ConcurrentHashMap<>();

    public KeywordViewCounter(KeywordRepository keywords, TransactionTemplate tx, Clock clock) {
        this.keywords = keywords; this.tx = tx; this.clock = clock;
    }

    /** 세었으면 true(크롤러이거나 오늘 이미 센 방문자면 false) */
    public boolean hit(long keywordId, String ip, String userAgent) {
        if (ShareViewCounter.isCrawler(userAgent)) return false;
        String key = sha256(ip + '|' + userAgent + '|' + keywordId + '|' + LocalDate.now(clock.withZone(KST)));
        if (seen.asMap().putIfAbsent(key, Boolean.TRUE) != null) return false;
        pending.computeIfAbsent(keywordId, k -> new AtomicLong()).incrementAndGet();
        return true;
    }

    /** 아직 저장하지 않은 증가분. 페이지가 저장 전 값도 더해 보여 주려고 쓴다 */
    public long pendingOf(long keywordId) {
        AtomicLong a = pending.get(keywordId);
        return a == null ? 0 : a.get();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flush() {
        if (pending.isEmpty()) return;
        for (Long id : pending.keySet()) {
            AtomicLong a = pending.remove(id);
            if (a == null) continue;
            long n = a.get();
            if (n == 0) continue;
            try {
                tx.executeWithoutResult(s -> keywords.addViewCount(id, n));
            } catch (RuntimeException e) {
                // 저장에 실패하면 다음 주기에 다시 더한다
                pending.computeIfAbsent(id, k -> new AtomicLong()).addAndGet(n);
                log.warn("keyword view count flush failed id={}", id, e);
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
