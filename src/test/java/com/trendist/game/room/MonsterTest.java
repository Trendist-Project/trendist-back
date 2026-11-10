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
    void normalHitDealsScoreLosesKeywordAndKnocksBackWithProtection() {
        Map<String, Double> p = defaults();
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member a = r.join(1, "s1", "a", 1, 120, List.of(10L, 11L));
        r.join(2, "s2", "b", 1, 80, List.of(12L));
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        long hp0 = b.hp;
        touch(b, a);
        r.tick();
        assertThat(hp0 - b.hp).isEqualTo(120);               // 내 유행도만큼 피해
        assertThat(out.monsterLoses).containsExactly(new long[]{1, 1}); // 키워드 1개 잃음
        assertThat(a.protectUntil).isGreaterThan(r.clock());   // 3초 무적
        assertThat(a.kbTime).isGreaterThan(0);                 // 튕김
        assertThat(a.combo).isEqualTo(1);                      // 보스를 치는 것도 콤보
        assertThat(r.monsters().iterator().next().ledger.get(1L).damage).isEqualTo(120);
        // 무적인 동안 다시 부딪혀도 피해가 없다
        long hp1 = b.hp;
        touch(b, a);
        r.tick();
        assertThat(b.hp).isEqualTo(hp1);
    }

    @Test
    void flameHitDealsFiveTimesAndKeepsKeywords() {
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
        assertThat(a.protectUntil).isGreaterThan(r.clock());
    }

    @Test
    void flamingBossKillsNonFlamePlayersAndDousesFlamePlayers() {
        Map<String, Double> p = defaults();
        p.put("rage_remaining_seconds", 99999.0); // 바로 분노(불꽃 보스)
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member cold = r.join(1, "s1", "cold", 1, 100, List.of(10L));
        Room.Member hot = r.join(2, "s2", "hot", 1, 100, List.of(11L));
        hot.combo = 5;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        touch(b, cold);
        r.tick();
        assertThat(cold.dead).isTrue();                                // 불꽃이 아니면 즉사
        assertThat(out.monsterLoses).isEmpty();                        // 키워드는 잃지 않는다
        assertThat(out.defeats).containsExactly(1L);
        assertThat(out.texts).anyMatch(m -> "died".equals(m.get("type")) && "장로 거북이".equals(m.get("by")));
        long hp0 = b.hp;
        touch(b, hot);
        r.tick();
        assertThat(hot.dead).isFalse();
        assertThat(hot.combo).isZero();                                // 불꽃이 꺼진다
        assertThat(out.monsterLoses).containsExactly(new long[]{2, 1}); // 키워드 1개를 잃는다
        assertThat(hp0 - b.hp).isEqualTo(500);                         // 피해는 준다(불꽃 배수)
    }

    @Test
    void ringLosesMoreKeywordsCloserAndSkipsShieldSafeZoneAndFlame() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("dash_weight", 0.0); p.put("ring_windup", 0.2); p.put("ring_speed", 6000.0);
        p.put("ring_range_px", 5000.0); p.put("recover_seconds", 0.2);
        MOut out = new MOut();
        Room r = room(out, p);
        Room.Member near = r.join(1, "s1", "near", 1, 100, List.of(1L, 2L));
        Room.Member far = r.join(2, "s2", "far", 1, 100, List.of(3L, 4L));
        Room.Member shielded = r.join(3, "s3", "shield", 1, 100, List.of(5L));
        Room.Member hot = r.join(4, "s4", "hot", 1, 100, List.of(6L));
        hot.combo = 5;
        r.spawnMonster(spec(p));
        ElderTurtle b = boss(r); b.x = 0; b.y = 0;
        double edge = b.radius;
        near.x = edge + 500; near.y = 0;       // 몸 가장자리에서 500px(원 반지름보다 멀어 몸에는 닿지 않는다)
        far.x = edge + 3500; far.y = 0;        // 멀다(범위 5000의 70%)
        shielded.x = edge + 300; shielded.y = 300; shielded.protectUntil = 1e9;
        hot.x = edge + 400; hot.y = -300;
        for (Room.Member m : List.of(near, far, shielded, hot)) { m.prevX = m.x; m.prevY = m.y; }
        for (int i = 0; i < 20 * 6 && out.monsterLoses.size() < 2; i++) {
            // 보스가 따라오지 않게 제자리에 둔다(위치를 고정한 채 패턴만 본다)
            b.x = 0; b.y = 0;
            for (Room.Member m : List.of(near, far, shielded, hot)) { m.vx = 0; m.vy = 0; }
            r.tick();
        }
        Map<Long, Long> lost = new HashMap<>();
        for (long[] l : out.monsterLoses) lost.put(l[0], l[1]);
        assertThat(lost.get(1L)).isGreaterThanOrEqualTo(9);          // 가까우면 거의 최대(10)
        assertThat(lost.get(2L)).isLessThanOrEqualTo(4);             // 멀면 적게 잃는다(최소 1에 가깝다)
        assertThat(lost.get(1L)).isGreaterThan(lost.get(2L));
        assertThat(lost).doesNotContainKeys(3L, 4L);                 // 방패·불꽃은 잃지 않는다
    }

    @Test
    void skillsHaveGlobalCooldownAndDoNotRepeatBackToBack() {
        Map<String, Double> p = defaults();
        p.put("opening_delay_seconds", 0.0); p.put("gcd_seconds", 4.0); p.put("recover_seconds", 0.5);
        p.put("dash_windup", 0.5); p.put("dash_seconds", 0.5); p.put("dash_cooldown", 0.0); p.put("dash_min_distance", 0.0);
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
        assertThat(res.rewards().get(0).exp()).isEqualTo(50);       // 30 + 1~3위 추가 20
        assertThat(res.rewards().get(0).keywords()).isEqualTo(3);   // 2 + 추가 1
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
