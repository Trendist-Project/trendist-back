package com.trendist.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.trendist.guide.entity.Guide;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** 가이드 시드(4.17): 새 스킬 글이 들어 있고, 이미 만든 DB의 예전 본문만 새 본문으로 고쳐지며 관리자가 고친 글은 그대로 둔다. */
@SpringBootTest
@ActiveProfiles("test")
class GuideSeedTest {
    @Autowired DataSeeder seeder;
    @Autowired GuideRepository guides;
    @Autowired GuideCategoryRepository categories;
    @Autowired TransactionTemplate tx;

    private static final Pattern EMBED = Pattern.compile("\\[\\[[^\\]]+\\]\\]");

    private static int embeds(String body) {
        Matcher m = EMBED.matcher(body);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Test
    void addedArticlesAreUniqueAndEnglishKeepsTheSameGameImages() {
        Set<String> seen = new HashSet<>();
        for (GuideSeed.Added a : GuideSeed.ADDED) {
            assertThat(seen.add(a.categoryCode() + "/" + a.title())).as("중복 제목 " + a.title()).isTrue();
            assertThat(a.titleEn()).isNotBlank();
            assertThat(embeds(a.bodyEn())).as("영어 본문의 게임 그림 수: " + a.title()).isEqualTo(embeds(a.body()));
        }
        // 스킬마다 움직이는 그림이 하나씩 있다(대시·방패·갈고리·반발·충격파·은신·암흑·독·정화·슬로우·스턴·불꽃·흡수)
        List<String> skillBodies = GuideSeed.ADDED.stream().filter(a -> a.categoryCode().equals("skills")).map(GuideSeed.Added::body).toList();
        for (String token : List.of("[[스킬 대시]]", "[[스킬 방패]]", "[[스킬 갈고리]]", "[[스킬 반발]]", "[[스킬 충격파 키워드]]", "[[스킬 은신]]", "[[스킬 암흑]]", "[[스킬 독]]", "[[스킬 정화]]", "[[스킬 슬로우]]", "[[스킬 스턴]]", "[[스킬 불꽃]]", "[[스킬 흡수]]")) {
            assertThat(skillBodies.stream().filter(b -> b.contains(token)).count()).as(token).isEqualTo(1);
        }
    }

    @Test
    void newSkillArticlesAreInTheDatabaseInOrder() {
        var skills = categories.findByCodeAndEnabledTrue("skills").orElseThrow();
        List<String> titles = guides.findByGuideCategoryId(skills.getId()).stream()
                .sorted(java.util.Comparator.comparingLong(Guide::getSortOrder)).map(Guide::getTitle).toList();
        assertThat(titles).contains("스킬 장착하기", "🕶️ 암흑", "☠️ 독", "✨ 정화");
        assertThat(titles.indexOf("🕶️ 암흑")).isGreaterThan(titles.indexOf("👻 은신"));
    }

    @Test
    void oldSeedBodyIsReplacedButAdminEditsAreKept() {
        var skills = categories.findByCodeAndEnabledTrue("skills").orElseThrow();
        GuideSeed.Fix overview = GuideSeedOld.FIXES.stream().filter(f -> f.title().equals("스킬 한눈에 보기")).findFirst().orElseThrow();
        GuideSeed.Fix hook = GuideSeedOld.FIXES.stream().filter(f -> f.title().equals("🪝 갈고리")).findFirst().orElseThrow();
        tx.executeWithoutResult(st -> {
            for (Guide g : guides.findByGuideCategoryId(skills.getId())) {
                if (g.getTitle().equals("스킬 한눈에 보기")) g.changeBody(overview.oldBody()); // 예전 시드 그대로
                if (g.getTitle().equals("🪝 갈고리")) g.changeBody("관리자가 고친 내용");        // 관리자가 고친 글
            }
        });
        tx.executeWithoutResult(st -> seeder.run(new DefaultApplicationArguments()));
        tx.executeWithoutResult(st -> {
            GuideSeed.Added fresh = GuideSeed.ADDED.stream().filter(a -> a.title().equals("스킬 한눈에 보기")).findFirst().orElseThrow();
            for (Guide g : guides.findByGuideCategoryId(skills.getId())) {
                if (g.getTitle().equals("스킬 한눈에 보기")) {
                    assertThat(g.getBody().trim()).isEqualTo(fresh.body().trim());
                    assertThat(g.getBodyEn().trim()).isEqualTo(fresh.bodyEn().trim());
                    assertThat(g.getBody()).contains("스킬 장착하기");
                }
                if (g.getTitle().equals("🪝 갈고리")) assertThat(g.getBody()).isEqualTo("관리자가 고친 내용");
            }
        });
        assertThat(hook.oldBody()).contains("반지름의 5배쯤"); // 예전 본문은 옛 사거리 설명을 담고 있다
    }

    @Test
    void newerSeedVersionReplacesUneditedBodyButKeepsAdminEdits() {
        // 판 번호 방식(GuideSeed.VERSIONS): 행의 판이 낮고 본문이 시더가 넣은 그대로(해시 일치)면 새 본문, 관리자가 고친 글은 그대로
        var skills = categories.findByCodeAndEnabledTrue("skills").orElseThrow();
        tx.executeWithoutResult(st -> {
            for (Guide g : guides.findByGuideCategoryId(skills.getId())) {
                if (g.getTitle().equals("☠️ 독")) { g.changeBody("예전 시드 본문"); g.markSeed(0, DataSeeder.seedHash("예전 시드 본문", g.getBodyEn())); }
                if (g.getTitle().equals("🕶️ 암흑")) { String h = DataSeeder.seedHash("예전 시드 본문", g.getBodyEn()); g.changeBody("관리자가 고친 본문"); g.markSeed(0, h); }
            }
        });
        tx.executeWithoutResult(st -> seeder.run(new DefaultApplicationArguments()));
        tx.executeWithoutResult(st -> {
            GuideSeed.Added poison = GuideSeed.ADDED.stream().filter(a -> a.title().equals("☠️ 독")).findFirst().orElseThrow();
            for (Guide g : guides.findByGuideCategoryId(skills.getId())) {
                if (g.getTitle().equals("☠️ 독")) {
                    assertThat(g.getBody()).isEqualTo(poison.body());
                    assertThat(g.getSeedVersion()).isEqualTo(GuideSeed.version("skills", "☠️ 독"));
                    assertThat(g.getSeedHash()).isEqualTo(DataSeeder.seedHash(poison.body(), poison.bodyEn()));
                }
                if (g.getTitle().equals("🕶️ 암흑")) {
                    assertThat(g.getBody()).isEqualTo("관리자가 고친 본문");
                    assertThat(g.getSeedVersion()).isEqualTo(GuideSeed.version("skills", "🕶️ 암흑")); // 판은 올라가서 다시 묻지 않는다
                }
            }
        });
    }

    @Test
    void everySeededArticleGetsAVersionAndTheVersionTableNamesRealArticles() {
        var keys = new HashSet<String>();
        for (DataSeeder.SeedArticle a : DataSeeder.seedArticles()) keys.add(a.categoryCode() + "/" + a.title());
        assertThat(keys).containsAll(GuideSeed.VERSIONS.keySet()); // 오타로 판 번호가 엉뚱한 글을 가리키지 않게
        tx.executeWithoutResult(st -> {
            for (var c : categories.findAll()) for (Guide g : guides.findByGuideCategoryId(c.getId()))
                if (keys.contains(c.getCode() + "/" + g.getTitle())) assertThat(g.getSeedVersion()).as(g.getTitle()).isNotNull();
        });
    }

    @Test
    void everySettingSlotInSeedArticlesRendersWithTheDefaults() {
        // 시드 글의 {{설정이름}}은 모두 코드에 있는 설정이어야 한다(틀리면 글에 {{…}}가 그대로 보인다)
        for (DataSeeder.SeedArticle a : DataSeeder.seedArticles()) {
            for (String body : new String[]{a.body(), a.bodyEn()}) {
                String out = com.trendist.guide.support.GuideValues.render(body, n -> {
                    if (!com.trendist.setting.support.SettingKeys.isKnown(n)) throw new IllegalArgumentException(n);
                    return com.trendist.setting.support.SettingKeys.defaultOf(n);
                });
                assertThat(out).as(a.title()).doesNotContain("{{");
            }
        }
        GuideSeed.Added poison = GuideSeed.ADDED.stream().filter(x -> x.title().equals("☠️ 독")).findFirst().orElseThrow();
        assertThat(poison.body()).contains("{{poison_tick_seconds}}");
    }

    @Autowired com.trendist.guide.service.GuideService guideService;

    @Test
    void publicGuideShowsTheCurrentSettingValues() {
        // 공개 가이드는 설정값 자리를 지금 값으로 바꿔 보여 준다(기본값: 독 9초 동안 3초마다)
        var poison = guideService.articles("skills", com.trendist.common.support.Lang.KO).stream().filter(x -> x.title().equals("☠️ 독")).findFirst().orElseThrow();
        assertThat(poison.body()).contains("9초 동안 3초마다").doesNotContain("{{");
    }

    @Test
    void preVersionRowWithTheOldNumericBodyGetsTheSettingSlots() {
        // V62 이전 DB(판 번호 없음)의 독 글이 222판 직전 본문 그대로면 설정값 자리가 든 새 본문으로 바뀌고 판이 올라간다
        var skills = categories.findByCodeAndEnabledTrue("skills").orElseThrow();
        GuideSeed.Fix before = GuideSeedOld.FIXES.stream().filter(f -> f.title().equals("☠️ 독") && f.oldBody().contains("9초 동안 3초마다")).findFirst().orElseThrow();
        tx.executeWithoutResult(st -> {
            for (Guide g : guides.findByGuideCategoryId(skills.getId()))
                if (g.getTitle().equals("☠️ 독")) { g.changeBody(before.oldBody()); g.markSeed(null, null); }
        });
        tx.executeWithoutResult(st -> seeder.run(new DefaultApplicationArguments()));
        tx.executeWithoutResult(st -> {
            Guide g = guides.findByGuideCategoryId(skills.getId()).stream().filter(x -> x.getTitle().equals("☠️ 독")).findFirst().orElseThrow();
            assertThat(g.getBody()).contains("{{poison_tick_seconds}}");
            assertThat(g.getSeedVersion()).isEqualTo(2);
        });
    }
}
