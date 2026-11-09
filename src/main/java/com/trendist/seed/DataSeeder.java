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
import com.trendist.report.entity.ReportType;
import com.trendist.report.repository.ReportTypeRepository;
import com.trendist.setting.entity.Setting;
import com.trendist.setting.entity.SettingGroup;
import com.trendist.setting.repository.SettingGroupRepository;
import com.trendist.setting.repository.SettingRepository;
import com.trendist.setting.support.SettingKeys;
import com.trendist.effect.entity.Effect;
import com.trendist.effect.entity.EffectType;
import com.trendist.effect.repository.EffectRepository;
import com.trendist.effect.repository.EffectTypeRepository;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.TitleRepository;
import com.trendist.user.entity.User;
import com.trendist.user.entity.SanctionType;
import com.trendist.user.repository.SanctionTypeRepository;
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
    private final ReportTypeRepository reportTypes;
    private final SanctionTypeRepository sanctionTypes;
    private final DataSourceRepository dataSources;
    private final SettingRepository settings;
    private final SettingGroupRepository settingGroups;
    private final LevelRepository levels;
    private final TitleRepository titles;
    private final com.trendist.skill.repository.SkillRepository skills;
    private final EffectRepository effects;
    private final EffectTypeRepository effectTypes;
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
        for (String n : ReportType.ALL) {
            if (reportTypes.findByName(n).isEmpty()) { reportTypes.save(new ReportType(n)); added++; }
        }
        for (String n : SanctionType.ALL) {
            if (sanctionTypes.findByName(n).isEmpty()) { sanctionTypes.save(new SanctionType(n)); added++; }
        }
        record C(String code, String name, String locale, String tz, boolean enabled, int order) {}
        for (C c : List.of(
                new C("KR", "한국", "ko", "Asia/Seoul", true, 1),
                new C("GLOBAL", "글로벌", "en", "UTC", true, 2),
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
        // 더 이상 쓰지 않는 설정(콤보 궤도 위성 4.25, 지름 비율 4.2, 최소 반지름 → `min_diameter`로 바뀜 4.2)은 DB에서 지운다. 관리자 화면에 옛 설정으로 계속 남지 않게 한다
        for (String old : List.of("combo_orbit_at", "orbit_interval_seconds", "orbit_max", "max_diameter_ratio", "leaderboard_size", "min_radius")) {
            settings.findByName(old).ifPresent(settings::delete);
        }
        // 설정 그룹(13.3): 이름과 순서는 코드(SettingKeys.GROUP_ORDER)가 정한다. 설정이 외래 키로 가리키므로 설정보다 먼저 만든다
        java.util.Map<String, SettingGroup> groupByName = new java.util.HashMap<>();
        for (int i = 0; i < SettingKeys.GROUP_ORDER.size(); i++) {
            String name = SettingKeys.GROUP_ORDER.get(i);
            SettingGroup g = settingGroups.findByName(name).orElse(null);
            if (g == null) { g = settingGroups.save(new SettingGroup(name, i + 1)); added++; }
            else if (g.getSortOrder() != i + 1) { g.changeSortOrder(i + 1); settingGroups.save(g); }
            groupByName.put(name, g);
        }
        for (SettingKeys.Def d : SettingKeys.DEFAULTS) {
            SettingGroup group = groupByName.get(SettingKeys.groupOf(d.name()));
            String unit = SettingKeys.unitOf(d.name());
            long order = SettingKeys.sortOrderOf(d.name());
            String type = d.value().contains(".") ? "DECIMAL" : "INT";
            Setting s = settings.findByName(d.name()).orElse(null);
            if (s == null) {
                settings.save(new Setting(d.name(), d.value(), type, BigDecimal.valueOf(d.min()), BigDecimal.valueOf(d.max()), d.description(), unit, group, order));
                added++;
            } else {
                boolean changed = false;
                // 설명·값 형식·단위·그룹·화면 순서는 코드가 정한다(관리자 화면에 보이는 뜻이 바뀐 규칙과 맞게, 정수였던 값이 소수가 되는 경우)
                if (!d.description().equals(s.getDescription())) { s.changeDescription(d.description()); changed = true; }
                if (!type.equals(s.getValueType())) { s.changeValueType(type); changed = true; }
                if (!java.util.Objects.equals(unit, s.getUnit())) { s.changeUnit(unit); changed = true; }
                if (!group.getId().equals(s.getGroup().getId())) { s.changeGroup(group); changed = true; }
                if (s.getSortOrder() != order) { s.changeSortOrder(order); changed = true; }
                if (changed) settings.save(s);
            }
        }
        if (levels.count() < 100) {
            var existing = levels.findAllByOrderByLevelAsc().stream().map(Level::getLevel).toList();
            for (int lv = 1; lv <= 100; lv++) {
                if (!existing.contains((long) lv)) { levels.save(new Level(lv, defaultRequiredExp(lv))); added++; }
            }
        }
        // 레벨 표시 옆 아이콘 바탕(4.9): 값을 한 번도 넣지 않은 행(badge_alpha가 null)에만 기본 규칙으로 채운다. 그 뒤 운영에서 고친 값은 건드리지 않는다
        for (Level l : levels.findAllByOrderByLevelAsc()) {
            if (l.getBadgeAlpha() != null) continue;
            String[] b = badgeFor((int) l.getLevel());
            l.applyBadge(b[0], Long.parseLong(b[1]), "RAINBOW".equals(b[2]));
            levels.save(l);
            added++;
        }
        added += seedTitles();
        added += seedSkills();
        // 효과 종류와 효과(4.9, 4.23): 그림이 코드에 있으므로 코드에 있는데 DB에 없는 것만 넣는다(이름·조건·순서는 운영에서 고친다)
        for (String[] t : EFFECT_TYPES) {
            if (effectTypes.findByCode(t[0]).isEmpty()) { effectTypes.save(new EffectType(t[0], t[1])); added++; }
        }
        for (var entry : java.util.Map.of(EffectType.TRAIL, TRAIL_EFFECTS, EffectType.BORDER, BORDER_EFFECTS).entrySet()) {
            EffectType type = effectTypes.findByCode(entry.getKey()).orElseThrow();
            String[][] list = entry.getValue();
            for (int i = 0; i < list.length; i++) {
                if (effects.findByTypeAndCode(entry.getKey(), list[i][0]).isEmpty()) {
                    effects.save(new Effect(type, list[i][0], list[i][1], i + 1));
                    added++;
                }
            }
        }
        codeTables.clear();
        added += seedGuides();
        log.info("seed done, {} rows added", added);
    }

    /**
     * 가이드 카테고리와 글(4.17). 운영에서 고치는 데이터라 각 테이블이 비어 있을 때만 넣는다.
     */
    private int seedGuides() {
        int added = 0;
        if (guideCategories.count() == 0) {
            int order = 1;
            for (GuideSeed.Category c : GuideSeed.CATEGORIES) {
                guideCategories.save(new GuideCategory(c.code(), c.name(), c.displayType(), order++));
                added++;
            }
        }
        if (guides.count() == 0) added += insertGuideArticles();
        added += insertAddedGuideCategories();
        added += insertAddedGuideArticles();
        added += applyGuideFixes();
        return added;
    }

    /** 나중에 더한 카테고리(GuideSeed.ADDED_CATEGORIES): 같은 코드가 없을 때만, afterCode 카테고리 바로 뒤에 끼워 넣는다(뒤 카테고리는 순서를 하나씩 민다) */
    private int insertAddedGuideCategories() {
        int added = 0;
        for (GuideSeed.AddedCategory c : GuideSeed.ADDED_CATEGORIES) {
            if (guideCategories.existsByCode(c.code())) continue;
            List<GuideCategory> all = guideCategories.findAllByOrderBySortOrderAscIdAsc();
            GuideCategory after = all.stream().filter(x -> x.getCode().equals(c.afterCode())).findFirst().orElse(null);
            long at = after != null ? after.getSortOrder() + 1 : all.stream().mapToLong(GuideCategory::getSortOrder).max().orElse(0) + 1;
            for (GuideCategory x : all) if (x.getSortOrder() >= at) x.changeSortOrder(x.getSortOrder() + 1);
            GuideCategory created = new GuideCategory(c.code(), c.name(), c.displayType(), (int) at);
            created.changeNameEn(c.nameEn());
            guideCategories.save(created);
            added++;
        }
        return added;
    }

    private static String norm(String s) { return s == null ? "" : s.replace("\r\n", "\n").trim(); }

    /**
     * 가이드 글 고치기(GuideSeed.FIXES): DB의 본문이 처음 시드 그대로일 때만 새 본문(CATEGORIES·EnglishSeed의 같은 제목 글)으로 바꾼다. 영어도 함께.
     * 관리자가 고친 글은 건드리지 않는다.
     */
    private int applyGuideFixes() {
        int n = 0;
        for (GuideSeed.Fix fix : GuideSeed.FIXES) {
            GuideCategory category = guideCategories.findByCodeAndEnabledTrue(fix.categoryCode()).orElse(null);
            if (category == null) continue;
            int ci = -1;
            for (int i = 0; i < GuideSeed.CATEGORIES.size(); i++) if (GuideSeed.CATEGORIES.get(i).code().equals(fix.categoryCode())) ci = i;
            if (ci < 0) continue;
            GuideSeed.Category koCat = GuideSeed.CATEGORIES.get(ci);
            int ai = -1;
            for (int i = 0; i < koCat.articles().size(); i++) if (koCat.articles().get(i).title().equals(fix.title())) ai = i;
            if (ai < 0) continue;
            String newBody = koCat.articles().get(ai).body();
            String newBodyEn = EnglishSeed.GUIDE.get(ci).articles().get(ai).bodyEn();
            for (com.trendist.guide.entity.Guide g : guides.findByGuideCategoryId(category.getId())) {
                if (!g.getTitle().equals(fix.title()) || !norm(g.getBody()).equals(norm(fix.oldBody()))) continue;
                g.changeBody(newBody);
                g.changeEnglish(g.getTitleEn(), newBodyEn);
                n++;
            }
        }
        return n;
    }

    /** 나중에 더한 글(GuideSeed.ADDED): 같은 카테고리에 같은 제목이 없을 때만 맨 뒤에 한 번 넣는다. 영어도 함께 넣는다 */
    private int insertAddedGuideArticles() {
        int added = 0;
        User author = null;
        for (GuideSeed.Added a : GuideSeed.ADDED) {
            GuideCategory category = guideCategories.findByCodeAndEnabledTrue(a.categoryCode()).orElse(null);
            if (category == null) continue;
            List<com.trendist.guide.entity.Guide> existing = guides.findByGuideCategoryId(category.getId());
            if (existing.stream().anyMatch(g -> g.getTitle().equals(a.title()))) continue;
            if (author == null) author = operator();
            int next = existing.stream().mapToInt(g -> (int) g.getSortOrder()).max().orElse(0) + 1;
            com.trendist.guide.entity.Guide g = new com.trendist.guide.entity.Guide(category, author, a.title(), a.body(), next);
            g.changeEnglish(a.titleEn(), a.bodyEn());
            guides.save(g);
            added++;
        }
        return added;
    }

    private int insertGuideArticles() {
        int added = 0;
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
        return added;
    }

    /** 공지·가이드 작성자로 쓰는 운영자 계정. 없으면 만든다. */
    private User operator() {
        return users.findFirstByRoleOrderByIdAsc(User.ROLE_ADMIN)
                .orElseGet(() -> users.save(User.operator(userStates.findByName(CodeTables.USER_NORMAL).orElseThrow())));
    }

    /** 10레벨 구간별 바탕색(11~19, 20~29, … 90~99레벨) */
    private static final String[] BADGE_BAND_COLORS = {
            "#4f8cff", "#26c6da", "#66bb6a", "#d4e157", "#ffca28", "#ff9800", "#ef5350", "#ec407a", "#ab47bc"};

    /**
     * 레벨 표시 옆 성장 아이콘 바탕의 기본값(4.9) → {색, 진하기(%), 종류(SOLID/RAINBOW)}.
     * 1~10레벨은 기본 거북이 색(null), 11레벨부터는 10레벨 구간마다 색이 바뀌고 일의 자리가 0에서 9로 갈수록 옅은 색에서 진한 색(30~100%),
     * 100레벨은 무지개 그라데이션이다. 시드 값일 뿐이고 실제 값은 levels 테이블에서 관리한다.
     */
    static String[] badgeFor(int level) {
        if (level <= 10) return new String[]{null, "100", "SOLID"};
        if (level >= 100) return new String[]{null, "100", "RAINBOW"};
        String color = BADGE_BAND_COLORS[level / 10 - 1];
        long alpha = 30 + Math.round((level % 10) * 70 / 9.0);
        return new String[]{color, String.valueOf(alpha), "SOLID"};
    }

    /** 4.9의 기본 곡선: 2~10레벨은 표, 11~100레벨은 100 × (레벨/10)². */
    static long defaultRequiredExp(int level) {
        long[] early = {0, 0, 1, 3, 8, 13, 22, 33, 50, 72, 100};
        if (level <= 10) return early[level];
        return Math.round(100 * Math.pow(level / 10.0, 2));
    }

    /**
     * 칭호(4.10). 운영에서 고치는 데이터라 테이블이 비어 있을 때만 넣는다(관리자가 지운 것이 되살아나지 않게).
     * 칭호 ID는 번역 키라서 넣는 순서를 고정한다: TITLES(1~22) 다음에 MORE_TITLES(23~51). 표시 순서(sort_order)는 TITLE_ORDER의 순서 × 10.
     */
    private int seedTitles() {
        int added = 0;
        if (titles.count() == 0) {
            for (String[][] list : List.of(TITLES, MORE_TITLES)) {
                for (String[] t : list) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
            }
        }
        // 죽인 횟수 칭호(4.31): 나중에 추가했으므로 이미 있는 DB에도 한 번 넣는다. 죽인 횟수 칭호가 하나라도 있으면(관리자가 이름을 바꾸거나 지운 경우 포함) 다시 넣지 않는다
        if (titles.findAll().stream().noneMatch(t -> t.getConditionParams().contains("kill_count"))) {
            for (String[] t : KILL_TITLES) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        // 패배 횟수 칭호(4.31)도 같은 방식으로 한 번 넣는다
        if (titles.findAll().stream().noneMatch(t -> t.getConditionParams().contains("defeat_count"))) {
            for (String[] t : DEFEAT_TITLES) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        // 콤보 칭호(4.10, 4.25): 최고 콤보·불꽃 끈 횟수. 같은 방식으로 한 번 넣는다
        if (titles.findAll().stream().noneMatch(t -> t.getConditionParams().contains("max_combo"))) {
            for (String[] t : COMBO_TITLES) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        if (titles.findAll().stream().noneMatch(t -> t.getConditionParams().contains("flame_out_count"))) {
            for (String[] t : FLAME_OUT_TITLES) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        // 나중에 더한 콤보 칭호: 같은 조건(metric·값)의 칭호가 없을 때만 넣는다(이미 만든 DB에도 한 번 들어간다)
        for (String[] t : COMBO_EXTRA_TITLES) {
            if (titles.findAll().stream().noneMatch(x -> x.getName().equals(t[0]) || sameCondition(x.getConditionParams(), t[3]))) { titles.save(new Title(t[0], t[1], t[2], t[3], titleOrder(t[0]))); added++; }
        }
        return added;
    }

    private static final java.util.regex.Pattern METRIC_COND = java.util.regex.Pattern.compile("\"metric\"\\s*:\\s*\"([a-z_]+)\"");
    private static final java.util.regex.Pattern GTE_COND = java.util.regex.Pattern.compile("\"gte\"\\s*:\\s*(\\d+)");

    /** 두 칭호 조건(JSON)이 같은 metric·값인지. DB가 JSON의 공백·키 순서를 바꿔 저장해도 같게 본다. */
    private static boolean sameCondition(String a, String b) {
        var ma = METRIC_COND.matcher(a); var mb = METRIC_COND.matcher(b);
        var ga = GTE_COND.matcher(a); var gb = GTE_COND.matcher(b);
        return ma.find() && mb.find() && ga.find() && gb.find() && ma.group(1).equals(mb.group(1)) && ga.group(1).equals(gb.group(1));
    }

    /**
     * 스킬(4.33): 코드가 있는데 DB에 없는 스킬만 채운다(운영에서 고친 쿨타임·기본 키는 건드리지 않는다). 스킬을 늘릴 때는 여기에 한 줄을 더하거나 DB에 행을 넣고,
     * 동작은 Room.SKILLS와 Room.skill에, 이름·설명은 프론트엔드 번역 파일(skills.<코드>_name·_desc)에 더한다. 기본 단축키는 KeyboardEvent.code(Space, Digit1, KeyQ …).
     * 필드: 코드, 아이콘, 종류, 기본 키, 쿨타임(초), 해금 레벨(1 = 기본 스킬).
     */
    private static final String[][] SKILL_ROWS = {
            {"shield", "🛡", "DEFENSE", "Digit1", "30", "10"},   // 해금 레벨 순서 = 키 순서 = 버튼 순서
            {"hook", "🪝", "ATTACK", "Digit2", "30", "15"},
            {"shockwave", "💥", "ATTACK", "Digit3", "30", "20"},
            {"stealth", "👻", "STEALTH", "Digit4", "30", "25"},
            {"dash", "⚡", "MOVE", "Space", "30", "1"}, // 기본 스킬. 버튼은 스킬 4개 뒤(오른쪽)
    };

    private int seedSkills() {
        int added = 0;
        for (int i = 0; i < SKILL_ROWS.length; i++) {
            String[] r = SKILL_ROWS[i];
            if (skills.findByCode(r[0]).isPresent()) continue;
            skills.save(new com.trendist.skill.entity.Skill(r[0], r[1], r[2], r[3], Double.parseDouble(r[4]), Integer.parseInt(r[5]), (i + 1) * 10L));
            added++;
        }
        return added;
    }

    private static int titleOrder(String name) {
        int i = TITLE_ORDER.indexOf(name);
        return (i < 0 ? TITLE_ORDER.size() : i) * 10 + 10;
    }

    /** 칭호 23~51. ID가 TITLES 뒤로 이 순서대로 붙는다(순서를 바꾸지 않는다) */
    private static final String[][] MORE_TITLES = {
            {"트렌드 탐험가", "키워드를 30번 추가했어요", "METRIC", "{\"metric\":\"add_count\",\"gte\":30}"},
            {"일취월장", "20레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":20}"},
            {"그랜드마스터", "70레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":70}"},
            {"알뜰 수집가", "키워드를 동시에 10개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":10}"},
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
            {"키워드 새싹", "키워드를 동시에 5개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":5}"},
            {"키워드 달인", "키워드를 동시에 40개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":40}"},
            {"키워드 거물", "키워드를 동시에 70개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":70}"},
            {"키워드 제왕", "키워드를 동시에 100개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":100}"},
            {"키워드 신화", "키워드를 동시에 200개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":200}"},
    };

    /** 죽인 횟수 칭호(4.31) 5개 */
    private static final String[][] KILL_TITLES = {
            {"첫 처치", "상대를 처음 쓰러뜨렸어요", "METRIC", "{\"metric\":\"kill_count\",\"gte\":1}"},
            {"해결사", "상대를 10번 쓰러뜨렸어요", "METRIC", "{\"metric\":\"kill_count\",\"gte\":10}"},
            {"저승사자", "상대를 50번 쓰러뜨렸어요", "METRIC", "{\"metric\":\"kill_count\",\"gte\":50}"},
            {"아레나의 지배자", "상대를 200번 쓰러뜨렸어요", "METRIC", "{\"metric\":\"kill_count\",\"gte\":200}"},
            {"최후의 승자", "상대를 1,000번 쓰러뜨렸어요", "METRIC", "{\"metric\":\"kill_count\",\"gte\":1000}"},
    };

    /** 패배 횟수 칭호(4.31) 5개: 지는 것을 비웃지 않고 다시 일어서는 것을 칭찬하는 이름 */
    private static final String[][] DEFEAT_TITLES = {
            {"첫 패배", "처음으로 패배했어요", "METRIC", "{\"metric\":\"defeat_count\",\"gte\":1}"},
            {"칠전팔기", "10번 패배하고도 다시 일어났어요", "METRIC", "{\"metric\":\"defeat_count\",\"gte\":10}"},
            {"백전노장", "50번 패배했지만 계속 도전해요", "METRIC", "{\"metric\":\"defeat_count\",\"gte\":50}"},
            {"불사조", "200번 쓰러지고도 다시 날아올랐어요", "METRIC", "{\"metric\":\"defeat_count\",\"gte\":200}"},
            {"끝없는 도전자", "1,000번 패배해도 멈추지 않았어요", "METRIC", "{\"metric\":\"defeat_count\",\"gte\":1000}"},
    };

    /** 최고 콤보 칭호(4.25) 5개: 연속으로 빼앗을수록 불꽃이 거세진다 */
    private static final String[][] COMBO_TITLES = {
            {"연속 타격", "연속으로 3번 빼앗았어요", "METRIC", "{\"metric\":\"max_combo\",\"gte\":3}"},
            {"불꽃 점화", "연속으로 5번 빼앗아 불꽃을 붙였어요", "METRIC", "{\"metric\":\"max_combo\",\"gte\":5}"},
            {"연쇄 반응", "연속으로 10번 빼앗았어요", "METRIC", "{\"metric\":\"max_combo\",\"gte\":10}"},
            {"폭주 기관차", "연속으로 20번 빼앗았어요", "METRIC", "{\"metric\":\"max_combo\",\"gte\":20}"},
            {"멈추지 않는 불꽃", "연속으로 40번 빼앗았어요", "METRIC", "{\"metric\":\"max_combo\",\"gte\":40}"},
    };

    /** 나중에 더한 최고 콤보 칭호(4.25): 100 콤보 */
    private static final String[][] COMBO_EXTRA_TITLES = {
            {"100 콤보", "연속으로 100번 빼앗았어요", "METRIC", "{\"metric\":\"max_combo\",\"gte\":100}"},
    };

    /** 불꽃 끄기 칭호(4.25) 3개: 불타는 상대를 쓰러뜨린 사람 */
    private static final String[][] FLAME_OUT_TITLES = {
            {"불 끄미", "상대의 불꽃을 처음 꺼뜨렸어요", "METRIC", "{\"metric\":\"flame_out_count\",\"gte\":1}"},
            {"소방관", "상대의 불꽃을 10번 꺼뜨렸어요", "METRIC", "{\"metric\":\"flame_out_count\",\"gte\":10}"},
            {"화재 진압 대장", "상대의 불꽃을 50번 꺼뜨렸어요", "METRIC", "{\"metric\":\"flame_out_count\",\"gte\":50}"},
    };

    /** 칭호함에 보이는 순서: 종류별로 쉬운 것부터 */
    private static final List<String> TITLE_ORDER = List.of(
            "트렌드 워처", "트렌드 탐험가", "트렌드 수집가", "트렌드 사냥꾼", "트렌드 장인", "트렌드 전설",
            "완전체", "스타일리스트", "일취월장", "베테랑", "마스터", "그랜드마스터", "레전드",
            "키워드 새싹", "알뜰 수집가", "멀티태스커", "키워드 부자", "키워드 달인", "키워드 재벌", "키워드 거물", "키워드 제왕", "키워드 신화",
            "떠오르는 별", "큰손", "슈퍼스타", "거인",
            "핫스타", "시상대", "정상",
            "첫 마디", "해설 새싹", "친절한 해설가", "해설 대장",
            "호기심 많은", "탐색가", "정보통",
            "첫 승리", "사냥꾼의 본능", "아레나의 포식자",
            "봇 사냥 입문", "봇 사냥꾼", "기계 파괴자",
            "행운 탐색자", "구슬 수집가", "구슬 마니아",
            "오뚝이", "불굴",
            "첫 처치", "해결사", "저승사자", "아레나의 지배자", "최후의 승자",
            "첫 패배", "칠전팔기", "백전노장", "불사조", "끝없는 도전자",
            "연속 타격", "불꽃 점화", "연쇄 반응", "폭주 기관차", "멈추지 않는 불꽃",
            "100 콤보",
            "불 끄미", "소방관", "화재 진압 대장",
            "새내기", "단골", "터줏대감", "산증인");

    /** 이동 효과(4.23): 코드, 이름, 설명. 처음에는 모두 조건 없이(FREE) 쓸 수 있다. 순서가 고르는 화면의 순서다 */
    /** 효과 종류(4.9, 4.23) */
    private static final String[][] EFFECT_TYPES = {{EffectType.TRAIL, "이동 효과"}, {EffectType.BORDER, "테두리 효과"}};

    /** 이동 효과(4.23). 그림은 프론트엔드 game/trails.ts가 코드값으로 그린다 */
    private static final String[][] TRAIL_EFFECTS = {
            {"afterimage", "잔상"},
            {"sparkle", "별가루"},
            {"flame", "불꽃"},
            {"bubble", "물방울"},
            {"rainbow", "무지개"},
            {"footprint", "발자국"},
            {"petal", "꽃잎"},
            {"heart", "하트"},
            {"note", "음표"},
            {"bolt", "번개"},
            {"smoke", "연기"},
            {"snow", "눈송이"},
            {"pawprint", "고양이·강아지 발바닥"},
            {"birdprint", "새 발자국"},
            {"bearprint", "곰 발자국"},
            {"rabbitprint", "토끼 발자국"},
    };

    /** 테두리 효과(4.9). 그림은 프론트엔드 game/skin.ts가 코드값으로 그린다 */
    private static final String[][] BORDER_EFFECTS = {
            {"glow", "은은한 빛"},
            {"dash", "도는 점선"},
            {"gradient", "그라데이션"},
            {"pulse", "맥박"},
            {"rainbow", "무지개"},
    };

    private static final String[][] TITLES = {
            {"트렌드 워처", "처음으로 키워드를 추가했어요", "METRIC", "{\"metric\":\"add_count\",\"gte\":1}"},
            {"트렌드 수집가", "키워드를 100번 추가했어요", "METRIC", "{\"metric\":\"add_count\",\"gte\":100}"},
            {"트렌드 사냥꾼", "키워드를 500번 추가했어요", "METRIC", "{\"metric\":\"add_count\",\"gte\":500}"},
            {"트렌드 장인", "키워드를 2,000번 추가했어요", "METRIC", "{\"metric\":\"add_count\",\"gte\":2000}"},
            {"트렌드 전설", "키워드를 10,000번 추가했어요", "METRIC", "{\"metric\":\"add_count\",\"gte\":10000}"},
            {"완전체", "아이콘이 다 자랐어요(10레벨)", "METRIC", "{\"metric\":\"level\",\"gte\":10}"},
            {"스타일리스트", "꾸미기가 열렸어요(10레벨)", "METRIC", "{\"metric\":\"level\",\"gte\":10}"},
            {"베테랑", "30레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":30}"},
            {"마스터", "50레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":50}"},
            {"레전드", "100레벨에 도달했어요", "METRIC", "{\"metric\":\"level\",\"gte\":100}"},
            {"멀티태스커", "키워드를 동시에 20개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":20}"},
            {"키워드 부자", "키워드를 동시에 30개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":30}"},
            {"키워드 재벌", "키워드를 동시에 50개 가졌어요", "METRIC", "{\"metric\":\"hold_count\",\"gte\":50}"},
            {"떠오르는 별", "현재 유행도가 500에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":500}"},
            {"큰손", "현재 유행도가 1,000에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":1000}"},
            {"거인", "최대 크기(현재 유행도 2,000)에 도달했어요", "METRIC", "{\"metric\":\"current_score\",\"gte\":2000}"},
            {"핫스타", "현재 유행도 TOP 10에 들었어요(접속자 10명 이상일 때)", "RANK", "{\"metric\":\"current_score\",\"within\":10}"},
            {"시상대", "현재 유행도 TOP 3에 들었어요(접속자 10명 이상일 때)", "RANK", "{\"metric\":\"current_score\",\"within\":3}"},
            {"정상", "현재 유행도 1위에 올랐어요(접속자 10명 이상일 때)", "RANK", "{\"metric\":\"current_score\",\"within\":1}"},
            {"해설 새싹", "댓글 점수 10점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":10}"},
            {"친절한 해설가", "댓글 점수 100점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":100}"},
            {"해설 대장", "댓글 점수 500점을 모았어요", "METRIC", "{\"metric\":\"comment_score\",\"gte\":500}"},
    };
}
