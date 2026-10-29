package com.trendist.keyword.job;

import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.keyword.service.KeywordActivityService;
import com.trendist.keyword.service.KeywordService;
import com.trendist.keyword.support.SearchCounter;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.player.service.ScoreHistoryService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 배치 잡(9.7). 서버 1대 기준이라 ShedLock은 아직 붙이지 않았다. */
@Component
@RequiredArgsConstructor
public class KeywordJobs {
    private static final Logger log = LoggerFactory.getLogger(KeywordJobs.class);

    private final KeywordService keywordService;
    private final KeywordActivityService activity;
    private final PlayerService playerService;
    private final ScoreHistoryService scoreHistory;
    private final PlayerKeywordRepository playerKeywords;
    private final PlayerRepository players;
    private final KeywordRepository keywords;
    private final SearchCounter searchCounter;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** 1분: 만료된 + 삭제, 트렌드 값이 바뀐 키워드의 보유자 점수 재합산. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void everyMinute() {
        // 관리자가 넣은 키워드(13.7)는 유지 기간(7일)이 끝나면 internal로 내려간다. 다시 확인해 줄 목록이 없으므로 빈 목록으로 정리한다
        try {
            int dropped = keywordService.expireExternal(DataSource.ADMIN, Set.of());
            if (dropped > 0) log.info("admin keywords: {} moved to internal", dropped);
        } catch (RuntimeException e) { log.warn("admin keyword expire failed", e); }
        int expired = 0;
        List<Long> ids;
        do {
            ids = playerKeywords.findExpiredIds(Instant.now(clock), PageRequest.of(0, 500));
            for (Long id : ids) {
                try { if (keywordService.expireOne(id)) expired++; } catch (RuntimeException e) { log.warn("expire {} failed", id, e); }
            }
        } while (ids.size() == 500);

        Set<Long> playerIds = new HashSet<>();
        for (Long keywordId : keywordService.drainDirtyKeywords()) playerIds.addAll(keywordService.holderPlayerIds(keywordId));
        for (Long pid : playerIds) {
            try { playerService.recomputeLocked(pid, false); } catch (RuntimeException e) { log.warn("recompute {} failed", pid, e); }
        }
        if (expired > 0 || !playerIds.isEmpty()) log.info("minute job: expired={}, recomputed={}", expired, playerIds.size());
    }

    /**
     * 매시 정각: 검색 카운터를 DB로 옮기고, 끝난 시간의 활동 기록을 남기고(9.9), 키워드가 있는 플레이어 전원을 점검한 뒤,
     * 점검으로 맞춘 점수로 플레이어 점수 기록을 남긴다(4.15).
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void hourly() {
        Instant hourStart = Instant.now(clock).truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS);
        Map<Long, Long> counts = searchCounter.drain();
        tx.executeWithoutResult(s -> counts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> keywords.addSearchCount(e.getKey(), e.getValue())));
        try { activity.recordHour(hourStart, counts); } catch (RuntimeException e) { log.warn("activity log {} failed", hourStart, e); }
        for (Long pid : players.findCheckTargets()) {
            try { playerService.recomputeLocked(pid, true); } catch (RuntimeException e) { log.warn("check {} failed", pid, e); }
        }
        try { scoreHistory.recordHour(hourStart); } catch (RuntimeException e) { log.warn("score log {} failed", hourStart, e); }
    }
}
