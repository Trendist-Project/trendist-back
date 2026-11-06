package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 연결이 끊겨도 원이 아레나에 남아 있는 동안(offline_keep_seconds, 4.32): 마지막 위치에 그대로 있고 남아 있는 사람처럼 싸우며,
 * 시간이 지나면 진짜로 빠지고, 그 뒤 접속하면 늘 가운데 세이프존에서 시작한다. 마지막 위치는 기억하지 않는다.
 */
class RoomOfflineTest {
    private static void seconds(Room r, int s) { for (int i = 0; i < 20 * s; i++) r.tick(); }

    @Test
    void disconnectedCircleStaysAtItsLastPositionThenLeavesForReal() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        r.setOfflineKeepSeconds(60);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 1234; a.y = -567;
        a.dirX = 1; // 움직이던 중에 끊겼다
        r.disconnect(a, a.session);
        assertThat(a.offline).isTrue();
        seconds(r, 59);
        assertThat(r.byPlayer(1)).isSameAs(a);          // 끊긴 지 59초: 아직 아레나에 있다
        assertThat(a.x).isEqualTo(1234);                 // 마지막 위치에 그대로(움직이지 않는다)
        assertThat(a.y).isEqualTo(-567);
        seconds(r, 2);
        assertThat(r.byPlayer(1)).isNull();              // 60초가 지나면 진짜로 뺀다
        assertThat(r.members()).doesNotContain(a);
    }

    @Test
    void reconnectWithinTheTimeTakesOverTheSameCircle() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 1234; a.y = -567;
        a.protectUntil = 1e9;                            // 잃은 뒤 무적이 걸려 있던 중에 끊겼다고 본다
        double protectUntil = a.protectUntil;
        Object oldSession = a.session;
        r.disconnect(a, oldSession);
        seconds(r, 30);
        Room.Member back = r.join(1, "s1b", "a", 1, 0, List.of());
        assertThat(back).isSameAs(a);                    // 새 원이 아니라 남아 있던 원을 이어받는다
        assertThat(back.offline).isFalse();
        assertThat(back.session).isEqualTo("s1b");
        assertThat(back.x).isEqualTo(1234);              // 위치·보호 그대로
        assertThat(back.y).isEqualTo(-567);
        assertThat(back.protectUntil).isEqualTo(protectUntil);
        r.disconnect(back, oldSession);                  // 이전 연결이 뒤늦게 닫혀도 이어받은 원에는 영향이 없다
        assertThat(back.offline).isFalse();
        seconds(r, 90);
        assertThat(r.byPlayer(1)).isSameAs(a);
    }

    @Test
    void joinAfterTheTimeAlwaysStartsInTheCenterZoneWithoutProtection() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 3000; a.y = 3000;
        r.disconnect(a, a.session);
        seconds(r, 61);
        assertThat(r.byPlayer(1)).isNull();
        Room.Member later = r.join(1, "s2", "a", 1, 0, List.of());
        assertThat(later).isNotSameAs(a);
        assertThat(r.inSafeZone(later.x, later.y)).isTrue();   // 마지막 위치(3000, 3000)가 아니라 가운데 세이프존
        assertThat(later.protectUntil).isZero();               // 세이프존이 안전한 자리라 입장 보호는 따로 주지 않는다
    }

    @Test
    void zeroKeepTimeRemovesTheCircleAtOnce() {
        Room r = new Room(1, RoomTest.T, new RoomTest.Out());
        r.setOfflineKeepSeconds(0);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.disconnect(a, a.session);
        assertThat(r.byPlayer(1)).isNull();
    }

    @Test
    void offlineCircleStillFightsLosesKeywordsAndGetsProtection() {
        // 끊긴 원도 남아 있는 사람처럼 공격받으면 키워드를 잃고 무적이 걸린다
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 10, List.of(100L));
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.protectUntil = 0; b.protectUntil = 0;
        a.x = 800; a.y = 800; b.x = 810; b.y = 800;
        r.disconnect(a, a.session);
        r.tick();
        assertThat(out.loses).hasSize(1);
        assertThat(out.loses.get(0)[0]).isEqualTo(1L);
        assertThat(a.protectUntil).isGreaterThan(0);
        assertThat(out.texts).anyMatch(t -> "shield".equals(t.get("type")) && t.get("e").equals(a.entityNo));
    }

    @Test
    void offlineCircleWithNoKeywordsDiesAndIsRemovedWithoutWatching() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        r.setZoneTuning(RoomTest.NO_RANDOM_ZONES);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 50, List.of(200L));
        a.protectUntil = 0; b.protectUntil = 0;
        a.x = 800; a.y = 800; b.x = 810; b.y = 800;
        r.disconnect(a, a.session);
        r.tick();
        assertThat(a.dead).isTrue();
        assertThat(r.byPlayer(1)).isNull();
        assertThat(r.spectators()).doesNotContain(a);       // 구경할 연결이 없으니 구경 목록에도 넣지 않는다
        assertThat(out.kills).containsExactly(2L);          // 죽인 쪽은 처치로 센다
        assertThat(out.defeats).containsExactly(1L);        // 끊긴 채 남아 있던 원이 져도 패배로 센다
    }

    @Test
    void offlineCircleReceivesNoSnapshots() {
        RoomTest.Out out = new RoomTest.Out();
        Room r = new Room(1, RoomTest.T, out);
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        r.join(2, "s2", "b", 1, 0, List.of());
        r.tick(); r.tick();
        int both = out.snapshots;
        assertThat(both).isEqualTo(2);
        r.disconnect(a, a.session);
        r.tick(); r.tick();
        assertThat(out.snapshots - both).isEqualTo(1);       // 연결이 있는 b에게만 간다
    }
}
