package com.trendist.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trendist")
public record AppProperties(Jwt jwt, Cookie cookie, Ws ws, DevTrends devTrends, Google google, Share share) {

    /** 자랑하기 카드 공유 주소·OG 태그에 쓰는 사이트 주소(4.24, 12). 요청 Host 헤더로 만들지 않는다(위조 방지) */
    public record Share(String baseUrl) {
        public Share {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://trendist.kr" : baseUrl.replaceAll("/+$", "");
        }
    }

    /** 구글 로그인 클라이언트 ID(공개 값). */
    public record Google(String clientId) {}

    public record Jwt(String secret, int accessTtlMinutes) {}

    public record Cookie(boolean secure) {}

    public record Ws(List<String> allowedOrigins) {}

    /** keywords: KR용 "키워드:관심도" 목록. globalKeywords: GLOBAL(영어)용 같은 형식의 목록. */
    public record DevTrends(boolean enabled, List<String> keywords, List<String> globalKeywords) {
        public DevTrends {
            keywords = keywords == null ? List.of() : keywords;
            globalKeywords = globalKeywords == null ? List.of() : globalKeywords;
        }

        public Map<String, Integer> asMap() { return parse(keywords); }

        public Map<String, Integer> globalAsMap() { return parse(globalKeywords); }

        private static Map<String, Integer> parse(List<String> list) {
            Map<String, Integer> m = new java.util.LinkedHashMap<>();
            for (String s : list) {
                int i = s.lastIndexOf(':');
                if (i > 0) m.put(s.substring(0, i).strip(), Integer.parseInt(s.substring(i + 1).strip()));
            }
            return m;
        }
    }
}
