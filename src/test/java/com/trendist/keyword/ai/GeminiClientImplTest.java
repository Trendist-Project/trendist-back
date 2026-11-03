package com.trendist.keyword.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 제미나이 응답을 다듬는 부분(네트워크 없이). */
class GeminiClientImplTest {
    private final GeminiClientImpl client = new GeminiClientImpl(JsonMapper.builder().build(), "key", "model");

    @Test
    void parsesBatchByNumberAndDropsBrokenItems() {
        List<String> kws = List.of("가", "나", "다");
        var r = client.parse("""
                [{"no":1,"comment":"가는 뜻이에요."},{"no":2,"comment":"UNKNOWN"},{"no":9,"comment":"범위 밖"},{"comment":"번호 없음"}]""", kws);
        assertThat(r.get("가")).contains("가는 뜻이에요.");
        assertThat(r.get("나")).isEmpty();          // 모른다
        assertThat(r).doesNotContainKey("다");        // 답이 빠짐
        assertThat(r).hasSize(2);
    }

    @Test
    void brokenJsonGivesNoAnswers() {
        assertThat(client.parse("이건 JSON이 아니에요", List.of("가"))).isEmpty();
        assertThat(client.parse("{\"no\":1}", List.of("가"))).isEmpty();
    }

    @Test
    void cleanRejectsUnknownLinksAndLongText() {
        assertThat(GeminiClientImpl.clean("UNKNOWN")).isEmpty();
        assertThat(GeminiClientImpl.clean("자세한 건 https://x.com 에서 봐요")).isEmpty();
        assertThat(GeminiClientImpl.clean("가".repeat(201))).isEmpty();
        assertThat(GeminiClientImpl.clean("  **굵게** 쓴  설명이에요.\n두 번째 줄  ")).isEqualTo(Optional.of("굵게 쓴 설명이에요."));
    }
}
