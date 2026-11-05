package com.trendist.emergency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 긴급 안내(4.29): 관리자가 올리면 로그인 없이 읽을 수 있고, 한 번에 하나만 보이며, 끝나거나 취소되면 사라진다 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmergencyTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() { jdbc.update("delete from emergency_notices"); }

    /** 권한은 30초 캐시라 첫 요청 전에 관리자로 만든다 */
    private String admin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        jdbc.update("update users set role = 'ADMIN' where id = ?", Long.parseLong(JsonPath.read(payload, "$.sub")));
        return "Bearer " + token;
    }

    private static String json(String message, String messageEn, Integer minutes) {
        return "{\"message\":" + (message == null ? "null" : "\"" + message + "\"") + ",\"messageEn\":" + (messageEn == null ? "null" : "\"" + messageEn + "\"")
                + ",\"minutes\":" + minutes + "}";
    }

    private String create(String a, String message, String en, int minutes) throws Exception {
        return mvc.perform(post("/api/admin/emergency").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json(message, en, minutes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void nothingToShowByDefaultAndPublicCanReadWithoutLogin() throws Exception {
        mvc.perform(get("/api/emergency")).andExpect(status().isOk()).andExpect(jsonPath("$.notice").doesNotExist());
        String a = admin();
        create(a, "  서버 점검이  곧   시작돼요\\n잠시 후 접속해 주세요 ", "Maintenance soon", 10);
        // 로그인 없이 읽는다. 공백·줄바꿈은 한 칸으로, 보일 시간은 서버 시계 기준 남은 초로 준다
        String body = mvc.perform(get("/api/emergency")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(body, "$.notice.message")).isEqualTo("서버 점검이 곧 시작돼요 잠시 후 접속해 주세요");
        assertThat((String) JsonPath.read(body, "$.notice.messageEn")).isEqualTo("Maintenance soon");
        assertThat(((Number) JsonPath.read(body, "$.notice.remainingSeconds")).longValue()).isBetween(590L, 600L);
    }

    @Test
    void onlyOneIsShownAndNewOneReplacesTheOld() throws Exception {
        String a = admin();
        create(a, "첫 번째", null, 30);
        create(a, "두 번째", null, 5);
        mvc.perform(get("/api/emergency")).andExpect(jsonPath("$.notice.message").value("두 번째"));
        // 목록: 최신이 먼저, 이전 것은 취소 상태
        mvc.perform(get("/api/admin/emergency").header("Authorization", a)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("두 번째")).andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].message").value("첫 번째")).andExpect(jsonPath("$[1].status").value("CANCELED"));
    }

    @Test
    void cancelAndExpiryHideIt() throws Exception {
        String a = admin();
        long id = ((Number) JsonPath.read(create(a, "곧 끝낼 안내", null, 5), "$.id")).longValue();
        mvc.perform(post("/api/admin/emergency/{id}/cancel", id).header("Authorization", a)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELED"));
        mvc.perform(get("/api/emergency")).andExpect(jsonPath("$.notice").doesNotExist());
        mvc.perform(post("/api/admin/emergency/{id}/cancel", id).header("Authorization", a)).andExpect(status().isOk()); // 다시 끝내도 그대로
        mvc.perform(post("/api/admin/emergency/{id}/cancel", 999999).header("Authorization", a)).andExpect(status().isNotFound());
        // 시간이 지나면 저절로 사라진다
        create(a, "시간이 지나면 사라질 안내", null, 5);
        jdbc.update("update emergency_notices set ends_at = timestampadd(minute, -1, current_timestamp)");
        mvc.perform(get("/api/emergency")).andExpect(jsonPath("$.notice").doesNotExist());
        mvc.perform(get("/api/admin/emergency").header("Authorization", a)).andExpect(jsonPath("$[0].status").value("ENDED"));
    }

    @Test
    void validatesInputAndNeedsAdmin() throws Exception {
        String a = admin();
        for (String bad : new String[]{json("", null, 5), json("   ", null, 5), json(null, null, 5), json("가".repeat(201), null, 5),
                json("안내", null, 0), json("안내", null, 721), json("안내", null, null), json("안내", "e".repeat(201), 5)}) {
            mvc.perform(post("/api/admin/emergency").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/emergency").header("Authorization", a).contentType(MediaType.APPLICATION_JSON).content(json("가".repeat(200), null, 720))).andExpect(status().isOk());
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String user = "Bearer " + JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/admin/emergency").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(json("안내", null, 5))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/emergency").header("Authorization", user)).andExpect(status().isForbidden());
    }
}
