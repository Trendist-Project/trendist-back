package com.trendist.guide.service;

import com.trendist.common.exception.ApiException;
import com.trendist.guide.dto.GuideDtos;
import com.trendist.guide.entity.GuideCategory;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuideServiceImpl implements GuideService {
    private final GuideCategoryRepository categories;
    private final GuideRepository guides;

    @Override
    @Transactional(readOnly = true)
    public List<GuideDtos.Category> categories() {
        return categories.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(c -> new GuideDtos.Category(c.getCode(), c.getName(), c.getDisplayType())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideDtos.Article> articles(String categoryCode) {
        GuideCategory c = categories.findByCodeAndEnabledTrue(categoryCode)
                .orElseThrow(() -> ApiException.notFound("GUIDE_CATEGORY_NOT_FOUND", "가이드 카테고리를 찾을 수 없어요"));
        return guides.findByGuideCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(c.getId()).stream()
                .map(g -> new GuideDtos.Article(g.getId(), g.getTitle(), g.getBody(), g.getUpdatedAt())).toList();
    }
}
