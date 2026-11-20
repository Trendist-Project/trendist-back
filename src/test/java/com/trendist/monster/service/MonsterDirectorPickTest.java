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
            String first = MonsterDirector.pick(g, slot * 3600).type().getCode();
            for (int i = 0; i < 5; i++) assertThat(MonsterDirector.pick(g, slot * 3600).type().getCode()).isEqualTo(first); // 예고와 등장·서버 재시작 뒤에도 같다
        }
    }

    @Test
    void equalWeightsSplitEvenly() {
        List<MonsterCatalog.Entry> g = List.of(entry("elder_turtle", 1), entry("moon_rabbit", 1));
        Map<String, Integer> count = new HashMap<>();
        for (long slot = 0; slot < 2000; slot++) count.merge(MonsterDirector.pick(g, 1_700_000_000L + slot * 3600).type().getCode(), 1, Integer::sum);
        assertThat(count.get("moon_rabbit")).isBetween(850, 1150);
        assertThat(count.get("elder_turtle")).isBetween(850, 1150);
    }

    @Test
    void weightsBiasTheDraw() {
        List<MonsterCatalog.Entry> g = List.of(entry("elder_turtle", 3), entry("moon_rabbit", 1));
        int turtle = 0;
        for (long slot = 0; slot < 2000; slot++) if (MonsterDirector.pick(g, 1_700_000_000L + slot * 3600).type().getCode().equals("elder_turtle")) turtle++;
        assertThat(turtle).isBetween(1350, 1650); // 약 75%
        assertThat(MonsterDirector.pick(List.of(entry("moon_rabbit", 1)), 123).type().getCode()).isEqualTo("moon_rabbit"); // 하나뿐이면 그대로
    }

    private static List<MonsterCatalog.Entry> seven() {
        return java.util.stream.Stream.of("a", "b", "c", "d", "e", "f", "g").map(c -> entry(c, 1)).toList();
    }

    @Test
    void everyBossAppearsExactlyOncePerCycleOfAsManyHoursAsThereAreBosses() {
        List<MonsterCatalog.Entry> g = seven();
        for (long cycle = -3; cycle < 60; cycle++) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < 7; i++) seen.add(MonsterDirector.pick(g, (cycle * 7 + i) * 3600L).type().getCode());
            assertThat(seen).as("cycle %d", cycle).hasSize(7); // 일곱 정각 안에 일곱 보스가 모두 한 번씩
        }
    }

    @Test
    void cyclesAreShuffledAndNeverRepeatTheSameBossBackToBack() {
        List<MonsterCatalog.Entry> g = seven();
        java.util.Set<String> orders = new java.util.HashSet<>();
        for (long cycle = 0; cycle < 100; cycle++) {
            StringBuilder order = new StringBuilder();
            for (int i = 0; i < 7; i++) order.append(MonsterDirector.pick(g, (cycle * 7 + i) * 3600L).type().getCode());
            orders.add(order.toString());
        }
        assertThat(orders.size()).isGreaterThan(50);   // 사이클마다 순서가 달라진다
        for (long slot = 0; slot < 7 * 400; slot++)    // 어디서도 같은 보스가 연달아 나오지 않는다(사이클 경계 포함)
            assertThat(MonsterDirector.pick(g, (slot + 1) * 3600L).type().getCode()).isNotEqualTo(MonsterDirector.pick(g, slot * 3600L).type().getCode());
    }

    @Test
    void theOrderDependsOnlyOnTheTimeSoAllServersGetTheSameBossAtTheSameHour() {
        List<MonsterCatalog.Entry> g = seven();
        for (long slot = 0; slot < 500; slot++) { // 같은 시각이면 어느 나라든 같은 보스(뽑기에 나라가 들어가지 않는다)
            long at = 1_700_000_000L + slot * 3600;
            assertThat(MonsterDirector.pick(g, at).type().getCode()).isEqualTo(MonsterDirector.pick(seven(), at).type().getCode());
        }
    }

    @Test
    void weightIsHowManyTimesABossAppearsPerCycle() {
        List<MonsterCatalog.Entry> g = List.of(entry("a", 3), entry("b", 1), entry("c", 2)); // 한 사이클 6정각: a 3번, b 1번, c 2번
        for (long cycle = 0; cycle < 40; cycle++) {
            Map<String, Integer> n = new HashMap<>();
            for (int i = 0; i < 6; i++) n.merge(MonsterDirector.pick(g, (cycle * 6 + i) * 3600L).type().getCode(), 1, Integer::sum);
            assertThat(n).containsEntry("a", 3).containsEntry("b", 1).containsEntry("c", 2);
        }
        assertThat(MonsterDirector.bag(List.of(entry("x", 0.2), entry("y", 1.4))).size()).isEqualTo(2); // 반올림, 최소 1
    }

    @Test
    void theCurrentCycleListsEveryBossInOrderAndMatchesWhatWillSpawn() {
        List<MonsterCatalog.Entry> g = seven();
        long slot = 123 * 3600L;
        for (int i = 0; i < 7; i++) {
            long now = slot + i * 3600L + 600; // 사이클 안의 어느 시간이든
            long first = Math.floorDiv(now / 3600, 7) * 7;
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int j = 0; j < 7; j++) names.add(MonsterDirector.pick(g, (first + j) * 3600L).type().getCode());
            assertThat(names).hasSize(7);
        }
    }
}
