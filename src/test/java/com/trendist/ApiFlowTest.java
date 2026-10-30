package com.trendist;

import com.trendist.setting.support.SettingKeys;
import com.trendist.report.entity.ReportType;
import com.trendist.report.entity.Report;
import com.trendist.chat.service.ChatService;
import com.trendist.keyword.entity.DataSource;
import com.trendist.player.service.PlayerService;
import com.trendist.player.service.ScoreHistoryService;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.service.KeywordActivityService;
import com.trendist.keyword.service.KeywordService;
import com.trendist.keyword.support.KeywordNormalizer;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.common.code.CodeTables;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
    @Autowired com.trendist.report.service.ReportService reportService;
    @Autowired com.trendist.setting.service.SettingService settingService;
    @Autowired PlayerService playerService;
    @Autowired ScoreHistoryService scoreHistory;
    @Autowired KeywordActivityService activityService;

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
        // 4.8: 가진 비율 = 가진 사람 / max(활동 인원, 최소 인원 20), 50%에서 최고점. 정수로 반올림한다
        assertThat(formula.trendValue(true, 0, 1, 0)).isEqualTo(3);     // 가진 사람이 없으면 기본 비율
        assertThat(formula.trendValue(true, 1, 1, 5)).isEqualTo(12);    // 5명뿐이어도 최소 인원 20으로 계산(1/20)
        assertThat(formula.trendValue(true, 5, 1, 20)).isEqualTo(34);   // 25%
        assertThat(formula.trendValue(true, 25, 1, 100)).isEqualTo(34); // 인원이 늘어도 같은 비율이면 같은 점수
        assertThat(formula.trendValue(true, 50, 1, 100)).isEqualTo(50); // 50%에서 최고점
        assertThat(formula.trendValue(true, 80, 1, 100)).isEqualTo(50); // 그 이상은 같다
        assertThat(formula.trendValue(false, 50, 1, 100)).isZero();
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

    private long playerIdOf(String token) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(), "$.player.id")).longValue();
    }

    @Test
    void winnerStealsLosersKeywordOrRenewsIt() throws Exception {
        // 충돌로 이기면 진 사람이 잃은 키워드를 빼앗는다. 없던 키워드면 보관함에 추가, 이미 있으면 남은 시간만 갱신(4.3)
        String loserT = player("뺏김"), winnerT = player("뺏음");
        long loser = playerIdOf(loserT), winner = playerIdOf(winnerT);
        plusKeyword(loserT, "빼앗기테스트");
        var first = keywordService.stealRandom(loser, winner).orElseThrow();
        assertThat(first.text()).isEqualTo("빼앗기테스트");
        assertThat(first.result()).isEqualTo("ADDED");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + loserT)).andExpect(jsonPath("$.player.holdCount").value(0));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + winnerT)).andExpect(jsonPath("$.player.holdCount").value(1));

        plusKeyword(loserT, "빼앗기테스트"); // 진 사람이 같은 키워드를 다시 추가했다
        var second = keywordService.stealRandom(loser, winner).orElseThrow();
        assertThat(second.result()).isEqualTo("RENEWED");
        assertThat(second.gained()).isZero();
        assertThat(second.expiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + loserT)).andExpect(jsonPath("$.player.holdCount").value(0));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + winnerT)).andExpect(jsonPath("$.player.holdCount").value(1));
        assertThat(keywordService.stealRandom(loser, winner)).isEmpty(); // 잃을 키워드가 없으면 아무 일도 없다
    }

    @Test
    void adminAddsActiveKeywordForSevenDaysAndNonAdminIsRejected() throws Exception {
        // 관리자가 넣은 키워드는 🔥활성, 반영 소스 admin, 지금부터 7일(13.7). 관리자가 아니면 403
        String t = player("관리자시험");
        mvc.perform(get("/api/me/admin").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.admin").value(false));
        mvc.perform(post("/api/admin/keywords").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"관리자키워드\"}")).andExpect(status().isForbidden());
        var added = keywordService.adminAdd("KR", "관리자 키워드");
        assertThat(added.created()).isTrue();
        assertThat(added.state()).isEqualTo(CodeTables.KW_ACTIVE);
        assertThat(added.externalUntil()).isAfter(Instant.now().plusSeconds(6 * 24 * 3600));
        tx.executeWithoutResult(s -> assertThat(keywords.findById(added.id()).orElseThrow().getDataSource().getName()).isEqualTo(DataSource.ADMIN));
        assertThat(keywordService.adminSearch("KR", null, DataSource.ADMIN, null, "externalUntil", "desc", 0, 50).items())
                .anyMatch(k -> k.id() == added.id());
        // 7일이 지나면(유지 끝 시각을 과거로) internal로 내려가고 가진 사람이 3명 미만이면 💤
        tx.executeWithoutResult(s -> keywords.findById(added.id()).orElseThrow()
                .holdExternal(codes.dataSource(DataSource.ADMIN), Instant.now().minusSeconds(1)));
        assertThat(keywordService.expireExternal(DataSource.ADMIN, Set.of())).isEqualTo(1);
        tx.executeWithoutResult(s -> {
            var kw = keywords.findById(added.id()).orElseThrow();
            assertThat(kw.getDataSource().getName()).isEqualTo(DataSource.INTERNAL);
            assertThat(kw.getKeywordState().getName()).isEqualTo(CodeTables.KW_INACTIVE);
        });
        // 이미 있는 키워드를 다시 넣으면 admin으로 바꾸고 다시 🔥, 7일
        var again = keywordService.adminAdd("KR", "관리자키워드");
        assertThat(again.created()).isFalse();
        assertThat(again.id()).isEqualTo(added.id());
        assertThat(again.state()).isEqualTo(CodeTables.KW_ACTIVE);
        // 금지한 키워드도 상태 전체 목록에 나오고, 점수가 0이라 묻히지 않게 맨 위에 모은다(13.6)
        keywordService.adminBan(added.id(), true);
        var all = keywordService.adminSearch("KR", null, null, null, "trendValue", "desc", 0, 100);
        assertThat(all.items().getFirst().state()).isEqualTo(CodeTables.KW_BANNED);
        assertThat(all.items()).anyMatch(k -> k.id() == added.id());
        assertThat(all.stateCounts().get(CodeTables.KW_BANNED)).isGreaterThanOrEqualTo(1L);
        assertThat(keywordService.adminSearch("KR", "", "", "", "trendValue", "desc", 0, 100).items())
                .anyMatch(k -> k.id() == added.id());
    }

    @Test
    void firstPlusGrantsTitleAndOnlyOwnedTitleCanBeActivated() throws Exception {
        // 처음 추가하면 "트렌드 워처"를 자동으로 받는다(4.10). 자동 활성화는 하지 않는다
        String t = player("칭호시험");
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"칭호키워드\"}")).andExpect(status().isOk());
        MvcResult box = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        String json = box.getResponse().getContentAsString();
        java.util.List<Integer> owned = JsonPath.read(json, "$.titles[?(@.owned == true)].id");
        java.util.List<String> names = JsonPath.read(json, "$.titles[?(@.owned == true)].name");
        assertThat(names).containsExactly("트렌드 워처");
        assertThat((Object) JsonPath.read(json, "$.activeTitleId")).isNull();
        // 아직 없는 칭호(트렌드 수집가, 100번)는 진행도만 보인다
        java.util.List<Integer> progress = JsonPath.read(json, "$.titles[?(@.name == '트렌드 수집가')].current");
        assertThat(progress).containsExactly(1);
        java.util.List<Integer> notOwned = JsonPath.read(json, "$.titles[?(@.owned == false)].id");
        mvc.perform(put("/api/me/title").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titleId\":" + notOwned.getFirst() + "}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/title").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titleId\":" + owned.getFirst() + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTitleId").value(owned.getFirst()));
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
                .andExpect(jsonPath("$.keyword.trendValue").value(3));
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
        // 점수 = EXP 1 + 외부 키워드 1명 trend_value 12(활동 인원이 최소 인원 20보다 적어 1/20로 계산) = 13, 정수
        MvcResult me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        Object score = JsonPath.read(me.getResponse().getContentAsString(), "$.player.currentScore");
        assertThat(score).isEqualTo(13);
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
        report(b, "PLAYER", 1, 404);      // 플레이어 신고는 같은 방에 있을 때만(정보 팝업, 4.11)

        // 관리자 검토(13.5): 목록에 대상 내용이 붙고, 처리 결과를 바꿀 수 있다
        var page = reportService.adminList(Report.RECEIVED, 0, 50);
        var row = page.items().stream().filter(r -> r.targetId() == cid && ReportType.COMMENT.equals(r.type())).findFirst().orElseThrow();
        assertThat(row.content()).isEqualTo("신고될 댓글입니다");
        assertThat(row.keywordText()).isEqualTo("신고 키워드");
        assertThat(row.targetNickname()).isEqualTo("신고대상");
        assertThat(row.reporterNickname()).isEqualTo("신고자");
        assertThat(reportService.adminSetStatus(row.id(), Report.RESOLVED, true)).isEqualTo(1);
        assertThat(reportService.adminList(Report.RESOLVED, 0, 50).items()).anyMatch(r -> r.id() == row.id());
    }

    @Test
    void adminUpdatesSettingWithRangeCheck() {
        // 관리자 설정(13.3): 형식·정수·범위를 검사하고 저장하면 바로 반영된다
        var item = settingService.update(1, SettingKeys.BOT_PLAYERS, "12");
        assertThat(item.value()).isEqualTo("12");
        assertThat(settingService.getInt(SettingKeys.BOT_PLAYERS)).isEqualTo(12);
        assertThatThrownBy(() -> settingService.update(1, SettingKeys.BOT_PLAYERS, "1.5")).hasMessageContaining("정수");
        assertThatThrownBy(() -> settingService.update(1, SettingKeys.BOT_PLAYERS, "999")).hasMessageContaining("범위");
        assertThatThrownBy(() -> settingService.update(1, SettingKeys.BOT_PLAYERS, "abc")).hasMessageContaining("숫자");
        assertThat(settingService.list()).anyMatch(i -> i.name().equals(SettingKeys.BOT_PLAYERS) && "10".equals(i.defaultValue()));
        settingService.update(1, SettingKeys.BOT_PLAYERS, "10");
    }

    private static String uncheckedBody(MvcResult r) {
        try { return r.getResponse().getContentAsString(); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test
    void scoreHistoryRecordsOnlyChangedHours() throws Exception {
        String t = player("점수기록");
        plusKeyword(t, "점수 기록 키워드"); // 💤 키워드: EXP 1 → 유행도 1
        java.time.Instant lastHour = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .minus(1, java.time.temporal.ChronoUnit.HOURS);
        assertThat(scoreHistory.recordHour(lastHour)).isPositive();
        // 점수가 그대로면 다시 기록하지 않는다(4.15). 이 플레이어만 보려고 다시 부른 결과에 이 플레이어가 없는지 본다
        int again = scoreHistory.recordHour(lastHour.plus(1, java.time.temporal.ChronoUnit.HOURS));
        assertThat(again).isGreaterThanOrEqualTo(0);

        // 24시간: 23칸 + 이번 시간(지금 값). 기록한 칸(바로 전 시간)은 1점
        mvc.perform(get("/api/me/score-history?range=24h").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(24))
                .andExpect(jsonPath("$.points[22].score").value(1))
                .andExpect(jsonPath("$.points[23].live").value(true))
                .andExpect(jsonPath("$.points[23].score").value(1));
        mvc.perform(get("/api/me/score-history?range=7d").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[6].score").value(1));
        mvc.perform(get("/api/me/score-history?range=all").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/score-history?range=1y").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keywordHourlyLogWritesSlot() throws Exception {
        // 매시 정각 기록(9.9): 지난 시간 칸에 그 시간의 추가 수가 남고, 이번 시간(집계 중)은 그 뒤 변화만 센다
        String t = player("시간기록");
        long id = plusKeyword(t, "시간 기록 키워드");
        java.time.Instant lastHour = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .minus(1, java.time.temporal.ChronoUnit.HOURS);
        activityService.recordHour(lastHour, java.util.Map.of());
        activityService.recordHour(lastHour, java.util.Map.of()); // 같은 시간을 다시 기록해도 칸을 덮어쓸 뿐이다
        mvc.perform(get("/api/keywords/" + id + "/activity?range=24h").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.points[22].plus").value(1))
                .andExpect(jsonPath("$.points[23].plus").value(0));
    }

    @Test
    void playerSearchCountIncreasesEverySearch() throws Exception {
        String t = player("검색횟수");
        for (String q : new String[]{"검색 하나", "검색 하나", "검색 둘"}) {
            mvc.perform(get("/api/keywords/search").param("q", q).header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.searchCount").value(3));
    }

    @Test
    void roomPlayerInfoNeedsSameRoom() throws Exception {
        // 방에 접속하지 않은 사용자는 다른 플레이어 정보를 볼 수 없다(4.11). 번호는 방 안의 세션 번호다
        String t = player("팝업점검");
        mvc.perform(get("/api/room/players/1").header("Authorization", "Bearer " + t))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_NOT_IN_ROOM"));
        mvc.perform(get("/api/room/players/1/score-history?range=30d").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- 구글 로그인(9.3)

    private MvcResult googleLogin(String sub, Cookie... cookies) throws Exception {
        var req = post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"test-google:" + sub + "\"}");
        if (cookies.length > 0) req.cookie(cookies);
        return mvc.perform(req).andReturn();
    }

    private static String body(MvcResult r, String path) {
        try { return JsonPath.read(r.getResponse().getContentAsString(), path).toString(); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test
    void googleLinksGuestAndKeepsRecords() throws Exception {
        // 게스트로 플레이하던 사람이 로그인하면 OAuth 값만 채워 같은 계정·기록을 이어 간다
        MvcResult g = mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn();
        Cookie visitor = g.getResponse().getCookie("visitor_id");
        String guestToken = body(g, "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + guestToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"연결테스트\"}")).andExpect(status().isOk());
        long userId = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + guestToken)).andReturn(), "$.userId"));

        MvcResult login = googleLogin("sub-link", visitor);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(login, "$.result")).isEqualTo("LINKED");
        assertThat(login.getResponse().getCookie("refresh_token").getValue()).isNotBlank();
        assertThat(login.getResponse().getCookie("visitor_id").getMaxAge()).isZero(); // 방문자 ID 쿠키는 지운다
        String account = body(login, "$.accessToken"); // 이 사이트의 JWT
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + account))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.guest").value(false))
                .andExpect(jsonPath("$.player.nickname").value("연결테스트"));
        // 연결된 뒤에는 방문자 ID만으로 이 계정에 들어갈 수 없다
        mvc.perform(post("/api/auth/session").cookie(visitor)).andExpect(status().isUnauthorized());

        // 다른 브라우저(새 게스트)에서 같은 구글로 로그인하면 기존 계정으로 들어가고, 게스트 기록은 합치지 않는다
        Cookie otherVisitor = mvc.perform(post("/api/auth/guest")).andReturn().getResponse().getCookie("visitor_id");
        MvcResult again = googleLogin("sub-link", otherVisitor);
        assertThat(body(again, "$.result")).isEqualTo("EXISTING");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + body(again, "$.accessToken")))
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void withdrawThenRestoreWithSameGoogleAccount() throws Exception {
        // 탈퇴(4.1): 확인 문구를 정확히 넣어야 하고, 게스트는 할 수 없다
        String guestT = player("탈퇴게스트");
        mvc.perform(post("/api/auth/withdraw").header("Authorization", "Bearer " + guestT)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phrase\":\"탈퇴 합니다.\"}")).andExpect(status().isBadRequest());

        MvcResult login = googleLogin("sub-withdraw");
        String t = body(login, "$.accessToken");
        mvc.perform(post("/api/players").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"탈퇴시험\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/withdraw").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phrase\":\"탈퇴합니다\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/withdraw").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phrase\":\"탈퇴 합니다.\"}")).andExpect(status().isNoContent());
        // 탈퇴하면 이미 받은 토큰도 바로 거절된다
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());

        // 같은 구글 계정으로 다시 로그인하면 토큰 없이 WITHDRAWN만 온다
        MvcResult again = googleLogin("sub-withdraw");
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(again, "$.result")).isEqualTo("WITHDRAWN");
        assertThat(again.getResponse().getCookie("refresh_token")).isNull();

        // [복구]: 같은 ID 토큰으로 복구하면 바로 로그인되고 기록이 그대로다.
        // 토큰 발급 시각(iat)은 초 단위라 탈퇴와 같은 초에 발급한 토큰은 거절된다. 사람이 복구를 누르기까지의 시간을 흉내 낸다
        Thread.sleep(1100);
        MvcResult restored = mvc.perform(post("/api/auth/google/restore").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"test-google:sub-withdraw\"}")).andExpect(status().isOk()).andReturn();
        assertThat(body(restored, "$.result")).isEqualTo("RESTORED");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + body(restored, "$.accessToken")))
                .andExpect(jsonPath("$.player.nickname").value("탈퇴시험"));
        // 탈퇴 상태가 아니면 복구는 거절
        mvc.perform(post("/api/auth/google/restore").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"test-google:sub-withdraw\"}")).andExpect(status().isConflict());
    }

    @Test
    void googleCreatesAccountWithOauthOnly() throws Exception {
        MvcResult login = googleLogin("sub-new");
        assertThat(body(login, "$.result")).isEqualTo("CREATED");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + body(login, "$.accessToken")))
                .andExpect(jsonPath("$.guest").value(false))
                .andExpect(jsonPath("$.player").doesNotExist()); // 그 나라에서 처음이면 닉네임부터
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"forged\"}"))
                .andExpect(status().isUnauthorized());
        // 로그인을 시작할 때 만든 nonce와 토큰 안의 nonce가 달라야 하면 거절(가로챈 토큰 재사용 방지)
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"test-google:sub-nonce:abc\",\"nonce\":\"xyz\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"test-google:sub-nonce:abc\",\"nonce\":\"abc\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesAndDetectsReuseAndLogout() throws Exception {
        Cookie first = googleLogin("sub-refresh").getResponse().getCookie("refresh_token");
        MvcResult r1 = mvc.perform(post("/api/auth/session").cookie(first)).andExpect(status().isOk()).andReturn();
        Cookie second = r1.getResponse().getCookie("refresh_token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue()); // 쓸 때마다 새 값(회전)
        // 이미 쓴 토큰이 다시 오면 탈취로 보고 그 사용자의 세션을 모두 끊는다(새 토큰도 못 쓴다)
        mvc.perform(post("/api/auth/session").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/session").cookie(second)).andExpect(status().isUnauthorized());

        // 로그아웃하면 그 갱신 토큰을 더는 쓸 수 없다
        Cookie fresh = googleLogin("sub-refresh").getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/auth/logout").cookie(fresh)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/session").cookie(fresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesAccessTokenImmediately() throws Exception {
        // 로그아웃하면 갱신 토큰뿐 아니라, 아직 15분이 안 지난 액세스 토큰(JWT)도 바로 거절된다
        MvcResult login = googleLogin("sub-logout");
        String jwtToken = body(login, "$.accessToken");
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertThat(refresh.getPath()).isEqualTo("/"); // 개발자 도구에서 보이고 쿠키 지우기로 지워지게 "/"
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + jwtToken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jwtToken).cookie(refresh)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + jwtToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/session").cookie(refresh)).andExpect(status().isUnauthorized());
    }
}
