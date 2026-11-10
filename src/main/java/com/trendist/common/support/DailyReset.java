package com.trendist.common.support;

import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "하루에 한 번" 제한(돌림판·인기도·신고)의 하루 경계. 기본은 나라 시간대의 00:00이고, 설정 `daily_reset_hour`·`daily_reset_minute`만큼
 * 늦춘 시각이 새 하루의 시작이다(예: 5시로 두면 새벽 5시에 초기화). 활동한 날처럼 통계로 날짜를 세는 것은 이 설정과 상관없이 달력 날짜다.
 */
@Component
@RequiredArgsConstructor
public class DailyReset {
    private final SettingService settings;
    private final Clock clock;

    /** 하루가 시작되는 시각의 자정으로부터의 오프셋 */
    public Duration offset() {
        int h = Math.max(0, Math.min(23, settings.getInt(SettingKeys.DAILY_RESET_HOUR)));
        int m = Math.max(0, Math.min(59, settings.getInt(SettingKeys.DAILY_RESET_MINUTE)));
        return Duration.ofMinutes(h * 60L + m);
    }

    /** 지금이 속한 "하루"의 날짜(그 하루가 시작한 달력 날짜). 초기화 시각 전이면 어제 날짜다 */
    public LocalDate today(ZoneId zone) {
        return clock.instant().minus(offset()).atZone(zone).toLocalDate();
    }

    /** 다음 초기화 시각(다음에 다시 할 수 있는 시각) */
    public Instant nextReset(ZoneId zone) {
        return today(zone).plusDays(1).atStartOfDay(zone).toInstant().plus(offset());
    }
}
