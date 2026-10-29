package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomTest {
    static final Room.Tuning T = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 10, 0, 0, 3, 2.2, 5, 33, 0, 1.5, 20, 50, 100);

    /** 충돌 테스트: 무작위 세이프존을 끄고, 가운데 고정 세이프존(한 변 600px) 밖에서 부딪히게 한다(4.22). */
    static final Room.ZoneTuning NO_RANDOM_ZONES = new Room.ZoneTuning(0.06, 600, 0.035, 400, 0, 0, 300);

    static class Out implements Room.Outbox {
        final List<long[]> loses = new ArrayList<>();
        final List<ByteBuffer> sent = new ArrayList<>();
        int snapshots;
        public void requestLose(Room room, Room.Member loser, Room.Member winner) { loses.add(new long[]{loser.playerId, winner.playerId}); }
        final List<Long> orbEaters = new ArrayList<>();
        public void requestOrb(Room room, Room.Member eater) { orbEaters.add(eater.playerId); }
        public void send(Room.Member m, ByteBuffer binary) { snapshots++; sent.add(binary); }
        final List<Long> closed = new ArrayList<>();
        public void close(Room.Member m) { closed.add(m.playerId); }
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
        r.setZoneTuning(NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.x = 800; a.y = 800; b.x = 810; b.y = 800;
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
        // 봇도 유행도 = min(EXP, 100) + 키워드 점수 합, 레벨 = 레벨표(EXP), 사람 평균 × 1.5(이 테스트의 배수)를 넘지 않는다
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
        // 사람 평균이 작아지면 상한을 넘는 봇은 키워드를 내려놓거나 바뀐다
        r.members().stream().filter(m -> !m.bot).forEach(m -> m.score = 50);
        for (int i = 0; i < 20 * 30; i++) r.tick();
        assertThat(r.members().stream().filter(m -> m.bot)).allMatch(m -> m.score <= 75);
    }

    @Test
    void humanLosesKeywordToBotAndBotLosesKeywordToHuman() {
        Out out = new Out();
        Room r = new Room(1, withBots(2), out);
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
        // 90초 동안 아무 메시지(ping 포함)도 없으면 방에서 빼고 연결을 닫는다. 메시지를 보내는 사람은 남는다
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
    void firstTimePlayerStartsInCenterZoneAndZonesAreSent() {
        Out out = new Out();
        Room r = new Room(1, T, out);
        for (int i = 0; i < 20; i++) {
            Room.Member m = r.join(100 + i, "s" + i, "new", 1, 0, List.of(), true);
            assertThat(Math.abs(m.x)).isLessThanOrEqualTo(450); // 가운데 존 한 변 900px
            assertThat(Math.abs(m.y)).isLessThanOrEqualTo(450);
        }
        assertThat(out.texts).anyMatch(t -> "safeZones".equals(t.get("type")));
    }

    @Test
    void randomZonesSpawnUpToTargetAndExpire() {
        // 24,000px 기준 맵 1000명이면 넓이 비례로 여러 개, 맵이 3,000px이면 1개. 유지 시간이 지나면 사라진다
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
        Room.Tuning avg = new Room.Tuning(24000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.6, 0.5, 3, 0, 0, 3, 2.2, 5, 100, 10, 1.0, 20, 50, 100);
        Room r = new Room(1, avg, new Out());
        r.join(1, "s1", "작은", 1, 100, List.of());
        r.join(2, "s2", "큰", 1, 500, List.of());
        assertThat(r.botCap(avg)).isEqualTo(300.0);
        for (int i = 0; i < 20 * 12; i++) r.tick(); // 봇이 들어와도 평균은 사람만으로
        assertThat(r.members().stream().filter(m -> m.bot)).isNotEmpty();
        assertThat(r.botCap(avg)).isEqualTo(300.0);
        assertThat(r.members().stream().filter(m -> m.bot)).allMatch(m -> m.score <= 300);
    }
}
