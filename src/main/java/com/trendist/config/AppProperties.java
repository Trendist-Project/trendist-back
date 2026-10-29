package com.trendist.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trendist")
public record AppProperties(Jwt jwt, Cookie cookie, Ws ws, DevTrends devTrends, Google google) {

    /** 구글 로그인 클라이언트 ID(공개 값). */
    public record Google(String clientId) {}

    public record Jwt(String secret, int accessTtlMinutes) {}

    public record Cookie(boolean secure) {}

    public record Ws(List<String> allowedOrigins) {}

    /** keywords: "키워드:관심도" 목록. */
    public record DevTrends(boolean enabled, List<String> keywords) {
        public DevTrends {
            keywords = keywords == null ? List.of() : keywords;
        }

        public Map<String, Integer> asMap() {
            Map<String, Integer> m = new java.util.LinkedHashMap<>();
            for (String s : keywords) {
                int i = s.lastIndexOf(':');
                if (i > 0) m.put(s.substring(0, i).strip(), Integer.parseInt(s.substring(i + 1).strip()));
            }
            return m;
        }
    }
}
