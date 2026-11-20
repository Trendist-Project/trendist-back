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
    @Autowired com.trendist.monster.repository.MonsterSpawnRepository spawns;
    @Autowired com.trendist.monster.repository.MonsterTypeRepository monsterTypes;
    @Autowired com.trendist.country.repository.CountryRepository countries;

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
            hist = body(a, "/api/admin/monsters/spawns?code=elder_turtle&page=0&size=10");
            if (hist.contains("\"REMOVED\"")) break;
            Thread.sleep(100);
        }
        assertThat(hist).contains("\"REMOVED\"").contains("\"ADMIN\"");
        // 페이지 단위(10개씩, 종류별): 총 개수와 쪽 수가 나오고, 없는 종류·범위 밖 쪽은 빈 목록
        String pg = body(a, "/api/admin/monsters/spawns?code=elder_turtle&page=0&size=10");
        assertThat((Integer) JsonPath.read(pg, "$.size")).isEqualTo(10);
        assertThat(((Number) JsonPath.read(pg, "$.total")).longValue()).isGreaterThanOrEqualTo(1L);
        assertThat((Integer) JsonPath.read(pg, "$.totalPages")).isGreaterThanOrEqualTo(1);
        assertThat((String) JsonPath.read(pg, "$.items[0].code")).isEqualTo("elder_turtle");
        assertThat((Integer) JsonPath.read(body(a, "/api/admin/monsters/spawns?code=nope&page=0"), "$.items.length()")).isEqualTo(0);
        assertThat((Integer) JsonPath.read(body(a, "/api/admin/monsters/spawns?code=elder_turtle&page=9999"), "$.items.length()")).isEqualTo(0);
        mvc.perform(post("/api/admin/monsters/999999/end?country=KR").header("Authorization", a)).andExpect(status().isNotFound());
    }

    @Test
    void timelineListsSpawnedAndScheduledBossesAndHidesTheOldSkippedRows() throws Exception {
        String a = admin();
        long type = monsterTypes.findByCode("elder_turtle").orElseThrow().getId(), kr = countries.findByCode("KR").orElseThrow().getId();
        spawns.save(new com.trendist.monster.entity.MonsterSpawn(type, kr, "SCHEDULE", "SKIPPED", java.time.Instant.now())); // 예전에 남긴 건너뜀 행
        mvc.perform(patch("/api/admin/monsters/types/elder_turtle").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        try {
            mvc.perform(post("/api/admin/monsters/types/elder_turtle/spawn?country=KR").header("Authorization", a)).andExpect(status().isOk());
            String tl = body(a, "/api/admin/monsters/timeline?past=2&ahead=3");
            java.util.List<String> countriesShown = JsonPath.read(tl, "$.countries");
            assertThat(countriesShown).contains("KR");
            java.util.List<Object> rows = JsonPath.read(tl, "$.rows");
            assertThat(rows).hasSize(2 + 1 + 3);                                              // 지난 2시간 + 지금 + 앞으로 3시간
            java.util.List<String> hours = JsonPath.read(tl, "$.rows[*].hour");
            assertThat(hours).isSortedAccordingTo(java.util.Comparator.reverseOrder());        // 미래가 위
            java.util.List<String> statuses = JsonPath.read(tl, "$.rows[*].cells.KR[*].status");
            assertThat(statuses).contains("ACTIVE", "SCHEDULED").doesNotContain("SKIPPED");   // 방금 소환한 것과 예정, 건너뜀은 숨김
            java.util.List<String> sources = JsonPath.read(tl, "$.rows[*].cells.KR[?(@.status=='ACTIVE')].source");
            assertThat(sources).contains("ADMIN");
            String sp = body(a, "/api/admin/monsters/spawns?size=100");
            java.util.List<String> all = JsonPath.read(sp, "$.items[*].status");
            assertThat(all).doesNotContain("SKIPPED");                                         // 등장 기록 목록에서도 숨긴다
        } finally {
            String live = body(a, "/api/admin/monsters/live");
            java.util.List<Integer> ids = JsonPath.read(live, "$[?(@.code=='elder_turtle')].id");
            for (int id : ids) mvc.perform(post("/api/admin/monsters/" + id + "/end?country=KR").header("Authorization", a));
            for (int i = 0; i < 60 && !JsonPath.<java.util.List<Object>>read(body(a, "/api/admin/monsters/live"), "$[?(@.code=='elder_turtle')]").isEmpty(); i++) Thread.sleep(100); // 제거는 방 틱에서 처리되니 끝날 때까지 기다린다(다음 시험이 소환할 수 있게)
            mvc.perform(patch("/api/admin/monsters/types/elder_turtle").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"));
        }
    }
}
