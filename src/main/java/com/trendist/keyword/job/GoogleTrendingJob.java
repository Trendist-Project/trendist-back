package com.trendist.keyword.job;

import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.service.KeywordService;
import com.trendist.keyword.source.GoogleTrendingSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 5분마다 구글 실시간 인기 목록을 읽어 키워드로 넣고(🔥, 반영 소스 google, 목록에 보일 때마다 지금부터 7일 유지),
 * 유지 기간이 끝난 google 키워드를 정리한다(10.2).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "trendist.google-trends", name = "enabled", havingValue = "true")
public class GoogleTrendingJob {
    private static final Logger log = LoggerFactory.getLogger(GoogleTrendingJob.class);
    private static final String COUNTRY = "KR";

    private final GoogleTrendingSource source;
    private final KeywordService keywordService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 5_000)
    public void run() {
        try {
            List<String> titles = source.refresh(COUNTRY);
            int added = keywordService.applyExternalTitles(COUNTRY, DataSource.GOOGLE, titles);
            log.info("google trending: {} titles, {} newly held", titles.size(), added);
        } catch (Exception e) {
            log.warn("google trending fetch failed: {}", e.toString());
        }
        // 유지 기간(기본 7일)이 끝난 키워드를 정리한다. 마지막으로 읽은 목록을 기준으로 하고, 읽기 실패 여부는 따지지 않는다(10.2)
        try {
            int dropped = keywordService.expireExternal(DataSource.GOOGLE, source.lastTitles());
            if (dropped > 0) log.info("google trending: {} keywords moved to internal", dropped);
        } catch (RuntimeException e) {
            log.warn("google trending expire failed", e);
        }
    }
}
