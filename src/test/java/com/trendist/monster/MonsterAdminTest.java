package com.trendist.monster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** 관리자 몬스터(13.16, 4.35): 종류·설정값 읽고 고치기(범위 검사), 즉시 소환 → 모니터링 → 제거, 등장 기록 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MonsterAdminTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String admin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        jdbc.update("update users set role = 'ADMIN' where id = ?", Long.parseLong(JsonPath.read(payload, "$.sub")));
        return "Bearer " + token;
    }

    private String body(String a, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void notAdminCannotUseIt() throws Exception {
        mvc.perform(get("/api/admin/monsters/types")).andExpect(status().isUnauthorized());
    }

    @Test
    void typesAndSettingsAreSeededAndRangeChecked() throws Exception {
        String a = admin();
        mvc.perform(get("/api/admin/monsters/types").header("Authorization", a)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("elder_turtle")).andExpect(jsonPath("$[0].kind").value("BOSS"))
                .andExpect(jsonPath("$[0].settings.length()").value(org.hamcrest.Matchers.greaterThan(40)));
        mvc.perform(patch("/api/admin/monsters/types/elder_turtle/settings/duration_seconds").header("Authorization", a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":420}")).andExpect(status().isOk()).andExpect(jsonPath("$.value").value(420.0));
        mvc.perform(patch("/api/admin/monsters/types/elder_turtle/settings/duration_seconds").header("Authorization", a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":5}")).andExpect(status().isBadRequest()); // 허용 범위(60~3600) 밖
        mvc.perform(patch("/api/admin/monsters/types/elder_turtle/settings/nope").header("Authorization", a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/monsters/types/elder_turtle/settings/duration_seconds").header("Authorization", a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":600}")).andExpect(status().isOk()); // 되돌린다
    }

    @Test
    void spawnShowsInLiveThenRemoveRecordsHistory() throws Exception {
        String a = admin();
        mvc.perform(post("/api/admin/monsters/types/elder_turtle/spawn?country=KR").header("Authorization", a)).andExpect(status().isOk());
        // 이미 싸우는 중이면 다시 소환할 수 없다
        String live = "[]";
        for (int i = 0; i < 40 && "[]".equals(live); i++) { Thread.sleep(100); live = body(a, "/api/admin/monsters/live"); }
        assertThat((String) JsonPath.read(live, "$[0].code")).isEqualTo("elder_turtle");
        assertThat((String) JsonPath.read(live, "$[0].country")).isEqualTo("KR");
        assertThat(((Number) JsonPath.read(live, "$[0].maxHp")).longValue()).isGreaterThanOrEqualTo(2000L); // 사람이 없어도 하한 이상
        int id = JsonPath.read(live, "$[0].id");
        mvc.perform(post("/api/admin/monsters/types/elder_turtle/spawn?country=KR").header("Authorization", a)).andExpect(status().isConflict());
        // 제거(보상 없음)
        mvc.perform(post("/api/admin/monsters/" + id + "/end?country=KR&reward=false").header("Authorization", a)).andExpect(status().isOk());
        for (int i = 0; i < 40 && !"[]".equals(live); i++) { Thread.sleep(100); live = body(a, "/api/admin/monsters/live"); }
        assertThat(live).isEqualTo("[]");
        String hist = "";
        for (int i = 0; i < 40; i++) {
            hist = body(a, "/api/admin/monsters/spawns?limit=5");
            if (hist.contains("\"REMOVED\"")) break;
            Thread.sleep(100);
        }
        assertThat(hist).contains("\"REMOVED\"").contains("\"ADMIN\"");
        mvc.perform(post("/api/admin/monsters/999999/end?country=KR").header("Authorization", a)).andExpect(status().isNotFound());
    }
}
