package com.trendist.guide.service;

import com.trendist.common.exception.ApiException;
import com.trendist.guide.dto.AdminGuideDtos;
import com.trendist.guide.entity.Guide;
import com.trendist.guide.entity.GuideCategory;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminGuideServiceImpl implements AdminGuideService {
    static final int NAME_MAX = 100, TITLE_MAX = 100, BODY_MAX = 20000;
    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9-]{1,49}");
    private static final Set<String> TYPES = Set.of(GuideCategory.FAQ, GuideCategory.ARTICLE);

    private final GuideCategoryRepository categories;
    private final GuideRepository guides;
    private final UserRepository users;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<AdminGuideDtos.Category> categories() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(this::category).toList();
    }

    @Override
    @Transactional
    public AdminGuideDtos.Category createCategory(AdminGuideDtos.CategoryRequest req) {
        String code = req.code() == null ? "" : req.code().strip();
        if (!CODE.matcher(code).matches()) {
            throw ApiException.badRequest("GUIDE_CODE", "코드는 영어 소문자·숫자·하이픈(-) 2~50자로 써 주세요");
        }
        if (categories.existsByCode(code)) throw ApiException.conflict("GUIDE_CODE_EXISTS", "이미 있는 코드예요");
        long order = categories.findAllByOrderBySortOrderAscIdAsc().stream().mapToLong(GuideCategory::getSortOrder).max().orElse(0) + 1;
        GuideCategory c = new GuideCategory(code, name(req.name()), type(req.displayType()), Math.toIntExact(order));
        if (Boolean.FALSE.equals(req.enabled())) c.edit(c.getName(), c.getDisplayType(), false);
        categories.saveAndFlush(c);
        return category(c);
    }

    @Override
    @Transactional
    public AdminGuideDtos.Category updateCategory(long id, AdminGuideDtos.CategoryRequest req) {
        GuideCategory c = requireCategory(id);
        c.edit(name(req.name()), type(req.displayType()), req.enabled() == null ? c.isEnabled() : req.enabled());
        categories.flush();
        return category(c);
    }

    @Override
    @Transactional
    public List<AdminGuideDtos.Category> moveCategory(long id, int direction) {
        List<GuideCategory> list = new ArrayList<>(categories.findAllByOrderBySortOrderAscIdAsc());
        int i = indexOf(list.stream().map(GuideCategory::getId).toList(), id);
        if (i < 0) throw notFoundCategory();
        swap(list, i, direction);
        for (int k = 0; k < list.size(); k++) list.get(k).changeSortOrder(k + 1);
        categories.flush();
        return list.stream().map(this::category).toList();
    }

    @Override
    @Transactional
    public void deleteCategory(long id) {
        GuideCategory c = requireCategory(id);
        // 지운 글(소프트 삭제)도 카테고리를 가리키므로, 게시 중인 글이 없을 때 지운 글까지 함께 지운다
        if (!guides.findByGuideCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(id).isEmpty()) {
            throw ApiException.conflict("GUIDE_CATEGORY_NOT_EMPTY", "글이 있는 카테고리는 지울 수 없어요. 글을 먼저 지우거나 숨기기를 쓰세요");
        }
        guides.deleteAll(guides.findByGuideCategoryId(id));
        categories.delete(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminGuideDtos.Article> articles(long categoryId) {
        requireCategory(categoryId);
        return live(categoryId).stream().map(this::article).toList();
    }

    @Override
    @Transactional
    public AdminGuideDtos.Article createArticle(long authorUserId, long categoryId, AdminGuideDtos.ArticleRequest req) {
        GuideCategory c = requireCategory(categoryId);
        User author = users.findById(authorUserId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        long order = live(categoryId).stream().mapToLong(Guide::getSortOrder).max().orElse(0) + 1;
        Guide g = guides.saveAndFlush(new Guide(c, author, title(req.title()), body(req.body()), Math.toIntExact(order)));
        return article(g);
    }

    @Override
    @Transactional
    public AdminGuideDtos.Article updateArticle(long id, AdminGuideDtos.ArticleRequest req) {
        Guide g = requireArticle(id);
        g.edit(title(req.title()), body(req.body()));
        guides.flush();
        return article(g);
    }

    @Override
    @Transactional
    public List<AdminGuideDtos.Article> moveArticle(long id, int direction) {
        Guide g = requireArticle(id);
        List<Guide> list = new ArrayList<>(live(g.getGuideCategory().getId()));
        swap(list, indexOf(list.stream().map(Guide::getId).toList(), id), direction);
        for (int k = 0; k < list.size(); k++) list.get(k).changeSortOrder(k + 1);
        guides.flush();
        return list.stream().map(this::article).toList();
    }

    @Override
    @Transactional
    public void deleteArticle(long id) {
        requireArticle(id).delete(Instant.now(clock));
    }

    private List<Guide> live(long categoryId) {
        return guides.findByGuideCategoryIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(categoryId);
    }

    private static int indexOf(List<Long> ids, long id) { return ids.indexOf(id); }

    private static <T> void swap(List<T> list, int i, int direction) {
        if (direction != -1 && direction != 1) throw ApiException.badRequest("GUIDE_MOVE", "위(-1)나 아래(1)로만 옮길 수 있어요");
        int j = i + direction;
        if (j < 0 || j >= list.size()) return; // 맨 끝이면 그대로
        T t = list.get(i); list.set(i, list.get(j)); list.set(j, t);
    }

    private GuideCategory requireCategory(long id) { return categories.findById(id).orElseThrow(AdminGuideServiceImpl::notFoundCategory); }

    private static ApiException notFoundCategory() { return ApiException.notFound("GUIDE_CATEGORY_NOT_FOUND", "없는 카테고리예요"); }

    private Guide requireArticle(long id) {
        return guides.findById(id).filter(g -> g.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("GUIDE_NOT_FOUND", "없는 글이에요"));
    }

    private static String name(String raw) { return text(raw, NAME_MAX, "GUIDE_CATEGORY_NAME", "카테고리 이름은"); }

    private static String title(String raw) { return text(raw, TITLE_MAX, "GUIDE_TITLE", "제목은"); }

    private static String body(String raw) { return text(raw, BODY_MAX, "GUIDE_BODY", "본문은"); }

    private static String text(String raw, int max, String code, String what) {
        String t = raw == null ? "" : raw.strip();
        int len = t.codePointCount(0, t.length());
        if (len < 1 || len > max) throw ApiException.badRequest(code, what + " 1~" + max + "자로 써 주세요");
        return t;
    }

    private static String type(String raw) {
        if (raw == null || !TYPES.contains(raw)) throw ApiException.badRequest("GUIDE_DISPLAY_TYPE", "형식은 FAQ 또는 ARTICLE이에요");
        return raw;
    }

    private AdminGuideDtos.Category category(GuideCategory c) {
        return new AdminGuideDtos.Category(c.getId(), c.getCode(), c.getName(), c.getDisplayType(), c.isEnabled(), c.getSortOrder(),
                live(c.getId()).size());
    }

    private AdminGuideDtos.Article article(Guide g) {
        return new AdminGuideDtos.Article(g.getId(), g.getGuideCategory().getId(), g.getTitle(), g.getBody(), g.getSortOrder(),
                g.getCreatedAt(), g.getUpdatedAt());
    }
}
