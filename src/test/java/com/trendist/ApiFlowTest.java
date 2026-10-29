package com.trendist;

import com.trendist.chat.service.ChatService;
import com.trendist.keyword.entity.DataSource;
import com.trendist.player.service.PlayerService;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.service.KeywordService;
import com.trendist.keyword.support.KeywordNormalizer;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.common.code.CodeTables;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiFlowTest {
    @Autowired MockMvc mvc;
    @Autowired TrendFormula formula;
    @Autowired KeywordService keywordService;
    @Autowired KeywordRepository keywords;
    @Autowired CodeTables codes;
    @Autowired TransactionTemplate tx;
    @Autowired ChatService chatService;
    @Autowired PlayerService playerService;

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
    void guideAndNoticesAreReadableWithoutLogin() throws Exception {
        // 초기 가이드(4.17)가 들어 있고, 메인 화면에서도 보도록 로그인 없이 읽힌다
        mvc.perform(get("/api/guide/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("getting-started"))
                .andExpect(jsonPath("$[?(@.code == 'faq')].displayType").value("FAQ"));
        mvc.perform(get("/api/guide/categories/faq/articles")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").isNotEmpty())
                .andExpect(jsonPath("$[0].body").isNotEmpty());
        mvc.perform(get("/api/guide/categories/nope/articles")).andExpect(status().isNotFound());
        mvc.perform(get("/api/notices")).andExpect(status().isOk());
    }

    @Test
    void nicknameCanBeChangedOncePerHour() throws Exception {
        String token = player("처음이름");
        // 처음 정한 뒤 첫 변경은 바로 된다
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.player.nicknameChangeableAt").doesNotExist());
        mvc.perform(patch("/api/me/nickname").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새이름"))
                .andExpect(jsonPath("$.nicknameChangeableAt").isNotEmpty());
        // 1시간 안에 다시 바꾸면 거절
        mvc.perform(patch("/api/me/nickname").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"또다른이름\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TOO_SOON"));
        // 규칙에 맞지 않는 닉네임은 간격과 상관없이 거절
        mvc.perform(patch("/api/me/nickname").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"a#b\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleTitlesStayActiveForSevenDaysThenFallBackToInternal() {
        // 쓸 수 없는 문자(:)는 공백으로 바꿔 넣고, 규칙에 맞지 않는 제목(링크)은 버린다
        int held = keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("콜 오브 듀티: 모던 워페어 4", "www.example.com"));
        assertThat(held).isEqualTo(1);
        String norm = KeywordNormalizer.normalize("콜 오브 듀티 모던 워페어 4");
        var k = tx.execute(s -> {
            var kw = keywords.findAll().stream().filter(x -> x.getNormalized().equals(norm)).findFirst().orElseThrow();
            assertThat(kw.getDataSource().getName()).isEqualTo(DataSource.GOOGLE);
            assertThat(kw.getKeywordState().getName()).isEqualTo(CodeTables.KW_ACTIVE);
            assertThat(kw.getExternalUntil()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600)); // 7일
            return kw.getId();
        });
        // 목록에 다시 보이면 지금부터 7일로 다시 센다
        expireNow(k);
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("콜 오브 듀티: 모던 워페어 4"));
        tx.executeWithoutResult(s -> assertThat(keywords.findById(k).orElseThrow().getExternalUntil()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600)));
        // 7일이 지났다고 치고(유지 끝 시각을 과거로), 아직 목록에 있으면 지금부터 7일 더
        expireNow(k);
        assertThat(keywordService.expireExternal(DataSource.GOOGLE, Set.of(norm))).isZero();
        tx.executeWithoutResult(s -> assertThat(keywords.findById(k).orElseThrow().getExternalUntil()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600)));
        // 목록에서 빠졌고 가진 사람이 3명 미만이면 internal로 내려가고 💤
        expireNow(k);
        assertThat(keywordService.expireExternal(DataSource.GOOGLE, Set.of())).isEqualTo(1);
        tx.executeWithoutResult(s -> {
            var kw = keywords.findById(k).orElseThrow();
            assertThat(kw.getDataSource().getName()).isEqualTo(DataSource.INTERNAL);
            assertThat(kw.getKeywordState().getName()).isEqualTo(CodeTables.KW_INACTIVE);
            assertThat(kw.getExternalUntil()).isNull();
        });
    }

    private void expireNow(long keywordId) {
        tx.executeWithoutResult(s -> keywords.findById(keywordId).orElseThrow()
                .holdExternal(codes.dataSource(DataSource.GOOGLE), Instant.now().minusSeconds(1)));
    }

    @Test
    void orbGivesActiveKeywordAtSetChanceOtherwiseMiss() throws Exception {
        String t = player("구슬러");
        long playerId = ((Number) JsonPath.read(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString(), "$.player.id")).longValue();
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("구슬테스트"));
        // 33%만 🔥 키워드, 나머지는 꽝. 이미 가진 키워드가 나오면 아무것도 바뀌지 않는다
        int hold = 0, miss = 0;
        for (int i = 0; i < 300; i++) {
            var r = keywordService.openOrb(playerId);
            switch (r.status()) {
                case "GAINED" -> { hold++; assertThat(r.text()).isNotBlank(); }
                case "MISS" -> miss++;
                default -> { assertThat(r.status()).isEqualTo("DUPLICATE"); assertThat(r.gained()).isZero(); }
            }
        }
        assertThat(hold).isPositive();
        assertThat(miss).isBetween(150, 250); // 약 67%
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.holdCount").value(hold));
    }

    @Test
    void protectedApiNeedsToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestCookieIsReusedBySession() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        Cookie c = r.getResponse().getCookie("visitor_id");
        assertThat(c).isNotNull();
        assertThat(c.isHttpOnly()).isTrue();
        mvc.perform(post("/api/auth/session").cookie(c)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/session").cookie(new Cookie("visitor_id", "forged"))).andExpect(status().isUnauthorized());
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
        // 점수 = EXP 1 + 외부 키워드 1명 trend_value 20.75 ≈ 21.75
        MvcResult me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        double score = ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.player.currentScore")).doubleValue();
        assertThat(score).isBetween(21.5, 22.0);
        mvc.perform(get("/api/me/keywords").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$[0].text").value("양쯔 깐루")); // 처음 입력한 표기를 유지한다(4.18)
        // 랭킹은 방에 접속한 사람만 집계한다(4.14). 이 사용자는 REST로만 플레이해서 방에 없다
        mvc.perform(get("/api/rankings").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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

    private long plusKeyword(String token, String text) throws Exception {
        MvcResult r = mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.keyword.id")).longValue();
    }

    private long commentScore(String token) throws Exception {
        MvcResult me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andReturn();
        return ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.player.commentScore")).longValue();
    }

    @Test
    void keywordDetailAndActivity() throws Exception {
        String t = player("상세");
        long id = plusKeyword(t, "상세 카드 키워드");
        mvc.perform(get("/api/keywords/" + id).header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("상세 카드 키워드"))
                .andExpect(jsonPath("$.plusedByMe").value(true));
        // 24시간: 지난 23시간 + 이번 시간(집계 중). 이번 시간의 + 1회가 보인다
        mvc.perform(get("/api/keywords/" + id + "/activity?range=24h").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(24))
                .andExpect(jsonPath("$.points[23].live").value(true))
                .andExpect(jsonPath("$.points[23].plus").value(1))
                .andExpect(jsonPath("$.totalPlus").value(1));
        mvc.perform(get("/api/keywords/" + id + "/activity?range=7d").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[6].plus").value(1));
        mvc.perform(get("/api/keywords/" + id + "/activity?range=1y").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/keywords/99999999").header("Authorization", "Bearer " + t)).andExpect(status().isNotFound());
    }

    @Test
    void commentsAndVotes() throws Exception {
        String a = player("작성자"), b = player("투표자");
        long kid = plusKeyword(a, "댓글 키워드");
        String auth = "Bearer " + a;
        // 길이·링크 검사
        mvc.perform(post("/api/keywords/" + kid + "/comments").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMENT_LENGTH")); // 빈 댓글만 막는다
        mvc.perform(post("/api/keywords/" + kid + "/comments").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"여기 보세요 www.example.com\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMENT_LINK"));
        MvcResult w = mvc.perform(post("/api/keywords/" + kid + "/comments").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"망고 올린 홍콩 디저트예요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true)).andExpect(jsonPath("$.editable").value(true)).andReturn();
        long cid = ((Number) JsonPath.read(w.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(commentScore(a)).isEqualTo(1);

        // 자기 댓글에는 투표할 수 없다. 다른 사람의 추천은 작성자 점수 +1, 같은 버튼은 취소, 반대 버튼은 변경
        mvc.perform(post("/api/comments/" + cid + "/vote").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"LIKE\"}"))
                .andExpect(status().isBadRequest());
        String vb = "Bearer " + b;
        mvc.perform(post("/api/comments/" + cid + "/vote").header("Authorization", vb)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"LIKE\"}"))
                .andExpect(jsonPath("$.myVote").value("LIKE")).andExpect(jsonPath("$.likeCount").value(1));
        assertThat(commentScore(a)).isEqualTo(2);
        mvc.perform(get("/api/keywords/" + kid + "/comments/top").header("Authorization", vb))
                .andExpect(jsonPath("$[0].id").value(cid)).andExpect(jsonPath("$[0].myVote").value("LIKE"));
        // 추천 상위로 고정된 댓글은 최신순 목록에서 빠진다
        mvc.perform(get("/api/keywords/" + kid + "/comments").header("Authorization", vb))
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/comments/" + cid + "/vote").header("Authorization", vb)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"DISLIKE\"}"))
                .andExpect(jsonPath("$.likeCount").value(0)).andExpect(jsonPath("$.dislikeCount").value(1));
        assertThat(commentScore(a)).isEqualTo(0); // 1 + 1 − 2
        mvc.perform(post("/api/comments/" + cid + "/vote").header("Authorization", vb)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"DISLIKE\"}"))
                .andExpect(jsonPath("$.myVote").doesNotExist()).andExpect(jsonPath("$.dislikeCount").value(0));

        // 고치기는 내 댓글만, 지우면 안내 문구로 보이고 투표할 수 없다
        mvc.perform(patch("/api/comments/" + cid).header("Authorization", vb)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"남의 댓글 고치기\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/comments/" + cid).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"망고·자몽 올린 홍콩 디저트\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("망고·자몽 올린 홍콩 디저트"));
        mvc.perform(delete("/api/comments/" + cid).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/keywords/" + kid + "/comments").header("Authorization", vb))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").value("삭제된 댓글이에요"));
        mvc.perform(post("/api/comments/" + cid + "/vote").header("Authorization", vb)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"LIKE\"}"))
                .andExpect(status().isConflict());
    }

    private void report(String token, String target, long id, int expected) throws Exception {
        mvc.perform(post("/api/reports").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"" + target + "\",\"targetId\":" + id + ",\"reason\":\"욕설·비방\"}"))
                .andExpect(status().is(expected));
    }

    @Test
    void reportCommentAndChatOnce() throws Exception {
        String a = player("신고대상"), b = player("신고자");
        long kid = plusKeyword(a, "신고 키워드");
        MvcResult w = mvc.perform(post("/api/keywords/" + kid + "/comments").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"신고될 댓글입니다\"}"))
                .andExpect(status().isCreated()).andReturn();
        long cid = ((Number) JsonPath.read(w.getResponse().getContentAsString(), "$.id")).longValue();
        report(a, "COMMENT", cid, 400);   // 내 글은 신고할 수 없다
        report(b, "COMMENT", cid, 201);
        report(b, "COMMENT", cid, 409);   // 같은 대상은 한 번만
        report(b, "COMMENT", 99999999, 404);

        // 채팅은 게임 WebSocket으로 저장되므로 서비스로 한 줄 남겨 두고 신고한다
        MvcResult me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + a)).andReturn();
        long playerId = ((Number) JsonPath.read(me.getResponse().getContentAsString(), "$.player.id")).longValue();
        long countryId = tx.execute(s -> playerService.findCurrent(((Number) JsonPath.read(uncheckedBody(me), "$.userId")).longValue())
                .orElseThrow().getCountry().getId());
        long chatId = chatService.save(countryId, playerId, "신고될 채팅").id();
        report(b, "CHAT", chatId, 201);
        report(b, "CHAT", chatId, 409);
        report(b, "PLAYER", 1, 400);      // 지금은 댓글·채팅만 받는다
    }

    private static String uncheckedBody(MvcResult r) {
        try { return r.getResponse().getContentAsString(); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
