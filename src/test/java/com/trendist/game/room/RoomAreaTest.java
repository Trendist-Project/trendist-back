package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 활동 영역을 서서히 줄이기(9.2). 기준 맵 12,000px, 최소 영역은 그 1/4(3,000px). */
class RoomAreaTest {
    private static Room room() { return new Room(1, RoomTest.T, new RoomTest.Out()); }

    /** 250명이 들어와 영역을 6,000px로 키운 뒤 한 명(1번)만 남기고 모두 내보낸다. 목표 영역은 3,000px로 줄어든다. */
    private static Room roomShrinkingFromSixThousand(Room.Member[] keep) {
        Room r = room();
        r.setAreaGrowSpeed(0); // 커질 때 바로(이 테스트는 줄어드는 쪽을 본다)
        List<Room.Member> all = new ArrayList<>();
        for (int i = 1; i <= 250; i++) all.add(r.join(i, "s" + i, "p" + i, 1, 0, List.of()));
        r.tick();
        assertThat(r.currentArea()).isEqualTo(6000);
        for (int i = 1; i < all.size(); i++) r.leave(all.get(i));
        keep[0] = all.get(0);
        return r;
    }

    @Test
    void areaShrinksOnlyAsFastAsTheSpeedAndGrowsImmediately() {
        Room.Member[] keep = new Room.Member[1];
        Room r = roomShrinkingFromSixThousand(keep);
        r.setAreaShrinkSpeed(100);   // 한 변이 1초에 100px씩만 줄어든다
        for (int i = 0; i < 20; i++) r.tick(); // 1초
        assertThat(r.currentArea()).isCloseTo(5900, within(1.0));
        for (int i = 0; i < 40; i++) r.tick(); // 2초 더(모두 3초)
        assertThat(r.currentArea()).isCloseTo(5700, within(1.5));
        // 줄어드는 동안 원이 영역 안으로 한꺼번에 밀려나지 않는다(영역이 3,000으로 줄었다면 가장자리 ±1,500 안으로 밀렸을 것)
        Room.Member m = keep[0];
        m.x = 2500; m.y = 2500;
        r.tick();
        assertThat(m.x).isGreaterThan(2000);
    }

    @Test
    void areaGrowsOnlyAsFastAsTheGrowSpeedSoTheWallMovesSmoothly() {
        // 사람·봇이 늘어도 맵 벽이 갑자기 튀지 않고 areaGrowSpeed(px/s)로만 커진다
        Room r = room();
        r.setAreaGrowSpeed(100);
        for (int i = 1; i <= 100; i++) r.join(i, "s" + i, "p" + i, 1, 0, List.of());
        double target = r.activeArea(100);                // 12,000 × √(100/1000) ≈ 3,795
        assertThat(r.currentArea()).isEqualTo(3000);      // 입장만으로는 바로 커지지 않는다(방의 처음 크기)
        for (int i = 0; i < 20; i++) r.tick();             // 1초
        assertThat(r.currentArea()).isCloseTo(3100, within(1.0));
        for (int i = 0; i < 20 * 9; i++) r.tick();         // 모두 10초: 목표에 닿고 멈춘다
        assertThat(r.currentArea()).isCloseTo(target, within(0.5));
        // 0이면 바로 커진다
        Room fast = room();
        fast.setAreaGrowSpeed(0);
        for (int i = 1; i <= 100; i++) fast.join(i, "s" + i, "p" + i, 1, 0, List.of());
        fast.tick();
        assertThat(fast.currentArea()).isCloseTo(target, within(0.5));
    }

    @Test
    void speedZeroShrinksImmediatelyLikeBefore() {
        Room.Member[] keep = new Room.Member[1];
        Room r = roomShrinkingFromSixThousand(keep);
        r.setAreaShrinkSpeed(0);
        r.tick();
        assertThat(r.currentArea()).isEqualTo(3000);
    }
}
