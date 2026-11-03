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
    public static final String WORLD_SIZE = "world_size";
    public static final String MIN_RADIUS = "min_radius";
    public static final String MAX_DIAMETER_RATIO = "max_diameter_ratio";
    public static final String AREA_SHRINK_SPEED = "area_shrink_speed";
    public static final String SCORE_CAP = "score_cap";
    public static final String MAX_SPEED = "max_speed";
    public static final String ZOOM_FACTOR = "zoom_factor";
    public static final String BIG_RATIO = "collision_big_ratio";
    public static final String KNOCKBACK_CAP = "collision_knockback_cap";
    public static final String KNOCKBACK_SECONDS = "collision_knockback_seconds";
    public static final String KNOCKBACK_DISTANCE = "collision_knockback_distance";
    public static final String COLLISION_COOLDOWN = "collision_cooldown_seconds";
    public static final String LOSE_PROTECT_SECONDS = "lose_protect_seconds";
    public static final String JOIN_PROTECT_SECONDS = "join_protect_seconds";
    public static final String NICKNAME_CHANGE_MINUTES = "nickname_change_minutes";
    public static final String ORB_MAX = "orb_max";
    public static final String ORB_SPAWN_SECONDS = "orb_spawn_seconds";
    public static final String ORB_DENSITY = "orb_density";
    public static final String BOT_PLAYERS = "bot_players";
    public static final String BOT_MIN_PLAYERS = "bot_min_players";
    public static final String BOT_CAP_RATIO = "bot_cap_ratio";
    public static final String BOT_STEP_MIN = "bot_step_min";
    public static final String BOT_STEP_MAX = "bot_step_max";
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
    public static final String DASH_COOLDOWN_SECONDS = "dash_cooldown_seconds";
    public static final String COMBO_FLAME_AT = "combo_flame_at";
    public static final String COMBO_WIPE_ORB_PERCENT = "combo_wipe_orb_percent";
    public static final String AI_COMMENT_ENABLED = "ai_comment_enabled";
    public static final String AI_COMMENT_BATCH_SIZE = "ai_comment_batch_size";
    public static final String AI_COMMENT_DAILY_MAX = "ai_comment_daily_max";
    public static final String AI_COMMENT_GROUNDING = "ai_comment_grounding";
    public static final String COMBO_BOT_COUNTS = "combo_bot_counts";

    public static final List<Def> DEFAULTS = List.of(
            // 트렌드 값(4.8): 가진 비율 s = 가진 사람 / max(활동 인원, 최소 인원). s가 최고점 비율이면 최대 기준값
            new Def(TREND_FULL_SHARE, "0.5", 0.01, 1, "최고점 비율: 활동 인원 중 이 비율이 가지면 최대 기준값(그 이상은 같음)"),
            new Def(TREND_MIN_POPULATION, "20", 1, 10000, "최소 인원: 활동 인원(키워드를 가진 플레이어)이 이보다 적으면 이 수로 계산"),
            new Def(TREND_LOG_SOFTNESS, "0.3", 0.0001, 100000, "로그 완만도: 작을수록 처음 몇 명에서 크게 오르고, 클수록 비율에 비례"),
            new Def(TREND_BASE_RATIO, "3", 0, 100, "기본 비율: 가진 사람이 없는 🔥 키워드의 점수"),
            new Def(TREND_MAX_BASE, "50", 1, 50, "최대 기준값(× 최대 가중치 2 ≤ 100)"),
            new Def(EXTERNAL_CONFIRM_HEAT, "10", 0, 100, "외부 확인 관심도"),
            new Def(EXP_SCORE_CAP, "100", 0, 1000, "EXP 점수 반영 상한"),
            new Def(PLUS_TTL_HOURS, "24", 1, 168, "+ 삭제 시간(시간)"),
            new Def(ACTIVATE_HOLDERS, "3", 1, 1000, "🔥활성 전환 인원"),
            new Def(EXP_PER_PLUS, "1", 0, 100, "+ 한 번의 EXP"),
            new Def(WORLD_SIZE, "48000", 1000, 50000, "기준 맵 한 변(px)"),
            new Def(MIN_RADIUS, "16", 4, 100, "최소 반지름(px)"),
            new Def(AREA_SHRINK_SPEED, "60", 0, 10000, "활동 영역이 줄어드는 속도: 한 변이 1초에 이만큼(px)씩만 줄어든다(0이면 바로 줄어듦). 사람이 한꺼번에 나가거나 서버를 다시 켜도 원이 가장자리로 한꺼번에 밀리지 않게 한다"),
            new Def(MAX_DIAMETER_RATIO, "0.025", 0.01, 0.20, "최대 지름 비율(기준 맵 한 변 대비)"),
            new Def(SCORE_CAP, "2000", 1, 1000000, "크기 상한 점수(S_cap)"),
            new Def(MAX_SPEED, "480", 50, 1000, "최대 속도(px/s, 모든 플레이어 동일)"),
            new Def(ZOOM_FACTOR, "170", 1, 10000, "줌 계수"),
            new Def(BIG_RATIO, "1.15", 1, 5, "큰 쪽 판정 비율"),
            new Def(KNOCKBACK_CAP, "3", 1, 10, "튕김 세기 상한(배). 작은 쪽은 크기 비율만큼 더 멀리 튕긴다"),
            new Def(KNOCKBACK_SECONDS, "0.5", 0.05, 5, "튕김이 멈출 때까지 걸리는 시간(초). 처음에 빠르고 점점 느려진다"),
            // 튕기는 거리 = 최대 속도(px/s) × 이 값 × 세기. 1이면 최대 속도로 1초 동안 가는 거리만큼 튕긴다
            new Def(KNOCKBACK_DISTANCE, "0.6", 0, 5, "튕기는 거리: 최대 속도로 몇 초 동안 가는 거리만큼인지(같은 크기끼리 기준)"),
            new Def(COLLISION_COOLDOWN, "0.5", 0, 10, "같은 상대 연속 충돌 쿨다운(초)"),
            new Def(LOSE_PROTECT_SECONDS, "3", 0, 600, "키워드를 잃은 뒤 무적(초). 모두의 화면에 카운트다운이 보인다"),
            new Def(JOIN_PROTECT_SECONDS, "10", 0, 600, "입장 직후 보호(초)"),
            new Def(NICKNAME_CHANGE_MINUTES, "60", 0, 10080, "닉네임 변경 간격(분, 나라별)"),
            // ? 구슬 목표 개수 = 지금 활동 영역 넓이(100만 px² 단위) × 밀도. 맵이 넓어지면 같은 비율로 늘어난다(4.20).
            new Def(ORB_DENSITY, "2.2", 0, 50, "? 구슬 밀도: 1,000×1,000px 넓이마다 떠 있는 개수(0이면 끔)"),
            new Def(ORB_MAX, "300", 0, 5000, "방에 동시에 떠 있는 ? 구슬 수 상한(0이면 끔). 밀도로 정한 목표가 이보다 크면 이 값까지만"),
            new Def(ORB_SPAWN_SECONDS, "3", 0.5, 600, "? 구슬이 생기는 간격(초)"),
            new Def(ORB_SPAWN_PERCENT, "5", 1, 100, "? 구슬이 간격마다 목표 개수의 몇 %씩 생기는지(최소 1개). 5%면 빈 아레나가 약 20번의 간격에 다 찬다"),
            // NPC 봇(4.21): 사람 + 봇이 이 수가 되도록 채운다. 봇은 키워드 없이 방 메모리의 점수만 가진다
            new Def(BOT_PLAYERS, "10", 0, 200, "방 인원(사람 + 봇)이 이 수보다 적으면 봇으로 채운다"),
            // 사람이 많아도 봇이 하나도 없으면 봇 이기기(보상·칭호)를 할 수 없으므로 최소 인원을 둔다. 봇을 아예 없애려면 두 값 모두 0
            new Def(BOT_MIN_PLAYERS, "1", 0, 200, "봇 최소 인원: 사람이 있으면 방 인원과 관계없이 봇을 이 수 이상 둔다(0이면 사람이 많을 때 봇 없음)"),
            new Def(BOT_CAP_RATIO, "1.0", 0.1, 10, "봇 유행도 상한: 그 방에 접속 중인 사람 유행도 평균의 이 배수(1이면 평균)"),
            new Def(BOT_STEP_MIN, "20", 0, 100, "봇이 가진 키워드 하나의 점수 최소(사람의 키워드 점수처럼)"),
            new Def(BOT_STEP_MAX, "50", 0, 100, "봇이 가진 키워드 하나의 점수 최대"),
            new Def(RANK_TITLE_MIN_PLAYERS, "11", 1, 1000, "순위 칭호(핫스타·시상대·정상) 최소 접속 인원: 지금 그 나라에 접속 중인 사람이 이 인원 이상일 때만 순위 칭호를 준다"),
            new Def(ORB_ACTIVE_PERCENT, "50", 0, 100, "? 구슬에서 🔥활성 키워드를 얻을 확률(%). 나머지는 가진 키워드 하나를 잃음(🔥·💤 무관, 없으면 꽝)"),
            // 세이프존(4.22): 안에서는 부딪히지 않고(튕김·키워드 잃기 없음) 겹쳐 있을 수 있다
            new Def(SAFE_CENTER_RATIO, "0.09", 0.01, 0.5, "가운데 고정 세이프존 한 변: 활동 영역 한 변의 이 비율"),
            new Def(SAFE_CENTER_MIN, "1800", 100, 5000, "가운데 고정 세이프존 한 변 최소(px)"),
            new Def(SAFE_ZONE_RATIO, "0.025", 0.005, 0.2, "무작위 세이프존 한 변: 활동 영역 한 변의 이 비율"),
            new Def(SAFE_ZONE_MIN, "1200", 100, 3000, "무작위 세이프존 한 변 최소(px)"),
            new Def(SAFE_ZONE_DENSITY, "6", 0, 100, "무작위 세이프존 수: 10,000×10,000px 넓이마다 이 개수(0이면 끔)"),
            new Def(SAFE_ZONE_MAX, "80", 0, 300, "무작위 세이프존 동시 개수 상한"),
            new Def(SAFE_ZONE_SECONDS, "300", 10, 3600, "무작위 세이프존 유지 시간(초)"),
            new Def(CUSTOMIZE_UNLOCK_LEVEL, "11", 1, 100, "꾸미기 해금 레벨: 이 레벨부터 원의 바탕색·테두리를 바꾸고 픽셀 그림을 그릴 수 있다"),
            new Def(ART_CHANGE_MINUTES, "10", 0, 1440, "픽셀 그림 변경 간격(분): 그림을 저장한 뒤 이만큼 지나야 다시 바꿀 수 있다"),
            new Def(AI_COMMENT_ENABLED, "1", 0, 1, "AI 한 줄 설명: 처음 🔥가 된 키워드에 제미나이로 설명을 만들지(1이면 만듦, 0이면 멈춤). API 키가 없으면 어차피 만들지 않는다"),
            new Def(AI_COMMENT_BATCH_SIZE, "10", 1, 30, "AI 한 줄 설명을 한 번의 요청에 몇 개씩 묶어 보낼지(1분마다 한 번 요청). 많을수록 요청 수·토큰·시간이 줄지만 한 번 실패하면 같이 실패한다"),
            new Def(AI_COMMENT_DAILY_MAX, "400", 1, 5000, "AI 한 줄 설명 자동 요청의 하루 최대 횟수(요청 한 번에 여러 키워드를 묶음). 무료 한도 일일 요청 수보다 낮게. 관리자가 직접 누르는 재요청은 세지만 막지 않는다"),
            new Def(AI_COMMENT_GROUNDING, "0", 0, 1, "AI 한 줄 설명에 구글 검색 확인(그라운딩)을 쓸지(1이면 사용). 무료 등급은 검색 한도가 없어 429로 막히므로 결제를 연결한 뒤에 켠다. 끄면 최신 이슈는 모른다고 답할 수 있다"),
            new Def(DASH_SPEED_MULTIPLIER, "1.5", 1, 5, "대시 속도: 대시 중 최대 속도가 평소의 몇 배인지(1이면 대시가 효과 없음). 서버와 클라이언트 예측이 같은 값을 쓴다"),
            new Def(DASH_SECONDS, "3", 0.5, 30, "대시가 유지되는 시간(초)"),
            new Def(DASH_COOLDOWN_SECONDS, "30", 1, 600, "대시를 쓴 때부터 다시 쓸 수 있을 때까지의 시간(초, 대시 시간 포함)"),
            new Def(COMBO_FLAME_AT, "5", 1, 50, "콤보 불꽃: 키워드를 이만큼 연속으로 빼앗으면 원 둘레에 불꽃이 타오른다(한 번이라도 잃으면 꺼짐)"),
            new Def(COMBO_WIPE_ORB_PERCENT, "50", 0, 100, "불꽃인 사람에게 부딪혀 키워드를 모두 잃었을 때, 잃은 🔥활성 키워드 개수의 몇 %(올림)만큼 ! 구슬이 그 자리에 떨어지는지(0이면 안 떨어짐). ! 구슬은 먹으면 항상 키워드를 얻는다"),
            new Def(COMBO_BOT_COUNTS, "1", 0, 1, "봇을 이겨 키워드를 얻은 것도 콤보에 넣을지(1이면 넣음, 0이면 사람에게서 빼앗은 것만)"),
            new Def(EXTERNAL_HOLD_HOURS, "168", 1, 2160, "목록형 외부 소스(google)에 오른 키워드의 활성 유지 시간(시간). 목록에 보일 때마다 지금부터 다시 센다"));
}
