package com.trendist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.service.KeywordService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** GLOBAL(영어) 방: 나라 고르기, 영문 닉네임, 나라별 분리, 영어 키워드 검색, 나라별 외부 목록 정리. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GlobalCountryTest {
    @Autowired MockMvc mvc;
    @Autowired KeywordService keywordService;
    @Autowired KeywordRepository keywords;
    @Autowired JdbcTemplate jdbc;

    private String guest() throws Exception {
        return JsonPath.read(mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String auth(String t) { return "Bearer " + t; }

    private void create(String t, String nick, String country) throws Exception {
        mvc.perform(post("/api/players").header("Authorization", auth(t)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nick + "\",\"countryCode\":\"" + country + "\"}")).andExpect(status().isOk());
    }

    @Test
    void globalIsOpenAndMeSelectsCountry() throws Exception {
        assertThat(jdbc.queryForObject("select enabled from countries where code='GLOBAL'", Boolean.class)).isTrue();
        String t = guest();
        // 아직 플레이어가 없으면 player=null(닉네임 만들기)
        mvc.perform(get("/api/me").param("country", "GLOBAL").header("Authorization", auth(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.player").doesNotExist());
        create(t, "Alex_01", "GLOBAL"); // 영문·숫자·_ 닉네임
        mvc.perform(get("/api/me").param("country", "GLOBAL").header("Authorization", auth(t)))
                .andExpect(jsonPath("$.player.nickname").value("Alex_01")).andExpect(jsonPath("$.player.countryCode").value("GLOBAL"));
        // 나라를 안 주면 마지막으로 고른 나라
        mvc.perform(get("/api/me").header("Authorization", auth(t))).andExpect(jsonPath("$.player.countryCode").value("GLOBAL"));
        // KR로 바꾸면 KR 플레이어는 아직 없다(나라별로 분리)
        mvc.perform(get("/api/me").param("country", "KR").header("Authorization", auth(t))).andExpect(jsonPath("$.player").doesNotExist());
        create(t, "한국닉", "KR");
        mvc.perform(get("/api/me").param("country", "GLOBAL").header("Authorization", auth(t)))
                .andExpect(jsonPath("$.player.nickname").value("Alex_01"));
        mvc.perform(get("/api/me").header("Authorization", auth(t))).andExpect(jsonPath("$.player.countryCode").value("GLOBAL"));
        // 없는/닫힌 나라는 400
        mvc.perform(get("/api/me").param("country", "ZZ").header("Authorization", auth(t))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").param("country", "JP").header("Authorization", auth(t))).andExpect(status().isBadRequest());
    }

    @Test
    void englishKeywordsAreCaseAndSpaceInsensitiveAndSeparatePerCountry() throws Exception {
        String t = guest();
        create(t, "Bob", "GLOBAL");
        mvc.perform(post("/api/keywords/plus").header("Authorization", auth(t)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Super Bowl\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/keywords/search").param("q", "  super   BOWL ").header("Authorization", auth(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FOUND")).andExpect(jsonPath("$.keyword.text").value("Super Bowl"));
        // 같은 철자라도 KR 방에는 따로 있다
        String k = guest();
        create(k, "케이알", "KR");
        mvc.perform(get("/api/keywords/search").param("q", "super bowl").header("Authorization", auth(k)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));
        mvc.perform(get("/api/rankings").param("country", "GLOBAL").header("Authorization", auth(t))).andExpect(status().isOk());
    }

    @Test
    void externalExpiryIsPerCountry() {
        keywordService.applyExternalTitles("GLOBAL", DataSource.GOOGLE, List.of("Zzglobal Topic"));
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("지지케이알주제"));
        jdbc.update("update keywords set external_until = ? where normalized in ('zzglobaltopic','지지케이알주제')",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));
        // KR 목록 기준으로 정리해도 GLOBAL 키워드는 그대로 둔다
        assertThat(keywordService.expireExternal("KR", DataSource.GOOGLE, Set.of())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from keywords k join data_sources d on d.id=k.data_source_id where k.normalized='zzglobaltopic' and d.name='google'", Long.class)).isEqualTo(1);
        assertThat(keywordService.expireExternal("GLOBAL", DataSource.GOOGLE, Set.of())).isEqualTo(1);
    }
}
