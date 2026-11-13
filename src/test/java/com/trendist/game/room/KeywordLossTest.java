package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 키워드 잃기 한 건(KeywordLoss)과 알림(LossMessages): 원인마다 잃는 방식과 메시지 모양이 하나로 정해진다 */
class KeywordLossTest {
    @Test
    void eachAttackSkillMapsToOneCauseAndWay() {
        assertThat(KeywordLoss.Cause.ofSkill("hook").way).isEqualTo(KeywordLoss.Way.STEAL);
        assertThat(KeywordLoss.Cause.ofSkill("absorb").way).isEqualTo(KeywordLoss.Way.STEAL);
        assertThat(KeywordLoss.Cause.ofSkill("poison").way).isEqualTo(KeywordLoss.Way.DROP);
        assertThat(KeywordLoss.Cause.ofSkill("quake").way).isEqualTo(KeywordLoss.Way.DROP);
        assertThat(KeywordLoss.Cause.ofSkill("fireball").way).isEqualTo(KeywordLoss.Way.DROP);
        assertThatThrownBy(() -> KeywordLoss.Cause.ofSkill("fight")).isInstanceOf(IllegalArgumentException.class); // 스킬이 아닌 원인은 스킬 코드로 고를 수 없다
        assertThatThrownBy(() -> KeywordLoss.Cause.ofSkill("stun")).isInstanceOf(IllegalArgumentException.class); // CC는 키워드를 잃게 하지 않는다
    }

    @Test
    void onlyFightAndFlameLetTheServiceApplyTheComboAfterTheDatabase() {
        for (KeywordLoss.Cause c : KeywordLoss.Cause.values())
            assertThat(c.serviceCombo).as(c.code).isEqualTo(c == KeywordLoss.Cause.FIGHT || c == KeywordLoss.Cause.FLAME);
    }

    @Test
    void lostMessageCarriesTheCauseAndNoOldFlags() {
        var drop = LossMessages.lost(KeywordLoss.Cause.QUAKE, "a", 3, 120, "키워드");
        assertThat(drop).containsEntry("type", "lost").containsEntry("cause", "quake").containsEntry("count", 3).containsEntry("score", 120L)
                .doesNotContainKeys("poison", "monster", "wiped", "partial", "stolen");
        assertThat(LossMessages.lost(KeywordLoss.Cause.HOOK, "a", 1, 10, "k")).containsEntry("stolen", true);
        assertThat(LossMessages.lost(KeywordLoss.Cause.DEATH, "a", 2, 50, null)).doesNotContainKey("keyword");
    }
}
