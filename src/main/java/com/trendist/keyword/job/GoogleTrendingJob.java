package com.trendist.keyword.job;

import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
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

    private final GoogleTrendingSource source;
    private final KeywordService keywordService;
    private final CountryRepository countries;

    @Scheduled(fixedDelay = 300_000, initialDelay = 5_000)
    public void run() {
        // 열려 있고 이 소스가 지원하는 나라마다 따로 읽는다(KR=geo KR, GLOBAL=geo US). 한 나라가 실패해도 다른 나라는 계속한다
        for (Country c : countries.findByEnabledTrue()) {
            if (!source.supports(c.getCode())) continue;
            String code = c.getCode();
            try {
                List<String> titles = source.refresh(code);
                int added = keywordService.applyExternalTitles(code, DataSource.GOOGLE, titles);
                log.info("google trending [{}]: {} titles, {} newly held", code, titles.size(), added);
            } catch (Exception e) {
                log.warn("google trending [{}] fetch failed: {}", code, e.toString());
            }
            // 유지 기간(기본 7일)이 끝난 키워드를 정리한다. 마지막으로 읽은 목록을 기준으로 하고, 읽기 실패 여부는 따지지 않는다(10.2)
            try {
                int dropped = keywordService.expireExternal(code, DataSource.GOOGLE, source.lastTitles(code));
                if (dropped > 0) log.info("google trending [{}]: {} keywords moved to internal", code, dropped);
            } catch (RuntimeException e) {
                log.warn("google trending [{}] expire failed", code, e);
            }
        }
    }
}
