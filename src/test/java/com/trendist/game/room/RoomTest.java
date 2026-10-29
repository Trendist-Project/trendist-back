package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomTest {
    static final Room.Tuning T = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.5, 10, 0, 0, 3);

    static class Out implements Room.Outbox {
        final List<long[]> loses = new ArrayList<>();
        final List<ByteBuffer> sent = new ArrayList<>();
        int snapshots;
        public void requestLose(Room room, Room.Member loser, Room.Member winner) { loses.add(new long[]{loser.playerId, winner.playerId}); }
        final List<Long> orbEaters = new ArrayList<>();
        public void requestOrb(Room room, Room.Member eater) { orbEaters.add(eater.playerId); }
        public void send(Room.Member m, ByteBuffer binary) { snapshots++; sent.add(binary); }
        public void sendText(Room.Member m, Map<String, Object> message) { }
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
        Room r = new Room(1, new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.5, 10, 0, 5, 0.5), new Out());
        r.join(1, "s1", "a", 1, 0, List.of());
        for (int i = 0; i < 20 * 10; i++) r.tick(); // 10초
        assertThat(r.orbCount()).isEqualTo(5);
    }

    @Test
    void rejoinWithinOneMinuteKeepsLastPositionAndProtection() {
        Room.Tuning withProtect = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.5, 10, 5, 0, 3);
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
        assertThat(r.liveView().get(2L)).isEqualTo(new Room.Live(2, "나", 5, 120));
        r.leave(a);
        for (int i = 0; i < 20; i++) r.tick();
        assertThat(r.liveView()).containsOnlyKeys(2L);
    }
}
