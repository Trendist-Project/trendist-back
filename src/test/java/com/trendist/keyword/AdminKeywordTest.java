package com.trendist.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 관리자 키워드(13.6, 13.7): 등록 전 단어도 조회하고 바로 추가·금지한다 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminKeywordTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** 권한은 30초 캐시라 첫 요청 전에 관리자로 만든다 */
    private String admin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        jdbc.update("update users set role = 'ADMIN' where id = ?", Long.parseLong(sub(token)));
        return "Bearer " + token;
    }

    /** JWT의 sub(users.id) */
    private static String sub(String jwt) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.sub");
    }

    private static String json(String text) { return "{\"text\":\"" + text + "\",\"country\":\"KR\"}"; }

    @Test
    void lookupShowsUnregisteredAndInvalidWords() throws Exception {
        String a = admin();
        mvc.perform(get("/api/admin/keywords/lookup").param("text", "아직없는 단어").header("Authorization", a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.normalized").value("아직없는단어"))
                .andExpect(jsonPath("$.keyword").doesNotExist());
        mvc.perform(get("/api/admin/keywords/lookup").param("text", "https만들기").header("Authorization", a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.reason").isNotEmpty());
        mvc.perform(get("/api/admin/keywords/lookup").param("text", "   ").header("Authorization", a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void banUnregisteredWordRegistersItAsBannedAndBlocksItsPage() throws Exception {
        String a = admin();
        // 등록 전 단어를 바로 금지: 금지 상태로 새로 등록되고 공개 페이지는 404다
        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("미리 막을 단어")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("금지")).andExpect(jsonPath("$.normalized").value("미리막을단어"));
        mvc.perform(get("/kr/keywords/{k}", "미리막을단어")).andExpect(status().isNotFound());
        // 같은 단어를 다시 조회하면 금지 행이 보이고, 또 금지해도 그대로다
        mvc.perform(get("/api/admin/keywords/lookup").param("text", "미리막을단어").header("Authorization", a))
                .andExpect(jsonPath("$.keyword.state").value("금지"));
        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("미리막을단어")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("금지"));
        // 금지된 단어는 추가할 수 없고(409), 해제하면 💤비활성이 되어 추가할 수 있다
        mvc.perform(post("/api/admin/keywords").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("미리막을단어")))
                .andExpect(status().isConflict());
        long id = ((Number) JsonPath.read(mvc.perform(get("/api/admin/keywords/lookup").param("text", "미리막을단어").header("Authorization", a))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.keyword.id")).longValue();
        mvc.perform(post("/api/admin/keywords/{id}/unban", id).header("Authorization", a)).andExpect(jsonPath("$.state").value("비활성"));
        mvc.perform(post("/api/admin/keywords").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("미리막을단어")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("활성"));
    }

    @Test
    void listIsNewestFirstAndDoesNotPinBannedKeywords() throws Exception {
        String a = admin();
        // 먼저 만들어 금지한 단어, 나중에 만든 활성 단어: 기본 정렬은 등록 시간 최신순이라 나중 것이 위이고 금지는 맨 위로 올라가지 않는다
        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("정렬시험가")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/keywords").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("정렬시험나")))
                .andExpect(status().isOk());
        String body = mvc.perform(get("/api/admin/keywords").param("q", "정렬시험").header("Authorization", a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(body, "$.items[0].text")).isEqualTo("정렬시험나");
        assertThat((String) JsonPath.read(body, "$.items[1].text")).isEqualTo("정렬시험가");
        assertThat((String) JsonPath.read(body, "$.items[1].state")).isEqualTo("금지");
    }

    @Test
    void banTextRejectsInvalidWordsAndNeedsAdmin() throws Exception {
        String a = admin();
        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("https만들기")))
                .andExpect(status().isBadRequest());
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String user = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", "Bearer " + user).contentType(MediaType.APPLICATION_JSON).content(json("누구나")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/keywords/lookup").param("text", "누구나").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
    }
}
