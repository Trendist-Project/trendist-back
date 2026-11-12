package com.trendist.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.integration.service.IntegrationSeeder;
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

/** 연동 바로가기(13.15): 기본 4개, 관리자만 주소를 고칠 수 있고 http(s) 주소만 받는다 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IntegrationLinkTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired IntegrationSeeder seeder;

    private String token(boolean admin) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        if (admin) {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
            jdbc.update("update users set role = 'ADMIN' where id = ?", Long.parseLong(JsonPath.read(payload, "$.sub")));
        }
        return "Bearer " + token;
    }

    private String body(String url) { return "{\"url\":" + (url == null ? "null" : "\"" + url + "\"") + "}"; }

    @Test
    void 기본_세_개를_주고_주소를_고칠_수_있다() throws Exception {
        seeder.run(null);
        String admin = token(true);
        mvc.perform(get("/api/admin/integrations").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].code").value("NAVER_SEARCH_ADVISOR"))
                .andExpect(jsonPath("$[2].code").value("GOOGLE_ANALYTICS"));
        mvc.perform(patch("/api/admin/integrations/GOOGLE_ANALYTICS").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("  https://analytics.google.com/new  ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://analytics.google.com/new"));
        seeder.run(null); // 다시 채워도 고친 주소는 그대로
        mvc.perform(get("/api/admin/integrations").header("Authorization", admin))
                .andExpect(jsonPath("$[2].url").value("https://analytics.google.com/new"));
    }

    @Test
    void 이상한_주소와_없는_항목은_거절한다() throws Exception {
        seeder.run(null);
        String admin = token(true);
        for (String bad : new String[] {"javascript:alert(1)", "ftp://a.com/x", "not a url", "", null, "https://" + "a".repeat(500) + ".com"})
            mvc.perform(patch("/api/admin/integrations/GOOGLE_ANALYTICS").header("Authorization", admin)
                    .contentType(MediaType.APPLICATION_JSON).content(body(bad))).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/integrations/NOPE").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body("https://a.com"))).andExpect(status().isNotFound());
    }

    @Test
    void 관리자가_아니면_못_본다() throws Exception {
        mvc.perform(get("/api/admin/integrations").header("Authorization", token(false))).andExpect(status().isForbidden());
    }
}
