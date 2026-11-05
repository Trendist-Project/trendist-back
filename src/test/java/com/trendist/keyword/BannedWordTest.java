package com.trendist.keyword;

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

/** 금지어 포함 검사(4.6): 금지한 단어가 들어 있는 키워드는 검색·추가·공개 페이지에서 막힌다 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BannedWordTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String token(boolean admin, String nickname) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        if (admin) jdbc.update("update users set role = 'ADMIN' where id = ?", Long.parseLong(JsonPath.read(payload, "$.sub")));
        else mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}")).andExpect(status().isOk());
        return "Bearer " + token;
    }

    private static String json(String text) { return "{\"text\":\"" + text + "\",\"country\":\"KR\"}"; }

    @Test
    void 금지어가_들어_있으면_검색과_추가와_페이지가_막힌다() throws Exception {
        String admin = token(true, null), user = token(false, "금칙어시험");
        // 금지 전에는 "금칙테스트2"를 추가할 수 있다. 이미 만들어진 것도 금지어가 생기면 막힌다
        mvc.perform(post("/api/keywords/plus").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"금칙테스트2\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/kr/keywords/{k}", "금칙테스트2")).andExpect(status().isOk());

        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json("금칙테스트")))
                .andExpect(status().isOk());

        for (String q : new String[] {"금칙테스트", "금칙테스트2", "새 금칙테스트 단어", "금칙 테스트", "금칙테스트3"})
            mvc.perform(get("/api/keywords/search").param("q", q).header("Authorization", user))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BANNED"));
        mvc.perform(post("/api/keywords/plus").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"금칙테스트4\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("KEYWORD_BANNED"));
        mvc.perform(get("/kr/keywords/{k}", "금칙테스트2")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/keywords").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json("금칙테스트5")))
                .andExpect(status().isConflict());
        // 포함하지 않는 말은 그대로 된다(금칙 / 테스트 따로는 아님)
        mvc.perform(get("/api/keywords/search").param("q", "금칙").header("Authorization", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));

        // 해제하면 다시 열린다
        long id = ((Number) JsonPath.read(mvc.perform(get("/api/admin/keywords/lookup").param("text", "금칙테스트").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.keyword.id")).longValue();
        mvc.perform(post("/api/admin/keywords/{id}/unban", id).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/keywords/search").param("q", "금칙테스트3").header("Authorization", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void 한_글자_금지어는_포함_검사에서_뺀다() throws Exception {
        String admin = token(true, null), user = token(false, "한글자시험");
        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json("쀍")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/keywords/search").param("q", "쀍").header("Authorization", user)).andExpect(jsonPath("$.status").value("BANNED"));
        mvc.perform(get("/api/keywords/search").param("q", "쀍쀍가").header("Authorization", user)).andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void 금지하면_모든_플레이어의_보관함에서_바로_지워진다() throws Exception {
        String admin = token(true, null), a = token(false, "보관함가"), b = token(false, "보관함나");
        for (String u : new String[] {a, b})
            for (String kw : new String[] {"지울말씀", "지울말씀둘", "남길말씀"})
                mvc.perform(post("/api/keywords/plus").header("Authorization", u).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + kw + "\"}"))
                        .andExpect(status().isOk());
        mvc.perform(get("/api/me/keywords").header("Authorization", a)).andExpect(jsonPath("$.length()").value(3));

        mvc.perform(post("/api/admin/keywords/ban-text").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json("지울말씀")))
                .andExpect(status().isOk());

        // 금지한 키워드와 금지어가 들어 있는 키워드는 모든 플레이어에게서 사라지고, 나머지는 남는다
        for (String u : new String[] {a, b}) {
            mvc.perform(get("/api/me/keywords").header("Authorization", u))
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].text").value("남길말씀"));
            mvc.perform(get("/api/me").header("Authorization", u)).andExpect(jsonPath("$.player.holdCount").value(1));
        }
        Integer rows = jdbc.queryForObject("select count(*) from player_keywords pk join keywords k on k.id = pk.keyword_id where k.normalized like '%지울말씀%'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(rows).isZero();
    }
}
