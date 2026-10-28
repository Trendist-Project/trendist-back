package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomTest {
    static final Room.Tuning T = new Room.Tuning(12000, 16, 600, 2000, 240, 170, 1.15, 3, 0.4, 0.5, 10, 0);

    static class Out implements Room.Outbox {
        final List<long[]> loses = new ArrayList<>();
        final List<ByteBuffer> sent = new ArrayList<>();
        int snapshots;
        public void requestLose(Room room, Room.Member loser, Room.Member winner) { loses.add(new long[]{loser.playerId, winner.playerId}); }
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
}
