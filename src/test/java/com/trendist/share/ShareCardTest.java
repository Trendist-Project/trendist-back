package com.trendist.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.trendist.share.dto.ShareDtos;
import com.trendist.share.support.ShareCardRenderer;
import com.trendist.share.support.SharePage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 자랑하기 카드 공유 주소(4.24): 공유한 순간의 값이 유지되고, 서버가 그 값으로 이미지를 그린다 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShareCardTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShareCardRenderer renderer;
    @Autowired com.trendist.keyword.service.SitemapService sitemapService;

    private String player(String nickname) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}")).andExpect(status().isOk());
        return token;
    }

    private String create(String token) throws Exception {
        MvcResult r = mvc.perform(post("/api/share-cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        String body = r.getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");
        assertThat((String) JsonPath.read(body, "$.url")).isEqualTo("http://localhost:8080/share/" + code);
        assertThat((String) JsonPath.read(body, "$.imageUrl")).isEqualTo("http://localhost:8080/share/" + code + "/card.png");
        return code;
    }

    @Test
    void guestCreatesShareCardAndPageShowsOgImage() throws Exception {
        String token = player("공유왕");
        String code = create(token);
        assertThat(code).matches("[0-9A-Za-z]{10}");
        // 값이 그대로면 같은 주소를 다시 쓴다(행이 늘지 않음)
        assertThat(create(token)).isEqualTo(code);

        String html = mvc.perform(get("/share/" + code)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("<meta property=\"og:image\" content=\"http://localhost:8080/share/" + code + "/card.png\">")
                .contains("공유왕의 트렌드 성적표").contains("/share/" + code + "/play");
        // 검색 수집(index)과 방문 분석(GTM)
        assertThat(html).contains("content=\"index,follow\"").doesNotContain("noindex").contains("googletagmanager.com/gtm.js").contains("GTM-WRFP5PLN").contains("ns.html?id=GTM-WRFP5PLN");
        // 사이트맵에도 들어간다(5분 캐시를 비우고 확인)
        sitemapService.onChanged(null);
        assertThat(mvc.perform(get("/sitemap.xml")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("<loc>http://localhost:8080/share/" + code + "</loc>");

        byte[] png = mvc.perform(get("/share/" + code + "/card.png")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(1080);
        assertThat(img.getHeight()).isEqualTo(1350);
    }

    @Test
    void cardKeepsValuesFromShareTime() throws Exception {
        String token = player("그때모습");
        String first = create(token);
        // 공유한 뒤 점수가 바뀌면 새 주소가 생기고, 먼저 만든 주소의 카드는 그때 값 그대로다
        jdbc.update("update players set current_score = 777 where nickname = '그때모습'");
        String second = create(token);
        assertThat(second).isNotEqualTo(first);
        String oldPage = mvc.perform(get("/share/" + first)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String newPage = mvc.perform(get("/share/" + second)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(oldPage).doesNotContain("유행도 777");
        assertThat(newPage).contains("유행도 777");
    }

    @Test
    void unknownCodeIsNotFoundAndCreateNeedsLogin() throws Exception {
        mvc.perform(get("/share/AAAAAAAAAA")).andExpect(status().isNotFound());
        mvc.perform(get("/share/AAAAAAAAAA/card.png")).andExpect(status().isNotFound());
        mvc.perform(get("/share/../etc")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/share-cards")).andExpect(status().isUnauthorized());
    }

    @Autowired com.trendist.share.support.ShareViewCounter counter;

    private static final String CHROME = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1";

    @Test
    void countsViewsAndPlaysOncePerVisitorPerDayWithoutCrawlers() throws Exception {
        String token = player("카운트");
        String code = create(token);
        // 사람 두 명(다른 IP) + 같은 사람 다시 열기 + 카카오 미리보기 수집기 + 카카오톡 앱 안 브라우저
        mvc.perform(get("/share/" + code).header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("1.1.1.1"); return r; }))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
        mvc.perform(get("/share/" + code).header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("1.1.1.1"); return r; }));
        mvc.perform(get("/share/" + code).header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("2.2.2.2"); return r; }));
        mvc.perform(get("/share/" + code).header("User-Agent", "facebookexternalhit/1.1; kakaotalk-scrap/1.0").with(r -> { r.setRemoteAddr("3.3.3.3"); return r; }));
        mvc.perform(get("/share/" + code).header("User-Agent", CHROME + " KAKAOTALK 10.8.5").with(r -> { r.setRemoteAddr("4.4.4.4"); return r; }));
        // 클릭: 메인으로 보내고 따로 센다
        mvc.perform(get("/share/" + code + "/play").header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("1.1.1.1"); return r; }))
                .andExpect(status().isFound()).andExpect(header().string("Location", "http://localhost:8080/?ref=share"));
        mvc.perform(get("/share/" + code + "/play").header("User-Agent", CHROME).with(r -> { r.setRemoteAddr("1.1.1.1"); return r; }));
        mvc.perform(get("/share/ZZZZZZZZZZ/play").header("User-Agent", CHROME)).andExpect(status().isFound());
        // 이미지 요청은 세지 않는다
        mvc.perform(get("/share/" + code + "/card.png").header("User-Agent", CHROME));

        // 저장 전에도 내 카드 합계에 보인다
        String body = mvc.perform(post("/api/share-cards").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.totalViews")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(body, "$.totalPlays")).isEqualTo(1);
        counter.flush();
        assertThat(jdbc.queryForObject("select view_count from share_cards where code = ?", Long.class, code)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select play_count from share_cards where code = ?", Long.class, code)).isEqualTo(1);
        body = mvc.perform(post("/api/share-cards").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.totalViews")).isEqualTo(3); // 저장 뒤에도 두 번 더하지 않는다
    }

    @Test
    void crawlerFilter() {
        assertThat(com.trendist.share.support.ShareViewCounter.isCrawler("Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)")).isTrue();
        assertThat(com.trendist.share.support.ShareViewCounter.isCrawler("Twitterbot/1.0")).isTrue();
        assertThat(com.trendist.share.support.ShareViewCounter.isCrawler(null)).isTrue();
        assertThat(com.trendist.share.support.ShareViewCounter.isCrawler(CHROME)).isFalse();
        assertThat(com.trendist.share.support.ShareViewCounter.isCrawler("Mozilla/5.0 (Linux; Android 12; CUBOT X50) Chrome/120 Mobile")).isFalse();
    }

    @Test
    void adminStatsNeedsAdmin() throws Exception {
        String user = player("일반");
        mvc.perform(get("/api/admin/share-cards").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        // 권한은 30초 캐시라 첫 요청 전에 관리자로 만든다
        MvcResult r = mvc.perform(post("/api/auth/guest")).andReturn();
        String token = JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
        long uid = Long.parseLong(com.trendist.share.ShareCardTest.sub(token));
        jdbc.update("update users set role = 'ADMIN' where id = ?", uid);
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"통계\"}")).andExpect(status().isOk());
        create(token);
        String body = mvc.perform(get("/api/admin/share-cards?sort=createdAt").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat((Integer) JsonPath.read(body, "$.cards")).isGreaterThanOrEqualTo(1);
        // 같은 초에 만든 카드끼리는 정렬 순서가 정해져 있지 않으므로, 첫 항목이 아니라 목록 안에 있는지로 확인한다
        java.util.List<String> nicknames = JsonPath.read(body, "$.items[*].nickname");
        assertThat(nicknames).contains("통계");
    }

    /** JWT의 sub(users.id) */
    static String sub(String jwt) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.sub");
    }

    @Test
    void concurrentCreateReturnsSameCard() throws Exception {
        // 처음 카드를 열 때 개발 모드(StrictMode)가 만들기를 두 번 동시에 보내 늦은 쪽이 500이던 문제
        String token = player("동시에");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<MvcResult>> fs = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++)
                fs.add(pool.submit(() -> mvc.perform(post("/api/share-cards").header("Authorization", "Bearer " + token)).andReturn()));
            java.util.Set<String> codes = new java.util.HashSet<>();
            for (var f : fs) {
                MvcResult r = f.get();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                codes.add(JsonPath.read(r.getResponse().getContentAsString(), "$.code"));
            }
            assertThat(codes).hasSize(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void createIsRateLimited() throws Exception {
        String token = player("빈도");
        for (int i = 0; i < 10; i++) mvc.perform(post("/api/share-cards").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/share-cards").header("Authorization", "Bearer " + token)).andExpect(status().isTooManyRequests());
    }

    @Test
    void pageEscapesUserText() {
        assertThat(SharePage.escape("<a href=\"x\">'&")).isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;");
    }

    @Test
    void rendererDrawsEveryTrailAndLevel() throws Exception {
        List<ShareDtos.Record> recs = List.of(new ShareDtos.Record("보유 키워드", "7개"), new ShareDtos.Record("추가수", "1,234회"),
                new ShareDtos.Record("처치", "12회"), new ShareDtos.Record("활동한 날", "9일"));
        String[] trails = {null, "afterimage", "sparkle", "flame", "bubble", "rainbow", "footprint", "pawprint", "birdprint", "bearprint", "rabbitprint", "petal", "unknown"};
        for (int i = 0; i < trails.length; i++) {
            var d = new ShareDtos.CardData("아주아주긴닉네임이라서", i + 1, i % 2 == 0 ? "트렌드 사냥꾼" : null, 1234L * i,
                    i % 3 == 0 ? null : i, List.of(100L, 120L, 90L, 300L, 280L, 400L, 420L),
                    i % 4 == 0 ? List.of() : List.of("양쯔깐루", "두바이 쫀득 쿠키 아주 긴 이름", "탕후루"), recs,
                    i % 2 == 0 ? "#ff6b6b" : "#3fb68b", i % 2 == 0 ? "#ffffff" : null, i % 2 == 0 ? 4 : null, trails[i]);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(renderer.png(d, "trendist.kr")));
            assertThat(img.getWidth()).isEqualTo(1080);
        }
    }
}
