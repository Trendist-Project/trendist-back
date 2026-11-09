package com.trendist.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.setting.support.SettingKeys;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** 관리자 설정 그룹·단위(13.3): 새 설정을 만들면 그룹을 정해야 한다("기타"로 남지 않게). */
class SettingKeysTest {
    @Test
    void everySettingBelongsToAKnownGroup() {
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            String g = SettingKeys.groupOf(d.name());
            assertThat(g).as("그룹이 없는 설정: " + d.name()).isNotEqualTo("기타");
            assertThat(SettingKeys.GROUP_ORDER).contains(g);
        }
    }

    @Test
    void everySettingHasAUniqueSortOrderFollowingTheGroupOrder() {
        // 화면 순서(settings.sort_order): 모든 설정이 서로 다른 번호를 갖고, 그룹 순서를 따른다
        var orders = new HashSet<Long>();
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            long o = SettingKeys.sortOrderOf(d.name());
            assertThat(o).as("순서가 없는 설정: " + d.name()).isPositive();
            assertThat(orders.add(o)).as("순서가 겹치는 설정: " + d.name()).isTrue();
        }
        for (SettingKeys.Def a : SettingKeys.DEFAULTS) for (SettingKeys.Def b : SettingKeys.DEFAULTS) {
            int ga = SettingKeys.GROUP_ORDER.indexOf(SettingKeys.groupOf(a.name())), gb = SettingKeys.GROUP_ORDER.indexOf(SettingKeys.groupOf(b.name()));
            if (ga < gb) assertThat(SettingKeys.sortOrderOf(a.name())).as(a.name() + " < " + b.name()).isLessThan(SettingKeys.sortOrderOf(b.name()));
        }
    }

    @Test
    void similarSettingsAreNextToEachOther() {
        assertThat(SettingKeys.sortOrderOf(SettingKeys.MAX_RADIUS)).isEqualTo(SettingKeys.sortOrderOf(SettingKeys.MIN_RADIUS) + 1);
        assertThat(SettingKeys.sortOrderOf(SettingKeys.AREA_SHRINK_SPEED)).isEqualTo(SettingKeys.sortOrderOf(SettingKeys.AREA_GROW_SPEED) + 1);
        assertThat(SettingKeys.sortOrderOf(SettingKeys.HOOK_PULL_SECONDS)).isEqualTo(SettingKeys.sortOrderOf(SettingKeys.HOOK_SECONDS) + 1);
    }

    @Test
    void unknownSettingGoesToOther() {
        assertThat(SettingKeys.groupOf("no_such_setting")).isEqualTo("기타");
    }

    @Test
    void groupNamesAreUniqueAndFitTheColumn() {
        assertThat(new HashSet<>(SettingKeys.GROUP_ORDER)).hasSameSizeAs(SettingKeys.GROUP_ORDER);
        assertThat(SettingKeys.GROUP_ORDER).allMatch(g -> g.length() <= 50);
    }

    @Test
    void unitsFitTheColumnAndTimeSettingsHaveTheRightUnit() {
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            String u = SettingKeys.unitOf(d.name());
            if (u != null) assertThat(u.length()).as("단위가 너무 긴 설정: " + d.name()).isBetween(1, SettingKeys.UNIT_MAX_LENGTH);
        }
        assertThat(SettingKeys.unitOf(SettingKeys.OFFLINE_KEEP_SECONDS)).isEqualTo("초");
        assertThat(SettingKeys.unitOf(SettingKeys.NICKNAME_CHANGE_MINUTES)).isEqualTo("분");
        assertThat(SettingKeys.unitOf(SettingKeys.PLUS_TTL_HOURS)).isEqualTo("시간");
        assertThat(SettingKeys.unitOf(SettingKeys.MAX_SPEED)).isEqualTo("px/s");
        assertThat(SettingKeys.unitOf(SettingKeys.COMBO_BOT_COUNTS)).isNull();
    }

    @Test
    void settingsNamedWithATimeSuffixHaveATimeUnit() {
        // 이름이 _seconds·_minutes·_hours로 끝나는 설정은 단위가 이름과 맞아야 한다(단위를 빠뜨리거나 잘못 적지 않게)
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            String n = d.name();
            if (n.endsWith("_seconds")) assertThat(SettingKeys.unitOf(n)).as(n).isEqualTo("초");
            if (n.endsWith("_minutes")) assertThat(SettingKeys.unitOf(n)).as(n).isEqualTo("분");
            if (n.endsWith("_hours")) assertThat(SettingKeys.unitOf(n)).as(n).isEqualTo("시간");
        }
    }

    @Test
    void removedSettingsAreGone() {
        assertThat(SettingKeys.DEFAULTS).noneMatch(d -> d.name().equals("join_protect_seconds"));
        assertThat(SettingKeys.DEFAULTS).filteredOn(d -> d.name().equals(SettingKeys.CUSTOMIZE_UNLOCK_LEVEL)).extracting(SettingKeys.Def::value).containsExactly("10");
        assertThat(SettingKeys.DEFAULTS).filteredOn(d -> d.name().equals(SettingKeys.NICKNAME_CHANGE_MINUTES)).extracting(SettingKeys.Def::value).containsExactly("5");
    }
}
