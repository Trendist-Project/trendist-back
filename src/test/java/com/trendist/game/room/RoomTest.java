package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomTest {
    static final Room.Tuning T = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 0, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0);

    /** 충돌 테스트: 무작위 세이프존을 끄고, 가운데 고정 세이프존(한 변 600px) 밖에서 부딪히게 한다(4.22). */
    static final Room.ZoneTuning NO_RANDOM_ZONES = new Room.ZoneTuning(0.06, 600, 0.035, 400, 0, 0, 300);

    static class Out implements Room.Outbox {
        final List<long[]> loses = new ArrayList<>();
        final List<ByteBuffer> sent = new ArrayList<>();
        int snapshots;
        /** 모든 키워드 잃기(KeywordLoss). 아래 목록들은 원인별로 나눠 본 것이다 */
        final List<KeywordLoss> losses = new ArrayList<>();
        public void requestLoss(Room room, KeywordLoss l) {
            losses.add(l);
            if (l.victim().bot) { botWinners.add(l.by().playerId); botWinKeywords.add(l.botKeywordId()); return; }
            switch (l.way()) {
                case STEAL -> loses.add(new long[]{l.victim().playerId, l.by() == null ? 0 : l.by().playerId}); // 빼앗기(몸싸움·갈고리·흡수)
                case DROP -> poisonLoses.add(new long[]{l.victim().playerId, l.amount()});                      // 떨구기(독·충격파·화염구)
                case PERCENT -> {                                                                                 // 불꽃·죽음
                    boolean death = l.cause() == KeywordLoss.Cause.DEATH;
                    halves.add(new long[]{l.victim().playerId, l.by().playerId, l.amount(), death ? 1 : 0});
                    if (!death) wipes.add(new long[]{l.victim().playerId, l.by().playerId});
                }
            }
        }
        final List<Long> orbEaters = new ArrayList<>();
        public void requestOrb(Room room, Room.Member eater, boolean prize) { (prize ? prizeEaters : orbEaters).add(eater.playerId); }
        final List<Long> prizeEaters = new ArrayList<>();
        final List<long[]> wipes = new ArrayList<>();
        final List<long[]> halves = new ArrayList<>(); // {잃은 사람, 이긴 사람, 비율, 죽음이면 1}
        final List<String> savedSlots = new ArrayList<>(); // 저장 요청된 장착 4칸
        public void saveSkillSlots(Room room, Room.Member member, List<String> slots) { savedSlots.add(member.playerId + ":" + String.join(",", slots.stream().map(x -> x == null ? "" : x).toList())); }
        final List<long[]> poisonLoses = new ArrayList<>(); // 떨구기: {당한 사람, 잃은 개수}
        final List<Long> defeats = new ArrayList<>();
        public void requestDefeat(Room room, Room.Member loser) { defeats.add(loser.playerId); }
        final List<Long> steals = new ArrayList<>();
        public void requestSteal(Room room, Room.Member attacker) { steals.add(attacker.playerId); }
        final List<long[]> comboRecords = new ArrayList<>(); // {플레이어, 콤보}
        public void requestCombo(Room room, Room.Member winner, int combo) { comboRecords.add(new long[]{winner.playerId, combo}); }
        final List<Long> flameOuts = new ArrayList<>();
        public void requestFlameOut(Room room, Room.Member by) { flameOuts.add(by.playerId); }
        final List<Long> ties = new ArrayList<>();
        public void requestTie(Room room, Room.Member member) { ties.add(member.playerId); }
        final List<Long> kills = new ArrayList<>();
        public void requestKill(Room room, Room.Member killer) { kills.add(killer.playerId); }
        final List<Long> botWinners = new ArrayList<>();
        final List<Long> botWinKeywords = new ArrayList<>();
        public void send(Room.Member m, ByteBuffer binary) { snapshots++; sent.add(binary); }
        final List<Long> closed = new ArrayList<>();
        public void close(Room.Member m) { closed.add(m.playerId); }
        final List<Map<String, Object>> texts = new ArrayList<>(); // 사람에게 보낸 글 메시지(봇에게 가는 것은 실제로는 버려진다)
        public void sendText(Room.Member m, Map<String, Object> message) { if (!m.bot) texts.add(message); }
    }

    @Test
    void comboExpiresAfterMaxSecondsAndEachNewComboRestartsTheTime() {
        // 4.25: 콤보 유지시간: 마지막으로 콤보가 오른 때부터 maxSeconds가 지나면 연속 횟수와 불꽃이 사라진다(expired). 콤보가 추가될 때마다 다시 시작한다. 0이면 제한 없음
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setComboTuning(new Room.ComboTuning(5, true, 10));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        r.fightResult(a, b, false); // 콤보 1, 지금부터 10초
        for (int i = 0; i < 20 * 6; i++) r.tick();
        assertThat(a.combo).isEqualTo(1); // 6초 뒤에는 아직 유지
        r.fightResult(a, b, false);       // 콤보 2: 유지시간이 다시 10초부터 시작한다
        for (int i = 0; i < 20 * 6; i++) r.tick();
        assertThat(a.combo).isEqualTo(2); // 처음부터 12초가 지났지만 마지막 콤보로부터는 6초라 유지
        for (int i = 0; i < 20 * 6; i++) r.tick();
        assertThat(a.combo).isZero();     // 마지막 콤보로부터 12초: 사라진다
        assertThat(out.texts).anyMatch(m -> "combo".equals(m.get("type")) && "expired".equals(m.get("event")) && m.get("e").equals(a.entityNo));
        // 제한을 끄면(0) 오래 지나도 유지된다
        r.setComboTuning(new Room.ComboTuning(5, true, 0));
        r.fightResult(a, b, false);
        for (int i = 0; i < 20 * 30; i++) r.tick();
        assertThat(a.combo).isEqualTo(1);
    }

    @Test
    void comboLightsFlameAndBreaksOnLoss() {
        // 4.25: 5번 연속 이기면 불꽃이 붙고(start), 한 번이라도 잃으면 콤보와 불꽃이 모두 사라진다(broken)
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setComboTuning(new Room.ComboTuning(5, false, 300));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        for (int i = 0; i < 4; i++) r.fightResult(1L, 2L, false);
        r.fightResult(1L, null, true); // 봇을 이긴 것은 세지 않는 설정
        assertThat(a.combo).isEqualTo(4);
        r.fightResult(1L, 2L, false);
        assertThat(a.combo).isEqualTo(5);
        assertThat(out.texts).anyMatch(m -> "combo".equals(m.get("type")) && "start".equals(m.get("event")));
        r.fightResult(2L, 1L, false);
        assertThat(a.combo).isZero();
        assertThat(b.combo).isEqualTo(1);
        assertThat(out.texts).anyMatch(m -> "combo".equals(m.get("type")) && "broken".equals(m.get("event")) && "b".equals(m.get("by")));
    }

    @Test
    void levelUpBroadcastsPillarSignalToEveryone() {
        // 4.30: 레벨이 오르면 그 원에서 빛기둥이 나오도록 방 전체에 알린다(꾸미기와 무관한 고정 연출). 레벨이 그대로면 보내지 않는다
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.join(2, "s2", "b", 1, 0, List.of());
        r.applyScore(1L, 5, 1); // 점수만 바뀜
        assertThat(out.texts).noneMatch(m -> "levelFx".equals(m.get("type")));
        r.applyScore(1L, 9, 2);
        assertThat(out.texts.stream().filter(m -> "levelFx".equals(m.get("type"))))
                .hasSize(2) // 두 사람 모두에게
                .allMatch(m -> m.get("e").equals(a.entityNo) && m.get("level").equals(2));
    }

    /** 키워드가 없는 a(점수 0)와 있는 b(점수 50)를 가운데 세이프존 밖에서 겹치게 한다. */
    private static Room deathRoom(Out out, List<Long> aKeywords) {
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 0, aKeywords);
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.protectUntil = 0; b.protectUntil = 0;
        a.x = 800; a.y = 800; b.x = 810; b.y = 800;
        return r;
    }

    @Test
    void overlappingPairFightsOnlyOnceBecauseTheLoserBecomesAGhost() {
        // 4.3: 쿨다운 없이도 같은 싸움이 되풀이되지 않는다. 진 쪽이 잃은 뒤 무적이 되어 다음 틱부터 통과한다
        Out out = new Out();
        Room r = deathRoom(out, List.of(100L));
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
        for (int i = 0; i < 20; i++) { // 1초 동안 계속 겹쳐 있어도(매 틱 같은 자리로 되돌린다)
            a.x = 800; a.y = 800; b.x = 810; b.y = 800;
            r.tick();
        }
        assertThat(out.loses).hasSize(1);
    }

    @Test
    void noKeywordsAndBeatenMeansDeath() {
        // 4.31: 키워드가 하나도 없는 채로 지면 죽는다. 방에서 빠지고, 죽은 사람에게 died(죽인 사람 이름), 죽인 사람에게 kill(상대 이름)을 보낸다
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.setHpTuning(Room.HpTuning.on()); // 죽음은 체력이 0이 될 때만 온다(4.36)
        Room.Member a = r.byPlayer(1);
        r.tick();
        assertThat(a.dead).isTrue();
        assertThat(r.byPlayer(1)).isNull();
        assertThat(r.members()).hasSize(1);
        assertThat(out.loses).isEmpty(); // 잃을 키워드가 없으니 키워드 잃기 요청은 없다
        assertThat(out.texts).anyMatch(m -> "died".equals(m.get("type")) && "b".equals(m.get("by")));
        assertThat(out.texts).anyMatch(m -> "kill".equals(m.get("type")) && "a".equals(m.get("target")));
        assertThat(out.kills).containsExactly(2L); // 죽인 횟수 기록 요청(4.31)
        assertThat(out.defeats).containsExactly(1L); // 진 사람의 패배 횟수 기록 요청(4.31)
    }

    @Test
    void withKeywordsOrProtectionNobodyDies() {
        Out out = new Out();
        Room r = deathRoom(out, List.of(100L)); // 키워드가 있으면 하나 잃을 뿐
        r.tick();
        assertThat(r.byPlayer(1)).isNotNull();
        assertThat(out.loses).hasSize(1);
        Out out2 = new Out();
        Room r2 = deathRoom(out2, List.of());
        r2.byPlayer(1).protectUntil = 1e9; // 보호 중이면 죽지 않는다
        r2.tick();
        assertThat(r2.byPlayer(1)).isNotNull();
        assertThat(out2.texts).noneMatch(m -> "died".equals(m.get("type")));
    }

    @Test
    void withHpOnAKeywordlessLoserSurvivesWithTheHpThatIsLeft() {
        // 체력 방식(4.36): 키워드가 없어도 체력이 0이 되기 전에는 죽지 않는다. 99%를 맞아도 1%가 남으면 산다(키워드 없음 즉사 규칙은 체력이 꺼졌을 때만)
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.setHpTuning(Room.HpTuning.on()); // 죽음은 체력이 0이 될 때만 온다(4.36)
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
        a.score = 1; b.score = 99; // a가 받는 피해 = 100 × 99 ÷ (1 + 99) = 99
        r.tick();
        assertThat(a.dead).isFalse();
        assertThat(r.byPlayer(1)).isNotNull();
        assertThat(a.hp).isEqualTo(1.0);
        assertThat(out.defeats).isEmpty();
        assertThat(a.protectUntil).isGreaterThan(r.now()); // 잃을 키워드가 없어도 졌으니 무적이 붙는다
        assertThat(out.texts).anyMatch(m -> "invincibility".equals(m.get("type")) && m.get("e").equals(a.entityNo));
    }

    @Test
    void pushingAwayTheOtherRaisesComboAndGettingPushedResetsItEvenWhenNoKeywordMoves() {
        // 4.25: 충돌에서 상대를 튕겨 낸 쪽(큰 쪽)은 콤보 +1, 튕겨 나간 쪽(작은 쪽)은 콤보 0. 체력 피해·키워드와 관계없이 물리가 정한다
        Out out = new Out();
        Room r = deathRoom(out, List.of()); // a는 키워드가 없어 잃을 것도 없다
        r.setHpTuning(Room.HpTuning.on());
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
        a.score = 1; a.radius = a.displayRadius = r.radiusFor(1);
        b.score = 99; b.radius = b.displayRadius = r.radiusFor(99); // 크기 차이가 커서 작은 a만 튕긴다
        a.combo = 2;
        r.tick();
        assertThat(b.combo).isEqualTo(1);
        assertThat(a.combo).isZero();
    }

    @Test
    void whenBothBounceTogetherBothLoseComboAndNoOneGains() {
        // 4.25: 크기가 비슷해 서로 튕기면(반지름 비율 1.15 미만) 튕겨 나간 둘 다 콤보를 잃고, 튕겨 낸 쪽이 없으니 오르는 사람도 없다
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
        a.score = 50; b.score = 50; a.radius = a.displayRadius = b.radius = b.displayRadius = r.radiusFor(50);
        a.combo = 2; b.combo = 3;
        r.tick();
        assertThat(a.combo).isZero();
        assertThat(b.combo).isZero();
    }

    @Test
    void keywordResultOfACollisionNeverChangesCombo() {
        // 4.25: 콤보는 스킬 히트와 튕김으로만 바뀐다. 충돌에서 키워드가 오가는 것(진 쪽이 키워드를 잃고 이긴 쪽이 빼앗는 것)은 콤보를 올리거나 끊지 않는다
        Out out = new Out();
        Room r = fightRoom(out, 0, 0);
        r.setHpTuning(Room.HpTuning.on());
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2); // a 10, b 50 → 큰 b가 a를 튕겨 낸다
        a.radius = a.displayRadius = r.radiusFor(10); b.radius = b.displayRadius = r.radiusFor(50);
        a.combo = 4;
        r.tick();
        assertThat(b.combo).isEqualTo(1);                   // 튕겨 낸 쪽 +1 한 번
        assertThat(a.combo).isZero();                       // 튕겨 나간 쪽 0
        r.tick();                                           // 같은 충돌이 이어져도(쿨타임 안) 더 오르지 않는다
        assertThat(b.combo).isEqualTo(1);
    }

    @Test
    void killingWithHpDamageRaisesComboOnlyOnce() {
        // 체력 싸움으로 죽이면 hpDuel이 올리고 die의 싸움 결과가 또 올리지 않는다(+1)
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.setHpTuning(Room.HpTuning.on());
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
        a.score = 1; b.score = 99; a.hp = 50; // a는 99를 받아 죽는다
        a.radius = a.displayRadius = r.radiusFor(1); b.radius = b.displayRadius = r.radiusFor(99);
        r.tick();
        assertThat(r.byPlayer(1)).isNull();
        assertThat(b.combo).isEqualTo(1);
    }

    @Test
    void flameKillsSomeoneWithNoKeywords() {
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.setHpTuning(Room.HpTuning.on()); // 죽음은 체력이 0이 될 때만 온다(4.36)
        r.byPlayer(2).combo = 5; // 불꽃이 붙은 쪽은 피해가 5배라 키워드 없는 쪽의 체력이 바닥난다
        r.tick();
        assertThat(r.byPlayer(1)).isNull();
        assertThat(out.wipes).isEmpty();
    }

    @Test
    void deadPlayerKeepsWatchingLiveRoomAndIsNotCountedAsSpectator() {
        // 4.31: 죽은 뒤에도 방의 스냅샷을 계속 받아 움직임을 본다. 관리자 관전자 수에는 세지 않고, 리스폰하면 구경 목록에서 빠진다
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.setHpTuning(Room.HpTuning.on()); // 죽음은 체력이 0이 될 때만 온다(4.36)
        Room.Member a = r.byPlayer(1);
        r.tick();
        assertThat(a.dead).isTrue();
        assertThat(r.spectators()).contains(a);
        assertThat(r.spectatorCount()).isZero();
        int before = out.snapshots;
        for (int i = 0; i < 6; i++) r.tick();
        assertThat(out.snapshots).isGreaterThan(before); // 스냅샷이 계속 간다
        Room.Member again = r.join(1, "s1", "a", 1, 0, List.of());
        assertThat(r.spectators()).doesNotContain(a).doesNotContain(again);
    }

    @Test
    void botWithNoKeywordsDiesAndRespawnsInCenterAndKillIsRecorded() {
        // 4.31: 봇도 키워드가 없는 채로 지면 죽고(죽인 사람에게 kill, 죽인 횟수 기록), 다음 봇 채우기에서 가운데 세이프존에 새 봇이 나온다
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        r.setHpTuning(Room.HpTuning.on()); // 죽음은 체력이 0이 될 때만 온다(4.36)
        Room.Member human = h[0], bot = botsOf(r).getFirst();
        bot.botKeywords.clear();
        bot.botExp = 0;
        bot.score = 0; bot.radius = r.radiusFor(0); bot.displayRadius = bot.radius;
        bot.protectUntil = 0; human.protectUntil = 0;
        human.score = 500; human.radius = r.radiusFor(500); human.displayRadius = human.radius;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(bot.dead).isTrue();
        assertThat(r.members()).doesNotContain(bot);
        assertThat(out.kills).containsExactly(human.playerId);
        assertThat(out.defeats).isEmpty(); // 봇의 패배는 세지 않는다(사람만)
        assertThat(out.texts).anyMatch(m -> "kill".equals(m.get("type")) && "Bot".equals(m.get("target")));
        // 1초마다 봇을 채운다. 새 봇이 나온 그 틱에서 바로 자리를 본다(그 뒤로는 봇이 스스로 움직여 세이프존을 벗어날 수 있다)
        Room.Member again = null;
        for (int i = 0; i < 25 && again == null; i++) {
            r.tick();
            again = botsOf(r).stream().filter(x -> x != bot).findFirst().orElse(null);
        }
        assertThat(again).isNotNull();
        assertThat(r.inSafeZone(again.x, again.y)).isTrue();
        assertThat(again.protectUntil).isZero(); // 안전한 가운데에서 시작하므로 보호는 주지 않는다(4.31)
    }

    @Test
    void respawnStartsInCenterSafeZoneWithoutProtection() {
        // 4.31: 죽은 뒤 다시 들어오면 죽은 자리가 아니라 가운데 고정 세이프존에서 시작한다. 안전한 자리라 보호(무적)는 주지 않는다
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.setHpTuning(Room.HpTuning.on()); // 죽음은 체력이 0이 될 때만 온다(4.36)
        r.tick();
        assertThat(r.byPlayer(1)).isNull();
        Room.Member again = r.join(1, "s1", "a", 1, 0, List.of(), Room.Appearance.DEFAULT);
        assertThat(again.dead).isFalse();
        assertThat(r.inSafeZone(again.x, again.y)).isTrue();
        assertThat(again.protectUntil).isZero();
    }

    @Test
    void radiusFollowsDocumentCurve() {
        Room r = new Room(1, T, new Out());
        assertThat(r.radiusFor(0)).isEqualTo(16);
        assertThat(r.radiusFor(1000)).isEqualTo(308);
        assertThat(r.radiusFor(5000)).isEqualTo(600);
    }

    @Test
    void sameSpeedRegardlessOfSize() {
        Room r = new Room(1, T, new Out());
        Room.Member small = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member big = r.join(2, "s2", "b", 1, 1500, List.of());
        small.x = -1000; small.y = -1000; big.x = 1000; big.y = 1000;
        r.setInput(small, 1, 0); r.setInput(big, 1, 0);
        for (int i = 0; i < 40; i++) r.tick();
        assertThat(Math.abs(small.vx - big.vx)).isLessThan(0.001);
        assertThat(small.vx).isGreaterThan(230);
    }

    @Test
    void idlePlayerStaysStill() {
        Room r = new Room(1, T, new Out());
        Room.Member m = r.join(1, "s1", "a", 1, 0, List.of());
        m.x = 100; m.y = -50;
        for (int i = 0; i < 200; i++) r.tick(); // 10초 동안 아무 입력 없음
        assertThat(m.x).isEqualTo(100);
        assertThat(m.y).isEqualTo(-50);
    }

    @Test
    void shortKeyTapMovesOnlyForHeldTime() {
        // 한 틱(50ms) 안에서 10ms에 눌러 30ms에 떼면 20ms만큼만 움직인다(틱 경계로 반올림하지 않는다)
        long[] now = {1_000_000_000L};
        Room r = new Room(1, T, new Out(), () -> now[0]);
        Room.Member m = r.join(1, "s1", "a", 1, 0, List.of());
        m.x = 0; m.y = 0;
        r.tick();                                   // 기준 틱
        long base = now[0];
        r.submit(room -> room.setInput(m, 1, 0, base + 10_000_000L)); // 소켓 스레드처럼 큐로 넣으면 다음 틱 시작에 처리된다
        r.submit(room -> room.setInput(m, 0, 0, base + 30_000_000L));
        now[0] += 50_000_000L;
        r.tick();
        for (int i = 0; i < 40; i++) { now[0] += 50_000_000L; r.tick(); } // 멈출 때까지
        // 정확한 식: 20ms 가속 뒤 감속해 멈춘 거리 = 240 × 0.02 (속도 곡선 아래 넓이와 같다)
        assertThat(m.x).isCloseTo(240 * 0.02, org.assertj.core.data.Offset.offset(0.05));
        assertThat(m.vx).isCloseTo(0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void orbIsEatenWhenCircleCoversIt() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member m = r.join(1, "s1", "a", 1, 0, List.of());
        m.x = 1000; m.y = 1000; // 가운데 세이프존 밖(존 안의 구슬은 사라진다)
        r.placeOrb(1005, 1000); // 원(반지름 16) 안
        r.placeOrb(1300, 1300); // 멀리
        r.tick();
        assertThat(out.orbEaters).containsExactly(1L);
        assertThat(r.orbCount()).isEqualTo(1);
    }

    @Test
    void orbsSpawnUpToMax() {
        Room r = new Room(1, new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 5, 0.5, 2.2, 5, 33, 0, 0, 1.5, 100, 0), new Out());
        r.join(1, "s1", "a", 1, 0, List.of());
        for (int i = 0; i < 20 * 10; i++) r.tick(); // 10초
        assertThat(r.orbCount()).isEqualTo(5);
    }

    @Test
    void lowerScoreLosesKeywordOnCollision() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.x = 800; a.y = 800; b.x = 810; b.y = 800;
        r.tick();
        assertThat(out.loses).hasSize(1);
        assertThat(out.loses.get(0)[0]).isEqualTo(1L);
        assertThat(out.snapshots).isGreaterThanOrEqualTo(0);
    }

    /** 두 사람을 가운데 세이프존 밖에서 겹치게 하고 a에 콤보를 준다. */
    private static Room fightRoom(Out out, int comboA, int comboB) {
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.protectUntil = 0; b.protectUntil = 0;
        a.combo = comboA; b.combo = comboB;
        a.x = 800; a.y = 800; b.x = 810; b.y = 800;
        return r;
    }

    @Test
    void flameBeatsNonFlameRegardlessOfScoreAndWipesKeywords() {
        // 4.25: 불꽃인 사람(점수 10)이 불꽃 없는 사람(점수 50)과 부딪히면 점수와 상관없이 이기고 상대는 키워드를 모두 잃는다
        Out out = new Out();
        Room r = fightRoom(out, 5, 0);
        r.tick();
        assertThat(out.wipes).hasSize(1);
        assertThat(out.wipes.get(0)).containsExactly(2L, 1L);
        assertThat(out.loses).isEmpty();
    }

    @Test
    void protectedCircleIsAGhostThatPassesThroughOthers() {
        // 4.3: 무적(잃은 뒤·입장·리스폰 직후 보호)인 원은 유령처럼 충돌하지 않고 통과한다: 양쪽 모두 밀리거나 튕기지 않고, 싸움(키워드 잃기·죽음)도 없다
        for (boolean protectBig : new boolean[]{false, true}) {
            Out out = new Out();
            Room r = fightRoom(out, 0, 0); // a(점수 10, 작음), b(점수 50, 큼), 겹쳐 있다
            Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
            (protectBig ? b : a).protectUntil = 1e9;
            double ax = a.x, ay = a.y, bx = b.x, by = b.y;
            for (int i = 0; i < 3; i++) r.tick();
            assertThat(a.x).isEqualTo(ax);
            assertThat(a.y).isEqualTo(ay);
            assertThat(b.x).isEqualTo(bx);
            assertThat(b.y).isEqualTo(by);
            assertThat(a.kbX).isZero();
            assertThat(b.kbX).isZero();
            assertThat(out.loses).isEmpty();
            assertThat(out.texts).noneMatch(m -> "knock".equals(m.get("type")));
        }
    }

    @Test
    void smallFlameStaysPutAndBiggerNonFlameIsKnockedAway() {
        // 4.25: 작은 불꽃이 큰 상대와 부딪히면 이기는 쪽은 불꽃이므로 물리도 같다: 불꽃은 제자리, 불꽃 아닌 쪽이 밀려나 튕긴다
        Out out = new Out();
        Room r = fightRoom(out, 5, 0); // a(점수 10, 작음, 불꽃), b(점수 50, 큼)
        Room.Member a = r.byPlayer(1), b = r.byPlayer(2);
        assertThat(b.displayRadius).isGreaterThan(a.displayRadius);
        double ax = a.x, ay = a.y, bx = b.x;
        r.tick();
        assertThat(a.x).isEqualTo(ax); // 이긴 불꽃은 밀려나지 않는다
        assertThat(a.y).isEqualTo(ay);
        assertThat(b.x).isGreaterThan(bx); // 불꽃 아닌 쪽이 불꽃에서 멀어진다(b가 a의 오른쪽)
        assertThat(b.kbX).isGreaterThan(0);
        assertThat(a.kbX).isZero();
        assertThat(out.wipes).hasSize(1);
        assertThat(out.texts).anyMatch(m -> "knock".equals(m.get("type")));
    }

    @Test
    void bothFlameFallBackToScoreRule() {
        Out out = new Out();
        Room r = fightRoom(out, 5, 5);
        r.tick();
        assertThat(out.wipes).isEmpty();
        assertThat(out.loses).hasSize(1);
        assertThat(out.loses.get(0)[0]).isEqualTo(1L); // 점수가 낮은 쪽이 하나 잃는다
    }

    @Test
    void noFlameKeepsScoreRule() {
        Out out = new Out();
        fightRoom(out, 4, 0).tick();
        assertThat(out.wipes).isEmpty();
        assertThat(out.loses).hasSize(1);
    }

    /** 봇이 가질 🔥 키워드 목록(4.21): 번호 1~4, 유행도 30·40·20·10 */
    static final List<Room.CatalogKw> CATALOG = List.of(new Room.CatalogKw(1, "k1", 30), new Room.CatalogKw(2, "k2", 40),
            new Room.CatalogKw(3, "k3", 20), new Room.CatalogKw(4, "k4", 10));

    private static Room botRoomOf(Room.Tuning tu, Out out) {
        Room r = new Room(1, tu, out);
        r.setKeywordCatalog(CATALOG);
        return r;
    }

    /** 사람 1명 + 봇 n명을 만들고, 봇들이 채워진 뒤 돌려준다. 사람은 멀리 두어 싸움에 끼지 않는다. */
    static Room botRoom(Out out, int bots, Room.Member[] humanOut) {
        Room r = botRoomOf(withBots(1 + bots), out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member human = r.join(1, "s1", "사람", 1, 100, List.of(11L));
        for (int i = 0; i < 25 * bots; i++) r.tick();
        humanOut[0] = human;
        human.protectUntil = 0;
        return r;
    }

    static List<Room.Member> botsOf(Room r) { return r.members().stream().filter(m -> m.bot).toList(); }

    @Test
    void humanFlameWipesBotKeywordsLikeAnyoneElse() {
        // 4.25: 봇도 사람과 같은 규칙이다. 불꽃인 사람이 불꽃 없는 봇과 부딪히면 점수와 상관없이 이기고 봇은 키워드 점수를 모두 잃는다
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        Room.Member human = h[0], bot = botsOf(r).getFirst();
        bot.protectUntil = 0; bot.botKeywords.clear(); bot.botKeywords.add(new Room.BotKw(1)); bot.botKeywords.add(new Room.BotKw(2));
        bot.combo = 3;
        human.combo = 5;
        bot.score = 900; human.score = 100;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(bot.botKeywords).hasSize(1); // 2개 중 절반(내림, 최소 1개)인 1개를 잃는다(4.36)
        assertThat(out.wipes).isEmpty(); // 봇은 DB 키워드가 없어 방 안에서 처리한다
        assertThat(out.steals).containsExactly(human.playerId); // 모두 잃게 해도 공격 성공 한 번 = 빼앗은 횟수 +1(4.3)
        assertThat(human.combo).isEqualTo(6); // 봇을 이긴 것도 콤보(combo_bot_counts 기본 켜짐)
        assertThat(bot.combo).isZero();       // 잃은 봇의 콤보는 사라진다
        assertThat(out.texts).anyMatch(m -> "hit".equals(m.get("type")) && Integer.valueOf(1).equals(m.get("count")) && "flame".equals(m.get("cause")));
        assertThat(r.prizeOrbCount()).isGreaterThanOrEqualTo(1); // 잃은 개수의 일부만큼 ! 구슬
    }

    @Test
    void botFlameWipesHumanKeywords() {
        // 불꽃이 붙은 봇이 불꽃 없는 사람과 부딪히면 점수와 상관없이 이기고 사람은 키워드를 모두 잃는다(이긴 쪽이 봇이어도)
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        Room.Member human = h[0], bot = botsOf(r).getFirst();
        bot.protectUntil = 0;
        bot.combo = 5;
        bot.score = 1; human.score = 500;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(out.wipes).hasSize(1);
        assertThat(out.wipes.get(0)).containsExactly(1L, bot.playerId);
        assertThat(out.loses).isEmpty();
    }

    @Test
    void botLosingToAHumanLosesItsCombo() {
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        Room.Member human = h[0], bot = botsOf(r).getFirst();
        bot.protectUntil = 0; bot.botKeywords.clear(); bot.botKeywords.add(new Room.BotKw(1));
        bot.combo = 4; human.combo = 0; // 불꽃 전(4)이라 점수 싸움이다. 지면 봇의 콤보가 사라진다
        bot.score = 10; human.score = 900;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(bot.combo).isZero();
        assertThat(out.botWinners).containsExactly(1L);
        assertThat(out.botWinKeywords).containsExactly(1L); // 봇이 잃은 바로 그 키워드를 사람이 빼앗는다(4.21)
        assertThat(out.texts).anyMatch(m -> "combo".equals(m.get("type")) && "reset".equals(m.get("event")) && "Bot".equals(m.get("nickname")));
    }

    private static int snapshotCount(ByteBuffer b) { byte[] x = b.array(); return ((x[9] & 0xff) << 8) | (x[10] & 0xff); }

    @Test
    void snapshotsOnlyIncludeCirclesAroundEachPlayersView() {
        // 관심 영역 전송(9.2): 사람마다 화면 둘레(보이는 범위 + 여유)의 원만 받는다. 내 원은 늘 들어가고, 전원이 범위 안이면 같은 배열을 나눠 쓴다. 표시 칸은 0
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        Room.Member c = r.join(3, "s3", "c", 1, 0, List.of());
        a.x = 0; a.y = 0; b.x = 300; b.y = 0; c.x = 4000; c.y = 0;
        for (int i = 0; i < 2; i++) r.tick();
        // 기본 보이는 범위(5000)에서는 모두 보인다: 같은 배열
        assertThat(out.sent).hasSizeGreaterThanOrEqualTo(3);
        assertThat(snapshotCount(out.sent.get(0))).isEqualTo(3);
        assertThat(out.sent.get(0).array()).isSameAs(out.sent.get(1).array());
        byte[] first = out.sent.get(0).array();
        for (int i = 0; i < 3; i++) assertThat(first[11 + i * 23 + 22]).isZero();
        // a가 좁은 범위(500)를 알리면 a는 가까운 b까지만 받고, 먼 c는 빠진다(내 원은 그대로)
        out.sent.clear();
        r.setView(a, 500, 500); // 여유를 더해도 약 1,050px
        a.x = 0; b.x = 300; c.x = 4000;
        for (int i = 0; i < 2; i++) r.tick();
        assertThat(out.sent.stream().filter(x -> snapshotCount(x) == 2)).isNotEmpty();
        assertThat(out.sent.stream().filter(x -> snapshotCount(x) == 3)).isNotEmpty(); // 범위를 알리지 않은 b·c는 여전히 모두 받는다
        // 한도: 너무 작은 값은 300으로 맞춘다
        r.setView(a, 1, 1);
        assertThat(a.viewHalfW).isEqualTo(Room.VIEW_MIN);
    }

    @Test
    void botKeywordsFollowCatalogAndBotTakesHumanKeyword() {
        // 4.21: 봇의 키워드는 실제 키워드 번호이고 점수는 목록의 현재 유행도다. 목록에서 사라진 키워드는 봇도 잃고, 이긴 봇은 사람이 잃은 키워드를 가진다
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        Room.Member bot = botsOf(r).getFirst();
        bot.botExp = 0; bot.botKeywords.clear(); bot.botKeywords.add(new Room.BotKw(1));
        r.setKeywordCatalog(List.of(new Room.CatalogKw(2, "k2", 40))); // 1번이 목록에서 사라졌다(비활성·금지)
        assertThat(bot.botKeywords).isEmpty();
        r.setKeywordCatalog(List.of(new Room.CatalogKw(1, "k1", 30), new Room.CatalogKw(3, "k3", 20)));
        bot.botKeywords.clear();
        r.botTakes(bot, 3);
        r.botTakes(bot, 3);  // 이미 가졌으면 두 번 넣지 않는다
        r.botTakes(bot, 99); // 목록에 없는 키워드는 가지지 않는다
        assertThat(bot.botKeywords).extracting(Room.BotKw::id).containsExactly(3L);
        assertThat(bot.score).isEqualTo(Math.min(bot.botExp, 100) + bot.level + 20); // 점수 = EXP 몫 + 레벨(1:1) + 키워드의 현재 유행도
        r.setKeywordCatalog(List.of(new Room.CatalogKw(3, "k3", 35))); // 유행도가 바뀌면 봇 점수도 따라간다
        assertThat(bot.score).isEqualTo(Math.min(bot.botExp, 100) + bot.level + 35);
    }

    @Test
    void botBeatingBotBuildsComboAndLoserResets() {
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 2, h);
        Room.Member human = h[0];
        List<Room.Member> bots = botsOf(r);
        Room.Member win = bots.get(0), lose = bots.get(1);
        human.x = 5000; human.y = 5000;
        win.protectUntil = 0; lose.protectUntil = 0;
        lose.botKeywords.clear(); lose.botKeywords.add(new Room.BotKw(1));
        win.botKeywords.clear();
        win.combo = 4; lose.combo = 3;
        win.score = 900; lose.score = 10;
        win.radius = win.displayRadius = r.radiusFor(900); lose.radius = lose.displayRadius = r.radiusFor(10); // 반지름도 점수를 따른다
        win.x = 800; win.y = 800; lose.x = 810; lose.y = 800;
        r.tick();
        assertThat(win.combo).isEqualTo(5); // 봇이 봇을 이겨도 콤보 +1(설정 켜짐), 불꽃이 붙는다
        assertThat(lose.combo).isZero();
        assertThat(win.botKeywords).extracting(Room.BotKw::id).containsExactly(1L); // 이긴 봇이 진 봇의 키워드를 가져간다(4.21)
        assertThat(lose.botKeywords).isEmpty();
        assertThat(out.texts).anyMatch(m -> "combo".equals(m.get("type")) && "start".equals(m.get("event")) && "Bot".equals(m.get("nickname")));
    }

    @Test
    void announceReachesEveryoneInTheRoom() {
        // 긴급 안내(4.29): 서버 전체 알림은 방의 모두에게 간다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.join(1, "s1", "a", 1, 0, List.of());
        r.join(2, "s2", "b", 1, 0, List.of());
        out.texts.clear();
        r.announce(Map.of("type", "emergency", "notice", "공지"));
        assertThat(out.texts.stream().filter(m -> "emergency".equals(m.get("type")))).hasSize(2);
    }

    @Test
    void botRosterCarriesComboSoFlamesShowToNewcomers() {
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        botsOf(r).getFirst().combo = 7;
        Room.Member late = r.join(2, "s2", "늦게", 1, 0, List.of());
        assertThat(late).isNotNull();
        assertThat(out.texts).anyMatch(m -> "roster".equals(m.get("type")) && m.get("players").toString().contains("combo=7"));
    }

    @Test
    void prizeOrbsDropAroundAndAlwaysGiveKeyword() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of());
        a.x = 1000; a.y = 1000;
        r.dropPrizeOrbs(1L, 5); // 잃은 활성 키워드 5개 → 절반 내림 2개(4.36)
        assertThat(r.prizeOrbCount()).isEqualTo(2);
        r.dropPrizeOrbs(1L, 1); // 1개 잃으면 절반이 0이어도 최소 1개
        r.dropPrizeOrbs(1L, 0); // 활성이 없었어도(💤만 잃음) 최소 1개
        assertThat(r.prizeOrbCount()).isEqualTo(4);
        a.x = -900; a.y = -900; // 떨어진 구슬에서 멀리
        r.placePrizeOrb(-903, -900);
        r.tick();
        assertThat(out.prizeEaters).containsExactly(1L);
        assertThat(out.orbEaters).isEmpty();
    }

    @Test
    void dashSpeedsUpForSetTimeThenCoolsDown() {
        // 4.27: 쓰면 3초 동안 최대 속도 2배, 쓴 때부터 10초가 지나야 다시 쓴다. 가만히 있을 때도 쓸 수 있다(그때부터 움직이면 빠르다)
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 0; a.y = 0;
        r.dash(a); // 정지 상태에서 시작
        assertThat(out.texts).anyMatch(m -> "dash".equals(m.get("type")));
        a.dashUntil = 0; a.dashReadyAt = 0; out.texts.clear(); // 다시 처음부터
        r.setInput(a, 1, 0);
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(a.vx).isGreaterThan(230).isLessThan(250); // 평소 240
        r.dash(a);
        assertThat(out.texts).anyMatch(m -> "dash".equals(m.get("type")) && m.get("ms").equals(3000L) && m.get("cooldownMs").equals(10000L) && m.get("mult").equals(2.0));
        assertThat(out.texts).anyMatch(m -> "dashFx".equals(m.get("type")) && m.get("e").equals(a.entityNo) && m.get("ms").equals(3000L)); // 모두에게 효과를 알린다
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(a.vx).isGreaterThan(470).isLessThan(490); // 240 × 2
        int dashes = (int) out.texts.stream().filter(m -> "dash".equals(m.get("type"))).count();
        r.dash(a); // 쿨타임 중
        assertThat(out.texts.stream().filter(m -> "dash".equals(m.get("type"))).count()).isEqualTo(dashes);
        for (int i = 0; i < 20 * 3; i++) r.tick();
        assertThat(a.vx).isGreaterThan(230).isLessThan(250); // 3초가 지나 원래 속도
        for (int i = 0; i < 20 * 10; i++) r.tick();
        r.dash(a); // 쿨타임 10초가 지났다
        assertThat(out.texts.stream().filter(m -> "dash".equals(m.get("type"))).count()).isEqualTo(dashes + 1);
    }

    @Test
    void dashNeedsToBeEquippedAndSitsInTheFirstSlotByDefault() {
        // 4.27: 대시도 칸에 장착해야 쓴다. 고른 적 없으면 1번 칸이 대시이고, 칸에서 빼면 쓸 수 없다
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        assertThat(r.loadoutMessage(a).get("slots")).isEqualTo(java.util.Arrays.asList("dash", "repel", "hook", "invincibility", "stealth")); // 이 방은 해금 레벨이 모두 1이라 2~5번 칸이 찬다
        r.dash(a);
        assertThat(out.texts).anyMatch(m -> "dash".equals(m.get("type")));
        a.dashUntil = 0; a.dashReadyAt = 0; out.texts.clear();
        r.setLoadout(a, java.util.Arrays.asList(null, null, null, null, null)); // 대시를 뺐다
        r.dash(a);
        assertThat(out.texts).noneMatch(m -> "dash".equals(m.get("type")));
        r.setLoadout(a, java.util.Arrays.asList(null, null, null, null, "dash")); // 다른 칸에 넣어도 된다
        r.dash(a);
        assertThat(out.texts).anyMatch(m -> "dash".equals(m.get("type")));
    }

    @Test
    void dashMultiplierComesFromTuning() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setDashTuning(new Room.DashTuning(2.0, 1, 5));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 0; a.y = 0;
        r.setInput(a, 0, 1);
        r.dash(a);
        for (int i = 0; i < 15; i++) r.tick();
        assertThat(a.vy).isGreaterThan(470).isLessThan(490); // 240 × 2
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(a.vy).isLessThan(250); // 1초가 지나 원래 속도
    }

    @Test
    void minimapSendsTop20ByRadiusEverySecond() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        for (int i = 0; i < 25; i++) r.join(i + 1, "s" + i, "p" + i, 1, i * 40, List.of());
        for (int i = 0; i < 20; i++) r.tick();
        ByteBuffer mm = out.sent.stream().filter(b -> b.get(0) == 2).findFirst().orElseThrow();
        assertThat(mm.get(1)).isEqualTo((byte) 20);
        assertThat(mm.getInt(2)).isEqualTo(25); // 가장 큰 아이콘이 먼저
        assertThat(mm.getFloat(2 + 12)).isGreaterThan(mm.getFloat(2 + 19 * 16 + 12));
    }

    @Test
    void keywordDeltaAppliesToConnectedHolders() {
        Room r = new Room(1, T, new Out());
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(7L));
        Room.Member b = r.join(2, "s2", "b", 1, 10, List.of(7L));
        r.applyKeywordDelta(7L, 5, 1L);
        assertThat(a.score).isEqualTo(10);
        assertThat(b.score).isEqualTo(15);
        assertThat(b.radius).isGreaterThan(a.radius);
    }

    @Test
    void liveViewHoldsOnlyConnectedPlayers() {
        // 랭킹은 지금 방에 있는 사람만 집계한다(4.14). 나가면 다음 갱신(1초)에 빠진다
        Room r = new Room(1, T, new Out());
        Room.Member a = r.join(1, "s1", "가", 3, 40, List.of());
        r.join(2, "s2", "나", 5, 120, List.of());
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(r.liveView()).containsOnlyKeys(1L, 2L);
        assertThat(r.liveView().get(2L)).isEqualTo(new Room.Live(2, 2, "나", 5, 120));
        r.leave(a);
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(r.liveView()).containsOnlyKeys(2L);
    }

    @Test
    void activeAreaScalesWithWorldSize() {
        // 적은 인원에서도 기준 맵 한 변의 1/4, 100명이면 한 변 전체(4.2)
        Room small = new Room(1, new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 0, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0), new Out());
        assertThat(small.activeArea(1)).isEqualTo(6000);
        assertThat(small.activeArea(100)).isEqualTo(24000);
        assertThat(new Room(1, T, new Out()).activeArea(1)).isEqualTo(3000);
    }

    @Test
    void orbTargetFollowsArea() {
        // 3,000×3,000px에 20개였던 비율(밀도 2.2)을 넓이에 비례해 유지한다. 상한 orbMax를 넘지 않는다(4.20)
        Room.Tuning tu = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 1500, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0);
        Room r = new Room(1, tu, new Out());
        assertThat(r.orbTarget(tu, 1500)).isEqualTo(20);     // 3,000px 영역
        assertThat(r.orbTarget(tu, 3000)).isEqualTo(79);     // 6,000px 영역
        assertThat(r.orbTarget(tu, 12000)).isEqualTo(1267);  // 24,000px 영역(100명)
        Room.Tuning capped = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 100, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0);
        assertThat(r.orbTarget(capped, 12000)).isEqualTo(100);
    }

    @Test
    void orbsFillInBatches() {
        // 목표 79개면 간격마다 5%(4개)씩 생긴다
        Room.Tuning tu = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 10, 1500, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0);
        Room r = new Room(1, tu, new Out());
        r.join(1, "s1", "a", 1, 0, List.of());
        r.tick();
        assertThat(r.orbCount()).isEqualTo(4);
    }

    static Room.Tuning withBots(int botPlayers) { return withBots(botPlayers, 0); }

    static Room.Tuning withBots(int botPlayers, int botMinPlayers) {
        return new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 3, 0, 3, 2.2, 5, 100, botPlayers, botMinPlayers, 1.5, 100, 0);
    }

    @Test
    void botMinimumStaysEvenWhenRoomIsFullOfHumans() {
        // 사람이 방 인원(3명) 이상이어도 봇은 최소 인원(1명)만큼 남는다. 사람이 없으면 봇도 없다(4.21)
        Room r = new Room(1, withBots(3, 1), new Out());
        for (int i = 0; i < 100; i++) r.tick();
        assertThat(r.members()).isEmpty();
        for (int p = 1; p <= 4; p++) r.join(p, "s" + p, "사람" + p, 1, 0, List.of());
        for (int i = 0; i < 20 * 5; i++) r.tick();
        assertThat(r.members().stream().filter(m -> m.bot).count()).isEqualTo(1);
    }

    @Test
    void botsFillRoomToTargetAndLeaveAsHumansJoin() {
        // 사람이 있으면 사람 + 봇이 10명이 되도록 1초에 하나씩 채우고, 사람이 늘면 하나씩 뺀다(4.21)
        Room r = new Room(1, withBots(10), new Out());
        for (int i = 0; i < 400; i++) r.tick();
        assertThat(r.members()).isEmpty(); // 사람이 없으면 봇도 없다
        r.join(1, "s1", "사람", 1, 400, List.of());
        for (int i = 0; i < 20 * 12; i++) r.tick();
        assertThat(r.members()).hasSize(10);
        assertThat(r.size()).isEqualTo(1); // 접속 인원·정원은 사람만 센다
        assertThat(r.liveView()).containsOnlyKeys(1L); // 랭킹에 봇은 없다
        assertThat(r.members().stream().filter(m -> m.bot)).allMatch(m -> m.nickname.equals("Bot") && m.keywords.isEmpty());
        r.join(2, "s2", "사람2", 1, 0, List.of());
        for (int i = 0; i < 20 * 3; i++) r.tick();
        assertThat(r.members()).hasSize(10);
        assertThat(r.members().stream().filter(m -> m.bot).count()).isEqualTo(8);
    }

    @Test
    void botsFollowPlayerRulesAndCap() {
        // 봇도 유행도 = min(EXP, 100) + 키워드 점수 합, 레벨 = 레벨표(EXP), 사람 평균 × 1.5(이 테스트의 배수)를 넘지 않는다
        Room r = botRoomOf(withBots(10), new Out());
        r.join(1, "s1", "사람", 1, 400, List.of());
        for (int i = 0; i < 20 * 12; i++) r.tick();
        for (Room.Member b : r.members()) {
            if (!b.bot) continue;
            long sum = b.botKeywords.stream().mapToLong(k -> CATALOG.stream().filter(c -> c.id() == k.id()).findFirst().orElseThrow().value()).sum();
            assertThat(b.score).isEqualTo(Math.min(b.botExp, 100) + b.level + sum);
            assertThat(b.level).isEqualTo(Room.defaultLevelForExp(b.botExp));
            assertThat(b.score).isLessThanOrEqualTo(600); // 400 × 1.5
            assertThat((long) b.botKeywords.size()).isLessThanOrEqualTo(b.botExp); // 추가한 횟수보다 많이 가질 수 없다
        }
        // 사람 평균이 작아지면 상한을 넘는 봇은 키워드를 내려놓거나 바뀐다
        r.members().stream().filter(m -> !m.bot).forEach(m -> m.score = 50);
        for (int i = 0; i < 20 * 30; i++) r.tick();
        assertThat(r.members().stream().filter(m -> m.bot)).allMatch(m -> m.score <= 75);
    }

    @Test
    void humanLosesKeywordToBotAndBotLosesKeywordToHuman() {
        Out out = new Out();
        Room r = botRoomOf(withBots(2), out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member human = r.join(1, "s1", "사람", 1, 1000, List.of(11L));
        for (int i = 0; i < 25; i++) r.tick(); // 봇 하나가 들어온다
        Room.Member bot = r.members().stream().filter(m -> m.bot).findFirst().orElseThrow();
        bot.protectUntil = 0; human.protectUntil = 0;

        // 봇이 더 크면 사람이 키워드를 잃는다(봇에게 져도 잃는다)
        bot.score = 900; human.score = 100;
        bot.x = 800; bot.y = 800; human.x = 810; human.y = 800;
        r.tick();
        assertThat(out.loses).containsExactly(new long[]{1L, bot.playerId});
        // 잃은 쪽(사람)에 3초 무적: 모두에게 보호막 카운트다운을 알린다
        assertThat(out.texts).anyMatch(t -> "invincibility".equals(t.get("type")) && t.get("e").equals(human.entityNo) && t.get("ms").equals(3000L));

        // 사람이 더 크면 봇은 (메모리의) 키워드 하나를 잃는다
        // 1등(사람) 점수를 먼저 되돌린다. 100점이면 봇 상한이 150이라, 기다리는 동안 봇이 상한 정리로 바뀔 수 있다
        bot.x = -2500; bot.y = -2500; human.x = 2500; human.y = 2500;
        human.score = 900; bot.botExp = 10; bot.botKeywords.clear(); bot.botKeywords.addAll(List.of(new Room.BotKw(1), new Room.BotKw(2))); bot.score = 80;
        for (int i = 0; i < 20; i++) r.tick(); // 둘을 떨어뜨린 채 1초 기다린다
        bot.protectUntil = 0; human.protectUntil = 0;
        bot.botExp = 10; bot.botKeywords.clear(); bot.botKeywords.addAll(List.of(new Room.BotKw(1), new Room.BotKw(2)));
        bot.score = 80; human.score = 900;
        bot.x = 2000; bot.y = 2000; human.x = 2010; human.y = 2000;
        r.tick();
        assertThat(bot.botKeywords).hasSize(1);
        assertThat(bot.score).isIn(43.0, 53.0); // 10(EXP) + 레벨 3(1:1) + 남은 키워드 30 또는 40
        // 이긴 사람은 보상으로 무작위 🔥 키워드를 받는다(틱 밖에서 처리하도록 요청), 봇에게도 무적 카운트다운
        assertThat(out.botWinners).containsExactly(human.playerId);
        assertThat(out.texts).anyMatch(t -> "invincibility".equals(t.get("type")) && t.get("e").equals(bot.entityNo));
        assertThat(out.loses).hasSize(1); // 봇이 진 것은 서버 키워드 잃기 요청이 아니다
    }

    @Test
    void knockbackDistanceFollowsMaxSpeed() {
        // 같은 크기끼리 부딪히면 각자 최대 속도 × 0.6초만큼(240px/s면 144px) 밀려나고, 처음에 빠르고 점점 느려진다(4.3)
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 100, List.of());
        a.x = 500; a.y = 800; b.x = 500 + 2 * a.radius - 10; b.y = 800; // 10px 겹침(가운데 세이프존 밖)
        double bStart = b.x;
        r.tick(); // 이번 틱에 부딪힘을 찾아 겹친 만큼 떼어 놓고, 튕김은 다음 틱부터 움직인다
        double afterHit = b.x;
        r.tick();
        double firstStep = b.x - afterHit;
        for (int i = 0; i < 20; i++) r.tick(); // 0.5초 넘게
        double total = b.x - bStart;
        assertThat(total).isBetween(144.0 + 5 - 1, 144.0 + 5 + 1); // 겹친 10px의 절반 + 144px
        assertThat(firstStep).isGreaterThan(144.0 / 5); // 처음 0.05초에 많이 밀려난다(ease-out, 약 27%)
        // 밀려난 사람에게는 같은 튕김을 알려 클라이언트 예측도 같이 밀린다
        assertThat(out.texts).anyMatch(t -> "knock".equals(t.get("type")));
    }

    @Test
    void silentConnectionIsDroppedFromRoom() {
        // 90초 동안 아무 메시지(ping 포함)도 없으면 연결을 닫고 끊긴 것으로 본다: 원은 남은 시간(60초) 동안 남았다가 빠진다(4.32). 메시지를 보내는 사람은 남는다
        long[] now = {1_000_000_000L};
        Out out = new Out();
        Room r = new Room(1, T, out, () -> now[0]);
        Room.Member silent = r.join(1, "s1", "유령", 1, 0, List.of());
        Room.Member alive = r.join(2, "s2", "사람", 1, 0, List.of());
        for (int i = 0; i < 20 * 95; i++) {
            now[0] += 50_000_000L;
            alive.lastHeardNanos = now[0]; // 2초마다 ping을 보내는 사람
            r.tick();
        }
        assertThat(out.closed).containsExactly(1L);   // 90초가 지나 연결을 닫았고
        assertThat(silent.offline).isTrue();          // 원은 아직 남아 있다
        assertThat(r.members()).contains(silent, alive);
        for (int i = 0; i < 20 * 65; i++) {           // 끊긴 뒤 60초가 지나면 진짜로 뺀다
            now[0] += 50_000_000L;
            alive.lastHeardNanos = now[0];
            r.tick();
        }
        assertThat(r.members()).containsExactly(alive);
        assertThat(out.closed).containsExactly(1L);
        assertThat(r.liveView()).containsOnlyKeys(2L); // 랭킹에서도 빠진다
    }

    @Test
    void noCollisionInsideSafeZone() {
        // 가운데 세이프존 안에서는 겹쳐 있어도 튕기지 않고 키워드도 잃지 않는다(4.22)
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.protectUntil = 0; b.protectUntil = 0;
        a.x = 0; a.y = 0; b.x = 10; b.y = 0;
        r.tick();
        assertThat(out.loses).isEmpty();
        assertThat(a.x).isEqualTo(0.0);
        assertThat(b.x).isEqualTo(10.0);
        assertThat(out.texts).noneMatch(t -> "knock".equals(t.get("type")));
    }

    @Test
    void everyJoinStartsInCenterZoneAndZonesAreSent() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        for (int i = 0; i < 20; i++) {
            Room.Member m = r.join(100 + i, "s" + i, "new", 1, 0, List.of());
            assertThat(Math.abs(m.x)).isLessThanOrEqualTo(450); // 가운데 존 한 변 900px
            assertThat(Math.abs(m.y)).isLessThanOrEqualTo(450);
        }
        assertThat(out.texts).anyMatch(t -> "safeZones".equals(t.get("type")));
    }

    @Test
    void randomZonesSpawnUpToTargetAndExpire() {
        // 24,000px 기준 맵 100명이면 넓이 비례로 여러 개, 맵이 3,000px이면 1개. 유지 시간이 지나면 사라진다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(new Room.ZoneTuning(0.06, 600, 0.035, 400, 6, 40, 2));
        r.join(1, "s1", "a", 1, 0, List.of());
        for (int i = 0; i < 25; i++) r.tick();
        assertThat(r.zoneCount()).isEqualTo(1);
        assertThat(r.centerZoneSize()).isEqualTo(600.0);
        for (int i = 0; i < 30; i++) r.tick(); // 2초가 지나면 사라지고 1초 뒤 다시 생긴다
        assertThat(r.zoneCount()).isLessThanOrEqualTo(1);
    }

    @Test
    void orbsInsideSafeZonesAreRemovedAndNotSpawned() {
        // 세이프존이 생기면 그 안의 구슬은 사라지고, 세이프존 위에는 구슬이 생기지 않는다(4.22)
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        r.join(1, "s1", "a", 1, 0, List.of());
        int inside = r.placeOrb(0, 0), outside = r.placeOrb(1200, 1200);
        r.tick();
        assertThat(out.texts).anyMatch(t -> "orbEaten".equals(t.get("type")) && t.get("id").equals(inside) && t.get("e").equals(0));
        assertThat(out.texts).noneMatch(t -> "orbEaten".equals(t.get("type")) && t.get("id").equals(outside));
        for (int i = 0; i < 400; i++) r.tick(); // 구슬이 다 찰 때까지
        assertThat(out.texts).noneMatch(t -> "orbSpawn".equals(t.get("type"))
                && Math.abs((double) t.get("x")) <= 300 + 14 && Math.abs((double) t.get("y")) <= 300 + 14);
    }

    @Test
    void botCapIsAverageOfConnectedHumans() {
        // 봇 상한 = 접속 중인 사람 유행도 평균 × 배수(기본 1). 봇은 평균에 들어가지 않는다(4.21)
        Room.Tuning avg = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 3, 0, 3, 2.2, 5, 100, 10, 0, 1.0, 100, 0);
        Room r = new Room(1, avg, new Out());
        r.join(1, "s1", "작은", 1, 100, List.of());
        r.join(2, "s2", "큰", 1, 500, List.of());
        assertThat(r.botCap(avg)).isEqualTo(300.0);
        for (int i = 0; i < 20 * 12; i++) r.tick(); // 봇이 들어와도 평균은 사람만으로
        assertThat(r.members().stream().filter(m -> m.bot)).isNotEmpty();
        assertThat(r.botCap(avg)).isEqualTo(300.0);
        assertThat(r.members().stream().filter(m -> m.bot)).allMatch(m -> m.score <= 300);
    }
    /** 스킬 테스트: 가운데 세이프존 밖(800, 800)에 두 사람을 놓는다. 키워드가 없으면 충돌해도 잃을 것이 없다. */
    private static Room skillRoom(Out out, SkillConfig config) {
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        r.setSkillConfig(config);
        return r;
    }

    private static long count(Out out, String type, String id) {
        return out.texts.stream().filter(m -> type.equals(m.get("type")) && (id == null || id.equals(m.get("id")))).count();
    }

    /** 방 전체에 보낸 메시지는 받는 사람마다 한 번씩 기록되므로 같은 메시지는 하나로 센다 */
    private static long countFx(Out out, String type, String id) {
        return out.texts.stream().filter(m -> type.equals(m.get("type")) && (id == null || id.equals(m.get("id")))).distinct().count();
    }

    /** 독·암흑 테스트: a(800,800)가 +x 쪽 b(1100,800)를 겨눈다. 가운데 세이프존 밖이고 서로 부딪히지 않는 거리다. */
    private static Room boltRoom(Out out) {
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        return r;
    }

    /** 독·암흑·정화를 장착한 채로 입장시킨다(장착해야만 쓸 수 있다, 4.33) */
    private static Room.Member joinEq(Room r, long id, String session, String name, int level, double score, List<Long> kws) {
        Room.Member m = r.join(id, session, name, level, score, kws);
        r.setLoadout(m, java.util.Arrays.asList("poison", "darkness", "cleanse", null, null));
        return m;
    }

    private static void tickSeconds(Room r, double seconds) { for (int i = 0; i < Math.round(seconds * 20); i++) r.tick(); }

    @Test
    void healSkillRestoresFiftyPercentOnceAndSkipsTheCooldownWhenHpIsFull() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setSkillConfig(SkillConfig.DEFAULT.withCooldown("heal", 60));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("heal", null, null, null, null));
        r.tick();
        r.skill(a, "heal", 0, 0);                       // 체력이 가득이면 쓰지 않는다(쿨타임도 안 쓴다)
        assertThat(a.hp).isEqualTo(100.0);
        a.hp = 40;
        r.skill(a, "heal", 0, 0);
        assertThat(a.hp).isEqualTo(90.0);               // 최대 체력의 50%
        r.skill(a, "heal", 0, 0);                       // 쿨타임 중이라 다시 채우지 않는다
        assertThat(a.hp).isEqualTo(90.0);
    }

    @Test
    void poisonBoltHitsAndLosesOneKeywordEveryThreeSecondsThreeTimes() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        Room.Member b = joinEq(r, 2, "s2", "b", 1, 0, List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "poison", b.x, b.y);
        assertThat(r.boltCount()).isEqualTo(1);
        tickSeconds(r, 0.6); // 거리 300px, 속도 900px/초
        assertThat(r.boltCount()).isZero();
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isEqualTo(1);
        tickSeconds(r, 2.2);
        assertThat(out.poisonLoses).isEmpty();            // 아직 3초 전
        tickSeconds(r, 0.9);
        assertThat(out.poisonLoses).hasSize(1);
        assertThat(out.poisonLoses.get(0)).containsExactly(2, 1);
        tickSeconds(r, 6.5);
        assertThat(out.poisonLoses).hasSize(3);           // 3초, 6초, 9초
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isZero();
        tickSeconds(r, 5);
        assertThat(out.poisonLoses).hasSize(3);           // 끝난 뒤에는 더 잃지 않는다
    }

    @Test
    void poisonStacksWhenHitAgainAndRestartsTheNineSecondCount() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        Room.Member b = joinEq(r, 2, "s2", "b", 1, 0, List.of());
        Room.Member c = joinEq(r, 3, "s3", "c", 1, 0, List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800; c.x = 1400; c.y = 800;
        r.tick();
        r.skill(a, "poison", b.x, b.y);
        tickSeconds(r, 2.5);                              // 첫 독이 끝나기 전(첫 틱 직전)
        r.skill(c, "poison", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isEqualTo(2);
        assertThat(out.poisonLoses).isEmpty();            // 다시 맞았으니 3초를 처음부터 센다
        tickSeconds(r, 3.2);
        assertThat(out.poisonLoses).hasSize(1);
        assertThat(out.poisonLoses.get(0)).containsExactly(2, 1); // 2스택이어도 틱(히트) 하나에 1개
        tickSeconds(r, 6.5);
        assertThat(out.poisonLoses).hasSize(3);
        assertThat(out.poisonLoses).allSatisfy(l -> assertThat(l).containsExactly(2, 1));
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isZero();
    }

    @Test
    void invincibleOrSafeCirclesLetTheBoltPassThrough() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        Room.Member b = joinEq(r, 2, "s2", "b", 1, 0, List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        b.protectUntil = 1e9; // 방패·잃은 뒤 무적
        r.skill(a, "poison", b.x, b.y);
        tickSeconds(r, 2);
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isZero();
        assertThat(r.boltCount()).isZero(); // 사거리 끝에서 사라진다
    }

    @Test
    void darknessBoltTellsTheVictimAndCleanseClearsEverythingEvenWhilePulled() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        Room.Member b = joinEq(r, 2, "s2", "b", 1, 0, List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "darkness", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(count(out, "dark", null)).isEqualTo(1);
        assertThat(r.status().has(b, StatusEffects.Kind.DARK)).isTrue();
        r.skill(a, "poison", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isEqualTo(1);
        b.hookedBy = a; b.pullLeft = 1; // 갈고리에 끌려오는 중에도 정화는 쓴다
        r.skill(b, "cleanse", 0, 0);
        assertThat(b.hookedBy).isNull();
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isZero();
        assertThat(r.status().has(b, StatusEffects.Kind.DARK)).isFalse();
        assertThat(count(out, "darkEnd", null)).isEqualTo(1);
        assertThat(count(out, "pullEnd", null)).isEqualTo(1);
        assertThat(countFx(out, "skillFx", "cleanse")).isEqualTo(1);
        tickSeconds(r, 10);
        assertThat(out.poisonLoses).hasSize(1); // 암흑에 맞을 때 1개만 잃었고, 풀린 독은 더 잃지 않는다
        r.skill(a, "poison", b.x, b.y);        // 면역 시간이 0이라 바로 다시 걸린다
        tickSeconds(r, 0.6);
        assertThat(r.status().stacks(b, StatusEffects.Kind.POISON)).isEqualTo(1);
    }

    @Test
    void newSkillsHaveNoCooldownByDefault() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "poison", 1100, 800);
        r.skill(a, "poison", 1100, 800);
        assertThat(r.boltCount()).isEqualTo(2);
        r.setSkillConfig(SkillConfig.DEFAULT.withCooldown("poison", 5));
        r.skill(a, "poison", 1100, 800);
        r.skill(a, "poison", 1100, 800); // 쿨타임 5초 중
        assertThat(r.boltCount()).isEqualTo(3);
    }

    @Test
    void unequippedSkillCannotBeUsedAndEquippingChangesWhatWorks() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        assertThat(r.equippedSkills(a)).containsExactly("dash", "repel", "hook", "invincibility", "stealth"); // 고른 적 없으면 1번 칸은 대시, 2번 칸부터 해금된 것이 순서대로
        r.skill(a, "poison", 1100, 800);
        assertThat(r.boltCount()).isZero();
        assertThat(count(out, "skillFail", "poison")).isEqualTo(1);
        r.setLoadout(a, java.util.Arrays.asList("poison", null, "invincibility", null, null));
        assertThat(r.equippedSkills(a)).containsExactly("poison", "invincibility"); // 한 번 고르면 고른 대로(대시도 빼면 없다)
        r.skill(a, "poison", 1100, 800);
        assertThat(r.boltCount()).isEqualTo(1);
        r.skill(a, "repel", 0, 0); // 해제했으니 못 쓴다
        assertThat(count(out, "skill", "repel")).isZero();
        assertThat(out.savedSlots).containsExactly("1:poison,,invincibility,,");
    }

    @Test
    void loadoutAcceptsDbOnlySkillsWithoutServerRulesButStillRejectsUnknownCodes() {
        // 서버 규칙이 아직 없는 새 스킬(DB에만 있음)도 해금되고 켜져 있으면 칸에 장착된다. DB에 없는 코드는 여전히 거절한다
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("future_a", 28)); // future_a·future_b는 서버 규칙(SKILLS)에 없고 DB에만 있는 가상의 스킬
        r.setKnownSkills(java.util.Set.of("future_a", "future_b"));
        Room.Member a = r.join(1, "s1", "a", 30, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("future_a", "future_b", "invincibility", null, null)); // future_b는 레벨 1이라 바로 장착된다(levelsMap에 없어도 DB에 있다)
        assertThat(r.equippedSkills(a)).containsExactly("future_a", "future_b", "invincibility");
        assertThat(out.savedSlots).containsExactly("1:future_a,future_b,invincibility,,");
        r.setLoadout(a, java.util.Arrays.asList("nope", null, null, null, null)); // DB에 없는 코드는 거절하고 옛 장착을 유지한다
        assertThat(r.equippedSkills(a)).containsExactly("future_a", "future_b", "invincibility");
        r.setDisabledSkills(java.util.Set.of("future_a"));                       // 꺼진 스킬은 장착에서 빠진다
        assertThat(r.equippedSkills(a)).containsExactly("future_b", "invincibility");
        a.level = 10;                                                        // 해금 레벨이 안 되면 장착 못 한다
        r.setDisabledSkills(java.util.Set.of());
        r.setLoadout(a, java.util.Arrays.asList("future_a", null, null, null, null));
        assertThat(r.equippedSkills(a)).doesNotContain("future_a");
    }

    @Test
    void loadoutRejectsLockedUnknownDuplicateOrWrongSizeAndKeepsTheOldOne() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("poison", 40, "darkness", 30, "heal", 40, "slow", 45, "stun", 50, "fireball", 55, "quake", 60, "absorb", 65, "zone", 70, "burst", 75));
        Room.Member a = r.join(1, "s1", "a", 20, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("repel", null, null, null, null));
        r.setLoadout(a, java.util.Arrays.asList("poison", null, null, null, null));          // 40레벨 전이라 잠겨 있다
        r.setLoadout(a, java.util.Arrays.asList("hook", "hook", null, null, null));          // 중복
        r.setLoadout(a, java.util.Arrays.asList("nope", null, null, null, null));            // 모르는 스킬
        r.setLoadout(a, java.util.Arrays.asList("hook", "invincibility", "stealth", "repel", "x", "y")); // 칸 수가 다르다
        assertThat(r.equippedSkills(a)).containsExactly("repel");
        assertThat(out.savedSlots).containsExactly("1:repel,,,,");
        a.level = 40;
        r.setLoadout(a, java.util.Arrays.asList("poison", "darkness", "repel", null, null));
        assertThat(r.equippedSkills(a)).containsExactly("poison", "darkness", "repel");
    }

    @Test
    void slotCountComesFromTheCatalogSoAFifthSlotNeedsNoCodeChange() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setSkillSlotCount(6);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("repel", "hook", "invincibility", "stealth", "poison", "dash"));
        assertThat(r.equippedSkills(a)).containsExactly("repel", "hook", "invincibility", "stealth", "poison", "dash");
        assertThat(r.loadoutMessage(a).get("slots")).isEqualTo(java.util.Arrays.asList("repel", "hook", "invincibility", "stealth", "poison", "dash"));
        r.setLoadout(a, java.util.Arrays.asList("hook", null, null, null, null)); // 칸 수가 다르면 거절한다
        assertThat(r.equippedSkills(a)).hasSize(6);
        assertThat(out.savedSlots).containsExactly("1:repel,hook,invincibility,stealth,poison,dash");
    }

    @Test
    void defaultLoadoutFillsInUnlockOrderAndRestoreReadsTheSavedSlots() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("invincibility", 10, "hook", 15, "repel", 20, "stealth", 25, "darkness", 30, "cleanse", 35, "poison", 40, "heal", 40, "slow", 45, "stun", 50, "fireball", 55, "quake", 60, "absorb", 65, "zone", 70, "burst", 75));
        Room.Member a = r.join(1, "s1", "a", 12, 0, List.of());
        assertThat(r.equippedSkills(a)).containsExactly("dash", "invincibility");
        assertThat(r.loadoutMessage(a).get("slots")).isEqualTo(java.util.Arrays.asList("dash", "invincibility", null, null, null)); // 1번 칸은 대시, 해금 스킬은 2번 칸부터
        a.level = 40;
        assertThat(r.equippedSkills(a)).containsExactly("dash", "invincibility", "hook", "repel", "stealth"); // 2~5번 칸이 해금 순서대로 찬다
        r.restoreLoadout(a, java.util.Arrays.asList("poison", null, "cleanse", "bogus", null));
        assertThat(r.equippedSkills(a)).containsExactly("poison", "cleanse");
        assertThat(r.loadoutMessage(a).get("slots")).isEqualTo(java.util.Arrays.asList("poison", null, "cleanse", null, null));
        r.restoreLoadout(a, null);
        assertThat(r.equippedSkills(a)).hasSize(5);
    }

    @Test
    void repelSpreadsFromCastPointAtSetSpeedAndPushesOnlyWhatItReaches() {
        // 4.33: 쓴 순간 내 원 크기로 시작해 가장자리에서 화면 기준 300px까지 최대 속도 × 4(화면 기준)로 퍼진다. 닿은 원만 밀려나고, 쿨타임은 30초
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member near = r.join(2, "s2", "b", 1, 0, List.of());
        Room.Member far = r.join(3, "s3", "c", 1, 0, List.of());
        a.x = 800; a.y = 800;
        double r0 = a.displayRadius;
        near.x = 800 + r0 + near.displayRadius + 10; near.y = 800; // 가장자리 사이 10px
        far.x = 800 - (r0 + r.reachWorld(300, r0) + far.displayRadius + 40); far.y = 800; // 범위(가장자리에서 300px) 밖(밀려난 near와 부딪히지 않게 반대쪽)
        r.tick();
        r.skill(a, "repel", Double.NaN, Double.NaN);
        var fx = out.texts.stream().filter(m -> "skillFx".equals(m.get("type")) && "repel".equals(m.get("id"))).findFirst().orElseThrow();
        assertThat(fx.get("x")).isEqualTo(800.0);
        assertThat((double) fx.get("r0")).isEqualTo(r0);
        assertThat((double) fx.get("rMax")).isEqualTo(r0 + r.reachWorld(300, r0));
        assertThat((double) fx.get("speed")).isEqualTo(r.reachWorld(960.0, r0)); // 최대 속도 240 × 4(대시 속도의 2배)
        for (int i = 0; i < 6; i++) r.tick();
        assertThat(near.kbTime).isGreaterThan(0);  // 닿아서 바깥쪽으로 밀렸다
        assertThat(near.x).isGreaterThan(800 + r0 + near.displayRadius + 10);
        assertThat(far.kbTime).isZero();
        assertThat(count(out, "skill", "repel")).isEqualTo(1);
        r.skill(a, "repel", Double.NaN, Double.NaN); // 쿨타임 중
        assertThat(count(out, "skill", "repel")).isEqualTo(1);
        for (int i = 0; i < 20 * 30; i++) r.tick();
        r.skill(a, "repel", Double.NaN, Double.NaN);
        assertThat(count(out, "skill", "repel")).isEqualTo(2);
    }

    @Test
    void repelRangeSpeedAndCooldownComeFromTuning() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT.withParam("repel_speed_multiplier", 1).withCooldown("repel", 7).withParam("hook_seconds", 0.3).withCooldown("hook", 20).withCooldown("stealth", 40));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "repel", 0, 0);
        var fx = out.texts.stream().filter(m -> "skillFx".equals(m.get("type"))).findFirst().orElseThrow();
        assertThat((double) fx.get("rMax")).isEqualTo(a.displayRadius + r.reachWorld(300, a.displayRadius)); // 가장자리에서 화면 기준 300px
        assertThat((double) fx.get("speed")).isEqualTo(240.0);
        var skill = out.texts.stream().filter(m -> "skill".equals(m.get("type"))).findFirst().orElseThrow();
        assertThat(skill.get("cooldownMs")).isEqualTo(7000L);
    }

    @Test
    void offensiveSkillsCannotBeUsedInsideSafeZone() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 0; a.y = 0; // 가운데 세이프존
        r.tick();
        r.skill(a, "repel", 1, 0);
        r.skill(a, "hook", 100, 0);
        assertThat(count(out, "skillFail", "repel")).isEqualTo(1);
        assertThat(count(out, "skillFail", "hook")).isEqualTo(1);
        assertThat(count(out, "skill", null)).isZero();
    }

    @Test
    void hookCatchesFirstCircleOnLineLosesOneKeywordAndPullsItIntoAForcedCollision() {
        // 4.33: 갈고리가 사거리(가장자리에서 화면 기준 300px) 안의 원을 맞히면 키워드 1개를 잃고 0.3초 동안 끌려와 무조건 부딪힌다(여기서는 점수가 낮아 한 번 더 잃는다)
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 50, List.of(100L)); // 점수가 높다: 부딪히면 이긴다
        Room.Member b = r.join(2, "s2", "b", 1, 10, List.of(200L));
        a.x = 800; a.y = 800;
        double r0 = a.displayRadius;
        b.x = 800 + r0 * 2.5; b.y = 800;
        r.tick();
        r.skill(a, "hook", b.x, b.y);
        assertThat(out.texts).anyMatch(m -> "skillFx".equals(m.get("type")) && "hook".equals(m.get("id")) && m.get("ms").equals(150L) && (double) m.get("range") == r0 + r.reachWorld(300, r0));
        for (int i = 0; i < 4; i++) r.tick();
        assertThat(out.loses).hasSize(1); // 맞는 순간 키워드 1개
        assertThat(out.loses.get(0)).containsExactly(2L, 1L);
        assertThat(out.texts).anyMatch(m -> "pull".equals(m.get("type")) && m.get("ms").equals(300L));
        assertThat(countFx(out, "skillFx", "hookHit")).isEqualTo(1);
        for (int i = 0; i < 10; i++) r.tick();
        assertThat(out.loses).hasSize(2); // 끌려와서 부딪혀 한 번 더(점수가 낮은 쪽이 진다)
        assertThat(b.hookedBy).isNull();
        assertThat(b.protectUntil).isGreaterThan(0); // 다 끌려온 뒤 잃은 뒤 무적이 시작됐다
    }

    @Test
    void hookMissesWhenTargetIsOutOfRangeOrOffTheLine() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 50, List.of(100L));
        Room.Member far = r.join(2, "s2", "b", 1, 10, List.of(200L));
        Room.Member off = r.join(3, "s3", "c", 1, 10, List.of(300L));
        a.x = 800; a.y = 800;
        double r0 = a.displayRadius;
        far.x = 800 + r0 + r.reachWorld(300, r0) + far.displayRadius + 30; far.y = 800; // 사거리 밖
        off.x = 800 + r0 * 2; off.y = 800 + 200;                    // 선에서 벗어남
        r.tick();
        r.skill(a, "hook", 5000, 800);
        for (int i = 0; i < 10; i++) r.tick();
        assertThat(out.loses).isEmpty();
        assertThat(countFx(out, "skillFx", "hookHit")).isZero();
        assertThat(countFx(out, "skillFx", "hookEnd")).isEqualTo(1);
    }

    @Test
    void hookAndBoltReachLookTheSameLengthOnScreenAtAnyCircleSize() {
        // 사거리는 가장자리에서 화면 기준 px(갈고리 300, 투사체 600)라 월드 길이에 줌의 역수가 곱해진다: 큰 원일수록 월드 길이가 길다
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        for (double radius : new double[]{16, 100, 300}) {
            double zoom = 1 / r.reachWorld(1, radius); // 클라이언트 줌
            assertThat(r.reachWorld(300, radius) * zoom).isCloseTo(300, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(r.reachWorld(600, radius) * zoom).isCloseTo(600, org.assertj.core.data.Offset.offset(1e-9));
        }
        assertThat(r.reachWorld(600, 16)).isEqualTo(600);                       // 최소 크기는 줌 1: 화면 px = 월드 px
        assertThat(r.reachWorld(600, 300)).isGreaterThan(r.reachWorld(600, 100)); // 커질수록 월드 길이가 길다
    }

    @Test
    void boltRangeComesFromTuningAndBoltEndsAtThatDistance() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "poison", 5000, 800);
        assertThat(out.texts).anyMatch(m -> "bolt".equals(m.get("id")) && (double) m.get("range") == r.reachWorld(600, a.displayRadius));
    }

    @Test
    void botCannotSeeAStealthedPlayerSoItNeitherChasesNorFleesFromIt() {
        // 4.21, 4.33: 은신 중인 사람은 사람에게 안 보이듯 봇에게도 안 보인다. 보이면 사냥감 위치로 목표를 잡고, 은신이면 잡지 않는다
        for (boolean stealth : new boolean[]{false, true}) {
            Out out = new Out();
            Room.Member[] h = new Room.Member[1];
            Room r = botRoom(out, 1, h);
            Room.Member human = h[0], bot = botsOf(r).getFirst();
            bot.botStyle = 1; // 사냥꾼: 멀리서도 쫓는다
            bot.score = 500; bot.radius = bot.displayRadius = r.radiusFor(500);
            human.score = 0; human.radius = human.displayRadius = r.radiusFor(0);
            bot.protectUntil = 0; human.protectUntil = 0;
            bot.x = 800; bot.y = 800; human.x = 1500; human.y = 800;
            bot.safe = false; human.safe = false; // 세이프존 표시는 틱에서 갱신되므로 옮긴 위치에 맞춰 둔다
            if (stealth) human.stealthUntil = r.now() + 5;
            tickSeconds(r, 0.8); // 봇은 반응 속도(0.25~0.7초)마다 다시 판단한다
            boolean chasing = bot.targetX != null && Math.abs(bot.targetX - human.x) < 1 && Math.abs(bot.targetY - human.y) < 1;
            assertThat(chasing).isEqualTo(!stealth);
        }
    }

    @Test
    void darknessBoltAlsoHitsABotAndEveryoneSeesIt() {
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        Room.Member human = h[0], bot = botsOf(r).getFirst();
        r.setLoadout(human, java.util.Arrays.asList("darkness", null, null, null, null));
        bot.protectUntil = 0; human.protectUntil = 0;
        human.x = 800; human.y = 800; bot.x = 1100; bot.y = 800;
        r.tick();
        bot.x = 1100; bot.y = 800;
        r.skill(human, "darkness", bot.x, bot.y);
        tickSeconds(r, 0.4);
        assertThat(r.status().has(bot, StatusEffects.Kind.DARK)).isTrue();
        assertThat(countFx(out, "skillFx", "darkness")).isEqualTo(1); // 모두에게 표시하려고 방송한다
    }

    @Test
    void collidingWithEqualScoreCountsATieForEachHumanOncePerSecondAndLosesNothing() {
        // 4.3: 현재 유행도가 같은 두 원이 부딪히면 누구도 잃지 않고, 사람마다 "같은 유행도와 부딪힌 횟수"가 센다(같은 짝은 1초에 한 번)
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 10, List.of(200L));
        a.x = 800; a.y = 800; b.x = 800 + a.displayRadius + b.displayRadius - 5; b.y = 800;
        a.protectUntil = 0; b.protectUntil = 0;
        r.tick();
        assertThat(out.loses).isEmpty();
        assertThat(out.ties).containsExactlyInAnyOrder(1L, 2L);
        // 1초 안에 다시 부딪혀도 또 세지 않는다
        a.x = 800; b.x = 800 + a.displayRadius + b.displayRadius - 5;
        r.tick();
        assertThat(out.ties).hasSize(2);
        // 1초가 지난 뒤 다시 부딪히면 센다
        for (int i = 0; i < 25; i++) r.tick();
        a.x = 800; a.y = 800; b.x = 800 + a.displayRadius + b.displayRadius - 5; b.y = 800;
        r.tick();
        assertThat(out.ties).hasSize(4);
    }

    @Test
    void differentScoresAreNotATie() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.x = 800; a.y = 800; b.x = 800 + a.displayRadius + b.displayRadius - 5; b.y = 800;
        a.protectUntil = 0; b.protectUntil = 0;
        r.tick();
        assertThat(out.ties).isEmpty();
    }

    @Test
    void hookedCircleCannotUseSkillsAndOnlyCollidesWithTheHooker() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 50, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 10, List.of(200L));
        Room.Member d = r.join(3, "s3", "d", 1, 90, List.of(300L)); // 더 큰 원
        a.x = 800; a.y = 800;
        b.x = 800 + a.displayRadius * 2.5; b.y = 800;
        d.x = -800; d.y = -800;
        r.tick();
        r.skill(a, "hook", b.x, b.y);
        for (int i = 0; i < 4; i++) r.tick();
        assertThat(b.hookedBy).isSameAs(a);
        long skills = count(out, "skill", null);
        r.skill(b, "invincibility", 0, 0);   // 끌려오는 중: 스킬 불가
        r.dash(b);
        assertThat(count(out, "skill", null)).isEqualTo(skills);
        assertThat(b.protectUntil).isZero();
        out.loses.clear();
        d.x = b.x; d.y = b.y;         // 끌려오는 b와 겹친다: 충돌은 무효
        r.tick();
        assertThat(out.loses.stream().noneMatch(l -> (l[0] == 2L && l[1] == 3L) || (l[0] == 3L && l[1] == 2L))).isTrue(); // b와 d 사이의 싸움은 없다(d가 닿은 a와의 싸움은 따로)
    }

    @Test
    void hookedCircleKeepsNoProtectionUntilPullEndsAndDeadOwnerReleasesIt() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 50, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 10, List.of(200L));
        a.x = 800; a.y = 800;
        b.x = 800 + a.displayRadius * 2.5; b.y = 800;
        r.tick();
        r.skill(a, "hook", b.x, b.y);
        for (int i = 0; i < 4; i++) r.tick();
        assertThat(b.hookedBy).isSameAs(a);
        assertThat(b.protectUntil).isZero();
        r.leave(a); // 쓴 사람이 나갔다
        r.tick();
        assertThat(b.hookedBy).isNull();
    }

    @Test
    void invincibilitySkillGivesProtectionAndUsesCooldown() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "invincibility", 0, 0);
        assertThat(a.protectUntil).isGreaterThan(0);
        assertThat(out.texts).anyMatch(m -> "invincibility".equals(m.get("type")) && m.get("ms").equals(3000L) && m.get("e").equals(a.entityNo));
        assertThat(out.texts).anyMatch(m -> "skill".equals(m.get("type")) && "invincibility".equals(m.get("id")) && m.get("cooldownMs").equals(30000L));
    }

    @Test
    void stealthHidesFromMinimapAndEndsWhenAttackSkillIsUsed() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        a.x = 800; a.y = 800; b.x = -800; b.y = -800;
        r.tick();
        r.skill(a, "stealth", 0, 0);
        assertThat(out.texts).anyMatch(m -> "skillFx".equals(m.get("type")) && "stealth".equals(m.get("id")) && m.get("ms").equals(5000L));
        out.sent.clear();
        for (int i = 0; i < 20; i++) r.tick();
        ByteBuffer mm = out.sent.stream().filter(x -> x.get(0) == 2).findFirst().orElseThrow();
        assertThat(mm.get(1)).isEqualTo((byte) 1); // 은신 중인 a는 빠지고 b만
        assertThat(mm.getInt(2)).isEqualTo(b.entityNo);
        r.skill(a, "repel", 0, 0); // 공격 스킬을 쓰면 은신이 풀린다
        assertThat(a.stealthUntil).isZero();
        assertThat(countFx(out, "skillFx", "stealthEnd")).isEqualTo(1);
    }

    @Test
    void unknownSkillAndBotsAreIgnored() {
        Out out = new Out();
        Room r = skillRoom(out, SkillConfig.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.skill(a, "nuke", 0, 0);
        assertThat(count(out, "skill", null)).isZero();
    }
    @Test
    void comboRecordIsRequestedOnlyWhenItBeatsTheLastSentRecord() {
        // 4.10, 4.25: 콤보가 오를 때 이 접속에서 보낸 최고 기록을 넘을 때만 저장을 요청한다. 콤보가 끊겼다 다시 쌓여도 기록 이하면 요청하지 않는다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setComboTuning(new Room.ComboTuning(5, true, 300));
        r.join(1, "s1", "a", 1, 0, List.of());
        r.join(2, "s2", "b", 1, 0, List.of());
        for (int i = 0; i < 3; i++) r.fightResult(1L, 2L, false);
        assertThat(out.comboRecords).extracting(x -> x[1]).containsExactly(1L, 2L, 3L);
        r.fightResult(2L, 1L, false); // a의 콤보가 끊기고 b의 콤보 1
        for (int i = 0; i < 3; i++) r.fightResult(1L, 2L, false); // a가 다시 1, 2, 3: 기록(3)을 못 넘는다
        assertThat(out.comboRecords.stream().filter(x -> x[0] == 1L)).hasSize(3);
        r.fightResult(1L, 2L, false); // 4: 새 기록
        assertThat(out.comboRecords.stream().filter(x -> x[0] == 1L).map(x -> x[1]).toList()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void breakingAFlameCountsAsPuttingItOutButAPlainComboDoesNot() {
        // 4.25: 불꽃(콤보 5 이상)이 붙은 상대를 이겨 꺼뜨린 사람만 센다. 불꽃이 아닌 콤보를 끊거나 사람이 아닌 쪽이 끊은 것은 세지 않는다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setComboTuning(new Room.ComboTuning(5, true, 300));
        r.join(1, "s1", "a", 1, 0, List.of());
        r.join(2, "s2", "b", 1, 0, List.of());
        for (int i = 0; i < 3; i++) r.fightResult(2L, 1L, false); // b 콤보 3(불꽃 아님)
        r.fightResult(1L, 2L, false);                             // a가 b의 콤보를 끊는다
        assertThat(out.flameOuts).isEmpty();
        for (int i = 0; i < 5; i++) r.fightResult(2L, 1L, false); // b 불꽃
        r.fightResult(1L, 2L, false);                             // a가 불꽃을 끈다
        assertThat(out.flameOuts).containsExactly(1L);
    }
    @Test
    void comboResetsWhenTheSamePlayerComesBackAfterRefreshOrLeaving() {
        // 4.25: 새로고침하거나 화면을 떠났다가 원이 남아 있는 동안 다시 들어오면 콤보와 불꽃은 처음부터다(위치·점수는 이어받는다)
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setComboTuning(new Room.ComboTuning(5, true, 300));
        Room.Member a = r.join(1, "s1", "a", 1, 40, List.of());
        a.x = 800; a.y = 800;
        for (int i = 0; i < 6; i++) r.fightResult(a, null, true); // 콤보 6(불꽃)
        assertThat(a.combo).isEqualTo(6);
        r.disconnect(a, "s1");
        Room.Member back = r.join(1, "s2", "a", 1, 40, List.of()); // 같은 계정이 새 연결로 돌아온다
        assertThat(back).isSameAs(a);
        assertThat(a.combo).isZero();
        assertThat(a.x).isEqualTo(800.0); // 위치는 그대로
        assertThat(out.texts).anyMatch(m -> "combo".equals(m.get("type")) && "reset".equals(m.get("event")) && m.get("e").equals(a.entityNo) && Integer.valueOf(0).equals(m.get("combo")));
    }
    @Test
    void cooldownsSurviveRefreshAndAreSentAgainToTheReturningPlayer() {
        // 4.33: 쿨타임은 서버가 들고 있어서 새로고침(이어받기)으로 풀리지 않고, 다시 들어온 사람에게 남은 시간을 알려 준다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        assertThat(r.cooldownsMessage(a)).isNull();           // 쓴 적이 없으면 알릴 것이 없다
        r.skill(a, "invincibility", 0, 0);                            // 쿨타임 30초
        for (int i = 0; i < 20 * 10; i++) r.tick();            // 10초 뒤 새로고침
        r.disconnect(a, "s1");
        Room.Member back = r.join(1, "s2", "a", 1, 0, List.of());
        assertThat(back).isSameAs(a);
        var cd = r.cooldownsMessage(back);
        assertThat(cd).isNotNull();
        @SuppressWarnings("unchecked") var skills = (java.util.Map<String, Long>) cd.get("skills");
        assertThat(skills.get("invincibility")).isBetween(19_000L, 20_100L); // 남은 20초쯤
        int before = (int) out.texts.stream().filter(m -> "skill".equals(m.get("type"))).count();
        r.skill(back, "invincibility", 0, 0);                         // 새로고침했다고 바로 다시 쓸 수는 없다
        assertThat(out.texts.stream().filter(m -> "skill".equals(m.get("type"))).count()).isEqualTo(before);
    }

    @Test
    void clientCannotSkipCooldownByRepeatingOrForgingMessages() {
        // 서버가 판정한다: 같은 틱에 여러 번 보내도 한 번만 되고, 좌표를 NaN·무한대로 보내도 안전하게 처리한다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        for (int i = 0; i < 50; i++) r.skill(a, "repel", Double.NaN, Double.POSITIVE_INFINITY);
        assertThat(out.texts.stream().filter(m -> "skill".equals(m.get("type"))).count()).isEqualTo(1);
        for (int i = 0; i < 50; i++) r.skill(a, "hook", Double.NaN, Double.NaN);
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(a.x).isFinite();
    }
    @Test
    void skillsRequireTheirUnlockLevelAndTheServerEnforcesIt() {
        // 4.33: 방패 10레벨·갈고리 15레벨·충격파 20레벨·은신 25레벨, 대시는 기본. 레벨이 모자라면 서버가 막고 {skillFail: level}로 필요 레벨을 알린다
        Out out = new Out();
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        r.setRequiredLevels(levels("invincibility", 10, "hook", 15, "repel", 20, "stealth", 25, "heal", 40, "slow", 45, "stun", 50, "fireball", 55, "quake", 60, "absorb", 65, "zone", 70, "burst", 75));
        Room.Member a = r.join(1, "s1", "a", 9, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "invincibility", 0, 0);
        assertThat(count(out, "skill", null)).isZero();
        assertThat(out.texts).anyMatch(m -> "skillFail".equals(m.get("type")) && "level".equals(m.get("reason")) && Integer.valueOf(10).equals(m.get("level")) && "invincibility".equals(m.get("id")));
        assertThat(a.protectUntil).isZero();                  // 막혔으니 효과도 없다
        r.applyScore(1, 0, 10);                                // 10레벨이 되면 방패는 쓴다
        r.skill(a, "invincibility", 0, 0);
        assertThat(count(out, "skill", "invincibility")).isEqualTo(1);
        r.skill(a, "hook", 5000, 800);                         // 갈고리는 아직(15)
        r.skill(a, "stealth", 0, 0);                           // 은신도 아직(25)
        assertThat(count(out, "skill", "hook")).isZero();
        assertThat(count(out, "skill", "stealth")).isZero();
        r.dash(a);                                             // 대시는 기본 스킬(레벨 제한 없음): 막히지 않는다
        assertThat(out.texts.stream().noneMatch(m -> "skillFail".equals(m.get("type")) && "dash".equals(m.get("id")))).isTrue();
    }

    // ---- 구슬 먹기는 틱 사이에 움직인 경로(선분)로 판정한다: 점수가 낮은 작은 원이 최대 속도로 달릴 때 구슬을 지나치던 문제

    /** 운영 기본값(최소 반지름 16, 최대 속도 480)의 Tuning. 나머지는 T와 같다. 최대 속도에서 한 틱(50ms)에 24px 간다. */
    static final Room.Tuning FAST = new Room.Tuning(12000, 16, 600, 2000, 480, 170, 1.15, 3, 0.4, 0.6, 10, 0, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 0);

    /** (1000,1000)에서 +x 방향으로 이미 최대 속도(480)에 도달해 달리는 원 하나. 한 틱에 정확히 24px 간다. */
    private static Room.Member runner(Room r, long playerId, double y) {
        Room.Member m = r.join(playerId, "s" + playerId, "p" + playerId, 1, 0, List.of());
        m.x = 1000; m.y = y;
        m.protectUntil = 0;
        r.setInput(m, 1, 0);
        m.vx = 480;
        return m;
    }

    private static Room fastRoom(Out out) {
        Room r = new Room(1, FAST, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        return r;
    }

    @Test
    void lagCompensationExtendsTheSweepAlongTheVelocity() {
        // 입력 지연 0.1초(왕복 200ms)면 서버 위치(1024)를 속도 480 × 0.1 = 48px 앞(1072)으로 본다. 끝점 1024에서 60px 앞의 구슬(1084)은 30px 안
        Out lagOut = new Out();
        Room lagRoom = fastRoom(lagOut);
        Room.Member m = runner(lagRoom, 1, 1000);
        lagRoom.setLag(m, 200);
        lagRoom.placeOrb(1084, 1000);
        lagRoom.tick();
        assertThat(lagOut.orbEaters).containsExactly(1L);

        Out noLagOut = new Out();
        Room noLagRoom = fastRoom(noLagOut);
        runner(noLagRoom, 1, 1000);
        noLagRoom.placeOrb(1084, 1000);
        noLagRoom.tick();
        assertThat(noLagOut.orbEaters).isEmpty();
    }

    @Test
    void lagCompensationIsCappedAndIgnoresBadValues() {
        Room r = fastRoom(new Out());
        Room.Member m = runner(r, 1, 1000);
        r.setLag(m, 10_000);
        assertThat(m.lagSec).isEqualTo(Room.LAG_COMP_MAX_SEC);
        r.setLag(m, -5);
        assertThat(m.lagSec).isZero();
        r.setLag(m, Double.NaN);
        assertThat(m.lagSec).isZero();
    }

    @Test
    void minimumEatReachLetsSmallCirclesEatOrbsFurtherAway() {
        // 반지름 16 + 구슬 14 = 30px지만 최소 먹는 거리 64px면 진행선에서 60px 떨어진 구슬도 먹고, 70px는 먹지 않는다
        Room.Tuning reach = new Room.Tuning(12000, 16, 600, 2000, 480, 170, 1.15, 3, 0.4, 0.6, 10, 0, 3, 2.2, 5, 33, 0, 0, 1.5, 100, 64);
        Out nearOut = new Out();
        Room nearRoom = new Room(1, reach, nearOut);
        nearRoom.setZoneTuning(NO_RANDOM_ZONES);
        runner(nearRoom, 1, 1000);
        nearRoom.placeOrb(1012, 1060);
        nearRoom.tick();
        assertThat(nearOut.orbEaters).containsExactly(1L);

        Out farOut = new Out();
        Room farRoom = new Room(1, reach, farOut);
        farRoom.setZoneTuning(NO_RANDOM_ZONES);
        runner(farRoom, 1, 1000);
        farRoom.placeOrb(1012, 1070);
        farRoom.tick();
        assertThat(farOut.orbEaters).isEmpty();
    }

    @Test
    void orbSweepEatsOrbOnTheTravelLine() {
        Out out = new Out();
        Room r = fastRoom(out);
        Room.Member m = runner(r, 1, 1000);
        r.placeOrb(1012, 1000); // 선분(1000→1024) 한가운데: 현재 위치(1024)에서도 12px
        r.tick();
        assertThat(m.x).isCloseTo(1024, org.assertj.core.data.Offset.offset(0.01));
        assertThat(out.orbEaters).containsExactly(1L);
        assertThat(r.orbCount()).isZero();
    }

    @Test
    void orbSweepEatsOrbPassedBetweenTicks29pxOffTheLine() {
        // 회귀: 구슬이 선분 중간에서 진행선과 29px 떨어져(먹기 거리 16 + 14 = 30px 안) 있다. 시작(1000)과 끝(1024)에서는 각각 약 31px라 점 판정은 놓친다
        Out out = new Out();
        Room r = fastRoom(out);
        runner(r, 1, 1000);
        r.placeOrb(1012, 1029);
        r.tick();
        assertThat(out.orbEaters).containsExactly(1L);
        assertThat(r.orbCount()).isZero();
    }

    @Test
    void orbSweepDoesNotEatOrbOutsideTheRadius() {
        // 진행선에서 31px 떨어진 구슬은 먹기 거리(16 + 14 = 30px) 밖이다
        Out out = new Out();
        Room r = fastRoom(out);
        runner(r, 1, 1000);
        r.placeOrb(1012, 1031);
        r.tick();
        assertThat(out.orbEaters).isEmpty();
        assertThat(r.orbCount()).isEqualTo(1);
    }

    @Test
    void orbSweepUsesEndpointDistanceBeyondTheSegmentEnd() {
        // 수직 발이 선분 밖이면 끝점(1024)과의 거리로만 본다: 29px는 먹고 31px는 먹지 않는다
        Out nearOut = new Out();
        Room nearRoom = fastRoom(nearOut);
        runner(nearRoom, 1, 1000);
        nearRoom.placeOrb(1053, 1000); // 끝점에서 29px
        nearRoom.tick();
        assertThat(nearOut.orbEaters).containsExactly(1L);

        Out farOut = new Out();
        Room farRoom = fastRoom(farOut);
        runner(farRoom, 1, 1000);
        farRoom.placeOrb(1055, 1000); // 끝점에서 31px
        farRoom.tick();
        assertThat(farOut.orbEaters).isEmpty();
        assertThat(farRoom.orbCount()).isEqualTo(1);
    }

    @Test
    void orbSweepWorksDuringDash() {
        // 대시 중에는 한 틱에 48px 간다. 진행선에서 29px 떨어져 중간(1024)에 있는 구슬도 먹는다(끝점 1048과는 약 38px)
        Out out = new Out();
        Room r = fastRoom(out);
        r.setDashTuning(new Room.DashTuning(2.0, 1, 5));
        Room.Member m = runner(r, 1, 1000);
        m.vx = 960;
        r.dash(m);
        assertThat(m.dashUntil).isGreaterThan(0);
        r.placeOrb(1024, 1029);
        r.tick();
        assertThat(m.x).isCloseTo(1048, org.assertj.core.data.Offset.offset(0.01));
        assertThat(out.orbEaters).containsExactly(1L);
    }

    @Test
    void orbSweepAlsoAppliesToPrizeOrbs() {
        Out out = new Out();
        Room r = fastRoom(out);
        runner(r, 1, 1000);
        r.placePrizeOrb(1012, 1029);
        r.tick();
        assertThat(out.prizeEaters).containsExactly(1L);
        assertThat(r.prizeOrbCount()).isZero();
    }

    @Test
    void orbSweepGivesTheOrbToTheFirstCircleAndConsumesItOnce() {
        // 서로 닿지 않게 40px 떨어져(16+16=32 초과) 같은 방향으로 달리는 두 원이 모두 구슬(1012,1020)을 지난다: 원 목록에서 먼저 나오는 1번이 먹는다
        Out out = new Out();
        Room r = fastRoom(out);
        runner(r, 1, 1000);
        runner(r, 2, 1040);
        r.placeOrb(1012, 1020);
        r.tick();
        assertThat(out.orbEaters).containsExactly(1L);
        assertThat(r.orbCount()).isZero();
        r.tick();
        assertThat(out.orbEaters).containsExactly(1L); // 다음 틱에도 더 먹히지 않는다
    }

    @Test
    void orbSweepIgnoresPositionJumpsBeforeTheMovePhase() {
        // 이동 단계 앞에서 직접 옮긴 위치(입장·리스폰 같은 순간이동)는 선분에 들어가지 않는다: 옛 자리와 새 자리 사이의 구슬은 먹지 않는다
        Out out = new Out();
        Room r = fastRoom(out);
        Room.Member m = r.join(1, "s1", "a", 1, 0, List.of());
        m.x = 1000; m.y = 1000;
        r.tick();
        m.x = 1600; m.y = 1000; // 순간이동(입력 없음)
        r.placeOrb(1300, 1000); // 옛 자리와 새 자리 한가운데
        r.tick();
        assertThat(out.orbEaters).isEmpty();
        assertThat(r.orbCount()).isEqualTo(1);
    }

    /** 슬로우·스턴·불꽃·충격파·흡수 테스트용: 한 칸씩 장착해 입장시킨다 */
    private static Room.Member joinWith(Room r, long id, String name, String skill, List<Long> kws) {
        Room.Member m = r.join(id, "s" + id, name, 1, 0, kws);
        r.setLoadout(m, java.util.Arrays.asList(skill, "cleanse", null, null, null));
        return m;
    }

    @Test
    void slowBoltHalvesSpeedForThreeSecondsAndStacksUpToThree() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "slow", List.of());
        Room.Member b = joinWith(r, 2, "b", "slow", List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "slow", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(r.status().stacks(b, StatusEffects.Kind.SLOW)).isEqualTo(1);
        r.setSkillConfig(SkillConfig.DEFAULT);
        a.skillReadyAt[Room.SKILLS.indexOf("slow")] = 0;
        r.skill(a, "slow", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(r.status().stacks(b, StatusEffects.Kind.SLOW)).isEqualTo(2);              // 겹친다
        tickSeconds(r, 3.2);
        assertThat(r.status().stacks(b, StatusEffects.Kind.SLOW)).isZero();                  // 3초가 지나 풀린다
    }

    @Test
    void stunBoltFreezesTheTargetForOneSecondAndBlocksSkills() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "stun", List.of());
        Room.Member b = joinWith(r, 2, "b", "stun", List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "stun", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(r.status().stunned(b)).isTrue();
        double x = b.x;
        r.setInput(b, 0, 1);
        tickSeconds(r, 0.5);
        assertThat(b.x).isEqualTo(x);                       // 스턴 중에는 움직이지 않는다
        r.skill(b, "stun", a.x, a.y);
        assertThat(r.boltCount()).isZero();                 // 스킬도 못 쓴다
        tickSeconds(r, 1.0);
        r.setInput(b, 0, 1);
        double y = b.y;
        tickSeconds(r, 0.5);
        assertThat(b.y).isNotEqualTo(y);                    // 1초가 지나 다시 움직인다
    }

    // ---- 속박·침묵·혼란·공격 증폭·피해 감소·보호막·광폭화(4.33)

    /** a가 b를 새 스킬 투사체로 맞힌다(a 800,800 → b 1100,800) */
    private static Room.Member[] boltPair(Room r, String skill) {
        Room.Member a = joinWith(r, 1, "a", skill, List.of());
        Room.Member b = joinWith(r, 2, "b", skill, List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, skill, b.x, b.y);
        tickSeconds(r, 0.6);
        return new Room.Member[]{a, b};
    }

    @Test
    void rootBoltStopsMovementButNotSkillsAndEndsAfterTwoSeconds() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("root", 1));
        Room.Member[] p = boltPair(r, "root");
        Room.Member a = p[0], b = p[1];
        assertThat(r.status().rooted(b)).isTrue();
        double x = b.x, y = b.y;
        r.setInput(b, 1, 1);
        tickSeconds(r, 1.0);
        assertThat(b.x).isEqualTo(x);
        assertThat(b.y).isEqualTo(y);                       // 속박 중에는 움직이지 못한다
        r.skill(b, "cleanse", 0, 0);                        // 스킬은 쓸 수 있다(정화로 풀린다)
        assertThat(r.status().rooted(b)).isFalse();
        assertThat(countFx(out, "skillFx", "root")).isEqualTo(1);
        r.setInput(b, 1, 0);
        tickSeconds(r, 0.5);
        assertThat(b.x).isGreaterThan(x);                   // 풀린 뒤에는 움직인다
        assertThat(a.hp).isEqualTo(100.0);
    }

    @Test
    void rootEndsOnItsOwnAfterTheConfiguredSeconds() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("root", 1));
        Room.Member[] p = boltPair(r, "root");
        Room.Member b = p[1];
        assertThat(r.status().rooted(b)).isTrue();
        tickSeconds(r, 2.2);
        assertThat(r.status().rooted(b)).isFalse();
        assertThat(r.status().speedMult(b)).isEqualTo(1.0);
    }

    @Test
    void silenceBoltBlocksSkillsButNotMovementAndCleanseFreesIt() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("silence", 1, "poison", 1));
        Room.Member a = joinWith(r, 1, "a", "silence", List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        r.setLoadout(b, java.util.Arrays.asList("poison", "cleanse", null, null, null));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "silence", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(r.status().silenced(b)).isTrue();
        r.skill(b, "poison", a.x, a.y);
        assertThat(r.boltCount()).isZero();                  // 침묵 중에는 스킬을 못 쓴다
        assertThat(count(out, "skillFail", "poison")).isEqualTo(1);
        double x = b.x;
        r.setInput(b, 1, 0);
        tickSeconds(r, 0.4);
        assertThat(b.x).isGreaterThan(x);                    // 이동은 된다
        r.skill(b, "cleanse", 0, 0);                         // 정화는 쓸 수 있고 침묵을 푼다
        assertThat(r.status().silenced(b)).isFalse();
        r.skill(b, "poison", a.x, a.y);
        assertThat(r.boltCount()).isEqualTo(1);
    }

    @Test
    void confuseBoltReversesTheDirectionOfMovementForThreeSeconds() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(levels("confuse", 1));
        Room.Member[] p = boltPair(r, "confuse");
        Room.Member b = p[1];
        assertThat(r.status().confused(b)).isTrue();
        double y = b.y;
        r.setInput(b, 0, 1);                                 // 아래로 누르면
        tickSeconds(r, 0.5);
        assertThat(b.y).isLessThan(y);                       // 위로 간다
        r.skill(b, "cleanse", 0, 0);
        assertThat(r.status().confused(b)).isFalse();
        r.setInput(b, 0, 0);
        tickSeconds(r, 1.0);
        double y2 = b.y;
        r.setInput(b, 0, 1);
        tickSeconds(r, 0.5);
        assertThat(b.y).isGreaterThan(y2);                   // 풀린 뒤에는 누른 방향으로 간다
    }

    @Test
    void amplifyDoublesDamageAndFortifyHalvesItAndTheyCombineForTenSeconds() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setRequiredLevels(levels("amplify", 1, "fortify", 1));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("amplify", "fortify", null, null, null));
        r.setLoadout(b, java.util.Arrays.asList("amplify", "fortify", null, null, null));
        r.tick();
        assertThat(r.modifyDamage(a, b, 10)).isEqualTo(10.0);
        r.skill(a, "amplify", 0, 0);
        assertThat(r.modifyDamage(a, b, 10)).isEqualTo(20.0);       // 내가 주는 피해 2배
        assertThat(r.modifyDamage(b, a, 10)).isEqualTo(10.0);       // 남의 피해는 그대로
        r.skill(b, "fortify", 0, 0);
        assertThat(r.modifyDamage(a, b, 10)).isEqualTo(10.0);       // 2배 × 절반
        assertThat(r.modifyDamage(null, b, 10)).isEqualTo(5.0);     // 보스 피해도 절반
        assertThat(countFx(out, "skillFx", "amplify")).isEqualTo(1);
        tickSeconds(r, 10.2);
        assertThat(r.modifyDamage(a, b, 10)).isEqualTo(10.0);       // 10초가 지나면 둘 다 끝난다
        assertThat(countFx(out, "skillFx", "amplifyEnd")).isEqualTo(1);
        assertThat(countFx(out, "skillFx", "fortifyEnd")).isEqualTo(1);
    }

    @Test
    void barrierStacksHalfOfMaxHpOnTopAndTakesDamageBeforeHp() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setRequiredLevels(levels("barrier", 1));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("barrier", null, null, null, null));
        r.tick();
        assertThat(a.hp).isEqualTo(100.0);
        r.skill(a, "barrier", 0, 0);
        assertThat(a.barrier).isEqualTo(50.0);                        // 체력이 100%여도 쉴드 50%가 위에 쌓인다
        assertThat(r.absorb(a, 30)).isEqualTo(0.0);                  // 쉴드가 먼저 받는다
        assertThat(a.barrier).isEqualTo(20.0);
        assertThat(countFx(out, "skillFx", "barrierHit")).isEqualTo(1);
        assertThat(r.absorb(a, 30)).isEqualTo(10.0);                 // 남는 10만 체력에 간다
        assertThat(a.barrier).isEqualTo(0.0);
        assertThat(countFx(out, "skillFx", "barrierEnd")).isEqualTo(1);
    }

    @Test
    void barrierAbsorbedDamageIsStillShownAsOneTotalHit() {
        // 쉴드가 막은 피해도 평소처럼 한 숫자(막은 몫 + 체력에 들어간 몫)로 알린다. 체력은 쉴드를 넘는 만큼만 깎인다
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setRequiredLevels(levels("barrier", 1));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("barrier", null, null, null, null));
        r.tick();
        r.skill(a, "barrier", 0, 0);
        r.monsterHurt(a, "boss", 30);                                // 쉴드 50이 30을 전부 막는다
        assertThat(a.hp).isEqualTo(100.0);
        assertThat(a.barrier).isEqualTo(20.0);
        assertThat(out.texts).anyMatch(m -> "hpHit".equals(m.get("type")) && Double.valueOf(30.0).equals(m.get("dmg")) && Integer.valueOf(100).equals(m.get("pct")));
        out.texts.clear();
        r.monsterHurt(a, "boss", 30);                                // 쉴드 20이 막고 10이 체력에 들어간다
        assertThat(a.hp).isEqualTo(90.0);
        assertThat(out.texts).anyMatch(m -> "hpHit".equals(m.get("type")) && Double.valueOf(30.0).equals(m.get("dmg")) && Integer.valueOf(90).equals(m.get("pct"))); // 표시는 합친 30
    }

    @Test
    void barrierEndsAfterTenSecondsEvenIfNotBroken() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setRequiredLevels(levels("barrier", 1));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("barrier", null, null, null, null));
        r.tick();
        r.skill(a, "barrier", 0, 0);
        tickSeconds(r, 9.0);
        assertThat(a.barrier).isEqualTo(50.0);
        tickSeconds(r, 1.2);
        assertThat(a.barrier).isEqualTo(0.0);                         // 10초가 지나면 남아 있어도 사라진다
        assertThat(r.absorb(a, 30)).isEqualTo(30.0);
    }

    @Test
    void berserkTogglesDrainsOnePercentPerSecondAndScalesScoreAndSpeedByLostHp() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setSkillConfig(SkillConfig.DEFAULT.withCooldown("berserk", 0));
        r.setRequiredLevels(levels("berserk", 1));
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of());
        r.setLoadout(a, java.util.Arrays.asList("berserk", null, null, null, null));
        a.x = 800; a.y = 800;                                        // 가운데 세이프존 밖(안에서는 1초마다 체력이 회복돼 소모와 섞인다)
        r.tick();
        a.hp = 70;
        r.skill(a, "berserk", 0, 0);
        assertThat(a.berserk).isTrue();
        assertThat(r.effScore(a)).isEqualTo(130.0);                  // 잃은 체력 30% → 유행도 +30%(표시값 100은 그대로)
        assertThat(a.score).isEqualTo(100.0);
        assertThat(r.status().speedMult(a)).isEqualTo(1.3);          // 이동 속도도 +30%
        tickSeconds(r, 1.1);
        assertThat(a.hp).isEqualTo(69.0);                            // 1초에 최대 체력의 1%
        assertThat(countFx(out, "skillFx", "berserk")).isGreaterThanOrEqualTo(1);
        r.skill(a, "berserk", 0, 0);                                 // 다시 누르면 꺼진다
        assertThat(a.berserk).isFalse();
        assertThat(r.effScore(a)).isEqualTo(100.0);
        assertThat(countFx(out, "skillFx", "berserkEnd")).isEqualTo(1);
    }

    @Test
    void berserkRefusesAtLowHpAndTurnsItselfOffBeforeDying() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        r.setSkillConfig(SkillConfig.DEFAULT.withCooldown("berserk", 0));
        r.setRequiredLevels(levels("berserk", 1));
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of());
        r.setLoadout(a, java.util.Arrays.asList("berserk", null, null, null, null));
        a.x = 800; a.y = 800;                                        // 가운데 세이프존 밖(안에서는 1초마다 체력이 회복돼 소모와 섞인다)
        r.tick();
        a.hp = 1;                                                    // 최소 체력(1%) 이하면 켜지 않는다
        r.skill(a, "berserk", 0, 0);
        assertThat(a.berserk).isFalse();
        assertThat(count(out, "skillFail", "berserk")).isEqualTo(1);
        a.hp = 2.5;
        r.skill(a, "berserk", 0, 0);
        assertThat(a.berserk).isTrue();
        tickSeconds(r, 1.1);                                         // 2.5 - 1 = 1.5 ≥ 1이라 한 번은 깎인다
        assertThat(a.hp).isEqualTo(1.5);
        tickSeconds(r, 1.1);                                         // 한 번 더 깎으면 0.5 < 1이라 깎지 않고 꺼진다
        assertThat(a.hp).isEqualTo(1.5);
        assertThat(a.berserk).isFalse();
    }

    @Test
    void flameThrowsThreeGuaranteedHitsEachLosingOneKeyword() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "fireball", List.of());
        Room.Member b = joinWith(r, 2, "b", "fireball", List.of(11L, 12L, 13L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "fireball", b.x + 20, b.y + 20);
        tickSeconds(r, 2);
        assertThat(out.poisonLoses).hasSize(3);             // 한 발에 1개, 총 3개
        assertThat(out.poisonLoses).allSatisfy(l -> assertThat(l).containsExactly(2, 1));
    }

    @Test
    void flameWithoutATargetInReachDoesNothingAndKeepsTheCooldown() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "fireball", List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "fireball", 900, 800);
        tickSeconds(r, 1);
        assertThat(out.poisonLoses).isEmpty();
        assertThat(a.skillReadyAt[Room.SKILLS.indexOf("fireball")]).isZero();
    }

    @Test
    void quakeMakesTouchedCirclesLoseOneKeywordPerHitWithoutKnockback() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "quake", List.of());
        Room.Member b = joinWith(r, 2, "b", "quake", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1000; b.y = 800;
        r.tick();
        r.skill(a, "quake", 0, 0);
        tickSeconds(r, 2);
        assertThat(out.poisonLoses).hasSize(1);             // 한 번만 맞고 히트 하나에 1개를 잃는다
        assertThat(out.poisonLoses.get(0)).containsExactly(2, 1);
    }

    @Test
    void absorbLinksEveryCircleInRangeAndStealsOneAtOnceThenEverySecondForThreeTotal() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "absorb", List.of());
        Room.Member b = joinWith(r, 2, "b", "absorb", List.of(11L, 12L, 13L, 14L));
        Room.Member c = joinWith(r, 3, "c", "absorb", List.of(21L, 22L, 23L, 24L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800; c.x = 800; c.y = 1100;
        r.tick();
        r.skill(a, "absorb", 0, 0);                         // 조준하지 않아도 사거리 안의 모든 원과 이어진다
        assertThat(out.loses).hasSize(2);                   // 0초에 둘에게서 1개씩
        tickSeconds(r, 0.5);
        assertThat(out.loses).hasSize(2);
        tickSeconds(r, 0.6);
        assertThat(out.loses).hasSize(4);                   // 1초 뒤 1개씩
        tickSeconds(r, 1.0);
        assertThat(out.loses).hasSize(6);                   // 2초 뒤 1개씩, 원마다 총 3개
        assertThat(out.loses.get(0)).containsExactly(2, 1); // 빼앗는 쪽은 쓴 사람
        tickSeconds(r, 3);
        assertThat(out.loses).hasSize(6);                   // 끝난 뒤에는 더 빼앗지 않는다

        Out out2 = new Out();
        Room r2 = boltRoom(out2);
        Room.Member d = joinWith(r2, 1, "d", "absorb", List.of());
        Room.Member e = joinWith(r2, 2, "e", "absorb", List.of(11L, 12L, 13L));
        Room.Member f = joinWith(r2, 3, "f", "absorb", List.of(21L, 22L, 23L));
        d.x = 800; d.y = 800; e.x = 1100; e.y = 800; f.x = 800; f.y = 1100;
        r2.tick();
        r2.skill(d, "absorb", 0, 0);
        e.x = 3000;                                          // 사거리 밖으로 벗어난 원의 선만 끊긴다
        tickSeconds(r2, 3.3);
        assertThat(out2.loses).hasSize(4);                   // e는 처음 1개만, f는 3개
        long fromE = out2.loses.stream().filter(l -> l[0] == 2).count();
        assertThat(fromE).isEqualTo(1);
    }

    @Test
    void attackSkillsBuildComboPerHitButCcSkillsDoNot() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "fireball", List.of());
        Room.Member b = joinWith(r, 2, "b", "fireball", List.of(11L, 12L, 13L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "fireball", b.x, b.y);
        tickSeconds(r, 2);
        assertThat(a.combo).isEqualTo(3);                   // 화염구 3발이 맞았으니 히트 3번 = +3

        Out out2 = new Out();
        Room r2 = boltRoom(out2);
        Room.Member c = joinWith(r2, 1, "c", "quake", List.of());
        Room.Member d = joinWith(r2, 2, "d", "quake", List.of(11L, 12L, 13L, 14L));
        Room.Member e = joinWith(r2, 3, "e", "quake", List.of(21L, 22L, 23L, 24L));
        c.x = 800; c.y = 800; d.x = 1000; d.y = 800; e.x = 800; e.y = 1000;
        r2.tick();
        r2.skill(c, "quake", 0, 0);
        tickSeconds(r2, 2);
        assertThat(c.combo).isEqualTo(2);                   // 충격파에 맞은 원마다 +1

        Out out3 = new Out();
        Room r3 = boltRoom(out3);
        Room.Member f = joinWith(r3, 1, "f", "stun", List.of());
        Room.Member g = joinWith(r3, 2, "g", "stun", List.of(11L));
        f.x = 800; f.y = 800; g.x = 1100; g.y = 800;
        r3.tick();
        r3.skill(f, "stun", g.x, g.y);
        tickSeconds(r3, 1);
        assertThat(r3.status().stunned(g)).isTrue();
        assertThat(f.combo).isEqualTo(1);                   // CC 스킬도 맞히면 히트 하나 = 콤보 +1(체력 피해와 함께 한 번만)
    }

    @Test
    void oldSkillCodesFromUnreloadedClientsStillWork() {
        // V63: 반발 shockwave → repel, 화염구 flame → fireball. 새로고침하지 않은 화면이 옛 코드로 장착·사용해도 새 코드로 받는다
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("shockwave", "flame", null, null, null));
        assertThat(r.equippedSkills(a)).containsExactly("repel", "fireball");
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "shockwave", 0, 0);
        assertThat(count(out, "skill", "repel")).isEqualTo(1);
        assertThat(countFx(out, "skillFx", "repel")).isEqualTo(1);
    }

    @Test
    void hittingATargetWithNoKeywordsStillBuildsComboAndBreaksTheirs() {
        // 4.25: 히트 하나당 콤보 +1. 맞은 사람에게 잃을 키워드가 없어도 오르고, 맞은 사람의 콤보는 끊긴다
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinWith(r, 1, "a", "hook", List.of());
        Room.Member b = joinWith(r, 2, "b", "hook", List.of());   // 키워드 없음
        a.x = 800; a.y = 800; b.x = 1000; b.y = 800;
        r.tick();
        r.fightResult(b, null, true);                              // b 콤보 1
        assertThat(b.combo).isEqualTo(1);
        r.skill(a, "hook", b.x, b.y);
        tickSeconds(r, 0.2);
        assertThat(a.combo).isEqualTo(1);
        assertThat(b.combo).isZero();
        assertThat(out.loses).isEmpty();                           // 빼앗을 키워드는 없다
    }

    /** 해금 레벨 표(코드, 레벨, 코드, 레벨 …)를 만든다. Map.of는 쌍이 10개까지라서 스킬이 늘면 쓸 수 없다 */
    private static java.util.Map<String, Integer> levels(Object... kv) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        for (String c : List.of("root", "silence", "confuse", "amplify", "fortify", "barrier", "berserk", "zone", "burst")) m.put(c, 99); // 나중에 더한 스킬은 따로 정하지 않으면 잠겨 있다(기존 시험이 자동 장착에 끼지 않게)
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------ 스킬 히트: 체력 피해 + 콤보 한 번(4.25, 4.33)

    private Room hpSkillRoom(Out out) {
        Room r = boltRoom(out);
        r.setHpTuning(Room.HpTuning.on());
        return r;
    }

    @Test
    void ccBoltHitsDealTenPercentDamageAndBuildOneComboEach() {
        for (String skill : List.of("darkness", "slow", "silence", "stun", "root", "confuse")) {
            Out out = new Out();
            Room r = hpSkillRoom(out);
            Room.Member a = joinWith(r, 1, "a", skill, List.of());
            Room.Member b = joinWith(r, 2, "b", skill, List.of(11L));
            a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
            r.tick();
            r.skill(a, skill, b.x, b.y);
            tickSeconds(r, 1);
            assertThat(b.hp).as(skill).isEqualTo(95.0);       // 몸싸움 피해(유행도가 같으면 50)의 10% = 5
            assertThat(a.combo).as(skill).isEqualTo(1);       // 히트 하나에 한 번만(피해와 따로 올리지 않는다)
        }
    }

    @Test
    void fireballDealsTenPercentPerShotAndComboStaysOnePerShot() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "fireball", List.of());
        Room.Member b = joinWith(r, 2, "b", "fireball", List.of(11L, 12L, 13L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "fireball", b.x, b.y);
        tickSeconds(r, 2);
        assertThat(b.hp).isEqualTo(85.0);                    // 3발 × 5
        assertThat(a.combo).isEqualTo(3);                    // 키워드 잃기와 체력 피해가 각각 올려 6이 되지 않는다
    }

    @Test
    void quakeAndAbsorbDealDamagePerHit() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member c = joinWith(r, 1, "c", "quake", List.of());
        Room.Member d = joinWith(r, 2, "d", "quake", List.of(11L, 12L, 13L, 14L));
        c.x = 800; c.y = 800; d.x = 1000; d.y = 800;
        r.tick();
        r.skill(c, "quake", 0, 0);
        tickSeconds(r, 2);
        assertThat(d.hp).isEqualTo(95.0);
        assertThat(c.combo).isEqualTo(1);

        Out out2 = new Out();
        Room r2 = hpSkillRoom(out2);
        Room.Member e = joinWith(r2, 1, "e", "absorb", List.of());
        Room.Member f = joinWith(r2, 2, "f", "absorb", List.of(21L, 22L, 23L));
        e.x = 800; e.y = 800; f.x = 1100; f.y = 800;
        r2.tick();
        r2.skill(e, "absorb", 0, 0);
        tickSeconds(r2, 3.3);
        assertThat(f.hp).isEqualTo(85.0);                    // 0·1·2초에 한 번씩 3히트
        assertThat(e.combo).isEqualTo(3);
    }

    @Test
    void killingHitCountsTheComboOnlyOnce() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "stun", List.of());
        Room.Member b = joinWith(r, 2, "b", "stun", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        b.hp = 3;                                            // 5를 맞으면 0이 된다
        r.skill(a, "stun", b.x, b.y);
        tickSeconds(r, 1);
        assertThat(b.dead).isTrue();
        assertThat(a.combo).isEqualTo(1);                    // 히트 +1, 처치가 한 번 더 올리지 않는다
    }

    @Test
    void damageIsConfigurableAndZeroMeansNone() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        r.setSkillConfig(SkillConfig.DEFAULT.withParam("skill_hit_damage_percent", 25));
        Room.Member a = joinWith(r, 1, "a", "slow", List.of());
        Room.Member b = joinWith(r, 2, "b", "slow", List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "slow", b.x, b.y);
        tickSeconds(r, 1);
        assertThat(b.hp).isEqualTo(87.5);

        Out out2 = new Out();
        Room r2 = hpSkillRoom(out2);
        r2.setSkillConfig(SkillConfig.DEFAULT.withParam("skill_hit_damage_percent", 0));
        Room.Member c = joinWith(r2, 1, "c", "slow", List.of());
        Room.Member d = joinWith(r2, 2, "d", "slow", List.of());
        c.x = 800; c.y = 800; d.x = 1100; d.y = 800;
        r2.tick();
        r2.skill(c, "slow", d.x, d.y);
        tickSeconds(r2, 1);
        assertThat(d.hp).isEqualTo(100.0);
        assertThat(c.combo).isEqualTo(1);                    // 피해가 0이어도 맞히면 히트다
    }

    @Test
    void zoneHitsAnyoneOnItFromZeroSecondsEverySecondForThreeTicks() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "zone", List.of());
        Room.Member b = joinWith(r, 2, "b", "zone", List.of(11L));
        Room.Member far = joinWith(r, 3, "far", "zone", List.of(21L));
        a.x = 800; a.y = 800; b.x = 1000; b.y = 800; far.x = 3000; far.y = 3000;
        r.tick();
        r.skill(a, "zone", 0, 0);
        assertThat(b.hp).isEqualTo(95.0);                    // 쓰자마자 0초 판정
        assertThat(a.combo).isEqualTo(1);
        tickSeconds(r, 0.9);
        assertThat(b.hp).isEqualTo(95.0);                    // 다음 틱(1초) 전까지는 그대로
        tickSeconds(r, 2.6);
        assertThat(b.hp).isEqualTo(85.0);                    // 0·1·2초 세 번 = 15
        assertThat(a.combo).isEqualTo(3);
        assertThat(far.hp).isEqualTo(100.0);                 // 장판 밖
        assertThat(a.hp).isEqualTo(100.0);                   // 쓴 사람은 맞지 않는다
        tickSeconds(r, 3);
        assertThat(b.hp).isEqualTo(85.0);                    // 장판이 사라진 뒤에는 더 맞지 않는다
    }

    @Test
    void zoneStopsHittingWhoeverLeaves() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "zone", List.of());
        Room.Member b = joinWith(r, 2, "b", "zone", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1000; b.y = 800;
        r.tick();
        r.skill(a, "zone", 0, 0);
        tickSeconds(r, 1.1);                                 // 0초·1초 두 번
        assertThat(b.hp).isEqualTo(90.0);
        b.x = 3000; b.y = 3000;                              // 장판을 벗어난다
        tickSeconds(r, 2);
        assertThat(b.hp).isEqualTo(90.0);
    }

    @Test
    void burstHitsOnceThenExplodesAfterTwoSecondsAndKnocksBackRandomly() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "burst", List.of());
        Room.Member b = joinWith(r, 2, "b", "burst", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "burst", b.x, b.y);
        tickSeconds(r, 0.5);
        assertThat(b.hp).isEqualTo(95.0);                    // 처음 맞았을 때
        assertThat(a.combo).isEqualTo(1);
        double bx = b.x, by = b.y;
        tickSeconds(r, 1.0);
        assertThat(b.hp).isEqualTo(95.0);                    // 아직 안 터졌다(맞은 지 1.5초)
        tickSeconds(r, 1.0);
        assertThat(b.hp).isEqualTo(90.0);                    // 2초 뒤에 터져 한 번 더
        assertThat(a.combo).isEqualTo(2);                    // 터진 것도 히트 하나
        assertThat(Math.hypot(b.x - bx, b.y - by)).isGreaterThan(5); // 무작위 방향으로 튕겨 나갔다
        assertThat(countFx(out, "skillFx", "burstBoom")).isEqualTo(1);
    }

    @Test
    void burstKnockbackUsesTheSameStrengthAsACollision() {
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        a.displayRadius = 60; b.displayRadius = 60;
        assertThat(r.collisionKnock(a, b)).isEqualTo(1.0);   // 크기가 비슷하면 둘 다 기본 세기
        double big = 60 * (T.bigRatio() + 1);
        a.displayRadius = big;
        assertThat(r.collisionKnock(a, b)).isEqualTo(Math.min(big / 60, T.knockbackCap())); // 작은 쪽이 맞으면 크기 비율만큼(상한 안)
        assertThat(r.collisionKnock(b, a)).isZero();         // 큰 쪽은 충돌에서처럼 밀리지 않는다
    }

    @Test
    void skillDamageIsTenPercentOfTheCollisionDamageSoItFollowsScores() {
        // 4.33: 모든 스킬 히트의 피해 = 몸싸움 피해(hpDamage: 내 유행도 ÷ (내 유행도 + 상대 유행도) × 100)의 skill_hit_damage_percent%
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "stun", List.of());
        Room.Member b = joinWith(r, 2, "b", "stun", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        a.score = 30; b.score = 10;                          // 몸싸움 피해 = 100 × 30/40 = 75
        a.radius = a.displayRadius = r.radiusFor(30); b.radius = b.displayRadius = r.radiusFor(10);
        r.tick();
        r.skill(a, "stun", b.x, b.y);
        tickSeconds(r, 1);
        assertThat(b.hp).isEqualTo(92.5);                    // 75의 10%
    }

    @Test
    void skillsNeverGrantInvincibilityAfterTheHit() {
        // 무적은 몸싸움에서 잃은 뒤에만 자동으로 붙는다. 스킬에 맞아도(피해가 들어가도) 무적이 붙지 않는다
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "stun", List.of());
        Room.Member b = joinWith(r, 2, "b", "stun", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "stun", b.x, b.y);
        tickSeconds(r, 1);
        assertThat(b.hp).isEqualTo(95.0);
        assertThat(b.protectUntil).isZero();
    }

    @Test
    void everySkillHitMakesTheVictimLoseOneKeywordAndAbsorbAlsoTakesIt() {
        // 4.33: 스킬 히트 하나마다 맞은 사람은 키워드 1개를 잃는다. 흡수는 거기에 더해 맞힌 사람이 그 키워드를 가져간다(out.loses = 빼앗기, out.poisonLoses = 떨구기)
        for (String skill : List.of("darkness", "slow", "silence", "stun", "root", "confuse", "burst", "zone", "fireball", "quake")) {
            Out out = new Out();
            Room r = hpSkillRoom(out);
            Room.Member a = joinWith(r, 1, "a", skill, List.of());
            Room.Member b = joinWith(r, 2, "b", skill, List.of(11L, 12L, 13L));
            a.x = 800; a.y = 800; b.x = 1000; b.y = 800;
            r.tick();
            r.skill(a, skill, b.x, b.y);
            tickSeconds(r, "fireball".equals(skill) ? 2 : 0.6);
            int hits = "fireball".equals(skill) ? 3 : 1;      // 화염구는 3발이 각각 히트
            assertThat(out.poisonLoses).as(skill).hasSize(hits);
            assertThat(out.poisonLoses).as(skill).allSatisfy(l -> assertThat(l).containsExactly(2, 1));
            assertThat(out.loses).as(skill).isEmpty();        // 아무도 가져가지 않는다
        }
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "absorb", List.of());
        Room.Member b = joinWith(r, 2, "b", "absorb", List.of(11L, 12L, 13L));
        a.x = 800; a.y = 800; b.x = 1000; b.y = 800;
        r.tick();
        r.skill(a, "absorb", 0, 0);
        assertThat(out.loses).hasSize(1);                     // 쓰자마자 히트 하나: b가 1개를 잃고 a가 가져간다
        assertThat(out.loses.get(0)).containsExactly(2, 1);
        assertThat(out.poisonLoses).isEmpty();
    }

    @Test
    void quakeHitMarkerShowsOnlyTheKeywordsReallyLost() {
        // 가진 키워드가 없으면 충격파가 닿아도 맞은 원 위에 "-N"을 띄우지 않는다(콤보·피해는 그대로). 있으면 실제 잃는 개수만 보인다
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "quake", List.of());
        Room.Member none = joinWith(r, 2, "none", "quake", List.of());
        Room.Member has = joinWith(r, 3, "has", "quake", List.of(11L, 12L));
        a.x = 800; a.y = 800; none.x = 1000; none.y = 800; has.x = 800; has.y = 1000;
        r.tick();
        r.skill(a, "quake", 0, 0);
        tickSeconds(r, 2);
        assertThat(a.combo).isEqualTo(2);                     // 둘 다 맞았다
        assertThat(none.hp).isEqualTo(95.0);
        assertThat(countFx(out, "skillFx", "quakeHit")).isEqualTo(1); // 키워드가 있는 원 하나에만
        assertThat(out.texts.stream().filter(m -> "skillFx".equals(m.get("type")) && "quakeHit".equals(m.get("id"))).findFirst().orElseThrow().get("n")).isEqualTo(1);
    }

    @Test
    void zoneFollowsTheCasterWhenItMoves() {
        // 장판은 시전자 원을 중심으로 따라다닌다: 쓴 뒤 시전자가 멀리 움직여도 그 둘레에 있는 원이 맞고, 처음 쓴 자리에 남은 원은 맞지 않는다
        // (활동 영역 안에서 시험한다: 원은 영역 밖으로 나가지 못한다)
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "zone", List.of());
        Room.Member stay = joinWith(r, 2, "stay", "zone", List.of(11L));
        Room.Member near = joinWith(r, 3, "near", "zone", List.of(21L));
        a.x = 300; a.y = 800; stay.x = 500; stay.y = 800; near.x = 1400; near.y = 800;
        r.tick();
        r.skill(a, "zone", 0, 0);
        assertThat(stay.hp).isEqualTo(95.0);                  // 쓰자마자 0초 판정(처음 자리 둘레)
        assertThat(near.hp).isEqualTo(100.0);                 // 아직 멀다
        a.x = 1200; a.y = 800;                                // 시전자가 멀리 움직인다(장판도 같이 간다)
        tickSeconds(r, 1.1);
        assertThat(near.hp).isEqualTo(95.0);                  // 옮겨 간 둘레의 원이 1초 틱에 맞았다
        assertThat(stay.hp).isEqualTo(95.0);                  // 처음 자리에 남은 원은 더 맞지 않는다
        assertThat(countFx(out, "skillFx", "zoneEnd")).isZero();
    }

    @Test
    void usedSkillMessageCarriesHowLongTheEffectLastsForTheSlotIndicator() {
        // 지속되는 스킬은 쓴 뒤 효과가 이어지는 시간(activeMs)을 알려 스킬 칸에 "켜져 있음"을 띄운다. 맞은 상대에게 걸리는 효과는 0
        Out out = new Out();
        Room r = hpSkillRoom(out);
        Room.Member a = joinWith(r, 1, "a", "zone", List.of());
        Room.Member b = joinWith(r, 2, "b", "slow", List.of(11L));
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        r.skill(a, "zone", 0, 0);
        java.util.Map<String, Object> used = out.texts.stream().filter(m -> "skill".equals(m.get("type")) && "zone".equals(m.get("id"))).findFirst().orElseThrow();
        assertThat(used.get("activeMs")).isEqualTo(3000L);   // 장판 3초
        assertThat(SkillConfig.DEFAULT.activeSeconds("slow")).isZero();      // 슬로우는 상대에게 걸리는 효과라 내 칸에는 표시 없음
        assertThat(SkillConfig.DEFAULT.activeSeconds("invincibility")).isEqualTo(3.0);
        assertThat(SkillConfig.DEFAULT.activeSeconds("berserk")).isZero();   // 켜고 끄는 스킬은 따로(skillOn)
    }
}
