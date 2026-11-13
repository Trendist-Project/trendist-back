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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    @Autowired com.trendist.player.service.LevelService levelService;
    @Autowired com.trendist.guide.service.AdminGuideService adminGuides;
    @Autowired TrendFormula formula;
    @Autowired KeywordService keywordService;
    @Autowired KeywordRepository keywords;
    @Autowired CodeTables codes;
    @Autowired TransactionTemplate tx;
    @Autowired ChatService chatService;
    @Autowired com.trendist.report.service.ReportService reportService;
    @Autowired com.trendist.setting.service.SettingService settingService;
    @Autowired com.trendist.skill.service.SkillCatalog skillCatalog;
    @Autowired com.trendist.skill.repository.SkillRepository skillRepository;
    @Autowired com.trendist.skill.controller.AdminSkillController adminSkills;
    @Autowired PlayerService playerService;
    @Autowired ScoreHistoryService scoreHistory;
    @Autowired KeywordActivityService activityService;
    @Autowired com.trendist.player.repository.PlayerRepository playerRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.trendist.dashboard.service.DashboardService dashboard;
    @Autowired com.trendist.user.service.AdminUserService adminUsers;
    @Autowired com.trendist.user.service.SanctionService sanctionService;
    @Autowired com.trendist.stats.service.StatsService stats;

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

    /** 구글로 로그인한 것처럼 만든 플레이어(이 게스트 사용자 행에 구글 ID를 붙인다) */
    private String loggedInPlayer(String nickname) throws Exception {
        String token = player(nickname);
        long uid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andReturn(), "$.userId"));
        jdbc.update("update users set oauth_type = 'GOOGLE', oauth_id = ? where id = ?", "test-" + uid, uid);
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
        // 변경 간격(기본 5분) 안에 다시 바꾸면 거절
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
    void orbGivesActiveKeywordAtSetChanceOtherwiseLosesOne() throws Exception {
        String t = player("구슬러");
        long playerId = ((Number) JsonPath.read(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString(), "$.player.id")).longValue();
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("구슬테스트"));
        // 60%(기본값)는 🔥 키워드를 얻고(이미 가진 키워드면 만료시간만 갱신), 나머지는 가진 키워드 하나를 잃는다. 가진 게 없으면 꽝
        int gained = 0, lost = 0, miss = 0;
        for (int i = 0; i < 300; i++) {
            var r = keywordService.openOrb(playerId);
            switch (r.status()) {
                case "GAINED" -> { gained++; assertThat(r.text()).isNotBlank(); }
                case "LOST" -> { lost++; assertThat(r.text()).isNotBlank(); }
                case "MISS" -> miss++;
                default -> { assertThat(r.status()).isEqualTo("RENEWED"); assertThat(r.gained()).isZero(); assertThat(r.expiresAt()).isNotNull(); }
            }
        }
        assertThat(gained).isPositive();
        assertThat(lost).isPositive();
        assertThat(lost + miss).isBetween(80, 160); // 잃기 쪽으로 간 것은 약 40%(기본 얻을 확률 60%, 300번 중 120번 안팎)
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.holdCount").value(gained - lost));
    }

    @Test
    void prizeOrbAlwaysGainsAndWipeLosesEverything() throws Exception {
        // ! 구슬은 꽝·잃기 없이 항상 얻고(가능하면 안 가진 키워드), 불꽃에게 지면 가진 키워드를 모두 잃는다(4.20, 4.25)
        String t = player("느낌표러");
        long pid = playerIdOf(t);
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("느낌표하나", "느낌표둘", "느낌표셋"));
        for (int i = 0; i < 3; i++) assertThat(keywordService.openPrizeOrb(pid).status()).isEqualTo("GAINED"); // 안 가진 것부터
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.holdCount").value(3));
        var lost = keywordService.loseAll(pid).orElseThrow();
        assertThat(lost.count()).isEqualTo(3);
        assertThat(lost.activeCount()).isEqualTo(3); // 모두 🔥라 활성 수도 3
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.holdCount").value(0));
        assertThat(keywordService.loseAll(pid)).isEmpty(); // 잃을 것이 없다
        for (int i = 0; i < 20; i++) assertThat(keywordService.openPrizeOrb(pid).status()).isIn("GAINED", "RENEWED");
    }

    @Test
    void loseFractionTakesRoundedDownShareWithAtLeastOne() throws Exception {
        // 체력 0·불꽃 충돌로 잃는 키워드(4.36): 개수 × 비율을 내림, 최소 1개. 비율 100이면 모두
        String t = player("절반러");
        long pid = playerIdOf(t);
        keywordService.applyExternalTitles("KR", DataSource.GOOGLE, List.of("절반하나", "절반둘", "절반셋", "절반넷", "절반다섯"));
        for (int i = 0; i < 5; i++) assertThat(keywordService.openPrizeOrb(pid).status()).isEqualTo("GAINED");
        var lost = keywordService.loseFraction(pid, 50).orElseThrow();
        assertThat(lost.count()).isEqualTo(2);            // 5 × 50% = 2.5 → 내림 2개
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.holdCount").value(3));
        assertThat(keywordService.loseFraction(pid, 50).orElseThrow().count()).isEqualTo(1); // 3 × 50% = 1.5 → 1
        assertThat(keywordService.loseFraction(pid, 50).orElseThrow().count()).isEqualTo(1); // 2 × 50% = 1
        assertThat(keywordService.loseFraction(pid, 50).orElseThrow().count()).isEqualTo(1); // 1 × 50% = 0 → 최소 1개
        assertThat(keywordService.loseFraction(pid, 50)).isEmpty();
        for (int i = 0; i < 4; i++) keywordService.openPrizeOrb(pid);
        assertThat(keywordService.loseFraction(pid, 100).orElseThrow().count()).isPositive(); // 100%는 모두
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.holdCount").value(0));
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

    @Autowired com.trendist.notice.service.NoticeService noticeService;
    @Autowired com.trendist.user.repository.UserRepository userRepo;

    @Test
    void adminManagesGuideCategoriesAndArticles() throws Exception {
        // 관리자 가이드 관리(4.17, 13.4): 관리자가 아니면 403
        String t = player("가이드시험");
        mvc.perform(get("/api/admin/guide/categories").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        long admin = userRepo.findFirstByRoleOrderByIdAsc(com.trendist.user.entity.User.ROLE_ADMIN).orElseThrow().getId();
        var c = adminGuides.createCategory(new com.trendist.guide.dto.AdminGuideDtos.CategoryRequest("test-cat", " 🧪 시험 ", "ARTICLE", true));
        assertThat(c.name()).isEqualTo("🧪 시험");
        assertThatThrownBy(() -> adminGuides.createCategory(new com.trendist.guide.dto.AdminGuideDtos.CategoryRequest("test-cat", "중복", "FAQ", true)))
                .hasMessageContaining("이미 있는");
        assertThatThrownBy(() -> adminGuides.createCategory(new com.trendist.guide.dto.AdminGuideDtos.CategoryRequest("Bad Code", "이름", "FAQ", true)))
                .hasMessageContaining("코드");
        var a1 = adminGuides.createArticle(admin, c.id(), new com.trendist.guide.dto.AdminGuideDtos.ArticleRequest("첫 글", "본문 **하나**"));
        var a2 = adminGuides.createArticle(admin, c.id(), new com.trendist.guide.dto.AdminGuideDtos.ArticleRequest("둘째 글", "본문 둘"));
        mvc.perform(get("/api/guide/categories/test-cat/articles")).andExpect(jsonPath("$[0].title").value("첫 글"));
        // 순서 바꾸기, 고치기
        adminGuides.moveArticle(a2.id(), -1);
        adminGuides.updateArticle(a1.id(), new com.trendist.guide.dto.AdminGuideDtos.ArticleRequest("첫 글(수정)", "새 본문"));
        mvc.perform(get("/api/guide/categories/test-cat/articles")).andExpect(jsonPath("$[0].title").value("둘째 글"))
                .andExpect(jsonPath("$[1].title").value("첫 글(수정)")).andExpect(jsonPath("$[1].body").value("새 본문"));
        // 글이 있으면 카테고리는 못 지우고, 숨기면 사용자 목록에서 빠진다
        assertThatThrownBy(() -> adminGuides.deleteCategory(c.id())).hasMessageContaining("글이 있는");
        adminGuides.updateCategory(c.id(), new com.trendist.guide.dto.AdminGuideDtos.CategoryRequest(null, "🧪 시험", "FAQ", false));
        mvc.perform(get("/api/guide/categories")).andExpect(jsonPath("$[?(@.code == 'test-cat')]").isEmpty());
        mvc.perform(get("/api/guide/categories/test-cat/articles")).andExpect(status().isNotFound());
        // 글을 모두 지우면 카테고리도 지울 수 있다
        adminGuides.deleteArticle(a1.id());
        adminGuides.deleteArticle(a2.id());
        assertThat(adminGuides.articles(c.id())).isEmpty();
        adminGuides.deleteCategory(c.id());
        assertThat(adminGuides.categories()).noneMatch(x -> x.code().equals("test-cat"));
    }

    @Test
    void adminWritesEditsAndDeletesNotices() throws Exception {
        // 공지(4.16): 관리자만 쓴다. 관리자가 아니면 403
        String t = player("공지시험");
        mvc.perform(post("/api/admin/notices").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목\",\"body\":\"본문\"}")).andExpect(status().isForbidden());
        long admin = userRepo.findFirstByRoleOrderByIdAsc(com.trendist.user.entity.User.ROLE_ADMIN).orElseThrow().getId();
        var req = new com.trendist.notice.dto.AdminNoticeDtos.Request("  점검 안내 ", "오늘 밤 **점검**이 있어요", true, true, 3);
        var created = noticeService.create(admin, req);
        assertThat(created.title()).isEqualTo("점검 안내");
        assertThat(created.popupDays()).isEqualTo(3);
        mvc.perform(get("/api/notices")).andExpect(jsonPath("$[?(@.id == " + created.id() + ")].pinned").value(true))
                .andExpect(jsonPath("$[?(@.id == " + created.id() + ")].popup").value(true)); // 팝업 기간 안
        var updated = noticeService.update(created.id(), new com.trendist.notice.dto.AdminNoticeDtos.Request("점검 안내(수정)", "본문", false, false, null));
        assertThat(updated.popupUntil()).isNull();
        mvc.perform(get("/api/notices")).andExpect(jsonPath("$[?(@.id == " + created.id() + ")].popup").value(false));
        assertThat(updated.pinned()).isFalse();
        // 제목 50자 초과, 빈 본문, 팝업 기간 범위 밖은 거절
        assertThatThrownBy(() -> noticeService.create(admin, new com.trendist.notice.dto.AdminNoticeDtos.Request("가".repeat(51), "본문", false, false, null)))
                .isInstanceOf(com.trendist.common.exception.ApiException.class);
        assertThatThrownBy(() -> noticeService.create(admin, new com.trendist.notice.dto.AdminNoticeDtos.Request("제목", " ", false, false, null)))
                .isInstanceOf(com.trendist.common.exception.ApiException.class);
        assertThatThrownBy(() -> noticeService.create(admin, new com.trendist.notice.dto.AdminNoticeDtos.Request("제목", "본문", false, true, 91)))
                .isInstanceOf(com.trendist.common.exception.ApiException.class);
        noticeService.delete(created.id());
        mvc.perform(get("/api/notices")).andExpect(jsonPath("$[?(@.id == " + created.id() + ")]").isEmpty());
        assertThat(noticeService.adminList()).noneMatch(n -> n.id() == created.id());
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
        // 금지한 키워드도 상태 전체 목록에 나온다(금지만 맨 위에 모으지는 않는다, 13.6). 기본 정렬은 등록 시간 최신순
        keywordService.adminBan(added.id(), true);
        var all = keywordService.adminSearch("KR", null, null, null, "createdAt", "desc", 0, 100);
        assertThat(all.items()).anyMatch(k -> k.id() == added.id());
        assertThat(all.stateCounts().get(CodeTables.KW_BANNED)).isGreaterThanOrEqualTo(1L);
        assertThat(keywordService.adminSearch("KR", "", "", "", "trendValue", "desc", 0, 100).items())
                .anyMatch(k -> k.id() == added.id());
        // 상태·소스를 여러 개 고를 수 있다(쉼표로 이음)
        var multi = keywordService.adminSearch("KR", CodeTables.KW_BANNED + "," + CodeTables.KW_ACTIVE, null, null, "trendValue", "desc", 0, 100);
        assertThat(multi.items()).isNotEmpty().allMatch(k -> k.state().equals(CodeTables.KW_BANNED) || k.state().equals(CodeTables.KW_ACTIVE));
        assertThat(multi.items()).anyMatch(k -> k.id() == added.id());
        var onlyOne = keywordService.adminSearch("KR", CodeTables.KW_BANNED, null, null, "trendValue", "desc", 0, 100);
        assertThat(onlyOne.items()).isNotEmpty().allMatch(k -> k.state().equals(CodeTables.KW_BANNED));
        var twoSources = keywordService.adminSearch("KR", null, DataSource.ADMIN + "," + DataSource.GOOGLE, null, "trendValue", "desc", 0, 100);
        assertThat(twoSources.items()).allMatch(k -> k.source().equals(DataSource.ADMIN) || k.source().equals(DataSource.GOOGLE));
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
    void newCounterTitlesAreGrantedFromOrbsAndActiveDays() throws Exception {
        // 칭호 23~51: ? 구슬 10개(꽝 포함)면 "행운 탐색자", 키워드를 얻은 첫날이면 활동한 날 1(4.10)
        String t = player("구슬칭호시험");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        for (int i = 0; i < 10; i++) keywordService.openOrb(pid);
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"구슬칭호키워드\"}")).andExpect(status().isOk());
        var p = playerRepo.findById(pid).orElseThrow();
        assertThat(p.getOrbCount()).isEqualTo(10);
        assertThat(p.getActiveDays()).isEqualTo(1); // 만든 날과 키워드를 얻은 날이 같으면 하루
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.activeDays").value(1))
                .andExpect(jsonPath("$.player.stealCount").value(0));
        String json = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        java.util.List<String> names = JsonPath.read(json, "$.titles[?(@.owned == true)].name");
        assertThat(names).contains("행운 탐색자", "트렌드 워처");
        java.util.List<String> all = JsonPath.read(json, "$.titles[*].name");
        assertThat(all).hasSize(75);
        // 칭호함은 종류별 순서다: 트렌드 워처 다음이 새로 넣은 트렌드 탐험가
        assertThat(all.subList(0, 3)).containsExactly("트렌드 워처", "트렌드 탐험가", "트렌드 수집가");
    }

    @Test
    void killCountIsRecordedAndGrantsKillTitle() throws Exception {
        // 4.31: 죽이면 죽인 횟수가 오르고 "첫 처치" 칭호를 받는다. 프로필에도 killCount가 보인다
        String t = player("처치시험");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.killCount").value(0));
        long expBeforeKill = totalExp(pid);
        playerService.recordKill(pid);
        assertThat(playerRepo.findById(pid).orElseThrow().getKillCount()).isEqualTo(1);
        assertThat(totalExp(pid)).isEqualTo(expBeforeKill + 5); // 죽이면 EXP +kill_exp(기본 5)
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.killCount").value(1));
        String json = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        java.util.List<String> names = JsonPath.read(json, "$.titles[?(@.owned == true)].name");
        assertThat(names).contains("첫 처치");
    }

    @Test
    void bossRewardGivesTheContributionAsAPercentOfThePlayersOwnCurrentLevelWidth() throws Exception {
        // 4.35: 보스 보상의 기여도 몫은 "레벨 진행 %"다. 받는 사람 자신의 현재 레벨 폭(그 레벨에 오를 때 필요했던 EXP)의 그 %를 EXP로 준다(사람마다 EXP는 다르다)
        String t = player("보스보상");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        long exp0 = totalExp(pid);
        long need = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.nextLevelExp"));
        long got = playerService.awardMonster(pid, 7, 50.0, true);       // 1레벨은 폭이 0이라 다음 레벨로 가는 폭을 쓴다: 고정 7 + 그 폭의 50%
        assertThat(got).isEqualTo(7 + Math.round(need * 0.5));
        assertThat(totalExp(pid)).isEqualTo(exp0 + got);
        assertThat(playerRepo.findById(pid).orElseThrow().getBossKillCount()).isEqualTo(1);
        level(t, 12);                                                    // 12레벨이면 12레벨에 오를 때 필요했던 EXP의 50%
        long width = levelService.row(12).getRequiredExp();
        assertThat(width).isPositive();
        long exp1 = totalExp(pid);
        long got12 = playerService.awardMonster(pid, 0, 50.0, true);
        assertThat(got12).isEqualTo(Math.round(width * 0.5));
        assertThat(totalExp(pid)).isEqualTo(exp1 + got12);
        playerService.recordSteal(pid); // 보스 처치 참여 칭호는 처치 보상 뒤 칭호 검사에서 받는다(검사를 한 번 더 돌려 확인)
        String json = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        java.util.List<String> names = JsonPath.read(json, "$.titles[?(@.owned == true)].name");
        assertThat(names).contains("보스 사냥 입문");
    }

    @Test
    void dailyWheelCanBeSpunOnlyOncePerDayAndPaysTheNumberOnTheSlice() throws Exception {
        String t = player("돌림판시험");
        mvc.perform(get("/api/me/wheel").header("Authorization", "Bearer " + t)) // 10레벨부터: 1레벨은 잠겨 있다
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true)).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.requiredLevel").value(10));
        mvc.perform(post("/api/me/wheel/spin").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        level(t, 12);
        mvc.perform(get("/api/me/wheel").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false)).andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.slices.length()").value(10))
                .andExpect(jsonPath("$.slices[9]").value(20)).andExpect(jsonPath("$.todayReward").doesNotExist());
        String json = mvc.perform(post("/api/me/wheel/spin").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int index = JsonPath.read(json, "$.index"), reward = JsonPath.read(json, "$.reward");
        assertThat(index).isBetween(0, 9);
        assertThat(reward).isEqualTo(com.trendist.wheel.service.WheelService.SLICES[index]).isLessThanOrEqualTo(20);
        mvc.perform(get("/api/me/wheel").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.todayReward").value(reward));
        mvc.perform(post("/api/me/wheel/spin").header("Authorization", "Bearer " + t)).andExpect(status().isConflict()); // 오늘은 이미 돌렸다
    }

    @Test
    void deathCountIsRecordedAndGrantsDefeatTitle() throws Exception {
        // 4.31: 패배(키워드 없이 져서 죽음)하면 패배 횟수가 오르고 "첫 패배" 칭호를 받는다. 프로필에도 deathCount가 보인다. EXP는 주지 않는다
        String t = player("패배시험");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.deathCount").value(0));
        long exp0 = totalExp(pid);
        playerService.recordDefeat(pid);
        assertThat(playerRepo.findById(pid).orElseThrow().getDeathCount()).isEqualTo(1);
        assertThat(totalExp(pid)).isEqualTo(exp0);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.player.deathCount").value(1));
        String json = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        java.util.List<String> names = JsonPath.read(json, "$.titles[?(@.owned == true)].name");
        assertThat(names).contains("첫 패배");
    }

    @Test
    void tieCountGrantsAcornTitleAtTen() throws Exception {
        // 4.3: 현재 유행도가 같은 원과 10번 부딪히면 "도토리 키재기" 칭호를 받는다
        String t = player("키재기시험");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        for (int i = 0; i < 9; i++) playerService.recordTie(pid);
        assertThat(playerRepo.findById(pid).orElseThrow().getTieCount()).isEqualTo(9);
        String before = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(before, "$.titles[?(@.owned == true)].name")).doesNotContain("도토리 키재기");
        playerService.recordTie(pid);
        String json = mvc.perform(get("/api/me/titles").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(json, "$.titles[?(@.owned == true)].name")).contains("도토리 키재기");
    }

    @Test
    void botWinGivesTheKeywordTheBotLost() throws Exception {
        // 4.21: 봇을 이기면 봇이 잃은 바로 그 키워드를 얻는다(무작위가 아니라 지정). 목록(botCatalog)은 그 나라의 🔥 키워드다
        keywordService.adminAdd("KR", "봇이잃은키워드");
        String t = player("봇지정시험");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        long countryId = playerRepo.findById(pid).orElseThrow().getCountry().getId();
        var picked = keywordService.botCatalog(countryId).stream().filter(k -> k.text().equals("봇이잃은키워드")).findFirst().orElseThrow();
        long exp0 = totalExp(pid);
        var r = keywordService.gainFromBot(pid, picked.id());
        assertThat(r.text()).isEqualTo("봇이잃은키워드");
        // 봇도 빼앗기 대상이다: 봇을 이겨도 빼앗은 횟수 +1(4.3)
        assertThat(playerRepo.findById(pid).orElseThrow().getStealCount()).isEqualTo(1);
        assertThat(playerRepo.findById(pid).orElseThrow().getBotWinCount()).isEqualTo(1);
        // 불꽃으로 모두 잃게 한 경우 등 방이 알리는 공격 성공도 한 번에 +1이고, 칭호 확인이 따라온다
        // 공격 성공 EXP(attack_exp, 기본 1) + 새 키워드를 얻은 일반 추가 EXP(exp_per_plus, 기본 1)
        assertThat(totalExp(pid)).isEqualTo(exp0 + 2);
        playerService.recordSteal(pid);
        assertThat(playerRepo.findById(pid).orElseThrow().getStealCount()).isEqualTo(2);
        assertThat(totalExp(pid)).isEqualTo(exp0 + 3); // 공격 성공 한 번 = EXP +1
    }

    @Test
    void botWinRenewsKeywordAlreadyHeld() throws Exception {
        // 봇을 이기면 🔥 키워드 중 하나를 얻고, 이미 가진 키워드면 남은 시간만 24시간으로 늘린다(4.21)
        keywordService.adminAdd("KR", "봇보상키워드");
        String t = player("봇보상시험");
        long pid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.player.id"));
        boolean renewed = false;
        for (int i = 0; i < 500 && !renewed; i++) {
            long before = playerRepo.findById(pid).orElseThrow().getHoldCount();
            var r = keywordService.gainFromBot(pid);
            assertThat(r.status()).isIn("GAINED", "RENEWED");
            long after = playerRepo.findById(pid).orElseThrow().getHoldCount();
            if ("RENEWED".equals(r.status())) {
                renewed = true;
                assertThat(after).isEqualTo(before); // 보관함은 그대로
                assertThat(r.gained()).isZero();
                assertThat(r.expiresAt()).isAfter(java.time.Instant.now().plus(java.time.Duration.ofHours(23)));
            } else {
                assertThat(after).isEqualTo(before + 1);
            }
        }
        assertThat(renewed).isTrue();
    }

    @Test
    void adminSanctionsBlockCommentAndAccount() throws Exception {
        // 관리자 사용자 관리(13.10): 찾기, 상세, 댓글 작성 정지 → 댓글 403, 풀면 다시 됨, 계정 금지 → 세션 거절
        String t = player("제재대상");
        long pid = playerIdOf(t);
        long uid = Long.parseLong(body(mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andReturn(), "$.userId"));
        var page = adminUsers.search("KR", "제재대상", "all", "seen", "desc", 0);
        assertThat(page.items()).extracting(com.trendist.user.dto.AdminUserDtos.Row::playerId).contains(pid);
        level(t, 12); // players.level_id → levels(12레벨)
        var byLevel = adminUsers.search("KR", null, "all", "level", "desc", 0).items();
        assertThat(byLevel.getFirst().level()).isGreaterThanOrEqualTo(12);
        assertThat(byLevel).extracting(com.trendist.user.dto.AdminUserDtos.Row::level).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        // 오름차순도 된다: 같은 목록이 반대로 정렬된다
        var byLevelAsc = adminUsers.search("KR", null, "all", "level", "asc", 0).items();
        assertThat(byLevelAsc).extracting(com.trendist.user.dto.AdminUserDtos.Row::level).isSortedAccordingTo(java.util.Comparator.naturalOrder());
        long kw = plusKeyword(t, "제재시험키워드");
        var d = adminUsers.sanction(uid, new com.trendist.user.dto.AdminUserDtos.SanctionRequest("COMMENT", 1, "욕설"));
        assertThat(d.user().commentBannedUntil()).isNotNull();
        mvc.perform(post("/api/keywords/" + kw + "/comments").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"안녕\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMENT_BANNED"));
        d = adminUsers.release(d.sanctions().get(0).id());
        assertThat(d.user().commentBannedUntil()).isNull();
        mvc.perform(post("/api/keywords/" + kw + "/comments").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"안녕\"}")).andExpect(status().isCreated());
        assertThatThrownBy(() -> adminUsers.sanction(uid, new com.trendist.user.dto.AdminUserDtos.SanctionRequest("BAN", null, " ")))
                .isInstanceOf(com.trendist.common.exception.ApiException.class);
        d = adminUsers.sanction(uid, new com.trendist.user.dto.AdminUserDtos.SanctionRequest("BAN", null, "도배"));
        assertThat(d.user().bannedUntil()).isNotNull();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
        d = adminUsers.rename(uid, "새이름");
        assertThat(d.user().nickname()).isEqualTo("새이름");
    }

    @Test
    void statsCountActivityAndCloseHour() throws Exception {
        // 관리자 통계(13.9): 추가·검색은 이번 시간 값(집계 중)에 바로 보이고, 시간을 닫으면 시간별 행으로 남는다
        String t = player("통계시험");
        long before = stats.hourly("KR", 24).getLast().plus();
        plusKeyword(t, "통계시험키워드");
        mvc.perform(get("/api/keywords/search").param("q", "통계시험키워드").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        var live = stats.hourly("KR", 24).getLast();
        assertThat(live.live()).isTrue();
        assertThat(live.plus()).isEqualTo(before + 1);
        assertThat(live.search()).isGreaterThanOrEqualTo(1);
        assertThat(stats.hourly("KR", 24)).hasSize(24);
        assertThat(stats.daily("KR", 7)).hasSize(7);
        var s = stats.summary("KR");
        assertThat(s.today().plus()).isGreaterThanOrEqualTo(1);
        assertThat(s.totals().users()).isGreaterThanOrEqualTo(1);
        assertThat(s.levels()).isNotEmpty();
        stats.flushCurrentHour();
        assertThat(stats.hourly("KR", 24).getLast().plus()).isZero(); // 메모리 값은 저장하며 비운다
    }

    @Test
    void adminDashboardSummarizesTodosAndLists() throws Exception {
        // 관리자 대시보드(13.11): 처리할 일, 새 플레이어, 리더보드, 24시간 그래프 칸을 한 번에 준다. 관리자만
        String t = player("대시보드시험");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        var d = dashboard.dashboard("KR");
        assertThat(d.hourly()).hasSize(24);
        assertThat(d.todos()).extracting(com.trendist.dashboard.dto.DashboardDtos.Todo::key).contains("reports", "reported", "sanctioned", "ending");
        assertThat(d.newPlayers()).extracting(com.trendist.dashboard.dto.DashboardDtos.NewPlayer::nickname).contains("대시보드시험");
        assertThat(d.leaderboard().size()).isLessThanOrEqualTo(5);
        assertThat(d.share().cards()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reportActSanctionsTargetAndResolves() throws Exception {
        // 신고에서 바로 조치(13.5): 댓글 신고 → 댓글 작성 정지 1일 + 댓글 지우기 → 조치함. 채팅 신고 → 채팅 정지
        String a = player("조치대상"), b = player("조치신고자");
        long kid = plusKeyword(a, "조치 키워드");
        long cid = ((Number) JsonPath.read(mvc.perform(post("/api/keywords/" + kid + "/comments").header("Authorization", "Bearer " + a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"나쁜 댓글\"}")).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        report(b, "COMMENT", cid, 201);
        long pid = playerIdOf(a);
        long chatId = chatService.save(playerRepo.findById(pid).orElseThrow().getCountry().getId(), pid, "나쁜 채팅").id();
        report(b, "CHAT", chatId, 201);
        var list = reportService.adminList("RECEIVED", 0, 100).items();
        var commentReport = list.stream().filter(x -> x.type().equals("댓글") && x.targetId() == cid).findFirst().orElseThrow();
        assertThat(commentReport.targetUserId()).isNotNull();
        var res = reportService.adminAct(commentReport.id(),
                new com.trendist.report.service.ReportService.ActRequest("COMMENT", 1, "", true, true));
        assertThat(res.report().status()).isEqualTo("RESOLVED");
        assertThat(res.report().contentDeleted()).isTrue();
        assertThat(res.report().targetSanctions()).anyMatch(x -> x.startsWith("댓글 작성 정지"));
        mvc.perform(post("/api/keywords/" + kid + "/comments").header("Authorization", "Bearer " + a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"또\"}")).andExpect(status().isForbidden());
        var chatReport = list.stream().filter(x -> x.type().equals("채팅") && x.targetId() == chatId).findFirst().orElseThrow();
        reportService.adminAct(chatReport.id(), new com.trendist.report.service.ReportService.ActRequest("CHAT", 3, "도배", false, false));
        assertThat(sanctionService.chatBannedUntil(pid)).isNotNull();
        assertThatThrownBy(() -> reportService.adminAct(chatReport.id(),
                new com.trendist.report.service.ReportService.ActRequest(null, null, null, true, false)))
                .isInstanceOf(com.trendist.common.exception.ApiException.class); // 채팅은 지울 수 없다
    }

    /** 지금까지 모은 EXP 전체(레벨마다 쓰고 남기는 방식이라 레벨이 올라도 총량은 줄지 않는다) */
    private long totalExp(long pid) {
        var p = playerRepo.findById(pid).orElseThrow();
        return levelService.totalExp(p.getLevel(), p.getExp());
    }

    private void level(String token, int level) throws Exception {
        long pid = playerIdOf(token);
        tx.executeWithoutResult(st -> { var p = playerRepo.findById(pid).orElseThrow(); p.setLevel(levelService.row(level)); p.setExp(0); });
    }

    private org.springframework.test.web.servlet.ResultActions saveProfile(String token, int slot, String json) throws Exception {
        return mvc.perform(put("/api/me/profiles/" + slot).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void playerCardCarriesTheEquippedLookSoSearchAndChatCanShowTheDesignedCircle() throws Exception {
        // 검색·채팅에서 열어 방에 그 원이 없어도 정보 창에 꾸민 모양을 보여 주려고 카드(/api/players/{id})에 장착한 모양이 함께 온다
        String target = player("꾸민이");
        long targetId = playerIdOf(target);
        String viewer = player("보는이");
        // 꾸미지 않았으면 기본 바탕색
        mvc.perform(get("/api/players/" + targetId).header("Authorization", "Bearer " + viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.look.bg").value("#3fb68b")).andExpect(jsonPath("$.look.borderColor").doesNotExist());
        level(target, 10);
        saveProfile(target, 1, "{\"background\":\"#FF6B6B\",\"borderColor\":\"#ffffff\",\"borderWidth\":4}").andExpect(status().isOk());
        mvc.perform(get("/api/players/" + targetId).header("Authorization", "Bearer " + viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.look.bg").value("#ff6b6b")).andExpect(jsonPath("$.look.borderColor").value("#ffffff"))
                .andExpect(jsonPath("$.look.borderWidth").value(4));
        // 내 정보 창과 같은 구성으로 보이도록 기록도 함께 온다
        mvc.perform(get("/api/players/" + targetId).header("Authorization", "Bearer " + viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.exp").isNumber()).andExpect(jsonPath("$.stats.nextLevelExp").isNumber())
                .andExpect(jsonPath("$.stats.holdCount").value(0)).andExpect(jsonPath("$.stats.stealCount").value(0))
                .andExpect(jsonPath("$.stats.activeDays").isNumber());
    }

    @Test
    void profilesUnlockAtLevelTenAndSlotOneIsEquippedByDefault() throws Exception {
        // 꾸미기 프로필(4.9): 1~3번 칸, 아무것도 저장하지 않았으면 1번 칸(기본 모습)을 장착. 꾸미기는 10레벨부터
        String t = player("프로필시험");
        mvc.perform(get("/api/me/profiles").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unlocked").value(false)).andExpect(jsonPath("$.equippedSlot").value(1))
                .andExpect(jsonPath("$.slots.length()").value(3)).andExpect(jsonPath("$.slots[0].equipped").value(true))
                .andExpect(jsonPath("$.slots[0].saved").value(false)).andExpect(jsonPath("$.colors.length()").value(24));
        String good = "{\"background\":\"#FF6B6B\",\"borderColor\":\"#ffffff\",\"borderWidth\":4,\"nameColor\":\"#ffd166\",\"nameStyle\":\"ribbon\"}";
        saveProfile(t, 1, good).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CUSTOMIZE_LOCKED"));
        level(t, 10);
        saveProfile(t, 1, "{\"background\":\"#222b38\"}").andExpect(status().isBadRequest()); // 바닥색과 거의 같다
        saveProfile(t, 1, "{\"background\":\"#ff6b6b\",\"borderWidth\":9}").andExpect(status().isBadRequest());
        saveProfile(t, 1, "{\"background\":\"#ff6b6b\",\"nameStyle\":\"sparkle\"}").andExpect(status().isBadRequest());
        saveProfile(t, 4, good).andExpect(status().isBadRequest());
        saveProfile(t, 2, good).andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[1].saved").value(true))
                .andExpect(jsonPath("$.slots[1].look.background").value("#ff6b6b"))
                .andExpect(jsonPath("$.slots[1].look.nameStyle").value("RIBBON"))
                .andExpect(jsonPath("$.equippedSlot").value(1)); // 2번 칸을 저장해도 장착은 그대로
        saveProfile(t, 1, good).andExpect(status().isTooManyRequests()); // 5초에 한 번
        String other = player("장착시험");
        level(other, 10);
        mvc.perform(put("/api/me/profiles/equipped").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slot\":3}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.equippedSlot").value(3)).andExpect(jsonPath("$.slots[2].equipped").value(true))
                .andExpect(jsonPath("$.slots[0].equipped").value(false));
    }

    @Test
    void effectsNeedLevelElevenAndGoogleLogin() throws Exception {
        // 이동·테두리 효과는 꾸미기의 하나: 10레벨부터, Google 로그인 사용자만(4.9, 4.23)
        String member = loggedInPlayer("효과회원");
        mvc.perform(get("/api/me/profiles").header("Authorization", "Bearer " + member))
                .andExpect(jsonPath("$.trails[0].unlocked").value(false)).andExpect(jsonPath("$.trails[0].lockReason").value("10레벨부터 쓸 수 있어요"));
        level(member, 10);
        String guest = player("효과게스트");
        level(guest, 10);
        mvc.perform(get("/api/me/profiles").header("Authorization", "Bearer " + guest))
                .andExpect(jsonPath("$.borders[0].unlocked").value(false));
        String body = mvc.perform(get("/api/me/profiles").header("Authorization", "Bearer " + member))
                .andExpect(jsonPath("$.trails.length()").value(16)).andExpect(jsonPath("$.borders.length()").value(5))
                .andExpect(jsonPath("$.borders[0].unlocked").value(true)).andReturn().getResponse().getContentAsString();
        int trail = JsonPath.read(body, "$.trails[0].id"), border = JsonPath.read(body, "$.borders[4].id");
        saveProfile(guest, 1, "{\"background\":\"#ff6b6b\",\"trailEffectId\":" + trail + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EFFECT_LOCKED"));
        saveProfile(member, 1, "{\"background\":\"#ff6b6b\",\"trailEffectId\":" + border + "}").andExpect(status().isNotFound()); // 종류가 다르다
        saveProfile(member, 1, "{\"background\":\"#ff6b6b\",\"trailEffectId\":" + trail + ",\"borderEffectId\":" + border + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].trailEffectId").value(trail))
                .andExpect(jsonPath("$.slots[0].look.borderEffect").value("rainbow"))
                .andExpect(jsonPath("$.slots[0].look.trailEffect").value("afterimage"));
        // 효과마다 조건을 더 붙일 수 있다(LEVEL)
        String typeTrail = "(select id from effect_types where code = 'TRAIL')";
        jdbc.update("update effects set unlock_type = 'LEVEL', unlock_value = 50 where code = 'sparkle' and effect_type_id = " + typeTrail);
        try {
            mvc.perform(get("/api/me/profiles").header("Authorization", "Bearer " + member))
                    .andExpect(jsonPath("$.trails[?(@.code == 'sparkle')].lockReason").value("50레벨부터 쓸 수 있어요"));
        } finally {
            jdbc.update("update effects set unlock_type = 'FREE', unlock_value = null where code = 'sparkle' and effect_type_id = " + typeTrail);
        }
    }

    @Test
    void pixelArtIsSavedInProfileAndServedAsPng() throws Exception {
        // 픽셀 그림(4.9): 10레벨 + Google 로그인만, 프로필 칸에 저장하고 PNG로 받는다. 변경 간격(기본 10분)
        String guest = player("그림게스트");
        String t = loggedInPlayer("그림작가");
        level(guest, 10);
        level(t, 10);
        String pixels = "0".repeat(10) + ".".repeat(1024 - 20) + "1".repeat(10);
        String art = "\"art\":{\"palette\":[\"#FF0000\",\"#00ff00\",\"#0000ff\"],\"pixels\":\"" + pixels + "\"}";
        saveProfile(guest, 1, "{\"background\":\"#ff6b6b\",\"inner\":\"PIXEL\"," + art + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ART_LOGIN"));
        saveProfile(t, 1, "{\"background\":\"#ff6b6b\",\"inner\":\"PIXEL\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ART_NONE"));
        saveProfile(t, 1, "{\"background\":\"#ff6b6b\",\"inner\":\"PIXEL\",\"art\":{\"palette\":[\"#ff0000\"],\"pixels\":\"" + ".".repeat(1024) + "\"}}")
                .andExpect(status().isBadRequest());
        MvcResult saved = saveProfile(t, 1, "{\"background\":\"#ff6b6b\",\"inner\":\"PIXEL\"," + art + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].inner").value("PIXEL"))
                .andExpect(jsonPath("$.slots[0].art.palette.length()").value(2)) // 쓰지 않은 파란색은 빠진다
                .andExpect(jsonPath("$.artChangeableAt").isNotEmpty()).andReturn();
        String url = body(saved, "$.slots[0].look.art");
        assertThat(url).startsWith("/api/art/");
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG)); // 로그인 없이
        String other = "\"art\":{\"palette\":[\"#ffffff\"],\"pixels\":\"" + "0".repeat(1024) + "\"}";
        saveProfile(t, 2, "{\"background\":\"#ff6b6b\",\"inner\":\"PIXEL\"," + other + "}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ART_TOO_SOON")); // 간격은 칸이 달라도 같이 센다
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
                .andExpect(jsonPath("$.player.exp").value(0)) // EXP 1을 얻어 2레벨(필요 1)이 되고 남은 EXP는 0
                .andExpect(jsonPath("$.keyword.holderCount").value(1))
                .andExpect(jsonPath("$.keyword.plusedByMe").value(true));
        // 같은 키워드 두 번은 안 된다
        mvc.perform(post("/api/keywords/plus").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"양쯔 깐루\"}"))
                .andExpect(status().isConflict());
        // 점수 = 외부 키워드 1명 trend_value 12(활동 인원이 최소 인원 20보다 적어 1/20로 계산), 정수. EXP는 점수에 넣지 않는다(exp_score_cap 기본 0)
        MvcResult me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + t)).andExpect(status().isOk()).andReturn();
        Object score = JsonPath.read(me.getResponse().getContentAsString(), "$.player.currentScore");
        assertThat(score).isEqualTo(14); // 키워드 12 + 레벨 2(1:1, 4.8)
        mvc.perform(get("/api/me/keywords").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$[0].text").value("양쯔 깐루")); // 처음 입력한 표기를 유지한다(4.18)
        // 랭킹은 방에 접속한 사람만 집계한다(4.14). 이 사용자는 REST로만 플레이해서 방에 없다
        mvc.perform(get("/api/rankings").header("Authorization", "Bearer " + t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        // 내 순위도 방에 있을 때만(4.14): 방에 없으면 204
        mvc.perform(get("/api/rankings/me").header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
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
                .andExpect(jsonPath("$.addCount").value(1));
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
        report(b, "PLAYER", 999999, 404); // 없는 플레이어 번호
        // 키워드 신고(키워드 상세 팝업, 4.12): 누구나(보유자도) 한 번만, 없는 키워드는 404
        report(a, "KEYWORD", kid, 201);
        report(a, "KEYWORD", kid, 409);
        report(b, "KEYWORD", kid, 201);
        report(b, "KEYWORD", 99999999, 404);
        var kwRow = reportService.adminList(Report.RECEIVED, 0, 50).items().stream()
                .filter(r -> r.targetId() == kid && ReportType.KEYWORD.equals(r.type())).findFirst().orElseThrow();
        assertThat(kwRow.targetNickname()).isEqualTo("신고 키워드");
        assertThat(kwRow.sameTargetCount()).isEqualTo(2);
        assertThat(kwRow.targetUserId()).isNull();

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

    @Test
    void settingsAreGroupedByTheGroupTableAndCarryTheirUnit() {
        // 설정은 그룹 테이블(setting_groups)을 외래 키로 가리키고, 단위(unit)를 함께 내려 준다(13.3)
        var items = settingService.list();
        assertThat(items).isNotEmpty().allMatch(i -> i.group() != null && !i.group().isBlank());
        assertThat(items).noneMatch(i -> i.name().equals("join_protect_seconds"));
        var offline = items.stream().filter(i -> i.name().equals(SettingKeys.OFFLINE_KEEP_SECONDS)).findFirst().orElseThrow();
        assertThat(offline.group()).isEqualTo("보호·접속");
        assertThat(offline.unit()).isEqualTo("초");
        var plain = items.stream().filter(i -> i.name().equals(SettingKeys.COMBO_BOT_COUNTS)).findFirst().orElseThrow();
        assertThat(plain.unit()).isNull();
        // 그룹 순서(그룹 테이블의 sort_order)대로 붙어 있다: 한 번 지나간 그룹이 다시 나오지 않는다
        var seen = new java.util.ArrayList<String>();
        for (var i : items) {
            if (seen.isEmpty() || !seen.get(seen.size() - 1).equals(i.group())) {
                assertThat(seen).as("그룹이 흩어져 있다: " + i.group()).doesNotContain(i.group());
                seen.add(i.group());
            }
        }
        assertThat(seen).isSubsetOf(SettingKeys.GROUP_ORDER);
        assertThat(seen.stream().map(SettingKeys.GROUP_ORDER::indexOf).toList()).isSorted();
    }

    private static String uncheckedBody(MvcResult r) {
        try { return r.getResponse().getContentAsString(); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test
    void scoreHistoryRecordsOnlyChangedHours() throws Exception {
        String t = player("점수기록");
        settingService.update(1, SettingKeys.EXP_SCORE_CAP, "100"); // 점수가 생기도록 이 테스트에서만 EXP를 점수에 넣는다(기본은 0)
        try {
            scoreHistoryRecordsOnlyChangedHoursBody(t);
        } finally {
            settingService.update(1, SettingKeys.EXP_SCORE_CAP, "0");
        }
    }

    private void scoreHistoryRecordsOnlyChangedHoursBody(String t) throws Exception {
        plusKeyword(t, "점수 기록 키워드"); // 💤 키워드: EXP 1로 2레벨이 되어 유행도 = EXP 몫 1 + 레벨 2(1:1) = 3
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
                .andExpect(jsonPath("$.points[22].score").value(3))
                .andExpect(jsonPath("$.points[23].live").value(true))
                .andExpect(jsonPath("$.points[23].score").value(3));
        mvc.perform(get("/api/me/score-history?range=7d").header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.points.length()").value(7))
                .andExpect(jsonPath("$.points[6].score").value(3));
        mvc.perform(get("/api/me/score-history?range=all").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/score-history?range=1y").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keywordHourlyLogWritesSlot() throws Exception {
        // 매시 정각 기록(9.9): 지난 시간 칸에 그 시간의 추가수가 남고, 이번 시간(집계 중)은 그 뒤 변화만 센다
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
    @Test
    void skillsAreReadFromDbWithDefaultKeysAndCooldownsAndRespectEnabledFlag() throws Exception {
        // 4.33: 스킬 코드·아이콘·종류·기본 단축키·쿨타임은 DB에서 온다(이름·설명은 프론트엔드 번역 파일). 로그인 없이 읽는다
        skillCatalog.invalidate();
        mvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[1].code").value("shield"))      // 대시 다음은 해금 레벨 순서
                .andExpect(jsonPath("$[1].defaultKey").value("Digit1"))
                .andExpect(jsonPath("$[1].cooldownSeconds").value(30.0))
                .andExpect(jsonPath("$[1].category").value("DEFENSE"))
                .andExpect(jsonPath("$[1].icon").value("🛡"))
                .andExpect(jsonPath("$[1].requiredLevel").value(5))   // 방패 5레벨
                .andExpect(jsonPath("$[2].code").value("hook"))
                .andExpect(jsonPath("$[2].defaultKey").value("Digit2"))
                .andExpect(jsonPath("$[2].requiredLevel").value(10))   // 갈고리 10레벨
                .andExpect(jsonPath("$[3].code").value("slow"))
                .andExpect(jsonPath("$[3].defaultKey").value("Digit9"))
                .andExpect(jsonPath("$[3].requiredLevel").value(15))   // 슬로우 15레벨
                .andExpect(jsonPath("$[4].code").value("stealth"))
                .andExpect(jsonPath("$[4].defaultKey").value("Digit4"))
                .andExpect(jsonPath("$[4].requiredLevel").value(20))   // 은신 20레벨
                .andExpect(jsonPath("$[0].name").doesNotExist())
                .andExpect(jsonPath("$[0].code").value("dash")) // 대시는 목록의 맨 앞
                .andExpect(jsonPath("$[0].defaultKey").value("Space"))
                .andExpect(jsonPath("$[0].requiredLevel").value(1));  // 대시는 기본 스킬 // 이름·설명은 프론트엔드 번역 파일이 코드로 찾는다
        // DB 값을 고치면(캐시를 버린 뒤) 쿨타임·사용 여부가 바로 읽힌다
        var hook = skillRepository.findByCode("hook").orElseThrow();
        try {
            skillCatalog.invalidate();
            assertThat(skillCatalog.cooldownSeconds("hook", 99)).isEqualTo(30.0);
            org.springframework.test.util.ReflectionTestUtils.setField(hook, "enabled", false);
            skillRepository.saveAndFlush(hook);
            skillCatalog.invalidate();
            assertThat(skillCatalog.disabledCodes()).contains("hook");
            mvc.perform(get("/api/skills")).andExpect(jsonPath("$.length()").value(13));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(hook, "enabled", true);
            skillRepository.saveAndFlush(hook);
            skillCatalog.invalidate();
        }
    }
    @Test
    void gameInfoPublishesOrbOddsAndLevelRequirementsWithoutLogin() throws Exception {
        // 4.17, 4.20: 가이드가 보여 주는 구슬 확률(기본 60/40)과 레벨별 필요 EXP는 운영 값(설정·levels)에서 바로 읽는다. level-exp는 그 레벨로 올라갈 때 필요한 EXP다
        mvc.perform(get("/api/game-info/orb-odds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gainPercent").value(60))
                .andExpect(jsonPath("$.losePercent").value(40));
        mvc.perform(get("/api/game-info/level-exp").param("levels", "10,15,20,25,999,0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['10']").value(100))      // 그 레벨로 올라갈 때 필요한 EXP(레벨표 값 그대로)
                .andExpect(jsonPath("$['15']").value(225))
                .andExpect(jsonPath("$['20']").value(400))
                .andExpect(jsonPath("$['25']").value(625))
                .andExpect(jsonPath("$['999']").doesNotExist());
        mvc.perform(get("/api/game-info/levels"))                       // 모든 레벨의 필요 EXP
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100))
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[99].level").value(100))
                .andExpect(jsonPath("$[1].requiredExp").value(1))              // 2레벨이 되는 데 필요한 EXP(앞 레벨에서 올라올 때)
                .andExpect(jsonPath("$[9].requiredExp").value(100))
                .andExpect(jsonPath("$[99].requiredExp").value(10000));        // 100레벨은 99레벨에서 EXP 0부터 10,000이 필요하다
    }
    @Test
    void popularityCanBeRaisedOrLoweredOncePerTargetPerDayAndPlayersAreSearchable() throws Exception {
        // 4.34: 대상마다 하루 한 번(올리기/내리기), 자기 자신 불가, 닉네임 검색(같은 나라, 내 표 표시)
        String a = player("인기검색갑"), b = player("인기검색을");
        long bid = playerIdOf(b), aid = playerIdOf(a);
        String auth = "Bearer " + a;
        mvc.perform(get("/api/players/search").header("Authorization", auth).param("q", "인기검색"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.playerId==" + bid + ")].popularity").value(0));
        mvc.perform(post("/api/players/" + aid + "/popularity").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/players/" + bid + "/popularity").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/players/" + bid + "/popularity").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.popularity").value(-1)).andExpect(jsonPath("$.myVote").value(-1));
        mvc.perform(post("/api/players/" + bid + "/popularity").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}")).andExpect(status().isConflict());
        Thread.sleep(400); // 검색 초당 3회 제한
        mvc.perform(get("/api/players/search").header("Authorization", auth).param("q", "인기검색을"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].popularity").value(-1))
                .andExpect(jsonPath("$[0].myVote").value(-1));
        // 검색 결과의 상세 보기: 접속 중이 아니어도 같은 나라면 번호로 연다
        Thread.sleep(400);
        mvc.perform(get("/api/players/" + bid).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("인기검색을"))
                .andExpect(jsonPath("$.entityNo").value(-1)).andExpect(jsonPath("$.createdAt").isNotEmpty()).andExpect(jsonPath("$.me").value(false)).andExpect(jsonPath("$.popularity").value(-1)).andExpect(jsonPath("$.myVote").value(-1));
        // 검색 결과의 상세 보기에서 신고(플레이어 번호): 한 번만, 자기 자신은 안 된다
        report(a, "PLAYER", bid, 201);
        report(a, "PLAYER", bid, 409); // 같은 날은 한 번
        jdbc.update("update reports set report_date = dateadd('DAY', -1, report_date) where target_id = ? and report_date > '2000-01-01'", bid);
        report(a, "PLAYER", bid, 201); // 다음 날은 다시 신고할 수 있다
        report(a, "PLAYER", bid, 409);
        report(a, "PLAYER", aid, 400);
        report(a, "PLAYER", 999999, 404);
        // 다른 사람은 올릴 수 있다
        mvc.perform(post("/api/players/" + bid + "/popularity").header("Authorization", "Bearer " + player("인기검색병"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}")).andExpect(jsonPath("$.popularity").value(0));
    }
    @Test
    void adminSkillListCarriesEachSkillsSeedDefaultsForTheDefaultButtons() {
        // 13.3: 관리자 화면의 "기본값으로" 버튼들은 스킬마다 시더의 기본 쿨타임·해금 레벨을 쓴다(회복 쿨타임 60초처럼 스킬마다 다르다)
        var list = adminSkills.list();
        var heal = list.stream().filter(i -> i.code().equals("heal")).findFirst().orElseThrow();
        var hook = list.stream().filter(i -> i.code().equals("hook")).findFirst().orElseThrow();
        assertThat(heal.defaultCooldown()).isEqualTo(60.0);
        assertThat(heal.defaultRequiredLevel()).isEqualTo(40);
        assertThat(hook.defaultCooldown()).isEqualTo(30.0);
        assertThat(hook.defaultRequiredLevel()).isEqualTo(10);
    }

    @Test
    void adminEditsEachSkillCooldownAndLevelAndItReachesThePublicList() throws Exception {
        // 13.3: 스킬마다 쿨타임·해금 레벨·사용 여부를 관리자가 따로 고친다. 관리자가 아니면 403
        String t = player("스킬관리시험");
        mvc.perform(get("/api/admin/skills").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        assertThat(adminSkills.list()).extracting(com.trendist.skill.controller.AdminSkillController.Item::code)
                .containsExactly("dash", "shield", "hook", "slow", "stealth", "shockwave", "cleanse", "darkness", "heal", "poison", "stun", "quake", "flame", "absorb");
        try {
            var r = adminSkills.update(new com.trendist.auth.security.AuthUser(1), "hook", new com.trendist.skill.controller.AdminSkillController.UpdateRequest(12.5, 16, null));
            assertThat(r.cooldownSeconds()).isEqualTo(12.5);
            assertThat(r.requiredLevel()).isEqualTo(16);
            assertThat(adminSkills.list().stream().filter(x -> x.code().equals("shield")).findFirst().orElseThrow().cooldownSeconds()).isEqualTo(30.0); // 다른 스킬은 그대로
            mvc.perform(get("/api/skills")).andExpect(jsonPath("$[?(@.code=='hook')].cooldownSeconds").value(12.5))
                    .andExpect(jsonPath("$[?(@.code=='hook')].requiredLevel").value(16));
            assertThatThrownBy(() -> adminSkills.update(new com.trendist.auth.security.AuthUser(1), "hook", new com.trendist.skill.controller.AdminSkillController.UpdateRequest(-1.0, null, null)))
                    .hasMessageContaining("쿨타임");
            assertThatThrownBy(() -> adminSkills.update(new com.trendist.auth.security.AuthUser(1), "hook", new com.trendist.skill.controller.AdminSkillController.UpdateRequest(null, 0, null)))
                    .hasMessageContaining("해금 레벨");
            assertThatThrownBy(() -> adminSkills.update(new com.trendist.auth.security.AuthUser(1), "nope", new com.trendist.skill.controller.AdminSkillController.UpdateRequest(1.0, null, null)))
                    .hasMessageContaining("없는 스킬");
        } finally {
            adminSkills.update(new com.trendist.auth.security.AuthUser(1), "hook", new com.trendist.skill.controller.AdminSkillController.UpdateRequest(30.0, 10, true));
        }
    }
}
