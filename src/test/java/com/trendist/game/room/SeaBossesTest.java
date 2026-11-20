package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.trendist.monster.support.MonsterDefs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 바다 보스 다섯 마리(4.35): 모두 소환되어 오래 돌아도 멀쩡하고, 패턴마다 맞는 사람과 안 맞는 사람이 설계대로 갈린다 */
class SeaBossesTest {
    /** 가운데 세이프존(한 변 수천 px)에서 벗어난 높이: 사람을 이보다 아래에 세워야 패턴에 맞는다 */
    private static final double Y = 4500;
    private static final Map<String, String> BEHAVIOR = Map.of("deep_octopus", "DEEP_OCTOPUS", "electric_eel", "ELECTRIC_EEL", "puffer_king", "PUFFER_KING", "great_shark", "GREAT_SHARK", "great_whale", "GREAT_WHALE");

    /** 그 보스의 기본 설정. only에 적은 패턴만 켜고(없으면 모두 켠다) 대기 시간은 0으로 줄인다 */
    private static Map<String, Double> defaults(String code, String... only) {
        Map<String, Double> p = new HashMap<>();
        for (MonsterDefs.Type t : MonsterDefs.types()) if (t.code().equals(code)) for (MonsterDefs.Def d : t.defs()) p.put(d.name(), d.value());
        p.put("opening_delay_seconds", 0.0); p.put("gcd_seconds", 0.0); p.put("recover_seconds", 0.0);
        if (only.length > 0) {
            for (String k : new ArrayList<>(p.keySet())) if (k.endsWith("_weight") && p.containsKey(k.replace("_weight", "_cooldown"))) p.put(k, 0.0);
            for (String id : only) p.put(id + "_weight", 1.0);
        }
        return p;
    }

    private static Room room(MonsterTest.MOut out, String code, Map<String, Double> p) {
        Room r = new Room(1, MonsterTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        r.setComboTuning(new Room.ComboTuning(5, true, 300));
        r.setHpTuning(Room.HpTuning.on());
        r.setMonsterParams(Map.of(code, p));
        return r;
    }

    private static void spawn(Room r, String code, Map<String, Double> p) {
        r.spawnMonster(new Monster.Spec(1, code, code, code, BEHAVIOR.get(code), code.replace('_', '-'), p));
    }

    private static SkillBoss boss(Room r) { return (SkillBoss) r.monsters().iterator().next(); }

    private static void pin(Room.Member m, double x, double y) { m.x = x; m.y = y; m.vx = 0; m.vy = 0; m.prevX = x; m.prevY = y; }

    private static List<Map<String, Object>> fx(MonsterTest.MOut out, String kind) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> m : out.texts) if ("monsterFx".equals(m.get("type")) && kind.equals(m.get("fx"))) list.add(m);
        return list;
    }

    /** 보스를 (bx, by)에 붙들어 두고 fxKind 연출이 나올 때까지(최대 seconds초) 한 틱씩 진행한다. 사람들은 pins에 적은 자리에 붙들어 둔다 */
    private static void run(Room r, SkillBoss b, MonsterTest.MOut out, String fxKind, double seconds, double bx, double by, Map<Room.Member, double[]> pins, boolean pinPeople) {
        for (int i = 0; i < seconds * 20; i++) {
            b.x = bx; b.y = by;
            if (pinPeople) pins.forEach((m, at) -> pin(m, at[0], at[1]));
            r.tick();
            if (fxKind != null && !fx(out, fxKind).isEmpty()) break;
        }
    }

    // ------------------------------------------------------------ 공통

    @Test
    void everySeaBossSpawnsAndSurvivesThreeMinutesWithAllPatterns() {
        for (String code : BEHAVIOR.keySet()) {
            Map<String, Double> p = defaults(code);
            for (String k : new ArrayList<>(p.keySet())) if (k.endsWith("_cooldown") && !k.equals("hp_cooldown")) p.put(k, 1.0); // 패턴을 자주 쓰게
            MonsterTest.MOut out = new MonsterTest.MOut();
            Room r = room(out, code, p);
            List<Room.Member> people = new ArrayList<>();
            for (int i = 0; i < 6; i++) people.add(r.join(i + 1, "s" + i, "p" + i, 1, 100 + i * 50, List.of((long) i)));
            spawn(r, code, p);
            assertThat(r.monsters()).hasSize(1);
            for (int t = 0; t < 20 * 180; t++) {
                if (t % 40 == 0) for (int i = 0; i < people.size(); i++) { Room.Member m = people.get(i); if (!m.dead) { m.x = Math.cos(i + t / 40.0) * 3000; m.y = Math.sin(i * 2 + t / 40.0) * 3000; } }
                r.tick();
            }
            assertThat(r.monsters().isEmpty() ? out.ends.size() : 1).as(code).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void seaBossSettingsFollowTheConventionsAndStayInRange() {
        for (MonsterDefs.Type t : MonsterDefs.types()) {
            Map<String, MonsterDefs.Def> by = new HashMap<>();
            for (MonsterDefs.Def d : t.defs()) assertThat(by.put(d.name(), d)).as(t.code() + " duplicate " + d.name()).isNull();
            for (MonsterDefs.Def d : t.defs()) { // 패턴은 `<id>_weight`와 `<id>_cooldown`·`_windup`·`_enabled`를 함께 가진다
                if (d.name().endsWith("_weight") && by.containsKey(d.name().replace("_weight", "_cooldown"))) {
                    String id = d.name().replace("_weight", "");
                    assertThat(by).containsKeys(id + "_windup", id + "_enabled");
                }
                assertThat(d.value()).as(t.code() + "." + d.name()).isBetween(d.min(), d.max());
            }
        }
        assertThat(MonsterDefs.types()).extracting(MonsterDefs.Type::code).contains("deep_octopus", "electric_eel", "puffer_king", "great_shark", "great_whale");
    }

    // ------------------------------------------------------------ 전기뱀장어

    @Test
    void laneHitsAndStunsOnlyThoseOnALane() {
        Map<String, Double> p = defaults("electric_eel", "lane");
        p.put("lane_windup", 0.3); p.put("lane_lock_seconds", 0.1);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "electric_eel", p);
        Room.Member onCenter = r.join(1, "s1", "a", 1, 100, List.of(1L)), onSide = r.join(2, "s2", "b", 1, 100, List.of(2L));
        Room.Member between = r.join(3, "s3", "c", 1, 100, List.of(3L)), outside = r.join(4, "s4", "d", 1, 100, List.of(4L)), immune = r.join(5, "s5", "e", 1, 100, List.of(5L));
        immune.protectUntil = 1e9;
        spawn(r, "electric_eel", p);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(onCenter, new double[]{0, Y}); at.put(onSide, new double[]{0, Y + 700}); at.put(between, new double[]{0, Y + 350}); at.put(outside, new double[]{0, Y + 1500}); at.put(immune, new double[]{200, Y});
        run(r, boss(r), out, "flash", 5, -4000, Y, at, true);
        assertThat(onCenter.hp).isCloseTo(90, within(0.5));
        assertThat(onSide.hp).isCloseTo(90, within(0.5));
        assertThat(between.hp).isEqualTo(100.0);
        assertThat(outside.hp).isEqualTo(100.0);
        assertThat(immune.hp).isEqualTo(100.0);
        assertThat(r.status().stunned(onCenter)).isTrue();
        assertThat(r.status().stunned(between)).isFalse();
    }

    @Test
    void chainJumpsToTheNearestWithinRangeAndStopsAtTheMaximum() {
        Map<String, Double> p = defaults("electric_eel", "chain");
        p.put("chain_windup", 0.3); p.put("chain_lock_seconds", 0.1);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "electric_eel", p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(1L)), b2 = r.join(2, "s2", "b", 1, 100, List.of(2L)), c = r.join(3, "s3", "c", 1, 100, List.of(3L)), far = r.join(4, "s4", "d", 1, 100, List.of(4L));
        spawn(r, "electric_eel", p);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(a, new double[]{0, Y}); at.put(b2, new double[]{500, Y}); at.put(c, new double[]{1000, Y}); at.put(far, new double[]{3000, Y});
        run(r, boss(r), out, "chain", 5, -4000, Y, at, true);
        assertThat(a.hp).isCloseTo(92, within(0.5));
        assertThat(b2.hp).isCloseTo(92, within(0.5));
        assertThat(c.hp).isCloseTo(92, within(0.5));
        assertThat(far.hp).isEqualTo(100.0); // 튀는 거리 밖
    }

    // ------------------------------------------------------------ 심해 문어

    @Test
    void sweepHitsTheArmAndPullDragsPeopleInThenLeavesTheBossDefenseless() {
        Map<String, Double> p = defaults("deep_octopus", "sweep");
        p.put("sweep_windup", 0.3); p.put("sweep_lock_seconds", 0.1);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "deep_octopus", p);
        Room.Member onArm = r.join(1, "s1", "a", 1, 100, List.of(1L)), tooFar = r.join(2, "s2", "b", 1, 100, List.of(2L)), immune = r.join(3, "s3", "c", 1, 100, List.of(3L));
        immune.protectUntil = 1e9;
        spawn(r, "deep_octopus", p);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(onArm, new double[]{1500, 0}); at.put(tooFar, new double[]{0, 2600}); at.put(immune, new double[]{1500, 100});
        run(r, boss(r), out, null, 1.0, 0, 0, at, true);
        assertThat(onArm.hp).isCloseTo(92, within(0.5));
        assertThat(tooFar.hp).isEqualTo(100.0); // 촉수 길이(1800) 밖
        assertThat(immune.hp).isEqualTo(100.0);

        Map<String, Double> q = defaults("deep_octopus", "pull");
        q.put("pull_windup", 0.3);
        MonsterTest.MOut out2 = new MonsterTest.MOut();
        Room r2 = room(out2, "deep_octopus", q);
        Room.Member a = r2.join(1, "s1", "a", 1, 100, List.of(1L));
        spawn(r2, "deep_octopus", q);
        SkillBoss b = boss(r2);
        pin(a, 2200, 0);
        boolean sawStun = false;
        for (int i = 0; i < 20 * 5; i++) { b.x = 0; b.y = 0; r2.tick(); if (Boolean.TRUE.equals(b.stateMessage(r2).get("stun"))) sawStun = true; }
        assertThat(Math.hypot(a.x, a.y)).isLessThan(1700); // 끌려와 몸 가까이에 있다
        assertThat(sawStun).isTrue();
    }

    // ------------------------------------------------------------ 복어 대왕

    @Test
    void inflateBurstsInsideTheCircleThenStandsDefenseless() {
        Map<String, Double> p = defaults("puffer_king", "inflate");
        p.put("inflate_windup", 0.3);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "puffer_king", p);
        Room.Member inside = r.join(1, "s1", "a", 1, 100, List.of(1L)), outside = r.join(2, "s2", "b", 1, 100, List.of(2L)), immune = r.join(3, "s3", "c", 1, 100, List.of(3L));
        immune.protectUntil = 1e9;
        spawn(r, "puffer_king", p);
        SkillBoss b = boss(r);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(inside, new double[]{1500, 0}); at.put(outside, new double[]{3200, 0}); at.put(immune, new double[]{-1500, 0});
        boolean sawStun = false;
        for (int i = 0; i < 20 * 6; i++) { b.x = 0; b.y = 0; at.forEach((m, a) -> pin(m, a[0], a[1])); r.tick(); if (Boolean.TRUE.equals(b.stateMessage(r).get("stun"))) sawStun = true; }
        assertThat(inside.hp).isCloseTo(86, within(0.5));
        assertThat(outside.hp).isEqualTo(100.0);
        assertThat(immune.hp).isEqualTo(100.0);
        assertThat(sawStun).isTrue();
    }

    @Test
    void spikeRayHitsAndPoisonsButLeavesKeywordsAlone() {
        Map<String, Double> p = defaults("puffer_king", "spike");
        p.put("spike_windup", 0.3); p.put("spike_count", 4.0);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "puffer_king", p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(1L, 2L, 3L));
        spawn(r, "puffer_king", p);
        SkillBoss b = boss(r);
        pin(a, 3000, 3000);
        int kw = a.keywords.size();
        boolean placed = false;
        for (int i = 0; i < 20 * 8; i++) {
            b.x = 0; b.y = 0;
            @SuppressWarnings("unchecked") Map<String, Object> tele = (Map<String, Object>) b.stateMessage(r).get("tele");
            if (!placed && tele != null && tele.get("radial") instanceof Map<?, ?> rad) { // 예고에 보이는 가시 선 위에 선다
                double rot = ((Number) rad.get("rot")).doubleValue();
                pin(a, Math.cos(rot) * 1500, Math.sin(rot) * 1500);
                placed = true;
            }
            if (placed) { double rot = Math.atan2(a.y, a.x); pin(a, Math.cos(rot) * 1500, Math.sin(rot) * 1500); }
            r.tick();
        }
        assertThat(placed).isTrue();
        assertThat(a.hp).isLessThan(95);                  // 가시 5% + 독
        assertThat(a.keywords.size()).isEqualTo(kw);       // 키워드는 그대로
    }

    @Test
    void bubbleChasesAndBurstsOnItsTarget() {
        Map<String, Double> p = defaults("puffer_king", "bubble");
        p.put("bubble_windup", 0.3); p.put("bubble_count", 1.0); p.put("bubble_seconds", 4.0);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "puffer_king", p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(1L));
        spawn(r, "puffer_king", p);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(a, new double[]{1100, Y});
        run(r, boss(r), out, null, 5.0, 0, Y, at, true);
        assertThat(a.hp).isCloseTo(94, within(0.5));
        assertThat(fx(out, "bubbles")).isNotEmpty();
    }

    // ------------------------------------------------------------ 대형 상어

    @Test
    void huntMarksTheWeakestPersonAndTailOnlyHitsBehind() {
        Map<String, Double> p = defaults("great_shark", "hunt");
        p.put("hunt_windup", 0.5);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "great_shark", p);
        Room.Member strongNear = r.join(1, "s1", "a", 1, 100, List.of(1L)), weakFar = r.join(2, "s2", "b", 1, 100, List.of(2L));
        weakFar.hp = 40;
        spawn(r, "great_shark", p);
        SkillBoss b = boss(r);
        double markX = Double.NaN;
        for (int i = 0; i < 20 * 3 && Double.isNaN(markX); i++) {
            b.x = 0; b.y = 0; pin(strongNear, 1500, 0); pin(weakFar, 0, 3000);
            @SuppressWarnings("unchecked") Map<String, Object> tele = (Map<String, Object>) b.stateMessage(r).get("tele");
            if (tele != null && tele.get("mark") instanceof Map<?, ?> mk) markX = ((Number) mk.get("x")).doubleValue() + 100000 * (((Number) mk.get("y")).doubleValue() > 2000 ? 0 : 1);
            r.tick();
        }
        assertThat(markX).isLessThan(100000); // 표식은 체력이 낮은 (0, 3000) 쪽: y가 크다

        Map<String, Double> q = defaults("great_shark", "tail");
        q.put("tail_windup", 0.3);
        MonsterTest.MOut out2 = new MonsterTest.MOut();
        Room r2 = room(out2, "great_shark", q);
        Room.Member target = r2.join(1, "s1", "t", 1, 100, List.of(1L)), behind = r2.join(2, "s2", "r", 1, 100, List.of(2L)), front = r2.join(3, "s3", "f", 1, 100, List.of(3L)), side = r2.join(4, "s4", "s", 1, 100, List.of(4L));
        spawn(r2, "great_shark", q);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(target, new double[]{1200, 0}); at.put(behind, new double[]{-1400, 0}); at.put(front, new double[]{1300, 200}); at.put(side, new double[]{300, 1400});
        run(r2, boss(r2), out2, "flash", 5, 0, 0, at, true);
        assertThat(behind.hp).isCloseTo(91, within(0.5));
        assertThat(target.hp).isEqualTo(100.0);
        assertThat(front.hp).isEqualTo(100.0);
        assertThat(side.hp).isEqualTo(100.0);
    }

    // ------------------------------------------------------------ 대왕 고래

    @Test
    void spoutWaveAndSongHitTheRightPeople() {
        Map<String, Double> p = defaults("great_whale", "spout");
        p.put("spout_windup", 0.3); p.put("spout_lock_seconds", 0.1);
        MonsterTest.MOut out = new MonsterTest.MOut();
        Room r = room(out, "great_whale", p);
        Room.Member onSpot = r.join(1, "s1", "a", 1, 100, List.of(1L)), far = r.join(2, "s2", "b", 1, 100, List.of(2L)), immune = r.join(3, "s3", "c", 1, 100, List.of(3L));
        immune.protectUntil = 1e9;
        spawn(r, "great_whale", p);
        Map<Room.Member, double[]> at = new HashMap<>();
        at.put(onSpot, new double[]{0, Y}); at.put(far, new double[]{0, Y + 9000}); at.put(immune, new double[]{100, Y});
        run(r, boss(r), out, null, 3.0, -5000, Y, at, true);
        assertThat(onSpot.hp).isCloseTo(92, within(0.5));
        assertThat(far.hp).isEqualTo(100.0);
        assertThat(immune.hp).isEqualTo(100.0);

        Map<String, Double> w = defaults("great_whale", "wave");
        w.put("wave_windup", 0.3); w.put("wave_lock_seconds", 0.1);
        MonsterTest.MOut out2 = new MonsterTest.MOut();
        Room r2 = room(out2, "great_whale", w);
        Room.Member inPath = r2.join(1, "s1", "a", 1, 100, List.of(1L)), beside = r2.join(2, "s2", "b", 1, 100, List.of(2L)), behind = r2.join(3, "s3", "c", 1, 100, List.of(3L));
        spawn(r2, "great_whale", w);
        SkillBoss b = boss(r2);
        for (int i = 0; i < 20 * 6; i++) { b.x = 0; b.y = 0; pin(beside, 2000, 3500); pin(behind, -2500, 0); if (i < 12) pin(inPath, 2200, 0); r2.tick(); }
        assertThat(inPath.hp).isCloseTo(90, within(5.0)); // 파도에 맞고(10%) 밀려난다
        assertThat(beside.hp).isEqualTo(100.0);
        assertThat(behind.hp).isEqualTo(100.0);

        Map<String, Double> s = defaults("great_whale", "song");
        s.put("song_windup", 0.3);
        MonsterTest.MOut out3 = new MonsterTest.MOut();
        Room r3 = room(out3, "great_whale", s);
        Room.Member heard = r3.join(1, "s1", "a", 1, 100, List.of(1L)), notHeard = r3.join(2, "s2", "b", 1, 100, List.of(2L)), safe = r3.join(3, "s3", "c", 1, 100, List.of(3L));
        safe.protectUntil = 1e9;
        spawn(r3, "great_whale", s);
        Map<Room.Member, double[]> at3 = new HashMap<>();
        at3.put(heard, new double[]{1500, 0}); at3.put(notHeard, new double[]{6000, 0}); at3.put(safe, new double[]{1500, 300});
        run(r3, boss(r3), out3, "pulse", 4, 0, 0, at3, true);
        assertThat(r3.status().confused(heard)).isTrue();
        assertThat(r3.status().confused(notHeard)).isFalse();
        assertThat(r3.status().confused(safe)).isFalse();
        assertThat(heard.hp).isEqualTo(100.0); // 피해는 없다
    }
}
