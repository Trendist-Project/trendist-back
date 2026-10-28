package com.trendist.setting;

import java.util.List;

/** settings 테이블의 이름과 기본값(13.3). 시더가 없는 행만 넣는다(11.3). */
public final class SettingKeys {
    private SettingKeys() {}

    public record Def(String name, String value, double min, double max, String description) {}

    public static final String TREND_BASE_POPULATION = "trend_base_population";
    public static final String TREND_LOG_SOFTNESS = "trend_log_softness";
    public static final String TREND_BASE_RATIO = "trend_base_ratio";
    public static final String TREND_MAX_BASE = "trend_max_base";
    public static final String EXTERNAL_CONFIRM_HEAT = "external_confirm_heat";
    public static final String SCORE_FACTOR = "score_factor";
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
    public static final String COLLISION_COOLDOWN = "collision_cooldown_seconds";
    public static final String LOSE_PROTECT_SECONDS = "lose_protect_seconds";
    public static final String JOIN_PROTECT_SECONDS = "join_protect_seconds";

    public static final List<Def> DEFAULTS = List.of(
            new Def(TREND_BASE_POPULATION, "1000", 1, 10000, "트렌드 값 식의 기준 인원(g(n)=1)"),
            new Def(TREND_LOG_SOFTNESS, "30", 0.0001, 100000, "로그 완만도"),
            new Def(TREND_BASE_RATIO, "10", 0, 100, "기본 비율"),
            new Def(TREND_MAX_BASE, "50", 1, 50, "최대 기준값(× 최대 가중치 2 ≤ 100)"),
            new Def(EXTERNAL_CONFIRM_HEAT, "10", 0, 100, "외부 확인 관심도"),
            new Def(SCORE_FACTOR, "0.5", 0.0001, 2, "점수 환산 계수"),
            new Def(EXP_SCORE_CAP, "100", 0, 1000, "EXP 점수 반영 상한"),
            new Def(PLUS_TTL_HOURS, "24", 1, 168, "+ 삭제 시간(시간)"),
            new Def(ACTIVATE_HOLDERS, "3", 1, 1000, "🔥활성 전환 인원"),
            new Def(EXP_PER_PLUS, "1", 0, 100, "+ 한 번의 EXP"),
            new Def(WORLD_SIZE, "12000", 1000, 50000, "기준 맵 한 변(px)"),
            new Def(MIN_RADIUS, "16", 4, 100, "최소 반지름(px)"),
            new Def(MAX_DIAMETER_RATIO, "0.10", 0.01, 0.20, "최대 지름 비율(기준 맵 한 변 대비)"),
            new Def(SCORE_CAP, "2000", 1, 1000000, "크기 상한 점수(S_cap)"),
            new Def(MAX_SPEED, "240", 50, 1000, "최대 속도(px/s, 모든 플레이어 동일)"),
            new Def(ZOOM_FACTOR, "170", 1, 10000, "줌 계수"),
            new Def(BIG_RATIO, "1.15", 1, 5, "큰 쪽 판정 비율"),
            new Def(KNOCKBACK_CAP, "3", 1, 10, "튕김 상한(배)"),
            new Def(KNOCKBACK_SECONDS, "0.4", 0.05, 5, "튕김 감속 시간(초)"),
            new Def(COLLISION_COOLDOWN, "0.5", 0, 10, "같은 상대 연속 충돌 쿨다운(초)"),
            new Def(LOSE_PROTECT_SECONDS, "10", 0, 600, "키워드를 잃은 뒤 보호(초)"),
            new Def(JOIN_PROTECT_SECONDS, "10", 0, 600, "입장 직후 보호(초)"));
}
