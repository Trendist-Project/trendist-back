package com.trendist.stats.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * 사이트 통계의 활동 수(13.9). 서비스가 일이 생길 때 나라별로 +1 하고, 매시 정각 잡이 비우며 시간별 행에 더한다.
 * DB를 건드리지 않는다. 서버가 갑자기 꺼지면 그 시간에 센 값은 사라진다(정상 종료 때는 저장한다).
 */
@Component
public class ActivityCounter {
    /** 순서가 통계 행의 counts() 순서다. */
    public enum Kind { NEW_PLAYER, PLUS, SEARCH, ORB, COLLISION, CHAT, COMMENT, SHARE }

    private final Map<Long, LongAdder[]> counters = new ConcurrentHashMap<>();

    public void add(long countryId, Kind kind) {
        counters.computeIfAbsent(countryId, k -> newAdders())[kind.ordinal()].increment();
    }

    /** 지금까지 센 값(비우지 않는다). 이번 시간의 "집계 중" 값에 쓴다. */
    public long[] peek(long countryId) {
        LongAdder[] a = counters.get(countryId);
        long[] out = new long[Kind.values().length];
        if (a != null) for (int i = 0; i < out.length; i++) out[i] = a[i].sum();
        return out;
    }

    /** 센 값을 돌려주고 0으로 비운다. */
    public long[] drain(long countryId) {
        LongAdder[] a = counters.get(countryId);
        long[] out = new long[Kind.values().length];
        if (a != null) for (int i = 0; i < out.length; i++) out[i] = a[i].sumThenReset();
        return out;
    }

    private static LongAdder[] newAdders() {
        LongAdder[] a = new LongAdder[Kind.values().length];
        for (int i = 0; i < a.length; i++) a[i] = new LongAdder();
        return a;
    }
}
