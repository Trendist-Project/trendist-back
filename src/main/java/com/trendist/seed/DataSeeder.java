package com.trendist.seed;

import com.trendist.common.code.CodeTables;
import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.guide.entity.Guide;
import com.trendist.guide.entity.GuideCategory;
import com.trendist.guide.repository.GuideCategoryRepository;
import com.trendist.guide.repository.GuideRepository;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.entity.KeywordState;
import com.trendist.keyword.repository.DataSourceRepository;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.repository.KeywordStateRepository;
import com.trendist.keyword.support.ActivePopulation;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.player.entity.Level;
import com.trendist.player.repository.LevelRepository;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.report.entity.ReportType;
import com.trendist.report.repository.ReportTypeRepository;
import com.trendist.setting.entity.Setting;
import com.trendist.setting.repository.SettingRepository;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import com.trendist.effect.entity.TrailEffect;
import com.trendist.effect.repository.TrailEffectRepository;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.TitleRepository;
import com.trendist.user.entity.User;
import com.trendist.user.entity.UserState;
import com.trendist.user.repository.UserRepository;
import com.trendist.user.repository.UserStateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
    /** 125판에서 없앤 데이터 소스. 예전 DB에 남아 있으면 지운다. */
    private static final List<String> REMOVED_SOURCES = List.of("naver_datalab", "bq_trends_rising");
    /** 쓰지 않게 된 설정. 예전 DB에 남아 있으면 지운다(128판: 기준 인원은 활동 인원으로 바뀜, 4.8). */
    private static final List<String> REMOVED_SETTINGS = List.of("trend_base_population");
    /** 124판까지 외부 소스의 기본 반영값. 이 값 그대로인 행은 1로 바꾼다. */
    private static final BigDecimal OLD_DEFAULT_WEIGHT = new BigDecimal("2");

    private final CountryRepository countries;
    private final UserStateRepository userStates;
    private final KeywordStateRepository keywordStates;
    private final ReportTypeRepository reportTypes;
    private final DataSourceRepository dataSources;
    private final SettingRepository settings;
    private final LevelRepository levels;
    private final TitleRepository titles;
    private final TrailEffectRepository trailEffects;
    private final GuideCategoryRepository guideCategories;
    private final GuideRepository guides;
    private final UserRepository users;
    private final KeywordRepository keywords;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final SettingService settingService;
    private final TrendFormula formula;
    private final ActivePopulation population;
    private final Clock clock;
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
        for (String n : ReportType.ALL) {
            if (reportTypes.findByName(n).isEmpty()) { reportTypes.save(new ReportType(n)); added++; }
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
        // 반영값은 모두 1(10.3)
        for (String name : List.of(DataSource.GOOGLE, DataSource.ADMIN, DataSource.INTERNAL)) {
            if (dataSources.findByName(name).isEmpty()) {
                dataSources.save(new DataSource(name, BigDecimal.ONE));
                added++;
            }
        }
        for (String name : REMOVED_SETTINGS) {
            settings.findByName(name).ifPresent(s -> { settings.delete(s); log.info("설정 삭제: {}", name); });
        }
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            if (settings.findByName(d.name()).isEmpty()) {
                String type = d.value().contains(".") ? "DECIMAL" : "INT";
                settings.save(new Setting(d.name(), d.value(), type, BigDecimal.valueOf(d.min()), BigDecimal.valueOf(d.max()), d.description()));
                added++;
            } else {
                Setting s = settings.findByName(d.name()).get();
                boolean changed = false;
                if (SettingKeys.REPLACED_DEFAULTS.getOrDefault(d.name(), Set.of()).contains(s.getValue())) {
                    log.info("설정 기본값 변경 반영: {} {} → {}", d.name(), s.getValue(), d.value());
                    s.changeValue(d.value());
                    changed = true;
                }
                // 설명과 값 형식은 코드가 정한다(관리자 화면에 보이는 뜻이 바뀐 규칙과 맞게, 정수였던 값이 소수가 되는 경우)
                if (!d.description().equals(s.getDescription())) { s.changeDescription(d.description()); changed = true; }
                String type = d.value().contains(".") ? "DECIMAL" : "INT";
                if (!type.equals(s.getValueType())) { s.changeValueType(type); changed = true; }
                if (changed) settings.save(s);
            }
        }
        if (levels.count() < 100) {
            var existing = levels.findAllByOrderByLevelAsc().stream().map(Level::getLevel).toList();
            for (int lv = 1; lv <= 100; lv++) {
                if (!existing.contains(lv)) { levels.save(new Level(lv, defaultRequiredExp(lv))); added++; }
            }
        }
        added += seedTitles();
        // 이동 효과(4.23): 그림이 코드에 있으므로 코드에 있는데 DB에 없는 효과만 넣는다(이름·조건·순서는 운영에서 고친다)
        for (int i = 0; i < TRAIL_EFFECTS.length; i++) {
            String[] t = TRAIL_EFFECTS[i];
            if (trailEffects.findByCode(t[0]).isEmpty()) { trailEffects.save(new TrailEffect(t[0], t[1], t[2], i + 1)); added++; }
        }
        codeTables.clear();
        migrateDataSources();
        added += seedGuides();
        log.info("seed done, {} rows added", added);
    }

    /**
     * 예전 DB 정리(125판): 쓰지 않는 소스(naver_datalab, bq_trends_rising)를 지우고 예전 기본 반영값 2를 1로 바꾼다.
     * 지운 소스를 반영 소스로 쓰던 키워드는 외부 데이터가 사라진 것과 같이 internal로 내리고 3명 미만이면 💤(4.7).
     * 바뀐 것이 있으면 모든 키워드의 trend_value와 보유자 점수를 다시 계산한다(13.2).
     */
    private void migrateDataSources() {
        boolean changed = false;
        DataSource internal = dataSources.findByName(DataSource.INTERNAL).orElseThrow();
        KeywordState active = keywordStates.findByName(CodeTables.KW_ACTIVE).orElseThrow();
        KeywordState inactive = keywordStates.findByName(CodeTables.KW_INACTIVE).orElseThrow();
        int activateHolders = settingService.getInt(SettingKeys.ACTIVATE_HOLDERS);
        Instant now = Instant.now(clock);
        for (String name : REMOVED_SOURCES) {
            DataSource old = dataSources.findByName(name).orElse(null);
            if (old == null) continue;
            List<Keyword> moved = keywords.findByDataSourceId(old.getId());
            for (Keyword k : moved) {
                k.dropExternal(internal);
                if (k.getKeywordState().getId().equals(active.getId()) && k.getHolderCount() < activateHolders) k.deactivate(inactive, now);
            }
            keywords.flush();
            dataSources.delete(old);
            log.info("데이터 소스 {} 삭제, 키워드 {}개를 internal로", name, moved.size());
            changed = true;
        }
        for (DataSource ds : dataSources.findAll()) {
            if (ds.getWeight().compareTo(OLD_DEFAULT_WEIGHT) == 0) {
                log.info("데이터 소스 반영값 변경: {} {} → 1", ds.getName(), ds.getWeight());
                ds.changeWeight(BigDecimal.ONE);
                changed = true;
            }
        }
        if (!changed) return;
        for (Keyword k : keywords.findAll()) {
            k.setTrendValue(formula.trendValue(k.getKeywordState().getId().equals(active.getId()), k.getHolderCount(),
                    k.getDataSource().getWeight().doubleValue(), population.get(k.getCountry().getId())));
        }
        keywords.flush();
        for (Long pid : players.findCheckTargets()) playerService.recomputeLocked(pid, true);
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

    /**
     * 칭호(4.10). 칭호 ID는 번역 키라서 넣는 순서를 고정한다: 처음 22개(TITLES) 다음에 133판에서 늘린 것(MORE_TITLES).
     * 처음 22개는 테이블이 비어 있을 때만 넣고(관리자가 지운 것이 되살아나지 않게), 늘린 칭호는 같은 이름이 없을 때 넣는다.
     * 표시 순서(sort_order)는 TITLE_ORDER의 순서 × 10. 처음 넣은 순서(1~22) 그대로인 행만 새 순서로 바꾼다.
     */
    private int seedTitles() {
        int added = 0;
        if (titles.count() == 0) {
            for (String[] t : TITLES) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        for (String[] t : MORE_TITLES) {
            if (titles.findByName(t[0]).isEmpty()) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        for (int i = 0; i < TITLES.length; i++) {
            int original = i + 1;
            titles.findByName(TITLES[i][0]).filter(t -> t.getSortOrder() == original)
                    .ifPresent(t -> t.changeSortOrder(titleOrder(t.getName())));
        }
        return added;
    }

    private static int titleOrder(String name) {
        int i = TITLE_ORDER.indexOf(name);
        return (i < 0 ? TITLE_ORDER.size() : i) * 10 + 10;
    }

    /** 133판에서 늘린 칭호. ID가 처음 22개 뒤로 이 순서대로 붙는다(순서를 바꾸지 않는다) */
    private static final String[][] MORE_TITLES = {
            {"트렌드 탐험가", "키워드를 30번 추가했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":30}"},
            {"일취월장", "20레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":20}"},
            {"그랜드마스터", "70레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":70}"},
            {"알뜰 수집가", "키워드를 동시에 5개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":5}"},
            {"슈퍼스타", "현재 유행도가 1,500에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":1500}"},
            {"첫 마디", "댓글 점수 1점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":1}"},
            {"호기심 많은", "키워드를 10번 검색했어요", "METRIC", "{\"metric\":\"search_count\",\"gte\":10}"},
            {"탐색가", "키워드를 100번 검색했어요", "METRIC", "{\"metric\":\"search_count\",\"gte\":100}"},
            {"정보통", "키워드를 1,000번 검색했어요", "METRIC", "{\"metric\":\"search_count\",\"gte\":1000}"},
            {"첫 승리", "다른 플레이어에게서 키워드를 처음 빼앗았어요", "METRIC", "{\"metric\":\"steal_count\",\"gte\":1}"},
            {"사냥꾼의 본능", "다른 플레이어에게서 키워드를 50번 빼앗았어요", "METRIC", "{\"metric\":\"steal_count\",\"gte\":50}"},
            {"아레나의 포식자", "다른 플레이어에게서 키워드를 300번 빼앗았어요", "METRIC", "{\"metric\":\"steal_count\",\"gte\":300}"},
            {"봇 사냥 입문", "봇을 10번 이겼어요", "METRIC", "{\"metric\":\"bot_win_count\",\"gte\":10}"},
            {"봇 사냥꾼", "봇을 100번 이겼어요", "METRIC", "{\"metric\":\"bot_win_count\",\"gte\":100}"},
            {"기계 파괴자", "봇을 500번 이겼어요", "METRIC", "{\"metric\":\"bot_win_count\",\"gte\":500}"},
            {"행운 탐색자", "? 구슬을 10개 먹었어요", "METRIC", "{\"metric\":\"orb_count\",\"gte\":10}"},
            {"구슬 수집가", "? 구슬을 100개 먹었어요", "METRIC", "{\"metric\":\"orb_count\",\"gte\":100}"},
            {"구슬 마니아", "? 구슬을 1,000개 먹었어요", "METRIC", "{\"metric\":\"orb_count\",\"gte\":1000}"},
            {"오뚝이", "키워드를 50번 잃고도 다시 일어났어요", "METRIC", "{\"metric\":\"lose_count\",\"gte\":50}"},
            {"불굴", "키워드를 300번 잃고도 포기하지 않았어요", "METRIC", "{\"metric\":\"lose_count\",\"gte\":300}"},
            {"새내기", "3일 동안 키워드를 모았어요", "METRIC", "{\"metric\":\"active_days\",\"gte\":3}"},
            {"단골", "7일 동안 키워드를 모았어요", "METRIC", "{\"metric\":\"active_days\",\"gte\":7}"},
            {"터줏대감", "30일 동안 키워드를 모았어요", "METRIC", "{\"metric\":\"active_days\",\"gte\":30}"},
            {"산증인", "100일 동안 키워드를 모았어요", "METRIC", "{\"metric\":\"active_days\",\"gte\":100}"},
    };

    /** 칭호함에 보이는 순서: 종류별로 쉬운 것부터 */
    private static final List<String> TITLE_ORDER = List.of(
            "트렌드 워처", "트렌드 탐험가", "트렌드 수집가", "트렌드 사냥꾼", "트렌드 장인", "트렌드 전설",
            "완전체", "스타일리스트", "일취월장", "베테랑", "마스터", "그랜드마스터", "레전드",
            "알뜰 수집가", "멀티태스커", "키워드 부자", "키워드 재벌",
            "떠오르는 별", "큰손", "슈퍼스타", "거인",
            "핫스타", "시상대", "정상",
            "첫 마디", "해설 새싹", "친절한 해설가", "해설 대장",
            "호기심 많은", "탐색가", "정보통",
            "첫 승리", "사냥꾼의 본능", "아레나의 포식자",
            "봇 사냥 입문", "봇 사냥꾼", "기계 파괴자",
            "행운 탐색자", "구슬 수집가", "구슬 마니아",
            "오뚝이", "불굴",
            "새내기", "단골", "터줏대감", "산증인");

    /** 이동 효과(4.23): 코드, 이름, 설명. 처음에는 모두 조건 없이(FREE) 쓸 수 있다. 순서가 고르는 화면의 순서다 */
    private static final String[][] TRAIL_EFFECTS = {
            {"afterimage", "잔상", "지나온 자리에 내 원의 흐릿한 그림자가 남았다 사라져요"},
            {"sparkle", "별가루", "반짝이는 작은 별이 흩날려요"},
            {"flame", "불꽃", "뒤로 불티가 튀며 사그라들어요"},
            {"bubble", "물방울", "동글동글 물방울이 떠오르다 톡 터져요"},
            {"rainbow", "무지개", "지나간 길을 따라 무지개 띠가 그려져요"},
            {"footprint", "발자국", "작은 거북이 발자국이 콕콕 찍혀요"},
            {"petal", "꽃잎", "분홍 꽃잎이 팔랑이며 흩날려요"},
    };

    private static final String[][] TITLES = {
            {"트렌드 워처", "처음으로 키워드를 추가했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":1}"},
            {"트렌드 수집가", "키워드를 100번 추가했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":100}"},
            {"트렌드 사냥꾼", "키워드를 500번 추가했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":500}"},
            {"트렌드 장인", "키워드를 2,000번 추가했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":2000}"},
            {"트렌드 전설", "키워드를 10,000번 추가했어요", "METRIC", "{\"metric\":\"total_plus_count\",\"gte\":10000}"},
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
