package com.trendist.monster.service;

import com.trendist.monster.entity.MonsterSetting;
import com.trendist.monster.entity.MonsterType;
import com.trendist.monster.repository.MonsterSettingRepository;
import com.trendist.monster.repository.MonsterTypeRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 몬스터 종류와 설정값을 읽는다(4.35). 방과 스케줄러가 1초마다 읽으므로 1초 동안 메모리에 둔다. 관리자가 저장하면 invalidate()로 바로 새로 읽는다.
 * 읽기만 하는 메서드라 호출하는 쪽의 트랜잭션이 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class MonsterCatalog {
    private static final long TTL_MS = 1000;
    private final MonsterTypeRepository types;
    private final MonsterSettingRepository settings;

    /** 한 종류의 읽은 값: 종류와 설정값(이름 → 값) */
    public record Entry(MonsterType type, Map<String, Double> params) {}
    private record Snapshot(long at, Map<String, Entry> byCode) {}
    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    public void invalidate() { cache.set(null); }

    public Map<String, Entry> all() {
        Snapshot s = cache.get();
        long now = System.currentTimeMillis();
        if (s != null && now - s.at() < TTL_MS) return s.byCode();
        Map<String, Entry> m = new HashMap<>();
        for (MonsterType t : types.findAllByOrderBySortOrderAscIdAsc()) {
            Map<String, Double> p = new HashMap<>();
            for (MonsterSetting st : settings.findByMonsterTypeIdOrderBySortOrderAscIdAsc(t.getId())) p.put(st.getName(), st.getValue().doubleValue());
            m.put(t.getCode(), new Entry(t, p));
        }
        cache.set(new Snapshot(now, m));
        return m;
    }

    public Entry get(String code) { return all().get(code); }

    public List<Entry> enabled() { return all().values().stream().filter(e -> e.type().isEnabled()).toList(); }
}
