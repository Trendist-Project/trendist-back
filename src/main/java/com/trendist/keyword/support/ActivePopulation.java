package com.trendist.keyword.support;

import com.trendist.common.code.CodeTables;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 나라별 활동 인원(4.8): 만료 전 키워드를 하나 이상 가진 플레이어 수(접속하지 않은 사람 포함, 봇·탈퇴 제외).
 * 트렌드 값의 가진 비율 분모다. 1분 잡이 다시 세고, 그 사이에는 마지막 값을 쓴다(+할 때마다 세지 않는다).
 */
@Component
@RequiredArgsConstructor
public class ActivePopulation {
    private final PlayerKeywordRepository playerKeywords;
    private final CodeTables codes;
    private final Clock clock;
    private final Map<Long, Long> byCountry = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public long get(long countryId) {
        if (!loaded) refresh();
        return byCountry.getOrDefault(countryId, 0L);
    }

    /** 다시 센다. 값이 바뀐 나라가 있으면 true. */
    public synchronized boolean refresh() {
        Map<Long, Long> next = new HashMap<>();
        for (Object[] row : playerKeywords.countHoldingPlayersByCountry(Instant.now(clock), codes.userState(CodeTables.USER_WITHDRAWN).getId())) {
            next.put((Long) row[0], (Long) row[1]);
        }
        boolean changed = !next.equals(byCountry);
        byCountry.keySet().retainAll(next.keySet());
        byCountry.putAll(next);
        loaded = true;
        return changed;
    }
}
