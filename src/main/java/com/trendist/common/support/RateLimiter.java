package com.trendist.common.support;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 요청 빈도 제한(9.5): 키마다 최근 통과한 요청 시각을 기억하는 슬라이딩 윈도 로그. 통과한 요청만 기록한다.
 * 서버 1대 기준 메모리라 재시작하면 비워진다. 여러 대가 되면 Redis로 옮긴다.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {
    private final ConcurrentHashMap<String, Deque<Long>> logs = new ConcurrentHashMap<>();
    private final Clock clock;

    /** window 안에 limit번보다 적게 통과했으면 기록하고 true. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        Deque<Long> log = logs.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            while (!log.isEmpty() && log.peekFirst() <= now - window.toMillis()) log.pollFirst();
            if (log.size() >= limit) return false;
            log.addLast(now);
            return true;
        }
    }

    /** 오래 쓰지 않은 키를 지운다(가장 긴 창 1시간 기준). */
    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        long cutoff = clock.millis() - Duration.ofHours(1).toMillis();
        logs.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff; }
        });
    }
}
