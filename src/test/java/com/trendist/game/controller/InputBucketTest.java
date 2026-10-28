package com.trendist.game.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputBucketTest {
    private static final long MS = 1_000_000L;

    @Test
    void burstArrivalsOfSteadyClicksAreNotDropped() {
        // 초당 10회 클릭이 프록시에서 몰려 0.3초 멈췄다가 한꺼번에 도착해도 하나도 버리지 않는다
        var b = new GameSocketHandler.Bucket(24, 0);
        long t = 0;
        int accepted = 0;
        for (int sec = 0; sec < 5; sec++) {
            for (int i = 0; i < 10; i++) {
                long arrive = t + (i < 3 ? 300 * MS : i * 100 * MS); // 앞의 3개가 0.3초 늦게 몰려 옴
                if (b.take(arrive, 12, 24)) accepted++;
            }
            t += 1000 * MS;
        }
        assertThat(accepted).isEqualTo(50);
    }

    @Test
    void sustainedFloodIsLimitedToRate() {
        var b = new GameSocketHandler.Bucket(24, 0);
        int accepted = 0;
        for (int i = 0; i < 1000; i++) if (b.take(i * 10 * MS, 12, 24)) accepted++; // 10초 동안 초당 100회
        assertThat(accepted).isBetween(120, 24 + 121); // 처음 모아 둔 24개 + 초당 12개
    }
}
