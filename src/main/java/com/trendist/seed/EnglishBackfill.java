package com.trendist.seed;

import com.trendist.effect.entity.Effect;
import com.trendist.effect.repository.EffectRepository;
import com.trendist.effect.repository.EffectTypeRepository;
import com.trendist.guide.entity.Guide;
import com.trendist.guide.entity.GuideCategory;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.TitleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영어판(*_en) 채우기. DataSeeder는 운영에서 고치는 테이블(칭호·가이드)이 비어 있을 때만 넣으므로,
 * 이미 데이터가 있는 DB에는 영어판이 비어 있다. 시작할 때마다 **비어 있는(NULL) 영어 칸만** 시드로 채운다.
 * 짝을 짓는 키: 칭호는 한국어 이름, 효과 종류·효과는 코드, 가이드 카테고리는 code, 글은 (카테고리 code, 한국어 제목).
 * 관리자가 고친 한국어 이름·제목은 짝이 안 맞아 건너뛰고, 이미 채워진 영어 칸은 절대 덮어쓰지 않는다.
 * (관리자가 영어 칸을 일부러 비우면 다음 시작 때 시드로 다시 채워진다.)
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class EnglishBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EnglishBackfill.class);

    private final TitleRepository titles;
    private final EffectTypeRepository effectTypes;
    private final EffectRepository effects;
    private final GuideCategoryRepository categories;
    private final GuideRepository guides;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int filled = titles() + effects() + guides();
        if (filled > 0) log.info("english backfill, {} values filled", filled);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private int titles() {
        int n = 0;
        for (Title t : this.titles.findAll()) {
            EnglishSeed.Title en = EnglishSeed.TITLES.get(t.getName());
            if (en == null) continue;
            // 이름·설명은 따로 보지만 한 칸이라도 비었을 때만 건드린다(채워진 칸은 그대로)
            String name = blank(t.getNameEn()) ? en.nameEn() : t.getNameEn();
            String desc = blank(t.getDescriptionEn()) ? en.descriptionEn() : t.getDescriptionEn();
            if (!name.equals(t.getNameEn()) || !desc.equals(t.getDescriptionEn())) { t.changeEnglish(name, desc); n++; }
        }
        return n;
    }

    private int effects() {
        int n = 0;
        for (var e : EnglishSeed.EFFECT_TYPES.entrySet()) {
            var type = effectTypes.findByCode(e.getKey()).orElse(null);
            if (type != null && blank(type.getNameEn())) { type.changeNameEn(e.getValue()); n++; }
        }
        for (var e : EnglishSeed.EFFECTS.entrySet()) {
            String[] key = e.getKey().split("/");
            Effect effect = this.effects.findByTypeAndCode(key[0], key[1]).orElse(null);
            if (effect != null && blank(effect.getNameEn())) { effect.changeNameEn(e.getValue()); n++; }
        }
        return n;
    }

    private int guides() {
        int n = 0;
        if (EnglishSeed.GUIDE.size() != GuideSeed.CATEGORIES.size()) {
            log.warn("english guide seed does not match the Korean one, skipped");
            return 0;
        }
        for (int i = 0; i < GuideSeed.CATEGORIES.size(); i++) {
            GuideSeed.Category ko = GuideSeed.CATEGORIES.get(i);
            EnglishSeed.Category en = EnglishSeed.GUIDE.get(i);
            if (!ko.code().equals(en.code()) || ko.articles().size() != en.articles().size()) {
                log.warn("english guide seed '{}' does not match the Korean one, skipped", ko.code());
                continue;
            }
            GuideCategory category = categories.findAll().stream().filter(c -> c.getCode().equals(ko.code())).findFirst().orElse(null);
            if (category == null) continue; // 관리자가 지운 카테고리
            if (blank(category.getNameEn())) { category.changeNameEn(en.nameEn()); n++; }
            List<Guide> existing = this.guides.findByGuideCategoryId(category.getId());
            for (int j = 0; j < ko.articles().size(); j++) {
                String koTitle = ko.articles().get(j).title();
                EnglishSeed.Article a = en.articles().get(j);
                for (Guide g : existing) {
                    if (!g.getTitle().equals(koTitle)) continue;
                    String title = blank(g.getTitleEn()) ? a.titleEn() : g.getTitleEn();
                    String body = blank(g.getBodyEn()) ? a.bodyEn() : g.getBodyEn();
                    if (!title.equals(g.getTitleEn()) || !body.equals(g.getBodyEn())) { g.changeEnglish(title, body); n++; }
                }
            }
        }
        return n;
    }
}
