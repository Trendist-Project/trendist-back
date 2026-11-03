package com.trendist.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.common.exception.ApiException;
import com.trendist.keyword.ai.GeminiClient;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.service.AiCommentService;
import com.trendist.keyword.service.KeywordService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 키워드 AI 한 줄 설명(4.12): 처음 🔥가 될 때 만들기, 카드에 보이기, 갱신 요청, 관리자 재요청·수정. 제미나이는 가짜로 바꿔 끼운다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AiCommentTest {
    @Autowired MockMvc mvc;
    @Autowired KeywordService keywordService;
    @Autowired KeywordRepository keywords;
    @Autowired com.trendist.keyword.repository.KeywordAiCommentRepository aiRows;
    @Autowired AiCommentService ai;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean GeminiClient gemini;
    @MockitoBean com.trendist.keyword.ai.NewsSearchClient newsSearch;

    /** 가짜 제미나이: 받은 키워드마다 같은 설명(또는 모름)을 돌려준다 */
    private void answer(String comment) throws Exception {
        when(gemini.describeAll(anyList(), anyMap(), anyBoolean())).thenAnswer(inv -> {
            java.util.Map<String, Optional<String>> m = new java.util.LinkedHashMap<>();
            for (String k : inv.<List<String>>getArgument(0)) m.put(k, Optional.ofNullable(comment));
            return m;
        });
    }

    private String guestWithNick(String nick) throws Exception {
        String t = JsonPath.read(mvc.perform(post("/api/auth/guest")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + t).contentType("application/json")
                .content("{\"nickname\":\"" + nick + "\"}")).andExpect(status().isOk());
        return t;
    }

    /** 키워드의 AI 설명(행이 없거나 설명이 비면 null) / 시도 횟수(행이 없으면 0) */
    private String commentOf(long id) { return aiRows.findByKeywordId(id).map(com.trendist.keyword.entity.KeywordAiComment::getComment).orElse(null); }
    private long attemptsOf(long id) { return aiRows.findByKeywordId(id).map(com.trendist.keyword.entity.KeywordAiComment::getAttempts).orElse(0L); }

    private long keywordId(String text) {
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of(text));
        return keywords.findAll().stream().filter(k -> k.getText().equals(text)).findFirst().orElseThrow().getId();
    }

    @Test
    void generatesOnceForNewActiveKeywordAndShowsOnCard() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        answer("테스트 유행어를 뜻하는 말이에요.");
        long id = keywordId("에이아이설명1");
        assertThat(commentOf(id)).isNull();
        ai.generatePending();
        assertThat(commentOf(id)).isEqualTo("테스트 유행어를 뜻하는 말이에요.");
        ai.generatePending(); // 이미 있으면 다시 만들지 않는다
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.times(1)).describeAll(anyList(), anyMap(), anyBoolean());

        String t = guestWithNick("설명보기");
        mvc.perform(get("/api/keywords/" + id).header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.aiComment").value("테스트 유행어를 뜻하는 말이에요.")).andExpect(jsonPath("$.aiCommentRequested").value(false));
    }

    @Test
    void unknownStopsAutoRetryAndAdminCanWriteByHand() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        answer(null);
        long id = keywordId("에이아이모름");
        ai.generatePending();
        assertThat(commentOf(id)).isNull();
        assertThat(attemptsOf(id)).isGreaterThanOrEqualTo(3); // 모른다고 하면 자동 요청은 멈춘다
        var missing = ai.list("KR", "missing", 0);
        assertThat(missing.items()).anyMatch(r -> r.keywordId() == id);
        assertThatThrownBy(() -> ai.regenerate(id)).isInstanceOf(ApiException.class); // AI가 계속 모르면 오류로 알려 준다
        var row = ai.edit(id, "  직접   쓴 설명이에요.  ");
        assertThat(row.comment()).isEqualTo("직접 쓴 설명이에요.");
        assertThat(row.by()).isEqualTo("ADMIN");
        assertThatThrownBy(() -> ai.edit(id, "")).isInstanceOf(ApiException.class);
    }

    @Test
    void requestFlowUserAsksAdminRegeneratesAndClosesRequests() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        answer("처음 설명이에요.");
        long id = keywordId("에이아이요청");
        ai.generatePending();
        String t = guestWithNick("요청자");
        mvc.perform(post("/api/keywords/" + id + "/ai-comment/request").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        mvc.perform(post("/api/keywords/" + id + "/ai-comment/request").header("Authorization", "Bearer " + t)).andExpect(status().isOk()); // 두 번 눌러도 하나
        mvc.perform(get("/api/keywords/" + id).header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.aiCommentRequested").value(true));
        var open = ai.list("KR", "requests", 0);
        var mine = open.items().stream().filter(r -> r.keywordId() == id).findFirst().orElseThrow();
        assertThat(mine.openRequests()).isEqualTo(1);
        assertThat(ai.openRequestCount()).isGreaterThanOrEqualTo(1);

        answer("다시 받은 설명이에요.");
        var re = ai.regenerate(id);
        assertThat(re.comment()).isEqualTo("다시 받은 설명이에요.");
        assertThat(re.openRequests()).isZero();
        assertThat(ai.list("KR", "requests", 0).items()).noneMatch(r -> r.keywordId() == id);
        mvc.perform(get("/api/keywords/" + id).header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.aiCommentRequested").value(false));
    }

    @Test
    void sendsManyKeywordsInOneRequestAndHandlesMissingAnswers() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        long a = keywordId("묶음하나"), b = keywordId("묶음둘"), c = keywordId("묶음셋");
        // 한 번의 요청: 하나는 설명, 하나는 모름(UNKNOWN), 하나는 답이 빠짐
        when(gemini.describeAll(anyList(), anyMap(), anyBoolean())).thenAnswer(inv -> {
            java.util.Map<String, Optional<String>> m = new java.util.LinkedHashMap<>();
            m.put("묶음하나", Optional.of("첫째 설명이에요."));
            m.put("묶음둘", Optional.empty());
            return m;
        });
        org.mockito.Mockito.clearInvocations(gemini);
        ai.generatePending();
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.times(1)).describeAll(anyList(), anyMap(), anyBoolean());
        assertThat(commentOf(a)).isEqualTo("첫째 설명이에요.");
        assertThat(attemptsOf(b)).isGreaterThanOrEqualTo(3); // 모른다 → 포기
        assertThat(commentOf(c)).isNull();
        assertThat(attemptsOf(c)).isEqualTo(1); // 답이 빠짐 → 다음에 다시
    }

    @Test
    void disabledWithoutApiKeyDoesNothing() throws Exception {
        when(gemini.enabled()).thenReturn(false);
        long id = keywordId("에이아이꺼짐");
        assertThat(ai.generatePending()).isZero();
        assertThat(commentOf(id)).isNull();
        assertThatThrownBy(() -> ai.regenerate(id)).isInstanceOf(ApiException.class);
    }

    // 한도를 만나면 서비스가 10분 쉬는 상태를 기억하므로, 다른 시험에 번지지 않게 이 시험이 끝나면 컨텍스트를 새로 만든다
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void rateLimitedPausesAutoRequests() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        when(gemini.describeAll(anyList(), anyMap(), anyBoolean())).thenThrow(new GeminiClient.RateLimitedException("429"));
        keywordId("에이아이한도");
        assertThat(ai.generatePending()).isZero();
        org.mockito.Mockito.clearInvocations(gemini);
        ai.generatePending(); // 한도를 만났으면 한동안 쉰다
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.never()).describeAll(anyList(), anyMap(), anyBoolean());
    }

    @Test
    void searchesNewsForKeywordWithoutRssContextAndPassesHeadlines() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        when(newsSearch.search("직접등록유행어")).thenReturn(List.of("직접등록유행어 열풍에 품절 대란"));
        answer("품절 대란이 일어나 화제가 됐어요.");
        long id = keywordId("직접등록유행어");
        org.mockito.Mockito.clearInvocations(gemini);
        ai.generatePending();
        org.mockito.ArgumentCaptor<java.util.Map<String, List<String>>> cap = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(gemini).describeAll(anyList(), cap.capture(), anyBoolean());
        assertThat(cap.getValue()).containsEntry("직접등록유행어", List.of("직접등록유행어 열풍에 품절 대란"));
        assertThat(commentOf(id)).isEqualTo("품절 대란이 일어나 화제가 됐어요.");
    }

    @Test
    void keywordPointsToAiRowOnlyAfterFirstAttemptAndDeletingRowClearsPointer() throws Exception {
        when(gemini.enabled()).thenReturn(true);
        answer("연결 확인용 설명이에요.");
        long id = keywordId("연결확인키워드");
        assertThat(jdbc.queryForObject("select ai_comment_id from keywords where id = ?", Long.class, id)).isNull(); // 시도 전에는 행이 없다
        ai.generatePending();
        Long rowId = jdbc.queryForObject("select ai_comment_id from keywords where id = ?", Long.class, id);
        assertThat(rowId).isNotNull();
        assertThat(jdbc.queryForObject("select comment from keyword_ai_comments where id = ?", String.class, rowId)).isEqualTo("연결 확인용 설명이에요.");
        jdbc.update("delete from keyword_ai_comments where id = ?", rowId); // 설명 행을 지워도 키워드는 남고 연결만 풀린다
        assertThat(jdbc.queryForObject("select ai_comment_id from keywords where id = ?", Long.class, id)).isNull();
    }
}
