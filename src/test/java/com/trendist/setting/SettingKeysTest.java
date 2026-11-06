package com.trendist.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.setting.support.SettingKeys;
import org.junit.jupiter.api.Test;

/** 관리자 설정 그룹(13.3): 새 설정을 만들면 그룹을 정해야 한다("기타"로 남지 않게). */
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
    void unknownSettingGoesToOther() {
        assertThat(SettingKeys.groupOf("no_such_setting")).isEqualTo("기타");
    }
}
