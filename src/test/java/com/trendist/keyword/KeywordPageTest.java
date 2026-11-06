package com.trendist.keyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.keyword.support.KeywordViewCounter;
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

/** 공개 키워드 페이지와 사이트맵(4.28): 서버(나라)가 앞인 주소, 금지 제외, 조회수, 사이트맵 자동 반영 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KeywordPageTest {
    private static final String CHROME = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KeywordViewCounter counter;

    private String plus(String nickname, String keyword) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + keyword + "\"}")).andExpect(status().isOk());
        return token;
    }

    private String html(String path, Object... vars) throws Exception {
        return mvc.perform(get(path, vars)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void everyKeywordGetsAPageUnderItsServer() throws Exception {
        plus("페이지왕", "소소한 페이지키워드");
        // 서버(kr)가 앞, 키워드는 공백이 빠진 정규화 키로 연다
        String page = html("/kr/keywords/{k}", "소소한페이지키워드");
        assertThat(page).contains("<h1>소소한 페이지키워드</h1>").contains("index,follow").contains("조회수")
                .contains("<link rel=\"canonical\" href=\"http://localhost:8080/kr/keywords/%EC%86%8C%EC%86%8C%ED%95%9C%ED%8E%98%EC%9D%B4%EC%A7%80%ED%82%A4%EC%9B%8C%EB%93%9C\">");
        // 게임 안 키워드 팝업과 같은 구성: 제목 줄, 상태·점수 영역, 통계 칸, 기간 탭(24시간·7일·30일·180일) 그래프
        assertThat(page).contains("class=\"modal keyword-modal\"").contains("class=\"kw-hero\"").contains("class=\"kw-stats\"")
                .contains("rng-24h").contains("rng-7d").contains("rng-30d").contains("rng-180d").contains("게임에서 추가");
        assertThat(page).contains("googletagmanager.com/gtm.js").contains("ns.html?id=GTM-WRFP5PLN"); // 방문 분석
        // 다른 서버의 같은 주소는 다른 키워드(없음)
        mvc.perform(get("/global/keywords/{k}", "소소한페이지키워드")).andExpect(status().isNotFound());
        String missing = mvc.perform(get("/kr/keywords/{k}", "없는키워드zzz")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(missing).contains("키워드를 찾을 수 없어요").contains("noindex").contains("없는키워드zzz").contains("Trendist 플레이하기");
        String missingEn = mvc.perform(get("/global/keywords/{k}", "no such <i>zz")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(missingEn).contains("We couldn't find that keyword").doesNotContain("<i>zz").contains("&lt;i&gt;zz"); // 주소의 값은 이스케이프한다
        // 규칙에 맞지 않는 값(개인정보 패턴)이나 모르는 서버는 열리지 않는다
        mvc.perform(get("/kr/keywords/{k}", "https만들기")).andExpect(status().isNotFound());
        mvc.perform(get("/jp/keywords/{k}", "x")).andExpect(status().isNotFound());
    }

    @Test
    void bannedKeywordHasNoPage() throws Exception {
        plus("금지당함", "금지될키워드");
        mvc.perform(get("/kr/keywords/{k}", "금지될키워드")).andExpect(status().isOk()); // 페이지 캐시를 먼저 데운다
        jdbc.update("update keywords set keyword_state_id = (select id from keyword_states where name = '금지') where normalized = '금지될키워드'");
        mvc.perform(get("/kr/keywords/{k}", "금지될키워드")).andExpect(status().isNotFound());
    }

    @Test
    void countsPageViewsOncePerVisitorPerDayWithoutCrawlers() throws Exception {
        String token = plus("조회왕", "조회수키워드");
        mvc.perform(get("/kr/keywords/{k}", "조회수키워드").header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("1.1.1.1"); return r; }))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
        mvc.perform(get("/kr/keywords/{k}", "조회수키워드").header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("1.1.1.1"); return r; }));
        mvc.perform(get("/kr/keywords/{k}", "조회수키워드").header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("2.2.2.2"); return r; }));
        mvc.perform(get("/kr/keywords/{k}", "조회수키워드").header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)").with(r -> { r.setRemoteAddr("3.3.3.3"); return r; }));
        counter.flush();
        Long views = jdbc.queryForObject("select view_count from keywords where normalized = '조회수키워드'", Long.class);
        assertThat(views).isEqualTo(2L); // 같은 방문자 중복·검색 크롤러는 센 값에 없다
        // 게임 안 키워드 팝업(4.12)에도 같은 조회수가 내려가고, User-Agent가 없는 요청(크롤러·도구)은 세지 않는다
        Long id = jdbc.queryForObject("select id from keywords where normalized = '조회수키워드'", Long.class);
        mvc.perform(get("/api/keywords/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.viewCount").value(2));
        // 팝업을 연 것도 조회수에 센다: 새 방문자가 열면 +1(카드에도 바로 반영), 같은 방문자가 다시 열면 그대로
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/keywords/{id}", id).header("Authorization", "Bearer " + token).header("User-Agent", CHROME)
                            .with(r -> { r.setRemoteAddr("9.9.9.9"); return r; }))
                    .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.viewCount").value(3));
        }
        // 같은 방문자가 공개 페이지를 열어도 하루 1번이라 더 늘지 않는다
        mvc.perform(get("/kr/keywords/{k}", "조회수키워드").header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("9.9.9.9"); return r; }));
        counter.flush();
        assertThat(jdbc.queryForObject("select view_count from keywords where normalized = '조회수키워드'", Long.class)).isEqualTo(3L);
    }

    @Test
    void sitemapXmlListsMainAndEveryKeywordAndSkipsBanned() throws Exception {
        plus("맵왕", "사이트맵키워드");
        plus("맵왕2", "사이트맵금지");
        jdbc.update("update keywords set keyword_state_id = (select id from keyword_states where name = '금지') where normalized = '사이트맵금지'");

        String xml = mvc.perform(get("/sitemap.xml")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("xml")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 메인 주소와 모든 키워드가 한 파일에 있고, 금지 키워드는 없다
        assertThat(xml).contains("<loc>http://localhost:8080/</loc>")
                .contains("<loc>http://localhost:8080/kr/keywords/" + java.net.URLEncoder.encode("사이트맵키워드", StandardCharsets.UTF_8) + "</loc>")
                .contains("<lastmod>")
                .doesNotContain(java.net.URLEncoder.encode("사이트맵금지", StandardCharsets.UTF_8));
        // 예전 별도 파일 주소는 없다
        mvc.perform(get("/sitemap-keywords.xml")).andExpect(status().isNotFound());
    }
}
