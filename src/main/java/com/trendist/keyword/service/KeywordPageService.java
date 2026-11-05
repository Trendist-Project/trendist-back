package com.trendist.keyword.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.common.code.CodeTables;
import com.trendist.keyword.support.BannedWordFilter;
import com.trendist.common.exception.ApiException;
import com.trendist.config.AppProperties;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.support.KeywordNormalizer;
import com.trendist.keyword.support.KeywordPage;
import com.trendist.keyword.support.KeywordViewCounter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 키워드 페이지(4.28). 금지가 아닌 모든 키워드가 `/{kr|global}/keywords/{키워드}` 주소를 가진다.
 * 크롤러가 몰려도 DB가 부담되지 않게 페이지 값을 30초 동안 메모리에 둔다(조회수는 매번 더해 보여 준다).
 */
@Service
public class KeywordPageService {
    /** 팝업과 같은 기간(24시간이 처음 선택) */
    private static final String[] RANGES = {"24h", "7d", "30d", "180d"};

    private final KeywordRepository keywords;
    private final KeywordActivityService activity;
    private final KeywordViewCounter views;
    private final CodeTables codes;
    private final BannedWordFilter bannedWords;
    private final String baseUrl;
    private final Cache<String, Optional<Cached>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(20_000).build();

    /** 캐시에 두는 값: 키워드 번호와 그려질 값(조회수는 저장된 값 그대로, 저장 전 값은 보여 줄 때 더한다) */
    private record Cached(long id, KeywordPage.Data data) {}

    public KeywordPageService(KeywordRepository keywords, KeywordActivityService activity, KeywordViewCounter views,
                              CodeTables codes, BannedWordFilter bannedWords, AppProperties props) {
        this.keywords = keywords; this.activity = activity; this.views = views; this.codes = codes; this.bannedWords = bannedWords;
        this.baseUrl = props.share() == null ? new AppProperties.Share(null).baseUrl() : props.share().baseUrl();
    }

    public String baseUrl() { return baseUrl; }

    /** 방문 한 번의 결과: 페이지 값과 키워드 번호 */
    public record Found(long keywordId, KeywordPage.Data data) {}

    /** 주소의 키워드로 페이지 값을 찾는다. 규칙에 맞지 않거나 없거나 금지·닫힌 나라면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<Found> find(String countryCode, String slug) {
        String code = countryCode.toUpperCase(Locale.ROOT);
        String normalized;
        try {
            normalized = KeywordNormalizer.validate(KeywordNormalizer.display(slug));
        } catch (ApiException e) {
            return Optional.empty();
        }
        if (bannedWords.contains(code, normalized)) return Optional.empty(); // 금지어가 들어 있는 키워드는 열리지 않는다(4.6)
        String key = code + "|" + normalized;
        Optional<Cached> cached = cache.get(key, k -> load(code, normalized));
        // 캐시된 값이라도 금지 여부는 매번 새로 본다(금지한 키워드가 캐시 시간 동안 열리지 않게)
        if (cached.isPresent() && isBanned(cached.get().id())) {
            cache.invalidate(key);
            return Optional.empty();
        }
        return cached.map(c -> new Found(c.id(), c.data()));
    }

    private boolean isBanned(long keywordId) {
        return keywords.findStateId(keywordId).map(codes.keywordStateId(CodeTables.KW_BANNED)::equals).orElse(true);
    }

    /** 금지·해제가 커밋되면 그 키워드의 페이지 캐시를 바로 비운다(해제 직후 404가 남지 않게). */
    @org.springframework.transaction.event.TransactionalEventListener
    public void onChanged(com.trendist.keyword.support.KeywordPageEvents.Changed e) {
        cache.invalidate(e.countryCode().toUpperCase(Locale.ROOT) + "|" + e.normalized());
    }

    /** 이번 방문을 센 뒤(크롤러 제외) 저장 전 값까지 더한 조회수로 값을 만든다. */
    public KeywordPage.Data countAndShow(Found f, String ip, String userAgent) {
        views.hit(f.keywordId(), ip, userAgent);
        KeywordPage.Data d = f.data();
        return new KeywordPage.Data(d.countryCode(), d.text(), d.normalized(), d.state(), d.trendValue(), d.holders(),
                d.totalPlus(), d.searches(), d.views() + views.pendingOf(f.keywordId()), d.aiComment(), d.googleSource(), d.activities());
    }

    private Optional<Cached> load(String countryCode, String normalized) {
        Optional<Keyword> found = keywords.findForPage(countryCode, normalized);
        if (found.isEmpty()) return Optional.empty();
        Keyword k = found.get();
        if (!k.getCountry().isEnabled() || CodeTables.KW_BANNED.equals(k.getKeywordState().getName())) return Optional.empty();
        Map<String, KeywordDtos.Activity> activities = new LinkedHashMap<>();
        for (String r : RANGES) activities.put(r, activity.publicActivity(k.getId(), r));
        String comment = k.getAiComment() == null ? null : k.getAiComment().getComment();
        KeywordPage.Data data = new KeywordPage.Data(k.getCountry().getCode(), k.getText(), k.getNormalized(),
                k.getKeywordState().getName(), k.getTrendValue(), k.getHolderCount(), k.getTotalPlusCount(),
                k.getSearchCount(), k.getViewCount(), comment, "google".equals(k.getDataSource().getName()), activities);
        return Optional.of(new Cached(k.getId(), data));
    }
}
