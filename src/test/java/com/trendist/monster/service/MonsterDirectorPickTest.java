package com.trendist.monster.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.monster.entity.MonsterType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 매시 정각 보스 랜덤 선택(4.35): 같은 시간대·방이면 항상 같은 결과, 비중대로 갈린다 */
class MonsterDirectorPickTest {
    private static MonsterCatalog.Entry entry(String code, double weight) {
        return new MonsterCatalog.Entry(new MonsterType(code, code, code, "BOSS", code.toUpperCase(), code, 1), Map.of("spawn_weight", weight));
    }

    @Test
    void sameSlotAndRoomAlwaysGiveTheSameBoss() {
        List<MonsterCatalog.Entry> g = List.of(entry("elder_turtle", 1), entry("moon_rabbit", 1));
        for (long slot = 0; slot < 50; slot++) {
            String first = MonsterDirector.pick(g, 1, slot * 3600).type().getCode();
            for (int i = 0; i < 5; i++) assertThat(MonsterDirector.pick(g, 1, slot * 3600).type().getCode()).isEqualTo(first); // 예고와 등장·서버 재시작 뒤에도 같다
        }
    }

    @Test
    void equalWeightsSplitRoughlyEvenlyAndRoomsDiffer() {
        List<MonsterCatalog.Entry> g = List.of(entry("elder_turtle", 1), entry("moon_rabbit", 1));
        Map<String, Integer> count = new HashMap<>();
        int differ = 0;
        for (long slot = 0; slot < 2000; slot++) {
            String kr = MonsterDirector.pick(g, 1, 1_700_000_000L + slot * 3600).type().getCode();
            String global = MonsterDirector.pick(g, 2, 1_700_000_000L + slot * 3600).type().getCode();
            count.merge(kr, 1, Integer::sum);
            if (!kr.equals(global)) differ++;
        }
        assertThat(count.get("moon_rabbit")).isBetween(850, 1150);
        assertThat(count.get("elder_turtle")).isBetween(850, 1150);
        assertThat(differ).isGreaterThan(0); // 방마다 따로 뽑는다
    }

    @Test
    void weightsBiasTheDraw() {
        List<MonsterCatalog.Entry> g = List.of(entry("elder_turtle", 3), entry("moon_rabbit", 1));
        int turtle = 0;
        for (long slot = 0; slot < 2000; slot++) if (MonsterDirector.pick(g, 1, 1_700_000_000L + slot * 3600).type().getCode().equals("elder_turtle")) turtle++;
        assertThat(turtle).isBetween(1350, 1650); // 약 75%
        assertThat(MonsterDirector.pick(List.of(entry("moon_rabbit", 1)), 1, 123).type().getCode()).isEqualTo("moon_rabbit"); // 하나뿐이면 그대로
    }
}
