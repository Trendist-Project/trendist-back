package com.trendist.keyword.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.common.code.CodeTables;
import com.trendist.keyword.repository.KeywordRepository;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 금지어 포함 검사(4.6, 13.1): 그 나라에서 ⛔금지된 키워드의 정규화 값이 입력 키워드(정규화 값)에 **들어 있으면** 막는다.
 * 예) "시발"을 금지하면 "시발2", "아시발"도 검색·추가할 수 없다. 정규화 값끼리 비교하므로 대소문자·공백·전각은 무시된다.
 * 한 글자짜리 금지어는 거의 모든 말에 들어가 버리므로 포함 검사에서는 빼고(그 글자와 똑같은 키워드만 금지), 두 글자부터 포함 검사를 한다.
 * 금지 목록은 나라별로 30초 메모리에 두고, 금지·해제가 커밋되면 바로 비운다.
 */
@Component
@RequiredArgsConstructor
public class BannedWordFilter {
    /** 포함 검사를 하는 금지어의 최소 길이(정규화 값 기준) */
    public static final int MIN_WORD_LENGTH = 2;

    private final KeywordRepository keywords;
    private final CodeTables codes;
    private final Cache<String, List<String>> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(50).build();

    /** 이 나라에서 normalized가 금지어를 품고 있는지(금지된 키워드와 똑같은 것도 포함) */
    public boolean contains(String countryCode, String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;
        for (String w : words(countryCode)) if (normalized.contains(w)) return true;
        return false;
    }

    private List<String> words(String countryCode) {
        return cache.get(countryCode, c -> keywords.findNormalizedByCountryCodeAndState(c, codes.keywordStateId(CodeTables.KW_BANNED)).stream()
                .filter(w -> w.length() >= MIN_WORD_LENGTH).toList());
    }

    /** 금지·해제가 커밋되면 목록을 바로 비운다 */
    @org.springframework.transaction.event.TransactionalEventListener
    public void onChanged(KeywordPageEvents.Changed e) { cache.invalidateAll(); }
}
