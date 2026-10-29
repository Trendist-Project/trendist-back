package com.trendist.setting.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.trendist.common.exception.ApiException;
import com.trendist.setting.entity.Setting;
import com.trendist.setting.repository.SettingRepository;
import com.trendist.setting.support.SettingKeys;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** settings를 1분 캐시로 읽는다(13.3). 행이 없으면 코드의 기본값을 쓴다. */
@Service
public class SettingServiceImpl implements SettingService {
    private static final Logger log = LoggerFactory.getLogger(SettingServiceImpl.class);
    private static final Map<String, String> DEFAULTS = new HashMap<>();
    static {
        SettingKeys.DEFAULTS.forEach(d -> DEFAULTS.put(d.name(), d.value()));
    }

    private final SettingRepository repository;
    private final LoadingCache<String, Map<String, String>> cache;

    public SettingServiceImpl(SettingRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build(k -> loadAll());
    }

    private Map<String, String> loadAll() {
        Map<String, String> m = new HashMap<>(DEFAULTS);
        for (Setting s : repository.findAll()) m.put(s.getName(), s.getValue());
        return m;
    }

    @Override
    public double getDouble(String name) {
        String v = cache.get("all").get(name);
        return v == null ? 0 : Double.parseDouble(v);
    }

    @Override
    public int getInt(String name) { return (int) Math.round(getDouble(name)); }

    @Override
    public void invalidate() { cache.invalidateAll(); }

    @Override
    @Transactional(readOnly = true)
    public List<Item> list() {
        Map<String, Setting> rows = new HashMap<>();
        for (Setting s : repository.findAll()) rows.put(s.getName(), s);
        List<Item> out = new ArrayList<>();
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            Setting s = rows.remove(d.name());
            if (s != null) out.add(item(s));
        }
        rows.values().stream().sorted(Comparator.comparing(Setting::getName)).forEach(s -> out.add(item(s))); // 코드에 없는 옛 설정
        return out;
    }

    @Override
    @Transactional
    public Item update(long adminUserId, String name, String raw) {
        Setting s = repository.findByName(name).orElseThrow(() -> ApiException.notFound("SETTING_NOT_FOUND", "없는 설정이에요"));
        BigDecimal v;
        try {
            v = new BigDecimal(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("SETTING_FORMAT", "숫자를 입력해 주세요");
        }
        if ("INT".equals(s.getValueType()) && v.stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest("SETTING_INT", "정수만 넣을 수 있어요");
        }
        if ((s.getMinValue() != null && v.compareTo(s.getMinValue()) < 0) || (s.getMaxValue() != null && v.compareTo(s.getMaxValue()) > 0)) {
            throw ApiException.badRequest("SETTING_RANGE", "허용 범위(" + plain(s.getMinValue()) + " ~ " + plain(s.getMaxValue()) + ")를 벗어났어요");
        }
        // 트렌드 값 식의 기본 비율은 최대 기준값을 넘을 수 없다(13.3)
        if (SettingKeys.TREND_BASE_RATIO.equals(name) && v.doubleValue() > getDouble(SettingKeys.TREND_MAX_BASE)
                || SettingKeys.TREND_MAX_BASE.equals(name) && v.doubleValue() < getDouble(SettingKeys.TREND_BASE_RATIO)) {
            throw ApiException.badRequest("SETTING_RANGE", "기본 비율은 최대 기준값보다 클 수 없어요");
        }
        String old = s.getValue();
        s.changeValue(plain(v));
        repository.saveAndFlush(s);
        invalidate();
        log.info("setting changed by admin {}: {} {} -> {}", adminUserId, name, old, s.getValue());
        return item(s);
    }

    private Item item(Setting s) {
        String def = SettingKeys.DEFAULTS.stream().filter(d -> d.name().equals(s.getName())).map(SettingKeys.Def::value).findFirst().orElse(null);
        return new Item(s.getName(), s.getValue(), s.getValueType(), s.getMinValue(), s.getMaxValue(), s.getDescription(), def, s.getUpdatedAt());
    }

    private static String plain(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().toPlainString(); }
}
