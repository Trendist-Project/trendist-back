package com.trendist.keyword.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 공개 키워드 페이지 그래프(4.28): 게임 안 팝업(chartMath.ts)과 같은 곡선·축 값을 만든다 */
class ChartSvgTest {
    @Test
    void smoothPathMatchesTheFrontendCurve() {
        List<double[]> pts = List.of(new double[]{34, 174}, new double[]{114, 120}, new double[]{194, 150}, new double[]{274, 40},
                new double[]{354, 90}, new double[]{434, 90}, new double[]{514, 60});
        // chartMath.ts smoothPath의 같은 입력 결과(소수 둘째 자리까지)
        assertThat(ChartSvg.smoothPath(pts)).isEqualTo("M34,174 C60.67,156 87.33,120 114,120 C140.67,120 167.33,150 194,150 C220.67,150 247.33,40 274,40 "
                + "C300.67,40 327.33,90 354,90 C380.67,90 407.33,90 434,90 C460.67,90 487.33,70 514,60");
        assertThat(ChartSvg.smoothPath(List.of())).isEmpty();
        assertThat(ChartSvg.smoothPath(List.of(new double[]{1, 2}))).isEqualTo("M1,2");
    }

    @Test
    void niceMaxKeepsAxisTicksAsWholeNumbers() {
        assertThat(ChartSvg.niceMax(1)).isEqualTo(2);
        assertThat(ChartSvg.niceMax(3)).isEqualTo(4);
        assertThat(ChartSvg.niceMax(5)).isEqualTo(6);
        assertThat(ChartSvg.niceMax(7)).isEqualTo(10);
        assertThat(ChartSvg.niceMax(10)).isEqualTo(10);
        assertThat(ChartSvg.niceMax(11)).isEqualTo(20);
    }
}
