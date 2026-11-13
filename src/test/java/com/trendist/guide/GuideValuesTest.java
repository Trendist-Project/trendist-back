package com.trendist.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.guide.support.GuideValues;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 가이드 본문의 설정값 자리(4.17): {{이름}}·식·내림, 모르는 이름은 그대로 */
class GuideValuesTest {
    private static final Map<String, Double> V = Map.of("a", 3.0, "b", 0.15, "c", 50.0);

    private static String r(String body) {
        return GuideValues.render(body, n -> { Double v = V.get(n); if (v == null) throw new IllegalArgumentException(n); return v; });
    }

    @Test
    void replacesNamesExpressionsAndFloors() {
        assertThat(r("{{a}}초, {{b}}초")).isEqualTo("3초, 0.15초");
        assertThat(r("{{a*a}}초")).isEqualTo("9초");
        assertThat(r("{{100-c}}%")).isEqualTo("50%");
        assertThat(r("{{c*c*c/10000}}% / {{c*c*c/10000|floor}}%")).isEqualTo("12.5% / 12%");
        assertThat(r("+{{3*a/1}}")).isEqualTo("+9");
    }

    @Test
    void unknownNamesAndBrokenExpressionsStayAsWritten() {
        assertThat(r("{{nope}} {{a*}} {{a}}")).isEqualTo("{{nope}} {{a*}} 3");
        assertThat(r("글자만 있는 본문")).isEqualTo("글자만 있는 본문");
        assertThat(GuideValues.render(null, n -> 0)).isNull();
    }
}
