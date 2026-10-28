package com.trendist.keyword.support;

import com.trendist.common.exception.ApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KeywordNormalizerTest {
    @Test
    void sameWordWithDifferentSpacingAndWidth() {
        assertThat(KeywordNormalizer.normalize("K POP")).isEqualTo("kpop");
        assertThat(KeywordNormalizer.normalize("ｋｐｏｐ")).isEqualTo("kpop");
        assertThat(KeywordNormalizer.normalize("두바이 쫀득 쿠키")).isEqualTo(KeywordNormalizer.normalize("두바이쫀득쿠키"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> KeywordNormalizer.validate("")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> KeywordNormalizer.validate("가".repeat(21))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> KeywordNormalizer.validate("www.test")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> KeywordNormalizer.validate("01012345678")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> KeywordNormalizer.validate("양쯔<깐루")).isInstanceOf(ApiException.class);
        assertThat(KeywordNormalizer.validate("양쯔깐루")).isEqualTo("양쯔깐루");
        // 최소 길이 제한은 없다: 한 글자 키워드도 된다
        assertThat(KeywordNormalizer.validate("a")).isEqualTo("a");
        assertThat(KeywordNormalizer.validate("밈")).isEqualTo("밈");
    }
}
