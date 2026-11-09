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
        // 스킬마다 움직이는 그림이 하나씩 있다(대시·방패·갈고리·충격파·은신·암흑·독·정화)
        List<String> skillBodies = GuideSeed.ADDED.stream().filter(a -> a.categoryCode().equals("skills")).map(GuideSeed.Added::body).toList();
        for (String token : List.of("[[스킬 대시]]", "[[스킬 방패]]", "[[스킬 갈고리]]", "[[스킬 충격파]]", "[[스킬 은신]]", "[[스킬 암흑]]", "[[스킬 독]]", "[[스킬 정화]]")) {
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
}
