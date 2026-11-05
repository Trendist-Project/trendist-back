package com.trendist.keyword.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.common.code.CodeTables;
import com.trendist.config.AppProperties;
import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.support.KeywordPage;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트맵 `/sitemap.xml`(4.28): 메인 주소와 금지가 아닌 모든 키워드 페이지를 한 파일로 만든다.
 * 키워드가 생길 때마다 파일을 고치는 대신 요청이 오면 DB에서 바로 만들고 5분 캐시한다.
 * 그래서 새 키워드는 최대 5분 안에 들어가고, 금지된 키워드는 5분 안에 빠진다.
 * 사이트맵 한 파일의 상한(5만 주소)을 넘으면 가장 최근에 만든 키워드부터 {@value #MAX_URLS}개만 담는다.
 */
@Service
public class SitemapService {
    private static final Logger log = LoggerFactory.getLogger(SitemapService.class);
    /** 사이트맵 한 파일은 5만 주소까지. 메인 주소 1개를 뺀 나머지를 키워드에 쓴다 */
    static final int MAX_URLS = 50_000;
    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private final KeywordRepository keywords;
    private final CountryRepository countries;
    private final CodeTables codes;
    private final String baseUrl;
    private final Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(1).build();

    public SitemapService(KeywordRepository keywords, CountryRepository countries, CodeTables codes, AppProperties props) {
        this.keywords = keywords; this.countries = countries; this.codes = codes;
        this.baseUrl = props.share() == null ? new AppProperties.Share(null).baseUrl() : props.share().baseUrl();
    }

    /** 금지·해제가 커밋되면 사이트맵 캐시를 바로 비운다(금지한 키워드가 5분 동안 목록에 남지 않게). */
    @org.springframework.transaction.event.TransactionalEventListener
    public void onChanged(com.trendist.keyword.support.KeywordPageEvents.Changed e) { cache.invalidateAll(); }

    @Transactional(readOnly = true)
    public String sitemap() {
        return cache.get("sitemap", k -> {
            StringBuilder b = new StringBuilder(HEAD).append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
            b.append("  <url><loc>").append(baseUrl).append("/</loc></url>\n");
            Long banned = codes.keywordStateId(CodeTables.KW_BANNED);
            int remaining = MAX_URLS - 1;
            for (Country c : countries.findByEnabledTrue()) {
                if (remaining <= 0) { log.warn("sitemap is full ({} urls); older keywords are left out", MAX_URLS); break; }
                List<Object[]> rows = keywords.findSitemapRows(c.getId(), banned, PageRequest.of(0, remaining));
                for (Object[] r : rows) {
                    String loc = KeywordPage.url(baseUrl, c.getCode(), (String) r[0]);
                    String lastmod = ((Instant) r[1]).truncatedTo(ChronoUnit.SECONDS).toString();
                    b.append("  <url><loc>").append(loc).append("</loc><lastmod>").append(lastmod).append("</lastmod></url>\n");
                }
                remaining -= rows.size();
            }
            return b.append("</urlset>\n").toString();
        });
    }
}
