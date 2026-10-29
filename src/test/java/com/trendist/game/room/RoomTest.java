package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomTest {
    static final Room.Tuning T = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 0, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100);

    static class Out implements Room.Outbox {
        final List<long[]> loses = new ArrayList<>();
        final List<ByteBuffer> sent = new ArrayList<>();
        int snapshots;
        public void requestLose(Room room, Room.Member loser, Room.Member winner) { loses.add(new long[]{loser.playerId, winner.playerId}); }
        final List<Long> orbEaters = new ArrayList<>();
        public void requestOrb(Room room, Room.Member eater) { orbEaters.add(eater.playerId); }
        public void send(Room.Member m, ByteBuffer binary) { snapshots++; sent.add(binary); }
        final List<Map<String, Object>> texts = new ArrayList<>(); // 사람에게 보낸 글 메시지(봇에게 가는 것은 실제로는 버려진다)
        public void sendText(Room.Member m, Map<String, Object> message) { if (!m.bot) texts.add(message); }
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
        Room.Member m = r.join(1, "s1", "a", 1, 0, List.of());
        m.x = 0; m.y = 0;
        r.placeOrb(5, 0);     // 원(반지름 16) 안
        r.placeOrb(300, 300); // 멀리
        r.tick();
        assertThat(out.orbEaters).containsExactly(1L);
        assertThat(r.orbCount()).isEqualTo(1);
    }

    @Test
    void orbsSpawnUpToMax() {
        Room r = new Room(1, new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 5, 0.5, 2.2, 5, 33, 0, 1.5, 20, 50, 100), new Out());
        r.join(1, "s1", "a", 1, 0, List.of());
        for (int i = 0; i < 20 * 10; i++) r.tick(); // 10초
        assertThat(r.orbCount()).isEqualTo(5);
    }

    @Test
    void rejoinWithinOneMinuteKeepsLastPositionAndProtection() {
        Room.Tuning withProtect = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 5, 0, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100);
        Room r = new Room(1, withProtect, new Out());
        Room.Member m = r.join(1, "s1", "a", 1, 0, List.of());
        m.x = 321; m.y = -123;
        double protectUntil = m.protectUntil;
        r.leave(m);
        for (int i = 0; i < 20 * 30; i++) r.tick(); // 30초 뒤 새로고침

        Room.Member back = r.join(1, "s2", "a", 1, 0, List.of());
        assertThat(back.x).isEqualTo(321);
        assertThat(back.y).isEqualTo(-123);
        assertThat(back.protectUntil).isEqualTo(protectUntil); // 들어올 때 보호를 다시 받지 않는다

        back.x = 321; back.y = -123;
        r.leave(back);
        for (int i = 0; i < 20 * 61; i++) r.tick(); // 1분이 지나면 새 자리
        Room.Member later = r.join(1, "s3", "a", 1, 0, List.of());
        assertThat(later.x != 321 || later.y != -123).isTrue();
        assertThat(later.protectUntil).isGreaterThan(protectUntil);
    }

    @Test
    void lowerScoreLosesKeywordOnCollision() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.x = 0; a.y = 0; b.x = 10; b.y = 0;
        r.tick();
        assertThat(out.loses).hasSize(1);
        assertThat(out.loses.get(0)[0]).isEqualTo(1L);
        assertThat(out.snapshots).isGreaterThanOrEqualTo(0);
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
        // 적은 인원에서도 기준 맵 한 변의 1/4, 1000명이면 한 변 전체(4.2)
        Room small = new Room(1, new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 0, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100), new Out());
        assertThat(small.activeArea(1)).isEqualTo(6000);
        assertThat(small.activeArea(1000)).isEqualTo(24000);
        assertThat(new Room(1, T, new Out()).activeArea(1)).isEqualTo(3000);
    }

    @Test
    void orbTargetFollowsArea() {
        // 3,000×3,000px에 20개였던 비율(밀도 2.2)을 넓이에 비례해 유지한다. 상한 orbMax를 넘지 않는다(4.20)
        Room.Tuning tu = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 1500, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100);
        Room r = new Room(1, tu, new Out());
        assertThat(r.orbTarget(tu, 1500)).isEqualTo(20);     // 3,000px 영역
        assertThat(r.orbTarget(tu, 3000)).isEqualTo(79);     // 6,000px 영역(지금 최소)
        assertThat(r.orbTarget(tu, 12000)).isEqualTo(1267);  // 24,000px 영역(1000명)
        Room.Tuning capped = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 100, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100);
        assertThat(r.orbTarget(capped, 12000)).isEqualTo(100);
    }

    @Test
    void orbsFillInBatches() {
        // 목표 79개면 간격마다 5%(4개)씩 생긴다
        Room.Tuning tu = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 1500, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100);
        Room r = new Room(1, tu, new Out());
        r.join(1, "s1", "a", 1, 0, List.of());
        r.tick();
        assertThat(r.orbCount()).isEqualTo(4);
    }

    static Room.Tuning withBots(int botPlayers) {
        return new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 3, 0, 0, 3, 2.2, 5, 100, botPlayers, 1.5, 20, 50, 100);
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
        // 봇도 유행도 = min(EXP, 100) + 키워드 점수 합, 레벨 = 레벨표(EXP), 1등 사람 × 1.5를 넘지 않는다
        Room r = new Room(1, withBots(10), new Out());
        r.join(1, "s1", "사람", 1, 400, List.of());
        for (int i = 0; i < 20 * 12; i++) r.tick();
        for (Room.Member b : r.members()) {
            if (!b.bot) continue;
            int sum = b.botKeywords.stream().mapToInt(Integer::intValue).sum();
            assertThat(b.score).isEqualTo(Math.min(b.botExp, 100) + sum);
            assertThat(b.level).isEqualTo(Room.defaultLevelForExp(b.botExp));
            assertThat(b.score).isLessThanOrEqualTo(600); // 400 × 1.5
            assertThat((long) b.botKeywords.size()).isLessThanOrEqualTo(b.botExp); // 추가한 횟수보다 많이 가질 수 없다
        }
        // 1등이 작아지면 상한을 넘는 봇은 키워드를 내려놓거나 바뀐다
        r.members().stream().filter(m -> !m.bot).forEach(m -> m.score = 50);
        for (int i = 0; i < 20 * 30; i++) r.tick();
        assertThat(r.members().stream().filter(m -> m.bot)).allMatch(m -> m.score <= 75);
    }

    @Test
    void humanLosesKeywordToBotAndBotLosesKeywordToHuman() {
        Out out = new Out();
        Room r = new Room(1, withBots(2), out);
        Room.Member human = r.join(1, "s1", "사람", 1, 1000, List.of(11L));
        for (int i = 0; i < 25; i++) r.tick(); // 봇 하나가 들어온다
        Room.Member bot = r.members().stream().filter(m -> m.bot).findFirst().orElseThrow();
        bot.protectUntil = 0; human.protectUntil = 0;

        // 봇이 더 크면 사람이 키워드를 잃는다(봇에게 져도 잃는다)
        bot.score = 900; human.score = 100;
        bot.x = 0; bot.y = 0; human.x = 10; human.y = 0;
        r.tick();
        assertThat(out.loses).containsExactly(new long[]{1L, bot.playerId});
        // 잃은 쪽(사람)에 3초 무적: 모두에게 보호막 카운트다운을 알린다
        assertThat(out.texts).anyMatch(t -> "shield".equals(t.get("type")) && t.get("e").equals(human.entityNo) && t.get("ms").equals(3000L));

        // 사람이 더 크면 봇은 (메모리의) 키워드 하나를 잃는다
        // 1등(사람) 점수를 먼저 되돌린다. 100점이면 봇 상한이 150이라, 기다리는 동안 봇이 상한 정리로 바뀔 수 있다
        bot.x = -2500; bot.y = -2500; human.x = 2500; human.y = 2500;
        human.score = 900; bot.botExp = 10; bot.botKeywords.clear(); bot.botKeywords.addAll(List.of(30, 40)); bot.score = 80;
        for (int i = 0; i < 20; i++) r.tick(); // 같은 쌍 쿨다운이 지나게 떨어뜨려 기다린다
        bot.protectUntil = 0; human.protectUntil = 0;
        bot.botExp = 10; bot.botKeywords.clear(); bot.botKeywords.addAll(List.of(30, 40));
        bot.score = 80; human.score = 900;
        bot.x = 2000; bot.y = 2000; human.x = 2010; human.y = 2000;
        r.tick();
        assertThat(bot.botKeywords).hasSize(1);
        assertThat(bot.score).isIn(40.0, 50.0); // 10(EXP) + 남은 키워드 30 또는 40
        // 이긴 사람 화면에 봇이 잃은 키워드 점수(봇 키워드는 이름이 없다), 봇에게도 무적 카운트다운
        assertThat(out.texts).anyMatch(t -> "hit".equals(t.get("type")) && "Bot".equals(t.get("target")) && (t.get("score").equals(30) || t.get("score").equals(40)));
        assertThat(out.texts).anyMatch(t -> "shield".equals(t.get("type")) && t.get("e").equals(bot.entityNo));
        assertThat(out.loses).hasSize(1); // 봇이 진 것은 서버 키워드 잃기 요청이 아니다
    }

    @Test
    void knockbackDistanceFollowsMaxSpeed() {
        // 같은 크기끼리 부딪히면 각자 최대 속도 × 0.6초만큼(240px/s면 144px) 밀려나고, 처음에 빠르고 점점 느려진다(4.3)
        Out out = new Out();
        Room r = new Room(1, T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 100, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 100, List.of());
        a.x = 0; a.y = 0; b.x = 2 * a.radius - 10; b.y = 0; // 10px 겹침
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
}
