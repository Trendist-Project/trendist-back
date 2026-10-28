package com.trendist.keyword.source;

import com.trendist.config.AppProperties;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.support.KeywordNormalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 네이버 데이터랩을 붙이기 전까지 쓰는 개발용 소스. application.yml의 trendist.dev-trends.keywords에 있는
 * 키워드만 외부 관심도가 있는 것으로 본다. 운영(prod)에서는 꺼진다.
 */
@Component
@ConditionalOnProperty(prefix = "trendist.dev-trends", name = "enabled", havingValue = "true")
public class DevTrendSource implements ExternalTrendSource {
    private final Map<String, Double> values = new HashMap<>();

    public DevTrendSource(AppProperties props) {
        props.devTrends().asMap().forEach((k, v) -> values.put(KeywordNormalizer.normalize(k), v.doubleValue()));
    }

    @Override public boolean supports(String countryCode) { return "KR".equals(countryCode); }
    @Override public String sourceName() { return DataSource.NAVER_DATALAB; }

    @Override
    public OptionalDouble heat(String countryCode, String normalized) {
        Double v = values.get(normalized);
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
    }
}
