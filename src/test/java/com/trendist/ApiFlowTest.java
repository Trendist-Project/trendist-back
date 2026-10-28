package com.trendist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.keyword.TrendFormula;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {
    @Autowired MockMvc mvc;
    @Autowired TrendFormula formula;

    private String guest() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
    }

    private String player(String nickname) throws Exception {
        String token = guest();
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
        return token;
    }

    @Test
    void trendFormulaMatchesDocumentTable() {
        // 4.8 표: 외부(w=2) 0명 20.0, 100명 53.2, 1000명 100 / 내부(w=1) 3명 11.1
        assertThat(formula.trendValue(true, 0, 2)).isEqualByComparingTo("20.00");
        assertThat(formula.trendValue(true, 100, 2).doubleValue()).isBetween(53.1, 53.3);
        assertThat(formula.trendValue(true, 1000, 2)).isEqualByComparingTo("100.00");
        assertThat(formula.trendValue(true, 3, 1).doubleValue()).isBetween(11.0, 11.2);
        assertThat(formula.trendValue(false, 50, 2)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void protectedApiNeedsToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestCookieIsReusedBySession() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        Cookie c = r.getResponse().getCookie("tv_id");
        assertThat(c).isNotNull();
        assertThat(c.isHttpOnly()).isTrue();
        mvc.perform(post("/api/auth/session").cookie(c)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/session").cookie(new Cookie("tv_id", "forged"))).andExpect(status().isUnauthorized());
    }

    @Test
    void externalKeywordPlusRaisesScore() throws Exception {
        String t = player("민수");
        mvc.perform(get("/api/keywords/search").param("q", "양쯔 깐루").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FOUND"))
                .andExpect(jsonPath("$.keyword.state").value("활성"))
                .andExpect(jsonPath("$.keyword.trendValue").value(20.0));
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"양쯔깐루\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.exp").value(1))
                .andExpect(jsonPath("$.keyword.holderCount").value(1))
                .andExpect(jsonPath("$.keyword.plusedByMe").value(true));
        // 같은 키워드 두 번은 안 된다
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"양쯔 깐루\"}"))
                .andExpect(status().isConflict());
        // 점수 = EXP 1 + 외부 키워드 1명 trend_value 20.75 × 0.5 ≈ 11.38
        MvcResult me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        double score = ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.player.currentScore")).doubleValue();
        assertThat(score).isBetween(11.0, 11.6);
        mvc.perform(get("/api/me/keywords").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$[0].text").value("양쯔 깐루")); // 처음 입력한 표기를 유지한다(4.18)
        mvc.perform(get("/api/rankings").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1));
    }

    @Test
    void newWordActivatesAtThreeHolders() throws Exception {
        String a = player("가나"), b = player("다라"), c = player("마바");
        for (String t : new String[]{a, b}) {
            mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + t)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"테스트밈\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.keyword.state").value("비활성"));
        }
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + c)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"테스트 밈\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyword.state").value("활성"))
                .andExpect(jsonPath("$.keyword.holderCount").value(3));
    }

    @Test
    void nicknameRules() throws Exception {
        String t = guest();
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"민수たろう\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NICKNAME_MIXED"));
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"a#b\"}"))
                .andExpect(status().isBadRequest());
    }
}
