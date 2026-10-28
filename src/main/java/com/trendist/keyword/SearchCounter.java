package com.trendist.keyword;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** 검색 수는 메모리 카운터로만 센다. 같은 사용자·같은 키워드는 한 시간에 한 번(9.9). 매시 정각에 DB로 옮긴다. */
@Component
public class SearchCounter {
    private final ConcurrentHashMap<Long, LongAdder> counts = new ConcurrentHashMap<>();
    private volatile Set<String> seen = ConcurrentHashMap.newKeySet();

    public void count(long userId, long keywordId) {
        if (seen.add(userId + ":" + keywordId)) counts.computeIfAbsent(keywordId, k -> new LongAdder()).increment();
    }

    public long pending(long keywordId) {
        LongAdder a = counts.get(keywordId);
        return a == null ? 0 : a.sum();
    }

    /** 카운터를 비우면서 돌려준다. 중복 검사 집합도 새 시간에 맞춰 비운다. */
    public Map<Long, Long> drain() {
        Map<Long, Long> out = new java.util.HashMap<>();
        for (Long id : counts.keySet()) {
            LongAdder a = counts.remove(id);
            if (a != null && a.sum() > 0) out.put(id, a.sum());
        }
        seen = ConcurrentHashMap.newKeySet();
        return out;
    }
}
