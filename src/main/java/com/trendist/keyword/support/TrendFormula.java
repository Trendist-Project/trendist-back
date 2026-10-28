package com.trendist.keyword.support;

import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 4.8의 식.
 * g(n) = log10(1 + min(n, N)/c) / log10(1 + N/c)
 * trend_value = 활성이면 w × (base + (max − base) × g(n)), 아니면 0
 * 키워드 점수 = trend_value(트렌드 값이 곧 내 유행도에 더해지는 점수)
 */
@Component
@RequiredArgsConstructor
public class TrendFormula {
    private final SettingService settings;

    public BigDecimal trendValue(boolean active, int holders, double weight) {
        if (!active) return BigDecimal.ZERO.setScale(2);
        double n0 = settings.getDouble(SettingKeys.TREND_BASE_POPULATION);
        double c = settings.getDouble(SettingKeys.TREND_LOG_SOFTNESS);
        double base = settings.getDouble(SettingKeys.TREND_BASE_RATIO);
        double max = settings.getDouble(SettingKeys.TREND_MAX_BASE);
        double g = Math.log10(1 + Math.min(holders, n0) / c) / Math.log10(1 + n0 / c);
        double v = Math.min(100, Math.max(0, weight * (base + (max - base) * g)));
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public long expScore(long exp) { return Math.min(exp, settings.getInt(SettingKeys.EXP_SCORE_CAP)); }
}
