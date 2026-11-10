package com.trendist.monster.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 몬스터의 종류와 설정값 기본값(4.35). 시더가 없는 행만 채우고, 이미 있는 값은 건드리지 않는다(운영에서 고친 값). 설명·범위·단위·그룹·순서는 코드가 정한다.
 * 새 몬스터는 여기에 종류와 설정 목록을 더하고 서버에 행동(Monster 구현)을 더한다.
 */
public final class MonsterDefs {
    private MonsterDefs() {}

    public record Type(String code, String name, String nameEn, String kind, String behavior, String image, boolean enabledByDefault, List<Def> defs) {}
    public record Def(String name, double value, double min, double max, String description, String unit, String group) {}

    public static final String ELDER_TURTLE = "elder_turtle";

    public static List<Type> types() {
        return List.of(new Type(ELDER_TURTLE, "장로 거북이", "Elder Turtle", "BOSS", "ELDER_TURTLE", "elder-turtle", false, elderTurtle())); // 정기 등장은 처음에는 꺼 둔다(운영자가 [정기 등장]을 켜야 한다)
    }

    private static List<Def> elderTurtle() {
        List<Def> d = new ArrayList<>();
        // ---- 등장
        d.add(new Def("interval_minutes", 60, 5, 1440, "등장 주기: 이 분마다 한 번 나온다(60이면 매시 정각)", "분", "등장"));
        d.add(new Def("offset_minutes", 0, 0, 1439, "정각에서 몇 분 뒤에 나올지(주기 기준)", "분", "등장"));
        d.add(new Def("duration_seconds", 600, 60, 3600, "제한 시간: 이 시간 안에 처치하지 못하면 사라진다", "초", "등장"));
        d.add(new Def("announce_seconds", 60, 0, 600, "등장 예고: 나오기 이 시간 전에 접속자 모두에게 알린다(0이면 예고 없음)", "초", "등장"));
        d.add(new Def("min_players", 3, 1, 1000, "최소 접속 사람 수: 이보다 적으면 그 시간은 건너뛴다(봇은 세지 않는다)", "명", "등장"));
        d.add(new Def("grace_seconds", 120, 10, 3600, "정각 뒤 이 시간 안에 서버가 켜져 있을 때만 나온다(서버를 다시 켠 직후 늦게 나오는 것을 막는다)", "초", "등장"));
        // ---- 체력(HP = 접속 사람 × 참여율 × 1인당 타격 수 × 기준 유행도 × 타격당 배수 × 여유)
        d.add(new Def("hp_participation_rate", 0.5, 0.05, 1, "체력 계산: 접속한 사람 중 보스를 치러 올 것으로 보는 비율", null, "체력"));
        d.add(new Def("hp_hits_per_player", 60, 1, 1000, "체력 계산: 참여자 한 명이 제한 시간 안에 칠 수 있다고 보는 횟수(3초 무적과 다시 다가가는 시간을 고려한 값)", "회", "체력"));
        d.add(new Def("hp_flame_ratio", 0.8, 0, 1, "체력 계산: 타격 중 불꽃 타격(유행도 × 불꽃 배수, 키워드를 잃지 않음)의 비율. 나머지는 일반 타격(유행도 그대로)", null, "체력"));
        d.add(new Def("hp_top_weight", 0.3, 0, 1, "체력 계산: 기준 유행도 = 평균 × (1 − 이 값) + 1등 × 이 값. 1등이 클수록 보스가 단단해진다", null, "체력"));
        d.add(new Def("hp_margin", 0.9, 0.1, 3, "체력 계산: 기대 총 피해에 곱하는 여유. 1보다 작으면 제한 시간보다 일찍 잡고, 1이면 시간이 거의 꽉 찬다. 아슬아슬하게 하려면 0.9 안팎", null, "체력"));
        d.add(new Def("hp_min_players", 3, 1, 1000, "체력 계산에서 접속 사람 수의 최소값(관리자 즉시 소환 때 사람이 적어도 너무 약하지 않게)", "명", "체력"));
        d.add(new Def("hp_min", 2000, 1, 1000000000, "체력 하한", null, "체력"));
        d.add(new Def("hp_max", 5000000, 1, 1000000000, "체력 상한", null, "체력"));
        // ---- 몸·이동
        d.add(new Def("radius", 2000, 100, 10000, "몸 반지름(고정). 2000이면 지름 4000px", "px", "몸·이동"));
        d.add(new Def("speed_ratio", 0.8, 0.05, 3, "이동 속도: 일반 사용자 최대 속도의 몇 배인지", "배", "몸·이동"));
        d.add(new Def("retarget_seconds", 3, 0.5, 60, "따라갈 사람을 이 시간마다 다시 정한다(가장 가까운 사람, 세이프존 안·은신 중인 사람은 제외)", "초", "몸·이동"));
        // ---- 싸움 규칙
        d.add(new Def("flame_damage_mult", 5, 1, 50, "불꽃 상태로 부딪히면 내 유행도의 이 배수만큼 피해(키워드는 잃지 않는다)", "배", "싸움 규칙"));
        d.add(new Def("hit_protect_seconds", 3, 0, 30, "보스에게 부딪힌 뒤 무적 시간(이 동안은 보스·충격고리와 부딪히지 않는다)", "초", "싸움 규칙"));
        d.add(new Def("hit_lose_keywords", 1, 0, 10, "일반 상태로 부딪히면 잃는 키워드 수(불꽃이면 잃지 않는다)", "개", "싸움 규칙"));
        d.add(new Def("hit_knockback", 3, 0, 10, "부딪힌 사람이 튕겨 나가는 세기(같은 크기끼리 부딪힐 때가 1)", "배", "싸움 규칙"));
        // ---- 분노
        d.add(new Def("rage_hp_percent", 20, 0, 100, "체력이 이 % 이하로 떨어지면 분노(0이면 이 조건 없음)", "%", "분노"));
        d.add(new Def("rage_remaining_seconds", 120, 0, 3600, "남은 시간이 이 시간 이하가 되면 분노(0이면 이 조건 없음)", "초", "분노"));
        d.add(new Def("rage_cooldown_mult", 0.6, 0.1, 1, "분노 중에는 패턴 쿨타임·공통 대기에 이 값을 곱한다(작을수록 자주 쓴다)", "배", "분노"));
        d.add(new Def("rage_speed_mult", 1, 0.5, 3, "분노 중 이동 속도에 곱하는 값", "배", "분노"));
        d.add(new Def("rage_flame", 1, 0, 1, "분노하면 보스에 불꽃이 붙는지(1이면 붙음). 불꽃 보스에게 불꽃 사용자가 부딪히면 불꽃이 꺼지고 키워드 1개를 잃고, 불꽃이 아닌 사용자가 부딪히면 즉사한다", null, "분노"));
        // ---- 패턴 공통
        d.add(new Def("opening_delay_seconds", 8, 0, 120, "등장한 뒤 첫 패턴을 쓰기까지 기다리는 시간(모일 틈)", "초", "패턴 공통"));
        d.add(new Def("gcd_seconds", 5, 0, 60, "공통 대기: 어떤 패턴이든 끝난 뒤 이 시간이 지나야 다음 패턴을 쓴다", "초", "패턴 공통"));
        d.add(new Def("recover_seconds", 1.5, 0, 10, "패턴이 끝난 뒤 제자리에서 멈추는 시간(공략 틈)", "초", "패턴 공통"));
        // ---- 돌진
        d.add(new Def("dash_weight", 3, 0, 100, "돌진 선택 가중치(쓸 수 있는 패턴 중 무작위로 고를 때의 비중, 0이면 쓰지 않음)", null, "돌진"));
        d.add(new Def("dash_cooldown", 20, 0, 600, "돌진 쿨타임(쓴 때부터)", "초", "돌진"));
        d.add(new Def("dash_windup", 1.5, 0.2, 10, "돌진 예고 시간(바닥에 붉은 길이 보인다)", "초", "돌진"));
        d.add(new Def("dash_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 방향을 고정한다(그 전에는 대상을 따라 돈다)", "초", "돌진"));
        d.add(new Def("dash_speed_mult", 2, 1, 10, "돌진 속도: 보스 기본 속도의 몇 배", "배", "돌진"));
        d.add(new Def("dash_seconds", 2, 0.2, 10, "돌진이 이어지는 시간", "초", "돌진"));
        d.add(new Def("dash_min_distance", 600, 0, 10000, "대상이 몸 가장자리에서 이 거리 이상 떨어져 있을 때만 돌진을 쓴다", "px", "돌진"));
        d.add(new Def("dash_knock_mult", 1.5, 0.5, 5, "돌진 중에 부딪히면 튕김 세기에 곱하는 값", "배", "돌진"));
        // ---- 충격고리
        d.add(new Def("ring_weight", 2, 0, 100, "충격고리 선택 가중치(0이면 쓰지 않음)", null, "충격고리"));
        d.add(new Def("ring_cooldown", 35, 0, 600, "충격고리 쿨타임(쓴 때부터)", "초", "충격고리"));
        d.add(new Def("ring_windup", 2, 0.2, 10, "충격고리 예고 시간(보스가 멈추고 몸이 붉게 빛난다)", "초", "충격고리"));
        d.add(new Def("ring_speed", 1800, 100, 20000, "충격고리가 퍼지는 속도", "px/초", "충격고리"));
        d.add(new Def("ring_range_px", 0, 0, 100000, "충격고리가 퍼지는 거리와 잃는 키워드 수가 줄어드는 거리(0이면 맵 끝까지)", "px", "충격고리"));
        d.add(new Def("ring_max_keywords", 10, 1, 100, "충격고리를 가장 가까이서 맞으면 잃는 키워드 수", "개", "충격고리"));
        d.add(new Def("ring_min_keywords", 1, 0, 100, "충격고리를 가장 멀리서 맞으면 잃는 키워드 수", "개", "충격고리"));
        // ---- 보상
        d.add(new Def("reward_min_share", 0.5, 0, 100, "참여로 인정하는 최소 피해(보스 최대 체력의 %)", "%", "보상"));
        d.add(new Def("reward_exp", 30, 0, 100000, "처치 성공 때 참여자가 받는 EXP", null, "보상"));
        d.add(new Def("reward_keywords", 2, 0, 20, "처치 성공 때 참여자가 받는 🔥 키워드 수(! 구슬과 같은 규칙: 무작위 🔥 키워드)", "개", "보상"));
        d.add(new Def("reward_top_exp", 20, 0, 100000, "피해 1~3위가 추가로 받는 EXP", null, "보상"));
        d.add(new Def("reward_top_keywords", 1, 0, 20, "피해 1~3위가 추가로 받는 🔥 키워드 수", "개", "보상"));
        d.add(new Def("fail_exp", 5, 0, 100000, "시간 안에 처치하지 못했을 때 참여자가 받는 EXP(키워드는 없음)", null, "보상"));
        return d;
    }
}
