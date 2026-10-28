package com.trendist.seed;

import com.trendist.common.code.CodeTables;
import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.guide.entity.Guide;
import com.trendist.guide.entity.GuideCategory;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.entity.KeywordState;
import com.trendist.keyword.repository.DataSourceRepository;
import com.trendist.keyword.repository.KeywordStateRepository;
import com.trendist.player.entity.Level;
import com.trendist.player.repository.LevelRepository;
import com.trendist.setting.entity.Setting;
import com.trendist.setting.repository.SettingRepository;
import com.trendist.setting.support.SettingKeys;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.TitleRepository;
import com.trendist.user.entity.User;
import com.trendist.user.entity.UserState;
import com.trendist.user.repository.UserRepository;
import com.trendist.user.repository.UserStateRepository;
import java.math.BigDecimal;
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
 * 시작할 때마다 초기 데이터를 채운다(11.3).
 * 코드가 이름으로 찾는 테이블은 없는 행만, 운영에서 고치는 테이블(titles, guide_categories, guides)은 비어 있을 때만 넣는다.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CountryRepository countries;
    private final UserStateRepository userStates;
    private final KeywordStateRepository keywordStates;
    private final DataSourceRepository dataSources;
    private final SettingRepository settings;
    private final LevelRepository levels;
    private final TitleRepository titles;
    private final GuideCategoryRepository guideCategories;
    private final GuideRepository guides;
    private final UserRepository users;
    private final CodeTables codeTables;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = 0;
        // ID가 번역 키가 되는 테이블은 순서를 고정한다
        for (String n : List.of(CodeTables.USER_NORMAL, CodeTables.USER_WITHDRAWN)) {
            if (userStates.findByName(n).isEmpty()) { userStates.save(new UserState(n)); added++; }
        }
        for (String n : List.of(CodeTables.KW_ACTIVE, CodeTables.KW_INACTIVE, CodeTables.KW_BANNED)) {
            if (keywordStates.findByName(n).isEmpty()) { keywordStates.save(new KeywordState(n)); added++; }
        }
        record C(String code, String name, String locale, String tz, boolean enabled, int order) {}
        for (C c : List.of(
                new C("KR", "한국", "ko", "Asia/Seoul", true, 1),
                new C("GLOBAL", "글로벌", "en", "UTC", false, 2),
                new C("JP", "일본", "ja", "Asia/Tokyo", false, 3),
                new C("IN", "인도", "hi", "Asia/Kolkata", false, 4),
                new C("CN", "중국", "zh-CN", "Asia/Shanghai", false, 5))) {
            if (countries.findByCode(c.code()).isEmpty()) {
                countries.save(new Country(c.code(), c.name(), c.locale(), c.tz(), c.enabled(), c.order()));
                added++;
            }
        }
        for (var e : List.of(
                new Object[]{DataSource.NAVER_DATALAB, "2"},
                new Object[]{DataSource.BQ_TRENDS_RISING, "2"},
                new Object[]{DataSource.GOOGLE, "2"},
                new Object[]{DataSource.INTERNAL, "1"})) {
            String name = (String) e[0];
            if (dataSources.findByName(name).isEmpty()) {
                dataSources.save(new DataSource(name, new BigDecimal((String) e[1])));
                added++;
            }
        }
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            if (settings.findByName(d.name()).isEmpty()) {
                String type = d.value().contains(".") ? "DECIMAL" : "INT";
                settings.save(new Setting(d.name(), d.value(), type, BigDecimal.valueOf(d.min()), BigDecimal.valueOf(d.max()), d.description()));
                added++;
            }
        }
        if (levels.count() < 100) {
            var existing = levels.findAllByOrderByLevelAsc().stream().map(Level::getLevel).toList();
            for (int lv = 1; lv <= 100; lv++) {
                if (!existing.contains(lv)) { levels.save(new Level(lv, defaultRequiredExp(lv))); added++; }
            }
        }
        if (titles.count() == 0) {
            int order = 1;
            for (String[] t : TITLES) {
                titles.save(new Title(t[0], t[1], t[2], t[3], order++));
                added++;
            }
        }
        codeTables.clear();
        added += seedGuides();
        log.info("seed done, {} rows added", added);
    }

    /** 가이드 카테고리와 글(4.17). 운영에서 고치는 데이터라 각 테이블이 비어 있을 때만 넣는다. */
    private int seedGuides() {
        int added = 0;
        if (guideCategories.count() == 0) {
            int order = 1;
            for (GuideSeed.Category c : GuideSeed.CATEGORIES) {
                guideCategories.save(new GuideCategory(c.code(), c.name(), c.displayType(), order++));
                added++;
            }
        }
        if (guides.count() == 0) {
            User author = operator();
            for (GuideSeed.Category c : GuideSeed.CATEGORIES) {
                GuideCategory category = guideCategories.findByCodeAndEnabledTrue(c.code()).orElse(null);
                if (category == null) continue; // 관리자가 지우거나 끈 카테고리
                int order = 1;
                for (GuideSeed.Article a : c.articles()) {
                    guides.save(new Guide(category, author, a.title(), a.body(), order++));
                    added++;
                }
            }
        }
        return added;
    }

    /** 공지·가이드 작성자로 쓰는 운영자 계정. 없으면 만든다. */
    private User operator() {
        return users.findFirstByRoleOrderByIdAsc(User.ROLE_ADMIN)
                .orElseGet(() -> users.save(User.operator(userStates.findByName(CodeTables.USER_NORMAL).orElseThrow())));
    }

    /** 4.9의 기본 곡선: 2~10레벨은 표, 11~100레벨은 100 × (레벨/10)². */
    static long defaultRequiredExp(int level) {
        long[] early = {0, 0, 1, 3, 8, 13, 22, 33, 50, 72, 100};
        if (level <= 10) return early[level];
        return Math.round(100 * Math.pow(level / 10.0, 2));
    }

    private static final String[][] TITLES = {
            {"트렌드 워처", "처음으로 키워드를 +했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":1}"},
            {"트렌드 수집가", "키워드를 100번 +했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":100}"},
            {"트렌드 사냥꾼", "키워드를 500번 +했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":500}"},
            {"트렌드 장인", "키워드를 2,000번 +했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":2000}"},
            {"트렌드 전설", "키워드를 10,000번 +했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":10000}"},
            {"완전체", "아이콘이 다 자랐어요(10레벨)", "METRIC", "{\"metric\":\"level\",\"gte\":10}"},
            {"스타일리스트", "꾸미기가 열렸어요(11레벨)", "METRIC", "{\"metric\":\"level\",\"gte\":11}"},
            {"베테랑", "30레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":30}"},
            {"마스터", "50레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":50}"},
            {"레전드", "100레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":100}"},
            {"멀티태스커", "키워드를 동시에 10개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":10}"},
            {"키워드 부자", "키워드를 동시에 30개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":30}"},
            {"키워드 재벌", "키워드를 동시에 50개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":50}"},
            {"떠오르는 별", "현재 유행도가 500에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":500}"},
            {"큰손", "현재 유행도가 1,000에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":1000}"},
            {"거인", "최대 크기(현재 유행도 2,000)에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":2000}"},
            {"핫스타", "현재 유행도 TOP 10에 들었어요", "RANK", "{\"metric\":\"current_score\",\"within\":10}"},
            {"시상대", "현재 유행도 TOP 3에 들었어요", "RANK", "{\"metric\":\"current_score\",\"within\":3}"},
            {"정상", "현재 유행도 1위에 올랐어요", "RANK", "{\"metric\":\"current_score\",\"within\":1}"},
            {"해설 새싹", "댓글 점수 10점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":10}"},
            {"친절한 해설가", "댓글 점수 100점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":100}"},
            {"해설 대장", "댓글 점수 500점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":500}"},
    };
}
