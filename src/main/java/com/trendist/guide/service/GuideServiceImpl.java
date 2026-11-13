package com.trendist.guide.service;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.Lang;
import com.trendist.guide.dto.GuideDtos;
import com.trendist.guide.entity.GuideCategory;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import com.trendist.guide.support.GuideValues;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuideServiceImpl implements GuideService {
    private final GuideCategoryRepository categories;
    private final GuideRepository guides;
    private final SettingService settings;

    /** 본문의 {{설정이름}}을 지금 값으로(4.17). 설정에 없는 이름이면 그 자리는 그대로 둔다 */
    private String render(String body) {
        return GuideValues.render(body, name -> {
            if (!SettingKeys.isKnown(name)) throw new IllegalArgumentException(name);
            return settings.getDouble(name);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideDtos.Category> categories(Lang lang) {
        return categories.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(c -> new GuideDtos.Category(c.getCode(), c.name(lang), c.getDisplayType())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideDtos.Article> articles(String categoryCode, Lang lang) {
        GuideCategory c = categories.findByCodeAndEnabledTrue(categoryCode)
                .orElseThrow(() -> ApiException.notFound("GUIDE_CATEGORY_NOT_FOUND", "가이드 카테고리를 찾을 수 없어요"));
        return guides.findByGuideCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(c.getId()).stream()
                .map(g -> new GuideDtos.Article(g.getId(), g.title(lang), render(g.body(lang)), g.getUpdatedAt())).toList();
    }
}
