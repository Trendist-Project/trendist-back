package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 체력(HP, 4.36): 부딪히면 서로의 유행도 비율만큼 깎고, 0이 되면 죽으며, 키워드를 얻으면 회복한다 */
class HpTest {
    private static Room room(RoomTest.Out out) {
        Room r = new Room(1, RoomTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        r.setHpTuning(Room.HpTuning.on());
        return r;
    }

    private static Room.Member human(Room r, long id, double score, double x) {
        Room.Member m = r.join(id, "s" + id, "p" + id, 1, score, List.of(100L + id));
        m.protectUntil = 0;
        m.x = x; m.y = 800;
        return m;
    }

    @Test
    void collisionDamagesBothByTheOthersShareOfTheTotal() {
        // 유행도 300 vs 100: 300이 100에게 100×300/400 = 75점, 100이 300에게 100×100/400 = 25점. 피해는 서로의 비율이라 합이 항상 100이다
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member a = human(r, 1, 300, 800), b = human(r, 2, 100, 810);
        r.tick();
        assertThat(b.hp).isCloseTo(25.0, within(0.001));
        assertThat(a.hp).isCloseTo(75.0, within(0.001));
        assertThat(out.loses).hasSize(1);                      // 키워드를 잃는 기존 규칙(낮은 쪽이 잃는다)은 그대로다
        assertThat(out.loses.get(0)).containsExactly(2L, 1L);
        assertThat(out.texts).anyMatch(m -> "hpHit".equals(m.get("type")) && m.get("e").equals(b.entityNo) && ((Number) m.get("pct")).intValue() == 25);
    }

    @Test
    void flameHitsNonFlameFiveTimesHarderAndTakesCounterDamageByDefault() {
        // 불꽃(100)이 일반(300)을 친다: 공격력 500 → 100×500/800 = 62.5점. 불꽃 쪽도 반격을 맞는다: 300이 100을 일반 공격 100×300/400 = 75점. 키워드는 설정 비율(50%)만큼 잃는다
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member flame = human(r, 1, 100, 800), cold = human(r, 2, 300, 810);
        flame.combo = 5;
        r.tick();
        assertThat(cold.hp).isCloseTo(37.5, within(0.001));
        assertThat(flame.hp).isCloseTo(25.0, within(0.001));
        assertThat(out.halves).hasSize(1);
        assertThat(out.halves.get(0)).containsExactly(2L, 1L, 50L, 0L); // 불꽃에게 부딪힘: 50%, 죽음 아님
    }

    @Test
    void flameCounterSettingOffMakesTheFlameTakeNoDamage() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        r.setHpTuning(new Room.HpTuning(true, 100, 1, 5, false, 2, true, 50, 50, 100));
        Room.Member flame = human(r, 1, 100, 800), cold = human(r, 2, 300, 810);
        flame.combo = 5;
        r.tick();
        assertThat(flame.hp).isEqualTo(100.0);
        assertThat(cold.hp).isCloseTo(37.5, within(0.001));
    }

    @Test
    void lethalHitKillsAndLosesKeywordsExactlyOnce() {
        // 체력이 모자란 채 불꽃에게 맞으면 죽는다: 키워드는 죽음 쪽 한 번(death_lose 50%)만 잃고, 불꽃에게 부딪힌 몫으로 한 번 더 잃지 않는다
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member flame = human(r, 1, 100, 800), cold = human(r, 2, 300, 810);
        flame.combo = 5;
        cold.hp = 20;
        r.tick();
        assertThat(cold.dead).isTrue();
        assertThat(cold.hp).isZero();
        assertThat(out.halves).hasSize(1);
        assertThat(out.halves.get(0)).containsExactly(2L, 1L, 50L, 1L);
        assertThat(out.wipes).isEmpty();
        assertThat(out.kills).containsExactly(1L);
        assertThat(flame.combo).isEqualTo(6); // 이긴 쪽 콤보 +1
        assertThat(out.texts).anyMatch(m -> "died".equals(m.get("type")) && "hp".equals(m.get("cause")));
    }

    @Test
    void bothReachingZeroKillsOnlyTheOneHitHarderAndLeavesTheOtherAtOnePercent() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member a = human(r, 1, 300, 800), b = human(r, 2, 100, 810);
        a.hp = 10; b.hp = 10; // a는 25점, b는 75점을 맞는다: b가 더 크게 맞는다
        r.tick();
        assertThat(b.dead).isTrue();
        assertThat(a.dead).isFalse();
        assertThat(a.hp).isCloseTo(26.0, within(0.001)); // 1%로 남은 뒤 처치 회복 100×100/(100+300) = 25%
    }

    @Test
    void healingIsTheKeywordsShareOfMyScoreWithNoCap() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member a = human(r, 1, 100, 800);
        a.hp = 20;
        r.applyKeywordGain(1, 10, 100); // 10% 차지 → +10
        assertThat(a.hp).isCloseTo(30.0, within(0.001));
        r.applyKeywordGain(1, 60, 100); // 60% 차지 → 상한 없이 +60
        assertThat(a.hp).isCloseTo(90.0, within(0.001));
        r.applyKeywordGain(1, 60, 100); // 최대 체력까지만
        assertThat(a.hp).isCloseTo(100.0, within(0.001));
        a.hp = 40;
        r.applyKeywordGain(1, 0, 100); // 💤 키워드(점수 0)는 회복 없음
        assertThat(a.hp).isEqualTo(40.0);
    }

    @Test
    void flameAlwaysAttacksFiveTimesEvenAgainstAnotherFlame() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member a = human(r, 1, 100, 800), b = human(r, 2, 100, 810);
        a.combo = 5; b.combo = 5;
        assertThat(r.hpDamage(a, b)).isEqualTo(83.33); // 500 ÷ (500 + 100)
        b.combo = 0;
        assertThat(r.hpDamage(a, b)).isEqualTo(83.33);
        assertThat(r.hpDamage(b, a)).isEqualTo(50.0);   // 불꽃이 아니면 ×1
    }

    @Test
    void onlyTheCenterSafeZoneRegeneratesTwoPercentPerSecond() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member inside = r.join(1, "s1", "a", 1, 100, List.of(101L)); // 입장하면 가운데 고정 세이프존
        inside.hp = 50;
        Room.Member outside = human(r, 2, 100, 5000); // 가운데 밖
        outside.hp = 50;
        for (int i = 0; i < 19; i++) r.tick();
        double before = inside.hp;
        for (int i = 0; i < 20; i++) r.tick(); // 1초 동안 한 번에 2%씩(매 틱 조금씩이 아니다)
        assertThat(inside.hp - 50).isIn(2.0, 4.0); // 19틱 안에 0~1번 + 20틱 안에 1번
        assertThat(before).isIn(50.0, 52.0);
        assertThat(inside.hp).isEqualTo(Math.round(inside.hp / 2) * 2.0); // 2%씩 계단으로만 오른다
        assertThat(outside.hp).isEqualTo(50.0);
    }

    @Test
    void botsHaveHpTooAndDieAtZero() {
        // 봇도 사람처럼 체력이 있다: 부딪히면 서로 깎이고 0이 되면 죽는다(죽은 봇은 새 봇으로 바뀐다)
        RoomTest.Out out = new RoomTest.Out();
        Room.Member[] h = new Room.Member[1];
        Room r = RoomTest.botRoom(out, 1, h);
        r.setHpTuning(Room.HpTuning.on());
        Room.Member human = h[0], bot = RoomTest.botsOf(r).getFirst();
        bot.hp = 100; human.hp = 100;
        bot.protectUntil = 0; human.protectUntil = 0;
        bot.score = 100; human.score = 300;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(bot.hp).isCloseTo(25.0, within(0.001)); // 300이 100을 친다: 75점
        assertThat(human.hp).isCloseTo(75.0, within(0.001));
        bot.hp = 10; bot.protectUntil = 0; human.protectUntil = 0;
        for (int i = 0; i < 25; i++) { // 1초 뒤 다시 부딪힌다
            human.x = 810; human.y = 800; bot.x = 800; bot.y = 800; bot.protectUntil = 0; human.protectUntil = 0; // 잃은 뒤 무적을 풀어 같은 자리에서 다시 부딪히게 한다
            r.tick();
            if (bot.dead) break;
        }
        assertThat(bot.dead).isTrue();
        assertThat(out.kills).containsExactly(human.playerId);
    }

    @Test
    void roundingKeepsTwoDecimalsAndShowsRoundedUpPercent() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member a = human(r, 1, 7, 800), b = human(r, 2, 3, 810);
        r.tick(); // a는 100×3/10 = 30점, b는 100×7/10 = 70점
        assertThat(a.hp).isEqualTo(70.0);
        assertThat(b.hp).isEqualTo(30.0);
        assertThat(r.hpDamage(a, b)).isEqualTo(70.0);
        Room.Member c = human(r, 3, 1, 5000), d = human(r, 4, 2, 5010);
        assertThat(r.hpDamage(c, d)).isEqualTo(33.33); // 100×1/3, 소수 둘째 자리 반올림
        c.hp = 0.01;
        assertThat(r.hpMessage(c, 0).get("pct")).isEqualTo(1); // 0.01점이 남았으면 1%로 보이고 살아 있다
    }

    @Test
    void disabledHpLeavesTheOldRulesUntouched() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out); // 기본은 꺼짐
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        Room.Member a = human(r, 1, 300, 800), b = human(r, 2, 100, 810);
        r.tick();
        assertThat(a.hp).isEqualTo(100.0);
        assertThat(b.hp).isEqualTo(100.0);
        assertThat(out.loses).hasSize(1);
    }

    @Test
    void deadBotLosesHalfItsKeywordsDropsOrbsAndComesBackWithTheRest() {
        // 봇도 사람처럼: 체력이 0이 되면 키워드의 절반(내림)을 잃고 ! 구슬이 떨어지며, 죽인 사람은 알림을 받고, 같은 봇이 남은 키워드·EXP로 다시 나온다(DB 없이 메모리에서만)
        RoomTest.Out out = new RoomTest.Out();
        Room.Member[] h = new Room.Member[1];
        Room r = RoomTest.botRoom(out, 1, h);
        r.setHpTuning(Room.HpTuning.on());
        Room.Member human = h[0], bot = RoomTest.botsOf(r).getFirst();
        bot.botKeywords.clear();
        for (long id = 1; id <= 4; id++) bot.botKeywords.add(new Room.BotKw(id));
        bot.botExp = 10; bot.score = 100; bot.hp = 5; bot.protectUntil = 0; human.protectUntil = 0;
        human.score = 900; human.hp = 100;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(bot.dead).isTrue();
        assertThat(bot.botKeywords).hasSize(2); // 4개 중 절반(2개)을 잃었다
        assertThat(r.prizeOrbCount()).isEqualTo(1); // 잃은 활성 키워드 2개 → 절반인 구슬 1개
        assertThat(out.texts).anyMatch(m -> "hit".equals(m.get("type")) && Integer.valueOf(2).equals(m.get("wiped")) && Boolean.TRUE.equals(m.get("partial")));
        assertThat(out.kills).containsExactly(human.playerId);
        Room.Member again = null;
        for (int i = 0; i < 60 && again == null; i++) {
            r.tick();
            again = RoomTest.botsOf(r).stream().filter(x -> x != bot).findFirst().orElse(null);
        }
        assertThat(again).isNotNull();
        assertThat(again.botExp).isEqualTo(10);        // 같은 봇이 이어받는다
        assertThat(again.botKeywords).hasSize(2);      // 남은 절반
        assertThat(again.hp).isEqualTo(100.0);         // 체력은 가득
    }

    @Test
    void killerHealsByTheVictimsShareOfTheTwoScores() {
        // 처치 회복: 100 × 처치당한 ÷ (처치당한 + 내) %. 센 쪽이 약한 쪽을 잡으면 조금, 약한 쪽이 센 쪽을 잡으면 많이
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member strong = human(r, 1, 900, 800), weak = human(r, 2, 100, 810);
        strong.hp = 50; weak.hp = 5;
        r.tick(); // 900이 100을 친다: 약한 쪽 죽음, 강한 쪽은 10점 맞는다 → 40, 처치 회복 100×100/1000 = 10 → 50
        assertThat(weak.dead).isTrue();
        assertThat(strong.hp).isCloseTo(50.0, within(0.001));

        RoomTest.Out out2 = new RoomTest.Out();
        Room r2 = room(out2);
        Room.Member w = human(r2, 3, 100, 800), s = human(r2, 4, 900, 810);
        w.hp = 100; s.hp = 5;
        r2.tick(); // 100이 900을 친다(10점)로 강한 쪽이 죽고, 약한 쪽은 90점 맞아 10 → 처치 회복 100×900/1000 = 90 → 100
        assertThat(s.dead).isTrue();
        assertThat(w.hp).isCloseTo(100.0, within(0.001));
    }

    @Test
    void levelingUpRestoresHpToFull() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = room(out);
        Room.Member a = human(r, 1, 100, 800);
        a.hp = 20;
        r.applyScore(1, 100, 1);   // 레벨이 그대로면 회복하지 않는다
        assertThat(a.hp).isEqualTo(20.0);
        r.applyScore(1, 100, 2);   // 레벨업
        assertThat(a.hp).isEqualTo(100.0);
        assertThat(out.texts).anyMatch(m -> "hp".equals(m.get("type")) && m.get("heal") != null); // 회복 알림도 나간다
    }

    @Test
    void killHealCanBeSwitchedOff() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        r.setHpTuning(new Room.HpTuning(true, 100, 1, 5, true, 2, true, 50, 50, 0));
        Room.Member strong = human(r, 1, 900, 800), weak = human(r, 2, 100, 810);
        strong.hp = 50; weak.hp = 5;
        r.tick();
        assertThat(strong.hp).isCloseTo(40.0, within(0.001));
    }

    @Test
    void botsWeighFlameAndHpWhenChoosingToChaseOrFlee() {
        // 봇의 승산(botMatchup): 점수만이 아니라 불꽃 배수와 남은 체력으로 "몇 번 쳐야 쓰러지나"를 비교한다
        Room r = room(new RoomTest.Out());
        Room.Member bot = human(r, 1, 100, 800), big = human(r, 2, 130, 5000), small = human(r, 3, 70, 8000);
        assertThat(r.botMatchup(bot, big)).isEqualTo(-1);   // 점수가 높으면 피한다(체력이 같으면 예전과 같다)
        assertThat(r.botMatchup(bot, small)).isEqualTo(1);  // 낮으면 쫓는다
        bot.combo = 5;                                      // 불꽃이 되면 공격력 ×5라 점수가 더 높은 상대도 사냥감이다
        assertThat(r.botMatchup(bot, big)).isEqualTo(1);
        bot.combo = 0; big.combo = 5;                       // 상대가 불꽃이면 점수가 낮아도 위협이다
        assertThat(r.botMatchup(bot, small)).isEqualTo(1);
        small.combo = 5;
        assertThat(r.botMatchup(bot, small)).isEqualTo(-1);
        small.combo = 0; big.combo = 0;
        bot.hp = 5;                                         // 체력이 얼마 안 남으면 점수가 낮아도 한두 대에 죽으니 쫓지 않는다
        assertThat(r.botMatchup(bot, small)).isNotEqualTo(1);
        bot.hp = 100; big.hp = 5;                           // 상대 체력이 바닥이면 점수가 조금 높아도 쫓는다
        assertThat(r.botMatchup(bot, big)).isEqualTo(1);
    }
}
