package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.trendist.monster.support.MonsterDefs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 달토끼(4.35): 깡충(착지 폭발·떠 있는 동안 몸 없음·무방비·분노 2연속), 끈끈 떡(슬로우 장판), 당근 부채(부채꼴 한 번만 맞기), 체력 계산 공유 */
class MoonRabbitTest {
    private static Map<String, Double> defaults() {
        Map<String, Double> p = new HashMap<>();
        for (MonsterDefs.Type t : MonsterDefs.types()) if (t.code().equals("moon_rabbit"))
            for (MonsterDefs.Def d : t.defs()) p.put(d.name(), d.value());
        p.put("opening_delay_seconds", 0.0); p.put("gcd_seconds", 0.0); p.put("recover_seconds", 0.0);
        p.put("hop_weight", 0.0); p.put("mochi_weight", 0.0); p.put("carrot_weight", 0.0); // 시험하는 패턴만 켠다
        return p;
    }

    private static Monster.Spec spec(Map<String, Double> p) {
        return new Monster.Spec(1, "moon_rabbit", "달토끼", "Moon Rabbit", "MOON_RABBIT", "moon-rabbit", p);
    }

    private static Room room(MonsterTest.MOut out, Map<String, Double> p) {
        Room r = new Room(1, MonsterTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        r.setComboTuning(new Room.ComboTuning(5, true, 300));
        r.setHpTuning(Room.HpTuning.on());
        r.setMonsterParams(Map.of("moon_rabbit", p));
        return r;
    }

    private static MoonRabbit boss(Room r) { return (MoonRabbit) r.monsters().iterator().next(); }

    private static void pin(Room.Member m, double x, double y) { m.x = x; m.y = y; m.vx = 0; m.vy = 0; m.prevX = x; m.prevY = y; }

    private static List<Map<String, Object>> fx(MonsterTest.MOut out, String kind) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> m : out.texts) if ("monsterFx".equals(m.get("type")) && kind.equals(m.get("fx"))) list.add(m);
        return list;
    }

    @Test
    void rabbitUsesTheSameHpFormulaAsTheTurtle() {
        Map<String, Double> p = defaults();
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, p);
        r.join(1, "s1", "a", 1, 100, List.of());
        r.join(2, "s2", "b", 1, 200, List.of());
        r.join(3, "s3", "c", 1, 600, List.of());
        r.spawnMonster(spec(p));
        double ref = 300 * 0.7 + 600 * 0.3, perHit = 0.8 * 5 + 0.2;
        assertThat(boss(r).maxHp).isEqualTo(Math.round(3 * 0.5 * 60 * ref * perHit * 0.9));
        assertThat(boss(r).radius).isEqualTo(800.0);
    }

    @Test
    void hopLandsOnTheShadowHurtingOnlyThoseInsideThenStandsDefenseless() {
        Map<String, Double> p = defaults();
        p.put("hop_weight", 1.0); p.put("hop_windup", 0.3); p.put("hop_lock_seconds", 0.1); p.put("hop_air_seconds", 0.3); p.put("hop_stun_seconds", 0.6);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, p);
        Room.Member hit = r.join(1, "s1", "hit", 1, 100, List.of(1L));
        Room.Member outside = r.join(2, "s2", "outside", 1, 100, List.of(2L));
        Room.Member immune = r.join(3, "s3", "immune", 1, 100, List.of(3L));
        r.spawnMonster(spec(p));
        MoonRabbit b = boss(r);
        double bx = -4000, by = 0;
        boolean airChecked = false, sawStun = false;
        for (int i = 0; i < 20 * 8 && !sawStun; i++) {
            if (fx(out, "hopJump").isEmpty()) b.x = bx; b.y = by; // 뛰기 전에는 제자리, 뛰면 그대로 난다
            pin(hit, 3000, 0); pin(outside, 3000 + 1500, 1500); pin(immune, 3000, 100); immune.protectUntil = 1e9;
            Map<String, Object> st = b.stateMessage(r);
            if (Boolean.TRUE.equals(st.get("air")) && !airChecked) { // 떠 있는 동안은 몸이 없다: 멀리 날아오는 보스와 닿아도 아무 일도 없다
                airChecked = true;
                long hp0 = b.hp; Room.Member probe = outside; double hpMe = probe.hp;
                probe.x = b.x + b.radius + probe.displayRadius - 10; probe.y = b.y; probe.prevX = probe.x; probe.prevY = probe.y;
                r.tick();
                assertThat(b.hp).isEqualTo(hp0);
                assertThat(probe.hp).isEqualTo(hpMe);
                continue;
            }
            r.tick();
            if (Boolean.TRUE.equals(b.stateMessage(r).get("stun"))) sawStun = true;
        }
        assertThat(airChecked).isTrue();
        assertThat(fx(out, "hopLand")).hasSize(3);  // 방송은 접속한 세 사람에게 하나씩 가므로 착지 한 번 = 3건
        assertThat(hit.hp).isCloseTo(100 - 12 - 5, within(0.5)); // 그림자 원 안: 폭발 12% + 제자리라 내려앉은 몸에 닿은 5%(착지하면 몸 판정이 돌아온다)
        assertThat(immune.hp).isEqualTo(100.0);                  // 무적이면 맞지 않는다
        assertThat(outside.hp).isEqualTo(100.0);                 // 원 밖
        assertThat(sawStun).isTrue();                            // 착지 뒤 무방비
        assertThat(b.stateMessage(r).get("ph")).isEqualTo("hop");
        assertThat(out.losses).isEmpty();                        // 키워드는 잃지 않는다
    }

    @Test
    void rageMakesItHopTwiceInARow() {
        Map<String, Double> p = defaults();
        p.put("hop_weight", 1.0); p.put("hop_windup", 0.2); p.put("hop_lock_seconds", 0.1); p.put("hop_air_seconds", 0.2); p.put("hop_chain_windup", 0.2); p.put("hop_stun_seconds", 0.4);
        p.put("rage_hp_percent", 100.0); // 등장하자마자 분노
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of());
        r.spawnMonster(spec(p));
        MoonRabbit b = boss(r);
        boolean stun = false;
        for (int i = 0; i < 20 * 8 && !stun; i++) {
            if (fx(out, "hopJump").isEmpty()) { b.x = -4000; b.y = 0; }
            pin(a, 3000, 0);
            r.tick();
            stun = Boolean.TRUE.equals(b.stateMessage(r).get("stun"));
        }
        assertThat(stun).isTrue();
        assertThat(fx(out, "hopLand")).hasSize(2);
        assertThat(fx(out, "hopMark")).hasSize(1);  // 두 번째 그림자를 예고한다
        assertThat(fx(out, "hopJump")).hasSize(2);
    }

    @Test
    void carrotFanHitsEachPersonOnceAndSkipsThoseOutsideOrImmune() {
        Map<String, Double> p = defaults();
        p.put("carrot_weight", 1.0); p.put("carrot_windup", 0.3); p.put("carrot_lock_seconds", 0.1); p.put("carrot_speed", 5000.0); p.put("carrot_range_px", 6000.0);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, p);
        Room.Member hit = r.join(1, "s1", "hit", 1, 100, List.of());
        Room.Member outside = r.join(2, "s2", "outside", 1, 100, List.of());
        Room.Member immune = r.join(3, "s3", "immune", 1, 100, List.of());
        r.spawnMonster(spec(p));
        MoonRabbit b = boss(r);
        for (int i = 0; i < 20 * 5; i++) {
            b.x = -3000; b.y = 0;
            pin(hit, 2000, 0); pin(outside, 2000, 5000); pin(immune, 2000, 30); immune.protectUntil = 1e9;
            r.tick();
        }
        assertThat(fx(out, "carrot")).isNotEmpty();
        assertThat(((List<?>) fx(out, "carrot").get(0).get("angles"))).hasSize(7);
        assertThat(hit.hp).isCloseTo(100 - 6, within(0.5));  // 7발이 지나가도 한 번만 맞는다
        assertThat(outside.hp).isEqualTo(100.0);               // 부채꼴 밖
        assertThat(immune.hp).isEqualTo(100.0);                // 무적이면 맞지 않는다
    }

    @Test
    void mochiLandsOnTheAttackTargetFirstThenOnOthersAndAroundTheTarget() {
        Map<String, Double> p = defaults();
        p.put("mochi_weight", 1.0); p.put("mochi_windup", 0.3); p.put("mochi_lock_seconds", 0.1); p.put("mochi_count", 3.0);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, p);
        Room.Member near = r.join(1, "s1", "near", 1, 100, List.of());   // 보스에 가장 가까움 = 공격 대상
        Room.Member far = r.join(2, "s2", "far", 1, 100, List.of());
        r.spawnMonster(spec(p));
        MoonRabbit b = boss(r);
        for (int i = 0; i < 20 * 5 && fx(out, "mochi").isEmpty(); i++) { b.x = 0; b.y = 4500; pin(near, 2000, 3000); pin(far, -3500, -3500); r.tick(); }
        assertThat(fx(out, "mochi")).isNotEmpty();
        @SuppressWarnings("unchecked") List<Map<String, Object>> spots = (List<Map<String, Object>>) fx(out, "mochi").get(0).get("spots");
        assertThat(spots).hasSize(3);
        assertThat(((Number) spots.get(0).get("x")).doubleValue()).isEqualTo(2000.0);   // 첫 번째: 공격 대상의 자리 그대로
        assertThat(((Number) spots.get(0).get("y")).doubleValue()).isEqualTo(3000.0);
        assertThat(((Number) spots.get(1).get("x")).doubleValue()).isEqualTo(-3500.0);  // 다음: 다른 사람의 자리
        assertThat(((Number) spots.get(0).get("r")).doubleValue()).isEqualTo(420.0);    // 기본 반지름 420
        double d2 = Math.hypot(((Number) spots.get(2).get("x")).doubleValue() - 2000, ((Number) spots.get(2).get("y")).doubleValue() - 3000);
        assertThat(d2).isGreaterThan(300.0).isLessThan(1200.0);                          // 남는 하나는 대상 둘레
    }

    @Test
    void mochiZoneSlowsThoseInsideWithoutStackingAndLeavesAfterItsTime() {
        Map<String, Double> p = defaults();
        p.put("mochi_weight", 1.0); p.put("mochi_windup", 0.3); p.put("mochi_count", 1.0); p.put("mochi_seconds", 2.0); p.put("mochi_slow_seconds", 0.4);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, p);
        Room.Member inside = r.join(1, "s1", "inside", 1, 100, List.of());
        Room.Member outside = r.join(2, "s2", "outside", 1, 100, List.of());
        r.spawnMonster(spec(p));
        MoonRabbit b = boss(r);
        pin(inside, 3000, 3000); pin(outside, -3000, -3000);
        for (int i = 0; i < 20 * 5 && fx(out, "mochi").isEmpty(); i++) { b.x = 0; b.y = 4500; pin(inside, 3000, 3000); pin(outside, -3000, -3000); r.tick(); }
        assertThat(fx(out, "mochi")).hasSize(2); // 두 사람에게 하나씩 간 방송 = 장판을 한 번 던진 것
        @SuppressWarnings("unchecked") List<Map<String, Object>> spots = (List<Map<String, Object>>) fx(out, "mochi").get(0).get("spots");
        assertThat(spots).hasSize(1);
        double sx = ((Number) spots.get(0).get("x")).doubleValue(), sy = ((Number) spots.get(0).get("y")).doubleValue();
        for (int i = 0; i < 20; i++) { b.x = 0; b.y = 4500; pin(inside, sx, sy); pin(outside, sx + 2000, sy + 2000); r.tick(); } // 1초 서 있기
        assertThat(r.status().has(inside, StatusEffects.Kind.SLOW)).isTrue();
        assertThat(r.status().stacks(inside, StatusEffects.Kind.SLOW)).isEqualTo(1); // 서 있어도 겹이 쌓이지 않는다
        assertThat(r.status().has(outside, StatusEffects.Kind.SLOW)).isFalse();
        assertThat(inside.hp).isEqualTo(100.0);                                       // 피해는 없다
        for (int i = 0; i < 20 * 3; i++) { b.x = 0; b.y = 4500; pin(inside, sx + 2500, sy + 2500); pin(outside, sx + 2000, sy + 2000); r.tick(); } // 장판이 사라지고 벗어난 뒤
        assertThat(r.status().has(inside, StatusEffects.Kind.SLOW)).isFalse();
    }
}
