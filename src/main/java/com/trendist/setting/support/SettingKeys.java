package com.trendist.setting.support;

import java.util.List;
import java.util.Map;

/** settings 테이블의 이름과 기본값(13.3). 시더가 없는 행만 넣는다(11.3). */
public final class SettingKeys {
    private SettingKeys() {}

    public record Def(String name, String value, double min, double max, String description) {}

    public static final String TREND_BASE_POPULATION = "trend_base_population";
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
    public static final String BOT_CAP_RATIO = "bot_cap_ratio";
    public static final String BOT_STEP_MIN = "bot_step_min";
    public static final String BOT_STEP_MAX = "bot_step_max";
    public static final String ORB_SPAWN_PERCENT = "orb_spawn_percent";
    public static final String ORB_ACTIVE_PERCENT = "orb_active_percent";
    public static final String EXTERNAL_HOLD_HOURS = "external_hold_hours";

    /**
     * 기본값을 바꾼 설정과 예전 기본값. 시더는 기존 행을 덮어쓰지 않지만, 아직 예전 기본값 그대로인 행만 새 기본값으로 바꾼다
     * (관리자가 직접 바꾼 값은 그대로 둔다).
     */
    public static final Map<String, String> REPLACED_DEFAULTS = Map.of(
            BOT_CAP_RATIO, "1.5"); // 봇이 1등 사람보다 커지지 않게 1.5 → 0.7(4.21)

    public static final List<Def> DEFAULTS = List.of(
            new Def(TREND_BASE_POPULATION, "1000", 1, 10000, "트렌드 값 식의 기준 인원(g(n)=1)"),
            new Def(TREND_LOG_SOFTNESS, "30", 0.0001, 100000, "로그 완만도"),
            new Def(TREND_BASE_RATIO, "10", 0, 100, "기본 비율"),
            new Def(TREND_MAX_BASE, "50", 1, 50, "최대 기준값(× 최대 가중치 2 ≤ 100)"),
            new Def(EXTERNAL_CONFIRM_HEAT, "10", 0, 100, "외부 확인 관심도"),
            new Def(EXP_SCORE_CAP, "100", 0, 1000, "EXP 점수 반영 상한"),
            new Def(PLUS_TTL_HOURS, "24", 1, 168, "+ 삭제 시간(시간)"),
            new Def(ACTIVATE_HOLDERS, "3", 1, 1000, "🔥활성 전환 인원"),
            new Def(EXP_PER_PLUS, "1", 0, 100, "+ 한 번의 EXP"),
            new Def(WORLD_SIZE, "24000", 1000, 50000, "기준 맵 한 변(px)"),
            new Def(MIN_RADIUS, "16", 4, 100, "최소 반지름(px)"),
            new Def(MAX_DIAMETER_RATIO, "0.05", 0.01, 0.20, "최대 지름 비율(기준 맵 한 변 대비)"),
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
            // 기본 밀도 4.4는 활동 영역 3,000×3,000px에 20개였던 예전 비율의 2배다
            new Def(ORB_DENSITY, "4.4", 0, 50, "? 구슬 밀도: 1,000×1,000px 넓이마다 떠 있는 개수(0이면 끔)"),
            new Def(ORB_MAX, "3000", 0, 5000, "방에 동시에 떠 있는 ? 구슬 수 상한(0이면 끔). 밀도로 정한 목표가 이보다 크면 이 값까지만"),
            new Def(ORB_SPAWN_SECONDS, "3", 0.5, 600, "? 구슬이 생기는 간격(초)"),
            new Def(ORB_SPAWN_PERCENT, "5", 1, 100, "? 구슬이 간격마다 목표 개수의 몇 %씩 생기는지(최소 1개). 5%면 빈 아레나가 약 20번의 간격에 다 찬다"),
            // NPC 봇(4.21): 사람 + 봇이 이 수가 되도록 채운다. 봇은 키워드 없이 방 메모리의 점수만 가진다
            new Def(BOT_PLAYERS, "10", 0, 200, "방 인원(사람 + 봇)이 이 수보다 적으면 봇으로 채운다(0이면 봇 없음)"),
            new Def(BOT_CAP_RATIO, "0.7", 0.1, 10, "봇 유행도 상한: 그 방 1등(사람) 유행도의 이 배수"),
            new Def(BOT_STEP_MIN, "20", 0, 100, "봇이 가진 키워드 하나의 점수 최소(사람의 키워드 점수처럼)"),
            new Def(BOT_STEP_MAX, "50", 0, 100, "봇이 가진 키워드 하나의 점수 최대"),
            new Def(ORB_ACTIVE_PERCENT, "33", 0, 100, "? 구슬에서 🔥활성 키워드가 나올 확률(%). 나머지는 꽝"),
            new Def(EXTERNAL_HOLD_HOURS, "168", 1, 2160, "목록형 외부 소스(google)에 오른 키워드의 활성 유지 시간(시간). 목록에 보일 때마다 지금부터 다시 센다"));
}
