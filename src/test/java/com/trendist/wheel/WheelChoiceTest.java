package com.trendist.wheel;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.wheel.service.WheelService;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/** 돌림판 확률: 칸 구간이 비중대로 나뉘고, 많이 돌리면 기대 비율에 가까워지며, 최대 칸은 20이다. */
class WheelChoiceTest {
    private static final double[] DEFAULT = {22, 20, 16, 13, 10, 7, 5, 4, 2, 1};

    private static int choose(double[] w, double roll) throws Exception {
        Class<?> c = Class.forName("com.trendist.wheel.service.WheelServiceImpl");
        Method m = c.getDeclaredMethod("choose", double[].class, double.class);
        m.setAccessible(true);
        return (int) m.invoke(null, w, roll);
    }

    @Test
    void slicesTopOutAtTwentyAndAreAscending() {
        int[] s = WheelService.SLICES;
        assertThat(s).hasSize(10).isSorted();
        assertThat(s[s.length - 1]).isEqualTo(20);
        assertThat(s[0]).isEqualTo(1);
    }

    @Test
    void rollsMapToSlicesByTheirWeightRanges() throws Exception {
        assertThat(choose(DEFAULT, 0)).isEqualTo(0);
        assertThat(choose(DEFAULT, 21.99)).isEqualTo(0);
        assertThat(choose(DEFAULT, 22)).isEqualTo(1);           // 0번 구간은 [0, 22)
        assertThat(choose(DEFAULT, 99.5)).isEqualTo(9);         // 마지막 칸은 [99, 100)
        double[] zeroFirst = {0, 5, 0, 5};
        assertThat(choose(zeroFirst, 0)).isEqualTo(1);          // 비중 0인 칸은 뽑히지 않는다
        assertThat(choose(zeroFirst, 4.999)).isEqualTo(1);
        assertThat(choose(zeroFirst, 5)).isEqualTo(3);
        assertThat(choose(zeroFirst, 10)).isEqualTo(3);         // 오차로 합을 넘어도 마지막으로 비중 있는 칸
    }

    @Test
    void manySpinsApproachTheConfiguredProbabilities() throws Exception {
        int n = 200_000;
        int[] hits = new int[DEFAULT.length];
        double total = 0; for (double d : DEFAULT) total += d;
        for (int i = 0; i < n; i++) hits[choose(DEFAULT, ThreadLocalRandom.current().nextDouble(total))]++;
        double mean = 0;
        for (int i = 0; i < DEFAULT.length; i++) {
            double expected = DEFAULT[i] / total;
            assertThat(hits[i] / (double) n).isCloseTo(expected, org.assertj.core.api.Assertions.within(0.006)); // 20만 번이면 ±0.6%p 안
            mean += WheelService.SLICES[i] * hits[i] / (double) n;
        }
        assertThat(mean).isCloseTo(3.84, org.assertj.core.api.Assertions.within(0.1)); // 기대 개수 약 3.8개(22+40+48+52+50+42+40+40+30+20 = 384 ÷ 100)
    }
}
