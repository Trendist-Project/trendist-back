package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.monster.support.MonsterDefs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 보스몹(장로 거북이, 4.35)의 체력 계산·충돌 규칙·충격고리·보상 */
class MonsterTest {
    /** 보스(반지름 2000)가 들어갈 만큼 넓은 맵: 활동 영역은 가장 좁아도 12,000px(half 6,000) */
    static final Room.Tuning T = new Room.Tuning(48000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 0, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0);

    static class MOut extends RoomTest.Out {
        final List<Monster.Start> starts = new ArrayList<>();
        final List<Monster.Result> ends = new ArrayList<>();
        final List<long[]> monsterLoses = new ArrayList<>(); // {당한 사람, 잃은 개수}
        @Override public void monsterStarted(Room room, Monster.Spec spec, Monster.Start start) { starts.add(start); }
        @Override public void monsterEnded(Room room, Monster.Result result) { ends.add(result); }
        @Override public void requestMonsterLose(Room room, Room.Member victim, String by, int count) { monsterLoses.add(new long[]{victim.playerId, count}); }
    }

    private static Map<String, Double> defaults() {
        Map<String, Double> p = new HashMap<>();
        for (MonsterDefs.Def d : MonsterDefs.types().get(0).defs()) p.put(d.name(), d.value());
        return p;
    }

    private static Monster.Spec spec(Map<String, Double> p) {
        return new Monster.Spec(1, "elder_turtle", "장로 거북이", "Elder Turtle", "ELDER_TURTLE", "elder-turtle", p);
    }

    private static Room room(MOut out, Map<String, Double> p) {
        Room r = new Room(1, T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        r.setComboTuning(new Room.ComboTuning(5, true, 300));
        r.setHpTuning(Room.HpTuning.on());
        r.setMonsterParams(Map.of("elder_turtle", p));
        return r;
    }

    /** 보스 몸에 막 닿는 자리(가운데 세이프존 밖)에 사람을 둔다 */
    private static void touch(Monster boss, Room.Member m) {
        m.x = boss.x + boss.radius + m.displayRadius - 10; m.y = boss.y;
        m.prevX = m.x; m.prevY = m.y;
    }

    private static ElderTurtle boss(Room r) { return (ElderTurtle) r.monsters().iterator().next(); }

    @Test
    void hpFollowsPlayersAverageAndTopScore() {
        // 체력 = 사람 수 × 참여율 × 1인당 타격 수 × 기준 유행도 × 타격당 배수 × 여유
        Map<String, Double> p = defaults();
        MOut out = new MOut();
        Room r = room(out, p);
        r.join(1, "s1", "a", 1, 100, List.of());
        r.join(2, "s2", "b", 1, 200, List.of());
        r.join(3, "s3", "c", 1, 600, List.of());
        r.spawnMonster(spec(p));
        double avg = 300, top = 600, ref = avg * 0.7 + top * 0.3, perHit = 0.8 * 5 + 0.2;
        long expected = Math.round(3 * 0.5 * 60 * ref * perHit * 0.9);
        assertThat(boss(r).maxHp).isEqualTo(expected);
        assertThat(out.starts).hasSize(1);
        assertThat(out.starts.get(0).humans()).isEqualTo(3);
        assertThat(out.starts.get(0).topScore()).isEqualTo(600);
        // 같은 종류는 한 번에 하나만
        r.spawnMonster(spec(p));
        assertThat(r.monsters()).hasSize(1);
    }

    @Test
    void normalHitDealsScoreAndOnlyKnocksBackWithoutKeywordLossOrProtection() {
        Map<String, Double> p = defaults();
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 120, List.of(10L, 11L));
        r.join(2, "s2", "b", 1, 80, List.of(12L));
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        long hp0 = b.hp;
        double hpMe = a.hp;
        touch(b, a);
        r.tick();
        assertThat(hp0 - b.hp).isEqualTo(120);                 // 내 유행도만큼 보스가 피해를 입는다
        assertThat(out.monsterLoses).isEmpty();                // 키워드를 잃지 않는다
        assertThat(a.protectUntil).isLessThanOrEqualTo(r.clock()); // 무적이 되지 않는다
        assertThat(a.hp).isCloseTo(hpMe - 5, org.assertj.core.api.Assertions.within(0.01)); // 부딪히면 내 체력이 5% 닳는다
        assertThat(a.kbTime).isGreaterThan(0);                 // 나만 튕겨 난다
        assertThat(a.combo).isEqualTo(1);                      // 보스를 치는 것도 콤보
        assertThat(r.monsters().iterator().next().ledger.get(1L).damage).isEqualTo(120);
        // 연타 방지 간격(hit_interval_seconds) 안에 다시 부딪혀도 피해가 없고, 지나면 다시 피해를 준다
        long hp1 = b.hp;
        touch(b, a);
        r.tick();
        assertThat(b.hp).isEqualTo(hp1);
        for (int i = 0; i < 25; i++) r.tick();
        touch(b, a);
        r.tick();
        assertThat(hp1 - b.hp).isEqualTo(120);
    }

    @Test
    void flameHitDealsFiveTimesAndKeepsKeywordsAndFlame() {
        Map<String, Double> p = defaults();
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(10L));
        r.join(2, "s2", "b", 1, 80, List.of());
        a.combo = 5; // 불꽃
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        long hp0 = b.hp;
        touch(b, a);
        r.tick();
        assertThat(hp0 - b.hp).isEqualTo(500);
        assertThat(out.monsterLoses).isEmpty();      // 불꽃이면 키워드를 잃지 않는다
        assertThat(a.combo).isEqualTo(6);            // 콤보는 이어진다
        assertThat(a.protectUntil).isLessThanOrEqualTo(r.clock()); // 무적이 되지 않는다
    }

    @Test
    void flamingBossTakesPlainDamageFromEveryoneAndOnlyKillsNonFlamePlayers() {
        Map<String, Double> p = defaults();
        p.put("rage_remaining_seconds", 99999.0); // 바로 분노(불꽃 보스)
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member cold = r.join(1, "s1", "cold", 1, 100, List.of(10L));
        Room.Member hot = r.join(2, "s2", "hot", 1, 100, List.of(11L));
        hot.combo = 5;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        long hp0 = b.hp;
        touch(b, cold);
        r.tick();
        assertThat(cold.dead).isTrue();                                // 불꽃 없이 덤비면 즉사하지만
        assertThat(hp0 - b.hp).isEqualTo(100);                         // 피해는 똑같이 준다
        assertThat(out.monsterLoses).isEmpty();                        // 키워드는 잃지 않는다
        assertThat(out.defeats).containsExactly(1L);
        assertThat(out.texts).anyMatch(m -> "died".equals(m.get("type")) && "장로 거북이".equals(m.get("by")));
        long hp1 = b.hp;
        touch(b, hot);
        r.tick();
        assertThat(hot.dead).isFalse();                                // 불꽃이면 즉사하지 않고
        assertThat(hot.combo).isEqualTo(6);                            // 불꽃도 그대로
        assertThat(out.monsterLoses).isEmpty();
        assertThat(hp1 - b.hp).isEqualTo(100);                         // 불꽃 없는 피해량(×5가 아니다)
    }

    @Test
    void rageFlameBurnsOnlyForTheConfiguredSeconds() {
        Map<String, Double> p = defaults();
        p.put("rage_remaining_seconds", 99999.0); p.put("rage_flame_seconds", 1.0); p.put("opening_delay_seconds", 999.0);
        MOut out = new MOut();
        Room r = room(out, p);
        r.join(1, "s1", "a", 1, 100, List.of(10L));
        r.spawnMonster(spec(p));
        r.tick();
        assertThat(boss(r).stateMessage(r)).containsEntry("flame", true).containsEntry("rage", true); // 분노하면 바로 불꽃
        for (int i = 0; i < 30; i++) r.tick();
        assertThat(boss(r).stateMessage(r)).containsEntry("flame", false).containsEntry("rage", true); // 1초 뒤 불꽃만 꺼진다
    }

    @Test
    void dashHitCostsTenPercentOnceAndRingCostsMoreTheCloser() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("dash_weight", 0.0); p.put("ring_windup", 0.2); p.put("ring_speed", 6000.0);
        p.put("ring_range_px", 5000.0); p.put("recover_seconds", 0.2);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member near = r.join(1, "s1", "near", 1, 100, List.of(1L, 2L));
        Room.Member far = r.join(2, "s2", "far", 1, 100, List.of(3L, 4L));
        Room.Member shielded = r.join(3, "s3", "shield", 1, 100, List.of(5L));
        Room.Member hot = r.join(4, "s4", "hot", 1, 100, List.of(6L));
        Room.Member weak = r.join(5, "s5", "weak", 1, 100, List.of(7L));
        hot.combo = 5;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        double edge = b.radius;
        near.x = edge + 500; near.y = 0;       // 몸 가장자리에서 500px(범위 5000의 10%)
        far.x = edge + 3500; far.y = 0;        // 멀다(범위의 70%)
        shielded.x = edge + 300; shielded.y = 300; shielded.protectUntil = 1e9;
        hot.x = edge + 400; hot.y = -300;
        weak.x = edge + 200; weak.y = 600; weak.hp = 25; // 체력이 최대 피해(33%)보다 적으면 가까이서 맞고 죽는다
        List<Room.Member> all = List.of(near, far, shielded, hot, weak);
        for (Room.Member m : all) { m.prevX = m.x; m.prevY = m.y; }
        for (int i = 0; i < 20 * 6; i++) {
            b.x = 0; b.y = 0; // 보스가 따라오지 않게 제자리에 둔다(위치를 고정한 채 패턴만 본다)
            for (Room.Member m : all) if (!m.dead) { m.vx = 0; m.vy = 0; }
            r.tick();
            if (near.hp < 100 && far.hp < 100) break;
        }
        assertThat(near.hp).isCloseTo(100 - (33 - 28 * 0.1), org.assertj.core.api.Assertions.within(1.0)); // 가까우면 최대 33%에 가깝게
        assertThat(far.hp).isCloseTo(100 - (33 - 28 * 0.7), org.assertj.core.api.Assertions.within(1.0));  // 멀수록 덜 깎인다
        assertThat(near.hp).isLessThan(far.hp);
        assertThat(shielded.hp).isEqualTo(100.0);                      // 무적 중이면 맞지 않는다
        assertThat(hot.hp).isLessThan(100.0);                          // 불꽃이어도 맞는다
        assertThat(weak.dead).isTrue();                                // 체력이 모자라면 죽는다
        assertThat(out.monsterLoses).isEmpty();                        // 키워드는 잃지 않는다
        assertThat(near.protectUntil).isLessThanOrEqualTo(r.clock());  // 무적도 되지 않는다
    }

    @Test
    void touchingTheBossCostsFivePercentHpAndRageDoublesIt() {
        for (boolean rage : new boolean[] {false, true}) {
            Map<String, Double> p = defaults();
            p.put("opening_delay_seconds", 0.0); p.put("dash_weight", 0.0); p.put("ring_weight", 0.0);
            if (rage) p.put("rage_remaining_seconds", 99999.0);
            MOut out = new MOut();
            Room r = room(out, p);
            Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(1L, 2L));
            r.spawnMonster(spec(p));
            ElderTurtle b = boss(r); b.x = 3500; b.y = 3500;
            if (rage) a.combo = 5; // 분노하면 보스에 불꽃이 붙어 불꽃 없는 사람은 즉사하니 불꽃으로 맞선다
            a.x = -3500; a.y = -3500; a.prevX = a.x; a.prevY = a.y;
            r.tick(); // 먼저 한 틱: 분노 판정이 끝난 뒤에 닿게 한다
            b.x = 3500; b.y = 3500;
            touch(b, a);
            r.tick();
            assertThat(a.hp).isCloseTo(100 - (rage ? 10 : 5), org.assertj.core.api.Assertions.within(0.5));
        }
    }

    @Test
    void bossWithNoTargetWandersAroundAndAvoidsTheSafeZone() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 999.0); p.put("wander_pause_seconds", 0.0); p.put("wander_speed_ratio", 1.0);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(1L)); // 세이프존(가운데)에 있어 쫓기지 않는다
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r);
        double x0 = b.x, y0 = b.y, far = 0;
        for (int i = 0; i < 20 * 60; i++) {
            r.tick();
            far = Math.max(far, Math.hypot(b.x - x0, b.y - y0));
            assertThat(r.nearSafeZone(b.x, b.y, 0)).isFalse(); // 몸 중심이 세이프존에 들어가지 않는다
        }
        assertThat(far).isGreaterThan(500);                    // 제자리에 서 있지 않고 돌아다닌다
    }

    @Test
    void disabledPatternsAreNeverChosen() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("gcd_seconds", 0.0); p.put("recover_seconds", 0.0);
        p.put("dash_enabled", 0.0); p.put("ring_enabled", 0.0);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(1L));
        a.x = 3500; a.y = 3500; a.prevX = a.x; a.prevY = a.y;
        r.spawnMonster(spec(p));
        for (int i = 0; i < 20 * 60; i++) {
            r.tick();
            assertThat(boss(r).stateMessage(r).get("ph")).isIn("chase");   // 패턴을 모두 끄면 쫓기만 한다
        }
    }

    @Test
    void rageDoublesTheHpPatternsTakeAndKeepsTheFlameLit() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("dash_weight", 0.0); p.put("ring_windup", 0.2); p.put("ring_speed", 6000.0);
        p.put("ring_range_px", 5000.0); p.put("recover_seconds", 0.2);
        p.put("rage_remaining_seconds", 99999.0); // 시작하자마자 분노
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member near = r.join(1, "s1", "near", 1, 100, List.of(1L, 2L));
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        near.x = b.radius + 500; near.y = 0; near.prevX = near.x; near.prevY = near.y;
        for (int i = 0; i < 20 * 6 && near.hp >= 100; i++) { b.x = 0; b.y = 0; near.vx = 0; near.vy = 0; r.tick(); }
        assertThat(near.hp).isCloseTo(100 - 2 * (33 - 28 * 0.1), org.assertj.core.api.Assertions.within(1.5)); // 분노 중이라 2배
        assertThat(b.stateMessage(r)).containsEntry("flame", true).containsEntry("rage", true);
    }

    @Test
    void dashingIntoSomeoneCostsTenPercentHpOnlyOncePerDash() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("ring_weight", 0.0); p.put("dash_windup", 0.2);
        p.put("dash_seconds", 6.0); p.put("dash_speed_mult", 10.0); p.put("hit_interval_seconds", 0.1); p.put("dash_cooldown", 999.0);
        p.put("contact_hp_percent", 0.0); // 이 테스트는 돌진 피해만 본다
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(10L));
        a.x = 3500; a.y = 3500; a.prevX = a.x; a.prevY = a.y;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = -3500; b.y = 3500;
        long hp0 = b.hp;
        for (int i = 0; i < 20 * 8; i++) {
            a.x = 3500; a.y = 3500; a.vx = 0; a.vy = 0; a.targetX = null; a.protectUntil = 0;
            r.tick();
        }
        assertThat(b.hp).isLessThan(hp0);                  // 돌진 중 몸에 부딪혀 보스도 피해를 입었고
        assertThat(a.hp).isCloseTo(90.0, org.assertj.core.api.Assertions.within(0.01)); // 한 번의 돌진에 10%만 잃는다
        assertThat(a.dead).isFalse();
    }

    @Test
    void dashStartsAndStopsSmoothlyAndCoversTheTelegraphedDistance() {
        // 돌진은 서서히 빨라졌다 서서히 느려진다(끊기듯 출발·정지하지 않는다). 가는 거리는 예고한 길이와 같고 상태는 돌진 중 매 틱 간다
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("ring_weight", 0.0); p.put("gcd_seconds", 0.0);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of(10L));
        a.x = 3500; a.y = 3500;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = -3500; b.y = -3500;
        List<Double> xs = new java.util.ArrayList<>();
        boolean dashed = false;
        for (int i = 0; i < 20 * 12; i++) {
            r.tick();
            if (b.fastState()) { dashed = true; xs.add(b.x); }
            else if (dashed) break;
            a.x = 3500; a.y = 3500;
        }
        assertThat(dashed).isTrue();
        assertThat(xs.size()).isGreaterThan(10);
        double first = xs.get(1) - xs.get(0), mid = xs.get(xs.size() / 2) - xs.get(xs.size() / 2 - 1), last = xs.get(xs.size() - 1) - xs.get(xs.size() - 2);
        assertThat(Math.abs(first)).isLessThan(Math.abs(mid) * 0.5);   // 처음은 느리다
        assertThat(Math.abs(last)).isLessThan(Math.abs(mid) * 0.5);    // 끝도 느리다
        for (int i = 1; i < xs.size(); i++) assertThat(Math.abs(xs.get(i) - xs.get(i - 1))).isLessThan(Math.abs(mid) * 1.2 + 1); // 갑자기 튀는 틱이 없다
    }

    @Test
    void skillsHaveGlobalCooldownAndDoNotRepeatBackToBack() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("gcd_seconds", 4.0); p.put("recover_seconds", 0.5);
        p.put("dash_windup", 0.5); p.put("dash_seconds", 0.5); p.put("dash_cooldown", 0.0);
        p.put("ring_windup", 0.5); p.put("ring_cooldown", 0.0); p.put("ring_range_px", 300.0); p.put("ring_speed", 6000.0);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 50, List.of());
        r.join(2, "s2", "b", 1, 50, List.of());
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r);
        a.x = 5000; a.y = 5000;
        double bx = -4000, by = -4000; // 보스가 대상에게 닿아 몸 싸움이 끼어들지 않게 매 틱 멀리 되돌려 둔다
        List<String> casts = new ArrayList<>();
        List<Double> castAt = new ArrayList<>();
        for (int i = 0; i < 20 * 25; i++) {
            a.x = 5000; a.y = 5000; a.vx = 0; a.vy = 0; a.prevX = a.x; a.prevY = a.y;
            b.x = bx; b.y = by;
            int before = out.texts.size();
            r.tick();
            for (int k = before; k < out.texts.size(); k++) {
                Map<String, Object> m = out.texts.get(k);
                if ("monsterFx".equals(m.get("type")) && ("dash".equals(m.get("fx")) || "ring".equals(m.get("fx")))) { casts.add((String) m.get("fx")); castAt.add(r.clock()); }
            }
        }
        // 두 사람에게 같은 글이 가므로 같은 시각의 같은 패턴은 한 번으로 센다
        List<String> uniq = new ArrayList<>(); List<Double> uat = new ArrayList<>();
        for (int i = 0; i < casts.size(); i++) {
            if (!uat.isEmpty() && Math.abs(castAt.get(i) - uat.get(uat.size() - 1)) < 1e-9 && casts.get(i).equals(uniq.get(uniq.size() - 1))) continue;
            uniq.add(casts.get(i)); uat.add(castAt.get(i));
        }
        assertThat(uniq.size()).isGreaterThanOrEqualTo(3);
        for (int i = 1; i < uniq.size(); i++) {
            assertThat(uniq.get(i)).isNotEqualTo(uniq.get(i - 1));                   // 직전과 같은 패턴은 피한다
            assertThat(uat.get(i) - uat.get(i - 1)).isGreaterThanOrEqualTo(4.0);     // 공통 대기(+경직)
        }
    }

    @Test
    void timeoutAndKillGiveRewardsOnlyToParticipantsWithEnoughDamage() {
        Map<String, Double> p = defaults();
        p.put("hp_min", 1000.0); p.put("hp_hits_per_player", 1.0); p.put("hp_min_players", 1.0); p.put("duration_seconds", 5.0);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 300, List.of(10L));
        r.join(2, "s2", "b", 1, 30, List.of(11L));
        a.combo = 5;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        b.hp = 100; // 한 번에 끝낼 수 있게
        touch(b, a);
        r.tick();
        assertThat(out.ends).hasSize(1);
        Monster.Result res = out.ends.get(0);
        assertThat(res.status()).isEqualTo(Monster.KILLED);
        assertThat(res.rewards()).hasSize(1);
        assertThat(res.rewards().get(0).playerId()).isEqualTo(1L);
        assertThat(res.rewards().get(0).exp()).isEqualTo(360);      // 기본 30 + 기여도 300(혼자 다 쳤다) + 1~3위 추가 30
        assertThat(res.rewards().get(0).keywords()).isEqualTo(7);   // min(상한 5, 기본 2 + 기여도 4) + 1~3위 추가 2
        assertThat(res.rewards().get(0).cleared()).isTrue();
        assertThat(r.monsters()).isEmpty();
        // 시간 초과
        Room r2 = room(out, p);
        r2.join(1, "s1", "a", 1, 300, List.of(10L));
        r2.spawnMonster(spec(p));
        for (int i = 0; i < 20 * 6; i++) r2.tick();
        assertThat(out.ends).hasSize(2);
        assertThat(out.ends.get(1).status()).isEqualTo(Monster.TIMEOUT);
        assertThat(out.ends.get(1).rewards()).isEmpty(); // 아무도 피해를 주지 못했다
    }
}
