package com.trendist.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 설정 변경 이력과 되돌리기(13.17): 설정·몬스터·스킬을 바꾸면 이력이 남고, 값이 그대로면 남지 않으며, 되돌리면 이전 값으로 가고 새 이력이 남는다 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ConfigChangeTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String admin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        jdbc.update("update users set role = 'ADMIN' where id = ?", Long.parseLong(JsonPath.read(payload, "$.sub")));
        return "Bearer " + token;
    }

    private String json(String a, org.springframework.test.web.servlet.RequestBuilder b) throws Exception {
        return mvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> changes(String a, String q) throws Exception {
        return JsonPath.read(json(a, get("/api/admin/config-changes?limit=100&q=" + q).header("Authorization", a)), "$");
    }

    @SuppressWarnings("unchecked")
    private static <T> T first(String json, String filterPath) { return ((List<T>) JsonPath.read(json, filterPath)).get(0); }

    private void patchJson(String a, String path, String body) throws Exception {
        mvc.perform(patch(path).header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void settingChangeIsRecordedAndCanBeRolledBack() throws Exception {
        String a = admin();
        String before = first(json(a, get("/api/admin/settings").header("Authorization", a)), "$[?(@.name=='dash_seconds')].value");
        String next = "3".equals(before) ? "4" : "3";
        int rows = changes(a, "dash_seconds").size();
        patchJson(a, "/api/admin/settings/dash_seconds", "{\"value\":\"" + next + "\"}");
        patchJson(a, "/api/admin/settings/dash_seconds", "{\"value\":\"" + next + "\"}"); // 같은 값: 이력이 남지 않는다
        List<Map<String, Object>> list = changes(a, "dash_seconds");
        assertThat(list).hasSize(rows + 1);
        Map<String, Object> row = list.get(0);
        assertThat(row.get("scope")).isEqualTo("SETTING");
        assertThat(row.get("oldValue")).isEqualTo(before);
        assertThat(row.get("newValue")).isEqualTo(next);
        assertThat(row.get("currentValue")).isEqualTo(next);
        assertThat((String) row.get("adminName")).isNotBlank();
        assertThat((String) row.get("label")).contains("대시");
        // 메모
        long id = ((Number) row.get("id")).longValue();
        patchJson(a, "/api/admin/config-changes/" + id + "/memo", "{\"memo\":\"테스트 조정\"}");
        assertThat(changes(a, "dash_seconds").get(0).get("memo")).isEqualTo("테스트 조정");
        // 되돌리기: 이전 값으로 가고, 되돌림 이력이 남는다
        mvc.perform(post("/api/admin/config-changes/" + id + "/rollback").header("Authorization", a)).andExpect(status().isOk());
        List<Map<String, Object>> after = changes(a, "dash_seconds");
        assertThat(after).hasSize(rows + 2);
        assertThat(after.get(0).get("newValue")).isEqualTo(before);
        assertThat(((Number) after.get(0).get("revertedFromId")).longValue()).isEqualTo(id);
        assertThat(ConfigChangeTest.<String>first(json(a, get("/api/admin/settings").header("Authorization", a)), "$[?(@.name=='dash_seconds')].value")).isEqualTo(before);
        mvc.perform(post("/api/admin/config-changes/999999/rollback").header("Authorization", a)).andExpect(status().isNotFound());
    }

    @Test
    void monsterAndSkillChangesAreRecordedAndRolledBack() throws Exception {
        String a = admin();
        // 몬스터 설정값
        patchJson(a, "/api/admin/monsters/types/elder_turtle/settings/hp_margin", "{\"value\":0.8}");
        List<Map<String, Object>> m = changes(a, "elder_turtle");
        Map<String, Object> mrow = m.stream().filter(r -> "elder_turtle/hp_margin".equals(r.get("target"))).findFirst().orElseThrow();
        assertThat(mrow.get("scope")).isEqualTo("MONSTER");
        assertThat(mrow.get("newValue")).isEqualTo("0.8");
        mvc.perform(post("/api/admin/config-changes/" + ((Number) mrow.get("id")).longValue() + "/rollback").header("Authorization", a)).andExpect(status().isOk());
        assertThat(changes(a, "elder_turtle/hp_margin").get(0).get("newValue")).isEqualTo(mrow.get("oldValue"));
        // 정기 등장 켜기·끄기(새로 만들면 꺼져 있으므로 켠 것이 변경이고, 되돌리면 꺼진다)
        patchJson(a, "/api/admin/monsters/types/elder_turtle", "{\"enabled\":true}");
        Map<String, Object> erow = changes(a, "elder_turtle").stream().filter(r -> "enabled".equals(r.get("field"))).findFirst().orElseThrow();
        assertThat(erow.get("newValue")).isEqualTo("true");
        mvc.perform(post("/api/admin/config-changes/" + ((Number) erow.get("id")).longValue() + "/rollback").header("Authorization", a)).andExpect(status().isOk());
        assertThat(JsonPath.<Boolean>read(json(a, get("/api/admin/monsters/types").header("Authorization", a)), "$[0].enabled")).isFalse();
        // 스킬 쿨타임
        patchJson(a, "/api/admin/skills/shield", "{\"cooldownSeconds\":12}");
        Map<String, Object> srow = changes(a, "shield").stream().filter(r -> "cooldown".equals(r.get("field"))).findFirst().orElseThrow();
        assertThat(srow.get("scope")).isEqualTo("SKILL");
        assertThat(srow.get("newValue")).isEqualTo("12");
        mvc.perform(post("/api/admin/config-changes/" + ((Number) srow.get("id")).longValue() + "/rollback").header("Authorization", a)).andExpect(status().isOk());
        assertThat(ConfigChangeTest.<Number>first(json(a, get("/api/admin/skills").header("Authorization", a)), "$[?(@.code=='shield')].cooldownSeconds").doubleValue()).isEqualTo(30.0);
    }

    @Test
    void notAdminCannotSeeHistory() throws Exception {
        mvc.perform(get("/api/admin/config-changes")).andExpect(status().isUnauthorized());
    }
}
