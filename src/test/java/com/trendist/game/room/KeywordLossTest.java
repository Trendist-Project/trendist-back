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
        for (String code : new String[]{"slow", "darkness", "silence", "stun", "root", "confuse", "zone", "burst"}) // 흡수를 뺀 나머지 히트도 키워드 1개를 떨군다
            assertThat(KeywordLoss.Cause.ofSkill(code).way).as(code).isEqualTo(KeywordLoss.Way.DROP);
        assertThatThrownBy(() -> KeywordLoss.Cause.ofSkill("heal")).isInstanceOf(IllegalArgumentException.class); // 맞히지 않는 스킬은 원인이 없다
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
