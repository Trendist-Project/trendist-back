package com.trendist.setting;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.trendist.domain.Setting;
import com.trendist.repo.SettingRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** settings를 1분 캐시로 읽는다(13.3). 행이 없으면 코드의 기본값을 쓴다. */
@Service
public class SettingService {
    private static final Map<String, String> DEFAULTS = new HashMap<>();
    static {
        SettingKeys.DEFAULTS.forEach(d -> DEFAULTS.put(d.name(), d.value()));
    }

    private final SettingRepository repository;
    private final LoadingCache<String, Map<String, String>> cache;

    public SettingService(SettingRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build(k -> loadAll());
    }

    private Map<String, String> loadAll() {
        Map<String, String> m = new HashMap<>(DEFAULTS);
        for (Setting s : repository.findAll()) m.put(s.getName(), s.getValue());
        return m;
    }

    public double getDouble(String name) {
        String v = cache.get("all").get(name);
        return v == null ? 0 : Double.parseDouble(v);
    }

    public int getInt(String name) { return (int) Math.round(getDouble(name)); }

    public void invalidate() { cache.invalidateAll(); }
}
