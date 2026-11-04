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
 * 로컬 개발·테스트용 소스. application.yml의 trendist.dev-trends.keywords에 있는 키워드만 외부 관심도가 있는 것으로 보고,
 * 구글 실시간 인기 목록에 오른 것처럼 반영 소스 google로 다룬다(RSS 없이도 외부 키워드를 시험할 수 있게). 운영(prod)에서는 꺼진다.
 */
@Component
@ConditionalOnProperty(prefix = "trendist.dev-trends", name = "enabled", havingValue = "true")
public class DevTrendSource implements ExternalTrendSource {
    // 나라마다 다른 목록: KR은 한국어, GLOBAL은 영어 개발 키워드
    private final Map<String, Map<String, Double>> byCountry = new HashMap<>();

    public DevTrendSource(AppProperties props) {
        byCountry.put("KR", normalized(props.devTrends().asMap()));
        byCountry.put("GLOBAL", normalized(props.devTrends().globalAsMap()));
    }

    private static Map<String, Double> normalized(Map<String, Integer> raw) {
        Map<String, Double> m = new HashMap<>();
        raw.forEach((k, v) -> m.put(KeywordNormalizer.normalize(k), v.doubleValue()));
        return m;
    }

    @Override public boolean supports(String countryCode) { return byCountry.containsKey(countryCode); }
    @Override public String sourceName() { return DataSource.GOOGLE; }

    @Override
    public OptionalDouble heat(String countryCode, String normalized) {
        Double v = byCountry.getOrDefault(countryCode, Map.of()).get(normalized);
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
    }
}
