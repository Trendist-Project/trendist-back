package com.trendist.keyword.support;

import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 4.8의 식.
 * x = min(1, (n / max(P, 최소 인원)) / 최고점 비율)     // n = 가진 사람 수, P = 그 나라 활동 인원
 * g = log10(1 + x/c) / log10(1 + 1/c)
 * trend_value = 활성이면 w × (base + (max − base) × g), 아니면 0
 * 키워드 점수 = trend_value(트렌드 값이 곧 내 유행도에 더해지는 점수). 정수로 반올림한다(0.5는 올림).
 */
@Component
@RequiredArgsConstructor
public class TrendFormula {
    private final SettingService settings;

    public long trendValue(boolean active, long holders, double weight, long population) {
        if (!active) return 0;
        double pop = Math.max(population, settings.getDouble(SettingKeys.TREND_MIN_POPULATION));
        double c = settings.getDouble(SettingKeys.TREND_LOG_SOFTNESS);
        double base = settings.getDouble(SettingKeys.TREND_BASE_RATIO);
        double max = settings.getDouble(SettingKeys.TREND_MAX_BASE);
        double x = Math.min(1, holders / pop / settings.getDouble(SettingKeys.TREND_FULL_SHARE));
        double g = Math.log10(1 + x / c) / Math.log10(1 + 1 / c);
        double v = Math.min(100, Math.max(0, weight * (base + (max - base) * g)));
        return Math.round(v);
    }

    public long expScore(long exp) { return Math.min(exp, settings.getInt(SettingKeys.EXP_SCORE_CAP)); }
}
