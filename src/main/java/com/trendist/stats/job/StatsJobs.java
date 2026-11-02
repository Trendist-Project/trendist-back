package com.trendist.stats.job;

import com.trendist.stats.service.StatsService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 관리자 통계(13.9): 10초마다 접속 인원 표본, 매시 정각 시간별·하루별 행 기록. */
@Component
@RequiredArgsConstructor
public class StatsJobs {
    private static final Logger log = LoggerFactory.getLogger(StatsJobs.class);
    private final StatsService stats;

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void sample() {
        try { stats.sample(); } catch (RuntimeException e) { log.warn("stats sample failed", e); }
    }

    @Scheduled(cron = "5 0 * * * *", zone = "UTC")
    public void closeHour() {
        try { stats.closeHour(); } catch (RuntimeException e) { log.warn("stats hour failed", e); }
    }

    @PreDestroy
    public void flush() {
        try { stats.flushCurrentHour(); } catch (RuntimeException e) { log.warn("stats flush failed", e); }
    }
}
