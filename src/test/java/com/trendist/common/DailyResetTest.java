package com.trendist.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trendist.common.support.DailyReset;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** "하루에 한 번" 제한의 하루 경계: 기본 00:00, 설정(daily_reset_hour·minute)으로 늦출 수 있다. */
class DailyResetTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static DailyReset at(String isoUtc, int hour, int minute) {
        SettingService s = mock(SettingService.class);
        when(s.getInt(SettingKeys.DAILY_RESET_HOUR)).thenReturn(hour);
        when(s.getInt(SettingKeys.DAILY_RESET_MINUTE)).thenReturn(minute);
        return new DailyReset(s, Clock.fixed(Instant.parse(isoUtc), ZoneOffset.UTC));
    }

    @Test
    void defaultResetsAtMidnightLocalTime() {
        // 2026-11-10 23:30 KST = 14:30Z → 아직 11월 10일, 다음 초기화는 11월 11일 00:00 KST(= 10일 15:00Z)
        DailyReset d = at("2026-11-10T14:30:00Z", 0, 0);
        assertThat(d.today(KST)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(d.nextReset(KST)).isEqualTo(Instant.parse("2026-11-10T15:00:00Z"));
        // 11일 00:10 KST(= 10일 15:10Z)면 새 하루
        assertThat(at("2026-11-10T15:10:00Z", 0, 0).today(KST)).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void configuredTimeShiftsTheDayBoundary() {
        // 새벽 5시로 두면: 11일 04:59 KST(= 10일 19:59Z)는 아직 10일, 5:00(= 20:00Z)부터 11일
        assertThat(at("2026-11-10T19:59:00Z", 5, 0).today(KST)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(at("2026-11-10T20:00:00Z", 5, 0).today(KST)).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(at("2026-11-10T19:59:00Z", 5, 0).nextReset(KST)).isEqualTo(Instant.parse("2026-11-10T20:00:00Z"));
        // 분도 반영: 6시 30분
        assertThat(at("2026-11-10T21:29:00Z", 6, 30).today(KST)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(at("2026-11-10T21:30:00Z", 6, 30).today(KST)).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test
    void outOfRangeSettingsAreClamped() {
        assertThat(at("2026-11-10T14:30:00Z", 99, 99).offset().toMinutes()).isEqualTo(23 * 60 + 59);
        assertThat(at("2026-11-10T14:30:00Z", -3, -1).offset().toMinutes()).isZero();
    }
}
