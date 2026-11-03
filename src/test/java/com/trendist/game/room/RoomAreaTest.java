package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 활동 영역을 서서히 줄이기와 서버를 다시 켠 뒤 위치 복원(9.2). 기준 맵 12,000px, 최소 영역은 그 1/4(3,000px). */
class RoomAreaTest {
    private static Room room() { return new Room(1, RoomTest.T, new RoomTest.Out()); }

    @Test
    void areaShrinksOnlyAsFastAsTheSpeedAndGrowsImmediately() {
        Room r = room();
        r.setAreaShrinkSpeed(100);   // 한 변이 1초에 100px씩만 줄어든다
        r.restoreArea(12000);        // 서버를 다시 켠 직후: 저장해 둔 큰 영역에서 시작
        r.join(1, "s1", "a", 1, 0, List.of());
        assertThat(r.currentArea()).isEqualTo(12000);
        for (int i = 0; i < 20; i++) r.tick(); // 1초
        assertThat(r.currentArea()).isCloseTo(11900, within(1.0));
        for (int i = 0; i < 40; i++) r.tick(); // 2초 더(모두 3초)
        assertThat(r.currentArea()).isCloseTo(11700, within(1.5));
        // 줄어드는 동안 원이 영역 안으로 한꺼번에 밀려나지 않는다(영역이 3,000으로 줄었다면 가장자리 ±1,500 안으로 밀렸을 것)
        Room.Member m = r.byPlayer(1);
        m.x = 5000; m.y = 5000;
        r.tick();
        assertThat(m.x).isGreaterThan(4000);
    }

    @Test
    void areaGrowsAtOnceWhenPeopleJoin() {
        Room r = room();
        r.setAreaShrinkSpeed(100);
        r.restoreArea(3000);
        for (int i = 1; i <= 100; i++) r.join(i, "s" + i, "p" + i, 1, 0, List.of());
        assertThat(r.currentArea()).isGreaterThanOrEqualTo(r.activeArea(100)); // 12,000 × √(100/1000) ≈ 3,795
    }

    @Test
    void speedZeroShrinksImmediatelyLikeBefore() {
        Room r = room();
        r.setAreaShrinkSpeed(0);
        r.restoreArea(12000);
        r.join(1, "s1", "a", 1, 0, List.of());
        r.tick();
        assertThat(r.currentArea()).isEqualTo(3000);
    }

    @Test
    void snapshotHasLiveAndRecentlyLeftPositionsWithAge() {
        Room r = room();
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        Room.Member b = r.join(2, "s2", "b", 1, 0, List.of());
        a.x = 100; a.y = 200; b.x = -50; b.y = -60;
        for (int i = 0; i < 20; i++) r.tick();      // 1초 뒤
        r.leave(b);                                  // b는 나감
        for (int i = 0; i < 40; i++) r.tick();      // 2초 더
        var snap = r.snapshotPositions();
        assertThat(snap).hasSize(2);
        var live = snap.stream().filter(p -> p.playerId() == 1).findFirst().orElseThrow();
        var left = snap.stream().filter(p -> p.playerId() == 2).findFirst().orElseThrow();
        assertThat(live.ageSeconds()).isZero();
        assertThat(left.ageSeconds()).isBetween(1.5, 3.0);   // 나간 지 약 2초
        assertThat(Room.REJOIN_KEEP_SECONDS).isEqualTo(300); // 기억하는 시간은 5분
    }

    @Test
    void resumeStartsAtSavedPositionOnlyWhenFreshAndInsideTheArea() {
        Room r = room();
        Room.Member fresh = r.join(1, "s1", "a", 1, 0, List.of(), false, Room.Appearance.DEFAULT, new Room.Resume(1234, -567, 30));
        assertThat(fresh.x).isEqualTo(1234);
        assertThat(fresh.y).isEqualTo(-567);
        // 저장한 지 5분이 지났으면 쓰지 않고 새 자리(가운데가 아닌 무작위 빈 자리)
        Room.Member stale = r.join(2, "s2", "b", 1, 0, List.of(), false, Room.Appearance.DEFAULT, new Room.Resume(1000, 1000, 301));
        assertThat(stale.x == 1000 && stale.y == 1000).isFalse();
        // 영역 밖 위치는 영역 안으로 맞춘다
        Room small = room();
        Room.Member out = small.join(3, "s3", "c", 1, 0, List.of(), false, Room.Appearance.DEFAULT, new Room.Resume(99999, -99999, 1));
        double half = small.currentArea() / 2;
        assertThat(out.x).isEqualTo(half);
        assertThat(out.y).isEqualTo(-half);
    }

    @Test
    void memoryOfLastPositionBeatsResume() {
        Room r = room();
        Room.Member a = r.join(1, "s1", "a", 1, 0, List.of());
        a.x = 400; a.y = 500;
        r.leave(a);
        Room.Member back = r.join(1, "s1b", "a", 1, 0, List.of(), false, Room.Appearance.DEFAULT, new Room.Resume(-900, -900, 10));
        assertThat(back.x).isEqualTo(400); // 방이 기억하는 위치(새로고침)를 먼저 쓴다
        assertThat(back.y).isEqualTo(500);
    }
}
