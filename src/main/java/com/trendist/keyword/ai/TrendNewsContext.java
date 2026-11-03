package com.trendist.keyword.ai;

import com.trendist.keyword.support.KeywordNormalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 키워드가 왜 유행인지 AI가 알 수 있게, RSS가 함께 주는 관련 뉴스 제목을 기억해 둔다(4.12, 10.4).
 * 서버 메모리에만 두고(재시작하면 다음 RSS 읽기에 다시 채워진다), 오래된 것부터 밀려나게 최대 개수를 둔다.
 */
@Component
public class TrendNewsContext {
    private static final int MAX_KEYWORDS = 1000;
    private static final int MAX_HEADLINES = 5;

    private final Map<String, List<String>> byKey = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) { return size() > MAX_KEYWORDS; }
    };

    /** 키워드(표기 그대로)의 관련 뉴스 제목을 기억한다. 비어 있으면 기존 것을 지우지 않는다. */
    public synchronized void put(String keyword, List<String> headlines) {
        if (headlines == null || headlines.isEmpty()) return;
        byKey.put(KeywordNormalizer.normalize(keyword), headlines.stream().limit(MAX_HEADLINES).toList());
    }

    /** 키워드의 관련 뉴스 제목. 모르면 빈 목록. */
    public synchronized List<String> get(String keyword) {
        return byKey.getOrDefault(KeywordNormalizer.normalize(keyword), List.of());
    }
}
