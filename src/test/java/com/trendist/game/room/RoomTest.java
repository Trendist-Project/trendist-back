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
        public void requestLose(Room room, Room.Member loser, Room.Member winner) { loses.add(new long[]{loser.playerId, winner.playerId}); }
        final List<Long> orbEaters = new ArrayList<>();
        public void requestOrb(Room room, Room.Member eater, boolean prize) { (prize ? prizeEaters : orbEaters).add(eater.playerId); }
        final List<Long> prizeEaters = new ArrayList<>();
        final List<long[]> wipes = new ArrayList<>();
        public void requestWipe(Room room, Room.Member loser, Room.Member winner) { wipes.add(new long[]{loser.playerId, winner.playerId}); }
        final List<long[]> halves = new ArrayList<>(); // {잃은 사람, 이긴 사람, 비율, 죽음이면 1}
        public void requestHalve(Room room, Room.Member loser, Room.Member winner, int percent, boolean death) {
            halves.add(new long[]{loser.playerId, winner.playerId, percent, death ? 1 : 0});
            if (!death) wipes.add(new long[]{loser.playerId, winner.playerId}); // 불꽃에게 부딪혀 잃기: 예전 모두 잃기 검사와 같은 자리
        }
        final List<String> savedSlots = new ArrayList<>(); // 저장 요청된 장착 4칸
        public void saveSkillSlots(Room room, Room.Member member, List<String> slots) { savedSlots.add(member.playerId + ":" + String.join(",", slots.stream().map(x -> x == null ? "" : x).toList())); }
        final List<long[]> poisonLoses = new ArrayList<>(); // {당한 사람, 잃은 개수}
        public void requestPoisonLose(Room room, Room.Member victim, Room.Member owner, int count) { poisonLoses.add(new long[]{victim.playerId, count}); }
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
        public void requestBotWin(Room room, Room.Member winner, Room.Member bot, long keywordId) { botWinners.add(winner.playerId); botWinKeywords.add(keywordId); }
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
    void flameKillsSomeoneWithNoKeywords() {
        Out out = new Out();
        Room r = deathRoom(out, List.of());
        r.byPlayer(2).combo = 5; // 불꽃이 붙은 쪽이 이긴다
        r.byPlayer(2).score = 0;
        r.tick();
        assertThat(r.byPlayer(1)).isNull();
        assertThat(out.wipes).isEmpty();
    }

    @Test
    void deadPlayerKeepsWatchingLiveRoomAndIsNotCountedAsSpectator() {
        // 4.31: 죽은 뒤에도 방의 스냅샷을 계속 받아 움직임을 본다. 관리자 관전자 수에는 세지 않고, 리스폰하면 구경 목록에서 빠진다
        Out out = new Out();
        Room r = deathRoom(out, List.of());
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
        assertThat(out.texts).anyMatch(m -> "hit".equals(m.get("type")) && Integer.valueOf(1).equals(m.get("wiped")));
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
        // 4.27: 움직이는 중에 쓰면 3초 동안 최대 속도 2배, 쓴 때부터 30초가 지나야 다시 쓴다. 가만히 있으면 쓸 수 없고 쿨타임도 쓰지 않는다
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 0; a.y = 0;
        r.dash(a);
        assertThat(out.texts).noneMatch(m -> "dash".equals(m.get("type"))); // 정지 상태
        r.setInput(a, 1, 0);
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(a.vx).isGreaterThan(230).isLessThan(250); // 평소 240
        r.dash(a);
        assertThat(out.texts).anyMatch(m -> "dash".equals(m.get("type")) && m.get("ms").equals(3000L) && m.get("cooldownMs").equals(30000L) && m.get("mult").equals(2.0));
        assertThat(out.texts).anyMatch(m -> "dashFx".equals(m.get("type")) && m.get("e").equals(a.entityNo) && m.get("ms").equals(3000L)); // 모두에게 효과를 알린다
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(a.vx).isGreaterThan(470).isLessThan(490); // 240 × 2
        int dashes = (int) out.texts.stream().filter(m -> "dash".equals(m.get("type"))).count();
        r.dash(a); // 쿨타임 중
        assertThat(out.texts.stream().filter(m -> "dash".equals(m.get("type"))).count()).isEqualTo(dashes);
        for (int i = 0; i < 20 * 3; i++) r.tick();
        assertThat(a.vx).isGreaterThan(230).isLessThan(250); // 3초가 지나 원래 속도
        for (int i = 0; i < 20 * 30; i++) r.tick();
        r.dash(a); // 30초가 지났다
        assertThat(out.texts.stream().filter(m -> "dash".equals(m.get("type"))).count()).isEqualTo(dashes + 1);
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
        assertThat(out.texts).anyMatch(t -> "shield".equals(t.get("type")) && t.get("e").equals(human.entityNo) && t.get("ms").equals(3000L));

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
        assertThat(out.texts).anyMatch(t -> "shield".equals(t.get("type")) && t.get("e").equals(bot.entityNo));
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
    private static Room skillRoom(Out out, Room.SkillTuning tuning) {
        Room r = new Room(1, T, out);
        r.setZoneTuning(NO_RANDOM_ZONES);
        r.setSkillTuning(tuning);
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
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
        return r;
    }

    /** 독·암흑·정화를 장착한 채로 입장시킨다(장착해야만 쓸 수 있다, 4.33) */
    private static Room.Member joinEq(Room r, long id, String session, String name, int level, double score, List<Long> kws) {
        Room.Member m = r.join(id, session, name, level, score, kws);
        r.setLoadout(m, java.util.Arrays.asList("poison", "darkness", "cleanse", null));
        return m;
    }

    private static void tickSeconds(Room r, double seconds) { for (int i = 0; i < Math.round(seconds * 20); i++) r.tick(); }

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
        assertThat(b.poisonStacks).isEqualTo(1);
        tickSeconds(r, 2.2);
        assertThat(out.poisonLoses).isEmpty();            // 아직 3초 전
        tickSeconds(r, 0.9);
        assertThat(out.poisonLoses).hasSize(1);
        assertThat(out.poisonLoses.get(0)).containsExactly(2, 1);
        tickSeconds(r, 6.5);
        assertThat(out.poisonLoses).hasSize(3);           // 3초, 6초, 9초
        assertThat(b.poisonStacks).isZero();
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
        assertThat(b.poisonStacks).isEqualTo(2);
        assertThat(out.poisonLoses).isEmpty();            // 다시 맞았으니 3초를 처음부터 센다
        tickSeconds(r, 3.2);
        assertThat(out.poisonLoses).hasSize(1);
        assertThat(out.poisonLoses.get(0)).containsExactly(2, 2); // 2스택이라 한 번에 2개
        tickSeconds(r, 6.5);
        assertThat(out.poisonLoses).hasSize(3);
        assertThat(out.poisonLoses).allSatisfy(l -> assertThat(l).containsExactly(2, 2));
        assertThat(b.poisonStacks).isZero();
    }

    @Test
    void shieldedOrSafeCirclesLetTheBoltPassThrough() {
        Out out = new Out();
        Room r = boltRoom(out);
        Room.Member a = joinEq(r, 1, "s1", "a", 1, 0, List.of());
        Room.Member b = joinEq(r, 2, "s2", "b", 1, 0, List.of());
        a.x = 800; a.y = 800; b.x = 1100; b.y = 800;
        r.tick();
        b.protectUntil = 1e9; // 방패·잃은 뒤 무적
        r.skill(a, "poison", b.x, b.y);
        tickSeconds(r, 2);
        assertThat(b.poisonStacks).isZero();
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
        assertThat(b.darkUntil).isGreaterThan(0);
        r.skill(a, "poison", b.x, b.y);
        tickSeconds(r, 0.6);
        assertThat(b.poisonStacks).isEqualTo(1);
        b.hookedBy = a; b.pullLeft = 1; // 갈고리에 끌려오는 중에도 정화는 쓴다
        r.skill(b, "cleanse", 0, 0);
        assertThat(b.hookedBy).isNull();
        assertThat(b.poisonStacks).isZero();
        assertThat(b.darkUntil).isZero();
        assertThat(count(out, "darkEnd", null)).isEqualTo(1);
        assertThat(count(out, "pullEnd", null)).isEqualTo(1);
        assertThat(countFx(out, "skillFx", "cleanse")).isEqualTo(1);
        tickSeconds(r, 10);
        assertThat(out.poisonLoses).isEmpty(); // 풀린 독은 더 잃지 않는다
        r.skill(a, "poison", b.x, b.y);        // 면역 시간이 0이라 바로 다시 걸린다
        tickSeconds(r, 0.6);
        assertThat(b.poisonStacks).isEqualTo(1);
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
        r.setSkillCooldowns(java.util.Map.of("poison", 5.0));
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
        assertThat(r.equippedSkills(a)).containsExactly("shockwave", "hook", "shield", "stealth"); // 고른 적 없으면 해금된 것이 순서대로
        r.skill(a, "poison", 1100, 800);
        assertThat(r.boltCount()).isZero();
        assertThat(count(out, "skillFail", "poison")).isEqualTo(1);
        r.setLoadout(a, java.util.Arrays.asList("poison", null, "shield", null));
        assertThat(r.equippedSkills(a)).containsExactly("poison", "shield");
        r.skill(a, "poison", 1100, 800);
        assertThat(r.boltCount()).isEqualTo(1);
        r.skill(a, "shockwave", 0, 0); // 해제했으니 못 쓴다
        assertThat(count(out, "skill", "shockwave")).isZero();
        assertThat(out.savedSlots).containsExactly("1:poison,,shield,");
    }

    @Test
    void loadoutRejectsLockedUnknownDuplicateOrWrongSizeAndKeepsTheOldOne() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(java.util.Map.of("poison", 40, "darkness", 30));
        Room.Member a = r.join(1, "s1", "a", 20, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("shockwave", null, null, null));
        r.setLoadout(a, java.util.Arrays.asList("poison", null, null, null));          // 40레벨 전이라 잠겨 있다
        r.setLoadout(a, java.util.Arrays.asList("hook", "hook", null, null));          // 중복
        r.setLoadout(a, java.util.Arrays.asList("nope", null, null, null));            // 모르는 스킬
        r.setLoadout(a, java.util.Arrays.asList("hook", "shield", "stealth", "shockwave", "x")); // 칸 수가 다르다
        assertThat(r.equippedSkills(a)).containsExactly("shockwave");
        assertThat(out.savedSlots).containsExactly("1:shockwave,,,");
        a.level = 40;
        r.setLoadout(a, java.util.Arrays.asList("poison", "darkness", "shockwave", null));
        assertThat(r.equippedSkills(a)).containsExactly("poison", "darkness", "shockwave");
    }

    @Test
    void slotCountComesFromTheCatalogSoAFifthSlotNeedsNoCodeChange() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setSkillSlotCount(5);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.setLoadout(a, java.util.Arrays.asList("shockwave", "hook", "shield", "stealth", "poison"));
        assertThat(r.equippedSkills(a)).containsExactly("shockwave", "hook", "shield", "stealth", "poison");
        assertThat(r.loadoutMessage(a).get("slots")).isEqualTo(java.util.Arrays.asList("shockwave", "hook", "shield", "stealth", "poison"));
        r.setLoadout(a, java.util.Arrays.asList("hook", null, null, null)); // 칸 수가 다르면 거절한다
        assertThat(r.equippedSkills(a)).hasSize(5);
        assertThat(out.savedSlots).containsExactly("1:shockwave,hook,shield,stealth,poison");
    }

    @Test
    void defaultLoadoutFillsInUnlockOrderAndRestoreReadsTheSavedSlots() {
        Out out = new Out();
        Room r = boltRoom(out);
        r.setRequiredLevels(java.util.Map.of("shield", 10, "hook", 15, "shockwave", 20, "stealth", 25, "darkness", 30, "cleanse", 35, "poison", 40));
        Room.Member a = r.join(1, "s1", "a", 12, 0, List.of());
        assertThat(r.equippedSkills(a)).containsExactly("shield");
        a.level = 40;
        assertThat(r.equippedSkills(a)).containsExactly("shield", "hook", "shockwave", "stealth"); // 칸은 4개
        r.restoreLoadout(a, java.util.Arrays.asList("poison", null, "cleanse", "bogus"));
        assertThat(r.equippedSkills(a)).containsExactly("poison", "cleanse");
        assertThat(r.loadoutMessage(a).get("slots")).isEqualTo(java.util.Arrays.asList("poison", null, "cleanse", null));
        r.restoreLoadout(a, null);
        assertThat(r.equippedSkills(a)).hasSize(4);
    }

    @Test
    void shockwaveSpreadsFromCastPointAtSetSpeedAndPushesOnlyWhatItReaches() {
        // 4.33: 쓴 순간 내 원 크기로 시작해 가장자리에서 화면 기준 300px까지 최대 속도 × 4(화면 기준)로 퍼진다. 닿은 원만 밀려나고, 쿨타임은 30초
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member near = r.join(2, "s2", "b", 1, 0, List.of());
        Room.Member far = r.join(3, "s3", "c", 1, 0, List.of());
        a.x = 800; a.y = 800;
        double r0 = a.displayRadius;
        near.x = 800 + r0 + near.displayRadius + 10; near.y = 800; // 가장자리 사이 10px
        far.x = 800 - (r0 + r.reachWorld(300, r0) + far.displayRadius + 40); far.y = 800; // 범위(가장자리에서 300px) 밖(밀려난 near와 부딪히지 않게 반대쪽)
        r.tick();
        r.skill(a, "shockwave", Double.NaN, Double.NaN);
        var fx = out.texts.stream().filter(m -> "skillFx".equals(m.get("type")) && "shockwave".equals(m.get("id"))).findFirst().orElseThrow();
        assertThat(fx.get("x")).isEqualTo(800.0);
        assertThat((double) fx.get("r0")).isEqualTo(r0);
        assertThat((double) fx.get("rMax")).isEqualTo(r0 + r.reachWorld(300, r0));
        assertThat((double) fx.get("speed")).isEqualTo(r.reachWorld(960.0, r0)); // 최대 속도 240 × 4(대시 속도의 2배)
        for (int i = 0; i < 6; i++) r.tick();
        assertThat(near.kbTime).isGreaterThan(0);  // 닿아서 바깥쪽으로 밀렸다
        assertThat(near.x).isGreaterThan(800 + r0 + near.displayRadius + 10);
        assertThat(far.kbTime).isZero();
        assertThat(count(out, "skill", "shockwave")).isEqualTo(1);
        r.skill(a, "shockwave", Double.NaN, Double.NaN); // 쿨타임 중
        assertThat(count(out, "skill", "shockwave")).isEqualTo(1);
        for (int i = 0; i < 20 * 30; i++) r.tick();
        r.skill(a, "shockwave", Double.NaN, Double.NaN);
        assertThat(count(out, "skill", "shockwave")).isEqualTo(2);
    }

    @Test
    void shockwaveRangeSpeedAndCooldownComeFromTuning() {
        Out out = new Out();
        Room r = skillRoom(out, new Room.SkillTuning(1, 300, 1.5, 7, 300, 0.3, 0.3, 20, 3, 30, 5, 40, 600));
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "shockwave", 0, 0);
        var fx = out.texts.stream().filter(m -> "skillFx".equals(m.get("type"))).findFirst().orElseThrow();
        assertThat((double) fx.get("rMax")).isEqualTo(a.displayRadius + r.reachWorld(300, a.displayRadius)); // 가장자리에서 화면 기준 300px
        assertThat((double) fx.get("speed")).isEqualTo(240.0);
        var skill = out.texts.stream().filter(m -> "skill".equals(m.get("type"))).findFirst().orElseThrow();
        assertThat(skill.get("cooldownMs")).isEqualTo(7000L);
    }

    @Test
    void offensiveSkillsCannotBeUsedInsideSafeZone() {
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 0; a.y = 0; // 가운데 세이프존
        r.tick();
        r.skill(a, "shockwave", 1, 0);
        r.skill(a, "hook", 100, 0);
        assertThat(count(out, "skillFail", "shockwave")).isEqualTo(1);
        assertThat(count(out, "skillFail", "hook")).isEqualTo(1);
        assertThat(count(out, "skill", null)).isZero();
    }

    @Test
    void hookCatchesFirstCircleOnLineLosesOneKeywordAndPullsItIntoAForcedCollision() {
        // 4.33: 갈고리가 사거리(가장자리에서 화면 기준 300px) 안의 원을 맞히면 키워드 1개를 잃고 0.3초 동안 끌려와 무조건 부딪힌다(여기서는 점수가 낮아 한 번 더 잃는다)
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
    void darknessBoltAlsoHitsABotAndEveryoneSeesIt() {
        Out out = new Out();
        Room.Member[] h = new Room.Member[1];
        Room r = botRoom(out, 1, h);
        Room.Member human = h[0], bot = botsOf(r).getFirst();
        r.setLoadout(human, java.util.Arrays.asList("darkness", null, null, null));
        bot.protectUntil = 0; human.protectUntil = 0;
        human.x = 800; human.y = 800; bot.x = 1100; bot.y = 800;
        r.tick();
        bot.x = 1100; bot.y = 800;
        r.skill(human, "darkness", bot.x, bot.y);
        tickSeconds(r, 0.4);
        assertThat(bot.darkUntil).isGreaterThan(0);
        assertThat(countFx(out, "skillFx", "darkness")).isEqualTo(1); // 모두에게 표시하려고 방송한다
    }

    @Test
    void collidingWithEqualScoreCountsATieForEachHumanOncePerSecondAndLosesNothing() {
        // 4.3: 현재 유행도가 같은 두 원이 부딪히면 누구도 잃지 않고, 사람마다 "같은 유행도와 부딪힌 횟수"가 센다(같은 짝은 1초에 한 번)
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        r.skill(b, "shield", 0, 0);   // 끌려오는 중: 스킬 불가
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
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
    void shieldSkillGivesProtectionAndUsesCooldown() {
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "shield", 0, 0);
        assertThat(a.protectUntil).isGreaterThan(0);
        assertThat(out.texts).anyMatch(m -> "shield".equals(m.get("type")) && m.get("ms").equals(3000L) && m.get("e").equals(a.entityNo));
        assertThat(out.texts).anyMatch(m -> "skill".equals(m.get("type")) && "shield".equals(m.get("id")) && m.get("cooldownMs").equals(30000L));
    }

    @Test
    void stealthHidesFromMinimapAndEndsWhenAttackSkillIsUsed() {
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        r.skill(a, "shockwave", 0, 0); // 공격 스킬을 쓰면 은신이 풀린다
        assertThat(a.stealthUntil).isZero();
        assertThat(countFx(out, "skillFx", "stealthEnd")).isEqualTo(1);
    }

    @Test
    void unknownSkillAndBotsAreIgnored() {
        Out out = new Out();
        Room r = skillRoom(out, Room.SkillTuning.DEFAULT);
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
        r.skill(a, "shield", 0, 0);                            // 쿨타임 30초
        for (int i = 0; i < 20 * 10; i++) r.tick();            // 10초 뒤 새로고침
        r.disconnect(a, "s1");
        Room.Member back = r.join(1, "s2", "a", 1, 0, List.of());
        assertThat(back).isSameAs(a);
        var cd = r.cooldownsMessage(back);
        assertThat(cd).isNotNull();
        @SuppressWarnings("unchecked") var skills = (java.util.Map<String, Long>) cd.get("skills");
        assertThat(skills.get("shield")).isBetween(19_000L, 20_100L); // 남은 20초쯤
        int before = (int) out.texts.stream().filter(m -> "skill".equals(m.get("type"))).count();
        r.skill(back, "shield", 0, 0);                         // 새로고침했다고 바로 다시 쓸 수는 없다
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
        for (int i = 0; i < 50; i++) r.skill(a, "shockwave", Double.NaN, Double.POSITIVE_INFINITY);
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
        r.setRequiredLevels(java.util.Map.of("shield", 10, "hook", 15, "shockwave", 20, "stealth", 25));
        Room.Member a = r.join(1, "s1", "a", 9, 0, List.of());
        a.x = 800; a.y = 800;
        r.tick();
        r.skill(a, "shield", 0, 0);
        assertThat(count(out, "skill", null)).isZero();
        assertThat(out.texts).anyMatch(m -> "skillFail".equals(m.get("type")) && "level".equals(m.get("reason")) && Integer.valueOf(10).equals(m.get("level")) && "shield".equals(m.get("id")));
        assertThat(a.protectUntil).isZero();                  // 막혔으니 효과도 없다
        r.applyScore(1, 0, 10);                                // 10레벨이 되면 방패는 쓴다
        r.skill(a, "shield", 0, 0);
        assertThat(count(out, "skill", "shield")).isEqualTo(1);
        r.skill(a, "hook", 5000, 800);                         // 갈고리는 아직(15)
        r.skill(a, "stealth", 0, 0);                           // 은신도 아직(25)
        assertThat(count(out, "skill", "hook")).isZero();
        assertThat(count(out, "skill", "stealth")).isZero();
        r.dash(a);                                             // 대시는 기본 스킬(레벨 제한 없음): 움직이는 중이 아니라 효과는 없지만 막히지는 않는다
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
}
