package com.trendist.setting.support;

import java.util.List;

/** settings 테이블의 이름과 기본값(13.3). 시더가 없는 행만 넣는다(11.3). */
public final class SettingKeys {
    private SettingKeys() {}

    public record Def(String name, String value, double min, double max, String description) {}

    public static final String TREND_FULL_SHARE = "trend_full_share";
    public static final String TREND_MIN_POPULATION = "trend_min_population";
    public static final String TREND_LOG_SOFTNESS = "trend_log_softness";
    public static final String TREND_BASE_RATIO = "trend_base_ratio";
    public static final String TREND_MAX_BASE = "trend_max_base";
    public static final String EXTERNAL_CONFIRM_HEAT = "external_confirm_heat";
    public static final String EXP_SCORE_CAP = "exp_score_cap";
    public static final String PLUS_TTL_HOURS = "plus_ttl_hours";
    public static final String ACTIVATE_HOLDERS = "activate_holders";
    public static final String EXP_PER_PLUS = "exp_per_plus";
    public static final String ATTACK_EXP = "attack_exp";
    public static final String KILL_EXP = "kill_exp";
    public static final String WORLD_SIZE = "world_size";
    public static final String MIN_RADIUS = "min_radius";
    public static final String MAX_DIAMETER = "max_diameter";
    public static final String AREA_GROW_SPEED = "area_grow_speed";
    public static final String AREA_SHRINK_SPEED = "area_shrink_speed";
    public static final String OFFLINE_KEEP_SECONDS = "offline_keep_seconds";
    public static final String SCORE_CAP = "score_cap";
    public static final String MAX_SPEED = "max_speed";
    public static final String ZOOM_FACTOR = "zoom_factor";
    public static final String BIG_RATIO = "collision_big_ratio";
    public static final String KNOCKBACK_CAP = "collision_knockback_cap";
    public static final String KNOCKBACK_SECONDS = "collision_knockback_seconds";
    public static final String KNOCKBACK_DISTANCE = "collision_knockback_distance";
    public static final String LOSE_PROTECT_SECONDS = "lose_protect_seconds";
    public static final String NICKNAME_CHANGE_MINUTES = "nickname_change_minutes";
    public static final String ORB_MAX = "orb_max";
    public static final String ORB_SPAWN_SECONDS = "orb_spawn_seconds";
    public static final String ORB_DENSITY = "orb_density";
    public static final String BOT_PLAYERS = "bot_players";
    public static final String BOT_MIN_PLAYERS = "bot_min_players";
    public static final String BOT_CAP_RATIO = "bot_cap_ratio";
    public static final String ORB_SPAWN_PERCENT = "orb_spawn_percent";
    public static final String ORB_ACTIVE_PERCENT = "orb_active_percent";
    public static final String RANK_TITLE_MIN_PLAYERS = "rank_title_min_players";
    public static final String EXTERNAL_HOLD_HOURS = "external_hold_hours";
    public static final String SAFE_CENTER_RATIO = "safe_center_ratio";
    public static final String SAFE_CENTER_MIN = "safe_center_min";
    public static final String SAFE_ZONE_RATIO = "safe_zone_ratio";
    public static final String SAFE_ZONE_MIN = "safe_zone_min";
    public static final String SAFE_ZONE_DENSITY = "safe_zone_density";
    public static final String SAFE_ZONE_MAX = "safe_zone_max";
    public static final String SAFE_ZONE_SECONDS = "safe_zone_seconds";
    public static final String CUSTOMIZE_UNLOCK_LEVEL = "customize_unlock_level";
    public static final String ART_CHANGE_MINUTES = "art_change_minutes";
    public static final String DASH_SPEED_MULTIPLIER = "dash_speed_multiplier";
    public static final String DASH_SECONDS = "dash_seconds";
    public static final String SHOCKWAVE_SPEED_MULTIPLIER = "shockwave_speed_multiplier";
    public static final String SHOCKWAVE_RANGE_RATIO = "shockwave_range_ratio";
    public static final String SHOCKWAVE_KNOCKBACK = "shockwave_knockback";
    public static final String HOOK_RANGE_RATIO = "hook_range_ratio";
    public static final String HOOK_SECONDS = "hook_seconds";
    public static final String HOOK_PULL_SECONDS = "hook_pull_seconds";
    public static final String SKILL_SHIELD_SECONDS = "skill_shield_seconds";
    public static final String STEALTH_SECONDS = "stealth_seconds";
    public static final String COMBO_FLAME_AT = "combo_flame_at";
    public static final String COMBO_WIPE_ORB_PERCENT = "combo_wipe_orb_percent";
    public static final String AI_COMMENT_ENABLED = "ai_comment_enabled";
    public static final String AI_COMMENT_BATCH_SIZE = "ai_comment_batch_size";
    public static final String AI_COMMENT_DAILY_MAX = "ai_comment_daily_max";
    public static final String AI_COMMENT_GROUNDING = "ai_comment_grounding";
    public static final String COMBO_BOT_COUNTS = "combo_bot_counts";
    public static final String COMBO_MAX_SECONDS = "combo_max_seconds";

    public static final List<Def> DEFAULTS = List.of(
            // 트렌드 값(4.8): 가진 비율 s = 가진 사람 / max(활동 인원, 최소 인원). s가 최고점 비율이면 최대 기준값
            new Def(TREND_FULL_SHARE, "0.5", 0.01, 1, "최고점 비율: 활동 인원 중 이 비율이 가지면 최대 기준값(그 이상은 같음)"),
            new Def(TREND_MIN_POPULATION, "20", 1, 10000, "최소 인원: 활동 인원(키워드를 가진 플레이어)이 이보다 적으면 이 수로 계산"),
            new Def(TREND_LOG_SOFTNESS, "0.3", 0.0001, 100000, "로그 완만도: 작을수록 처음 몇 명에서 크게 오르고, 클수록 비율에 비례"),
            new Def(TREND_BASE_RATIO, "3", 0, 100, "기본 비율: 가진 사람이 없는 🔥 키워드의 점수"),
            new Def(TREND_MAX_BASE, "50", 1, 50, "최대 기준값(× 최대 가중치 2 ≤ 100)"),
            new Def(EXTERNAL_CONFIRM_HEAT, "10", 0, 100, "외부 확인 관심도"),
            new Def(EXP_SCORE_CAP, "0", 0, 1000, "EXP 점수 반영 상한(기본 0 = 레벨·EXP는 원 크기와 싸움 판정에 관여하지 않고 점수는 키워드로만 정해진다)"),
            new Def(PLUS_TTL_HOURS, "24", 1, 168, "+ 삭제 시간"),
            new Def(ACTIVATE_HOLDERS, "3", 1, 1000, "🔥활성 전환 인원"),
            new Def(EXP_PER_PLUS, "1", 0, 100, "+ 한 번의 EXP"),
            new Def(ATTACK_EXP, "1", 0, 100, "공격에 성공할 때마다 얻는 EXP(키워드를 빼앗거나 잃게 한 것마다, 봇이 상대여도, 불꽃으로 모두 잃게 해도 한 번)"),
            new Def(KILL_EXP, "5", 0, 1000, "상대를 죽였을 때 얻는 EXP(키워드가 없는 사람·봇을 쓰러뜨렸을 때)"),
            new Def(WORLD_SIZE, "48000", 1000, 50000, "기준 맵 한 변"),
            new Def(MIN_RADIUS, "16", 4, 100, "최소 반지름"),
            new Def(OFFLINE_KEEP_SECONDS, "60", 0, 3600, "연결이 끊겨도 원이 아레나에 남아 있는 시간: 끊긴 자리에 그대로 남아 공격받거나 방어하는 등 남아 있는 사람처럼 동작하다가, 이 시간이 지나면 아레나에서 뺀다(0이면 끊기는 순간 뺀다). 그 뒤 접속하면 가운데 세이프존에서 시작한다"),
            new Def(AREA_GROW_SPEED, "300", 0, 10000, "활동 영역이 커지는 속도: 한 변이 1초에 이만큼(px)씩만 커진다(0이면 바로 커짐). 사람·봇이 들어올 때 맵 벽이 갑자기 튀지 않고 부드럽게 움직이게 한다"),
            new Def(AREA_SHRINK_SPEED, "60", 0, 10000, "활동 영역이 줄어드는 속도: 한 변이 1초에 이만큼(px)씩만 줄어든다(0이면 바로 줄어듦). 사람이 한꺼번에 나가거나 서버를 다시 켜도 원이 가장자리로 한꺼번에 밀리지 않게 한다"),
            new Def(MAX_DIAMETER, "600", 40, 5000, "최대 지름(고정값. 맵 크기와 관계없다)"),
            new Def(SCORE_CAP, "2000", 1, 1000000, "크기 상한 점수(S_cap)"),
            new Def(MAX_SPEED, "480", 50, 1000, "최대 속도(모든 플레이어 동일)"),
            new Def(ZOOM_FACTOR, "280", 1, 10000, "줌 계수"),
            new Def(BIG_RATIO, "1.15", 1, 5, "큰 쪽 판정 비율"),
            new Def(KNOCKBACK_CAP, "3", 1, 10, "튕김 세기 상한. 작은 쪽은 크기 비율만큼 더 멀리 튕긴다"),
            new Def(KNOCKBACK_SECONDS, "0.5", 0.05, 5, "튕김이 멈출 때까지 걸리는 시간. 처음에 빠르고 점점 느려진다"),
            // 튕기는 거리 = 최대 속도(px/s) × 이 값 × 세기. 1이면 최대 속도로 1초 동안 가는 거리만큼 튕긴다
            new Def(KNOCKBACK_DISTANCE, "0.6", 0, 5, "튕기는 거리: 최대 속도로 몇 초 동안 가는 거리만큼인지(같은 크기끼리 기준)"),
            new Def(LOSE_PROTECT_SECONDS, "3", 0, 600, "키워드를 잃은 뒤 무적 시간. 모두의 화면에 카운트다운이 보인다"),
            new Def(NICKNAME_CHANGE_MINUTES, "5", 0, 10080, "닉네임 변경 간격(나라별)"),
            // ? 구슬 목표 개수 = 지금 활동 영역 넓이(100만 px² 단위) × 밀도. 맵이 넓어지면 같은 비율로 늘어난다(4.20).
            new Def(ORB_DENSITY, "2.2", 0, 50, "? 구슬 밀도: 1,000×1,000px 넓이마다 떠 있는 개수(0이면 끔)"),
            new Def(ORB_MAX, "300", 0, 5000, "방에 동시에 떠 있는 ? 구슬 수 상한(0이면 끔). 밀도로 정한 목표가 이보다 크면 이 값까지만"),
            new Def(ORB_SPAWN_SECONDS, "5", 0.5, 600, "? 구슬이 생기는 간격"),
            new Def(ORB_SPAWN_PERCENT, "5", 1, 100, "? 구슬이 간격마다 목표 개수의 몇 %씩 생기는지(최소 1개). 5%면 빈 아레나가 약 20번의 간격에 다 찬다"),
            // NPC 봇(4.21): 사람 + 봇이 이 수가 되도록 채운다. 봇은 키워드 없이 방 메모리의 점수만 가진다
            new Def(BOT_PLAYERS, "10", 0, 200, "방 인원(사람 + 봇)이 이 수보다 적으면 봇으로 채운다"),
            // 사람이 많아도 봇이 하나도 없으면 봇 이기기(보상·칭호)를 할 수 없으므로 최소 인원을 둔다. 봇을 아예 없애려면 두 값 모두 0
            new Def(BOT_MIN_PLAYERS, "1", 0, 200, "봇 최소 인원: 사람이 있으면 방 인원과 관계없이 봇을 이 수 이상 둔다(0이면 사람이 많을 때 봇 없음)"),
            new Def(BOT_CAP_RATIO, "1.0", 0.1, 10, "봇 유행도 상한: 그 방에 접속 중인 사람 유행도 평균의 이 배수(1이면 평균)"),
            new Def(RANK_TITLE_MIN_PLAYERS, "10", 1, 1000, "순위 칭호(핫스타·시상대·정상) 최소 접속 인원: 지금 그 나라에 접속 중인 사람이 이 인원 이상일 때만 순위 칭호를 준다"),
            new Def(ORB_ACTIVE_PERCENT, "60", 0, 100, "? 구슬에서 🔥활성 키워드를 얻을 확률(%). 나머지는 가진 키워드 하나를 잃음(🔥·💤 무관, 없으면 꽝)"),
            // 세이프존(4.22): 안에서는 부딪히지 않고(튕김·키워드 잃기 없음) 겹쳐 있을 수 있다
            new Def(SAFE_CENTER_RATIO, "0.09", 0.01, 0.5, "가운데 고정 세이프존 한 변: 활동 영역 한 변의 이 비율"),
            new Def(SAFE_CENTER_MIN, "1800", 100, 5000, "가운데 고정 세이프존 한 변 최소"),
            new Def(SAFE_ZONE_RATIO, "0.025", 0.005, 0.2, "무작위 세이프존 한 변: 활동 영역 한 변의 이 비율"),
            new Def(SAFE_ZONE_MIN, "1200", 100, 3000, "무작위 세이프존 한 변 최소"),
            new Def(SAFE_ZONE_DENSITY, "6", 0, 100, "무작위 세이프존 수: 10,000×10,000px 넓이마다 이 개수(0이면 끔)"),
            new Def(SAFE_ZONE_MAX, "80", 0, 300, "무작위 세이프존 동시 개수 상한"),
            new Def(SAFE_ZONE_SECONDS, "300", 10, 3600, "무작위 세이프존 유지 시간"),
            new Def(CUSTOMIZE_UNLOCK_LEVEL, "10", 1, 100, "꾸미기 해금 레벨: 이 레벨부터 원의 바탕색·테두리를 바꾸고 픽셀 그림을 그릴 수 있다"),
            new Def(ART_CHANGE_MINUTES, "1", 0, 1440, "픽셀 그림 변경 간격: 그림을 저장한 뒤 이만큼 지나야 다시 바꿀 수 있다"),
            new Def(AI_COMMENT_ENABLED, "1", 0, 1, "AI 한 줄 설명: 처음 🔥가 된 키워드에 제미나이로 설명을 만들지(1이면 만듦, 0이면 멈춤). API 키가 없으면 어차피 만들지 않는다"),
            new Def(AI_COMMENT_BATCH_SIZE, "10", 1, 30, "AI 한 줄 설명을 한 번의 요청에 몇 개씩 묶어 보낼지(1분마다 한 번 요청). 많을수록 요청 수·토큰·시간이 줄지만 한 번 실패하면 같이 실패한다"),
            new Def(AI_COMMENT_DAILY_MAX, "400", 1, 5000, "AI 한 줄 설명 자동 요청의 하루 최대 횟수(요청 한 번에 여러 키워드를 묶음). 무료 한도 일일 요청 수보다 낮게. 관리자가 직접 누르는 재요청은 세지만 막지 않는다"),
            new Def(AI_COMMENT_GROUNDING, "0", 0, 1, "AI 한 줄 설명에 구글 검색 확인(그라운딩)을 쓸지(1이면 사용). 무료 등급은 검색 한도가 없어 429로 막히므로 결제를 연결한 뒤에 켠다. 끄면 최신 이슈는 모른다고 답할 수 있다"),
            new Def(DASH_SPEED_MULTIPLIER, "2", 1, 5, "대시 속도: 대시 중 최대 속도가 평소의 몇 배인지(1이면 대시가 효과 없음). 서버와 클라이언트 예측이 같은 값을 쓴다"),
            new Def(DASH_SECONDS, "3", 0.5, 30, "대시가 유지되는 시간"),
            // 스킬(충격파·갈고리·방패·은신·먹물): 테스트하며 조절할 값이라 모두 설정으로 둔다. 쓰는 키는 클라이언트(GameScreen)가 정한다
            new Def(SHOCKWAVE_SPEED_MULTIPLIER, "4", 0.1, 20, "충격파가 퍼지는 속도: 최대 속도의 몇 배인지(기본 4 = 대시 속도(기본 최대 속도의 2배)의 2배)"),
            new Def(SHOCKWAVE_RANGE_RATIO, "5", 1, 20, "충격파 범위: 쓴 순간 내 원 반지름의 몇 배까지 퍼지는지(시작은 내 원 크기, 중심은 쓴 자리)"),
            new Def(SHOCKWAVE_KNOCKBACK, "1.5", 0, 10, "충격파에 맞은 원이 밀려나는 세기(튕김 세기와 같은 단위. 1이면 같은 크기끼리 부딪힐 때만큼)"),
            new Def(HOOK_RANGE_RATIO, "5", 1, 20, "갈고리 사거리: 내 원 중심에서 내 원 반지름의 몇 배까지 닿는지"),
            new Def(HOOK_SECONDS, "0.15", 0.05, 5, "갈고리가 끝까지 날아가는 데 걸리는 시간(빗나가면 같은 시간에 걸쳐 돌아온다. 상대가 피할 수 있을 만큼은 걸리게)"),
            new Def(HOOK_PULL_SECONDS, "0.3", 0.05, 5, "갈고리에 맞은 원이 내게 끌려오는 시간(끌려오는 동안 스킬 사용·이동 불가, 다른 원과의 충돌 무효)"),
            new Def(SKILL_SHIELD_SECONDS, "3", 0.5, 60, "방패 스킬이 유지되는 시간(키워드를 잃은 뒤 무적과 같이 다른 원과 충돌하지 않는다)"),
            new Def(STEALTH_SECONDS, "5", 0.5, 60, "은신(유령)이 유지되는 시간(남에게는 원·이름·테두리·효과가 모두 배경색처럼 안 보이고 미니맵에서도 사라진다. 나에게는 반투명으로 보인다. 충격파·갈고리를 쓰면 풀린다)"),
            new Def(COMBO_FLAME_AT, "5", 1, 50, "콤보 불꽃: 키워드를 이만큼 연속으로 빼앗으면 원 둘레에 불꽃이 타오른다(한 번이라도 잃으면 꺼짐)"),
            new Def(COMBO_WIPE_ORB_PERCENT, "50", 0, 100, "불꽃인 사람에게 부딪혀 키워드를 모두 잃었을 때, 잃은 🔥활성 키워드 개수의 몇 %(올림)만큼 ! 구슬이 그 자리에 떨어지는지(0이면 안 떨어짐). ! 구슬은 먹으면 항상 키워드를 얻는다"),
            new Def(COMBO_MAX_SECONDS, "300", 0, 86400, "콤보 유지시간: 마지막으로 콤보가 오른 때부터 이 시간이 지나면 연속 횟수와 불꽃이 사라진다. 콤보가 추가될 때마다 다시 시작한다(기본 300초 = 5분, 0이면 제한 없음)"),
            new Def(COMBO_BOT_COUNTS, "1", 0, 1, "봇을 이겨 키워드를 얻은 것도 콤보에 넣을지(1이면 넣음, 0이면 사람에게서 빼앗은 것만)"),
            new Def(EXTERNAL_HOLD_HOURS, "168", 1, 2160, "목록형 외부 소스(google)에 오른 키워드의 활성 유지 시간. 목록에 보일 때마다 지금부터 다시 센다"));

    // ---------------------------------------------------------------- 관리자 화면 그룹(13.3)

    /** 관리자 설정 화면의 그룹 순서. 새 설정을 만들면 아래 GROUP_OF에서 그룹을 정한다(없으면 "기타"). */
    public static final List<String> GROUP_ORDER = List.of("트렌드·키워드", "성장·경험치·칭호", "맵·크기", "이동·충돌", "보호·접속",
            "세이프존", "? 구슬", "NPC 봇", "대시", "스킬", "콤보·불꽃", "꾸미기·계정", "AI 설명", "기타");

    private static final java.util.Map<String, String> GROUP_OF = new java.util.HashMap<>();
    private static void group(String group, String... names) { for (String n : names) GROUP_OF.put(n, group); }
    static {
        group("트렌드·키워드", TREND_FULL_SHARE, TREND_MIN_POPULATION, TREND_LOG_SOFTNESS, TREND_BASE_RATIO, TREND_MAX_BASE,
                EXTERNAL_CONFIRM_HEAT, EXTERNAL_HOLD_HOURS, PLUS_TTL_HOURS, ACTIVATE_HOLDERS);
        group("성장·경험치·칭호", EXP_SCORE_CAP, EXP_PER_PLUS, ATTACK_EXP, KILL_EXP, RANK_TITLE_MIN_PLAYERS);
        group("맵·크기", WORLD_SIZE, MIN_RADIUS, MAX_DIAMETER, SCORE_CAP, ZOOM_FACTOR, AREA_GROW_SPEED, AREA_SHRINK_SPEED);
        group("이동·충돌", MAX_SPEED, BIG_RATIO, KNOCKBACK_CAP, KNOCKBACK_SECONDS, KNOCKBACK_DISTANCE);
        group("보호·접속", LOSE_PROTECT_SECONDS, OFFLINE_KEEP_SECONDS);
        group("세이프존", SAFE_CENTER_RATIO, SAFE_CENTER_MIN, SAFE_ZONE_RATIO, SAFE_ZONE_MIN, SAFE_ZONE_DENSITY, SAFE_ZONE_MAX, SAFE_ZONE_SECONDS);
        group("? 구슬", ORB_DENSITY, ORB_MAX, ORB_SPAWN_SECONDS, ORB_SPAWN_PERCENT, ORB_ACTIVE_PERCENT);
        group("NPC 봇", BOT_PLAYERS, BOT_MIN_PLAYERS, BOT_CAP_RATIO);
        group("대시", DASH_SPEED_MULTIPLIER, DASH_SECONDS);
        group("스킬", SHOCKWAVE_SPEED_MULTIPLIER, SHOCKWAVE_RANGE_RATIO, SHOCKWAVE_KNOCKBACK,
                HOOK_RANGE_RATIO, HOOK_SECONDS, HOOK_PULL_SECONDS,
                SKILL_SHIELD_SECONDS, STEALTH_SECONDS);
        group("콤보·불꽃", COMBO_FLAME_AT, COMBO_WIPE_ORB_PERCENT, COMBO_MAX_SECONDS, COMBO_BOT_COUNTS);
        group("꾸미기·계정", CUSTOMIZE_UNLOCK_LEVEL, ART_CHANGE_MINUTES, NICKNAME_CHANGE_MINUTES);
        group("AI 설명", AI_COMMENT_ENABLED, AI_COMMENT_BATCH_SIZE, AI_COMMENT_DAILY_MAX, AI_COMMENT_GROUNDING);
    }

    /** 설정이 속한 그룹(관리자 화면에서 묶어 보여 준다). 그룹을 정하지 않은 설정은 "기타". */
    public static String groupOf(String name) { return GROUP_OF.getOrDefault(name, "기타"); }

    // ---------------------------------------------------------------- 단위(13.3)

    /** settings.unit 칸(최대 길이). 관리자 화면에서 값 입력칸 뒤에 붙여 보여 준다. */
    public static final int UNIT_MAX_LENGTH = 20;

    private static final java.util.Map<String, String> UNIT_OF = new java.util.HashMap<>();
    private static void unit(String unit, String... names) { for (String n : names) UNIT_OF.put(n, unit); }
    static {
        unit("초", OFFLINE_KEEP_SECONDS, KNOCKBACK_SECONDS, KNOCKBACK_DISTANCE, LOSE_PROTECT_SECONDS,
                ORB_SPAWN_SECONDS, SAFE_ZONE_SECONDS, DASH_SECONDS, COMBO_MAX_SECONDS,
                HOOK_SECONDS, HOOK_PULL_SECONDS, SKILL_SHIELD_SECONDS, STEALTH_SECONDS);
        unit("분", NICKNAME_CHANGE_MINUTES, ART_CHANGE_MINUTES);
        unit("시간", PLUS_TTL_HOURS, EXTERNAL_HOLD_HOURS);
        unit("px", WORLD_SIZE, MIN_RADIUS, MAX_DIAMETER, SAFE_CENTER_MIN, SAFE_ZONE_MIN);
        unit("px/s", MAX_SPEED, AREA_GROW_SPEED, AREA_SHRINK_SPEED);
        unit("배", BIG_RATIO, KNOCKBACK_CAP, BOT_CAP_RATIO, DASH_SPEED_MULTIPLIER, SHOCKWAVE_SPEED_MULTIPLIER, SHOCKWAVE_RANGE_RATIO,
                HOOK_RANGE_RATIO, SHOCKWAVE_KNOCKBACK);
        unit("%", ORB_SPAWN_PERCENT, ORB_ACTIVE_PERCENT, COMBO_WIPE_ORB_PERCENT);
        unit("명", TREND_MIN_POPULATION, ACTIVATE_HOLDERS, BOT_PLAYERS, BOT_MIN_PLAYERS, RANK_TITLE_MIN_PLAYERS);
        unit("개", ORB_MAX, SAFE_ZONE_MAX, AI_COMMENT_BATCH_SIZE);
        unit("개/1000×1000px", ORB_DENSITY);
        unit("개/10000×10000px", SAFE_ZONE_DENSITY);
        unit("점", TREND_BASE_RATIO, TREND_MAX_BASE, EXP_SCORE_CAP, SCORE_CAP);
        unit("EXP", EXP_PER_PLUS, ATTACK_EXP, KILL_EXP);
        unit("레벨", CUSTOMIZE_UNLOCK_LEVEL);
        unit("콤보", COMBO_FLAME_AT);
        unit("회", AI_COMMENT_DAILY_MAX);
    }

    /** 설정 값 뒤에 붙일 단위(예: "초", "px"). 비율·배수 같은 단위 없는 값과 켜기/끄기(0·1) 값은 null. */
    public static String unitOf(String name) { return UNIT_OF.get(name); }
}
