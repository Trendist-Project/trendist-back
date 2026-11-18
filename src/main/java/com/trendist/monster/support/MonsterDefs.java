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
    public static final String MOON_RABBIT = "moon_rabbit";

    /** 정기 등장은 처음에는 꺼 둔다(운영자가 [정기 등장]을 켜야 한다). 매시 같은 시각(같은 주기·오프셋)에 나오는 켜진 보스 중 하나가 무작위로 나온다(`spawn_weight` 비중) */
    public static List<Type> types() {
        return List.of(
            new Type(ELDER_TURTLE, "장로 거북이", "Elder Turtle", "BOSS", "ELDER_TURTLE", "elder-turtle", false, elderTurtle()),
            new Type(MOON_RABBIT, "달토끼", "Moon Rabbit", "BOSS", "MOON_RABBIT", "moon-rabbit", false, moonRabbit()));
    }

    private static List<Def> elderTurtle() {
        List<Def> d = commonDefs(1000);
        d.addAll(dashDefs());
        d.addAll(ringDefs());
        d.addAll(rewardDefs());
        return d;
    }

    private static List<Def> moonRabbit() {
        List<Def> d = commonDefs(800);
        d.addAll(hopDefs());
        d.addAll(mochiDefs());
        d.addAll(carrotDefs());
        d.add(new Def("contact_hp_percent", 5, 0, 100, "보스 몸에 부딪힐 때마다(hit_interval_seconds 간격) 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "몸·이동"));
        d.add(new Def("wander_speed_ratio", 0.5, 0.05, 1, "쫓을 사람이 없을 때 돌아다니는 속도: 이동 속도의 몇 배인지", "배", "몸·이동"));
        d.add(new Def("wander_pause_seconds", 2, 0, 30, "돌아다니다 도착하면 쉬는 시간(실제로는 절반~1.5배 사이로 흔들린다)", "초", "몸·이동"));
        d.addAll(rewardDefs());
        return d;
    }

    /** 모든 보스가 같은 설정(등장·체력·몸·싸움 규칙·분노·패턴 공통). 몸 반지름 기본값만 보스마다 다르다 */
    private static List<Def> commonDefs(double radius) {
        List<Def> d = new ArrayList<>();
        // ---- 등장
        d.add(new Def("interval_minutes", 60, 5, 1440, "등장 주기: 이 분마다 한 번 나온다(60이면 매시 정각)", "분", "등장"));
        d.add(new Def("offset_minutes", 0, 0, 1439, "정각에서 몇 분 뒤에 나올지(주기 기준)", "분", "등장"));
        d.add(new Def("duration_seconds", 600, 60, 3600, "제한 시간: 이 시간 안에 처치하지 못하면 사라진다", "초", "등장"));
        d.add(new Def("announce_seconds", 60, 0, 600, "등장 예고: 나오기 이 시간 전에 접속자 모두에게 알린다(0이면 예고 없음)", "초", "등장"));
        d.add(new Def("min_players", 1, 1, 1000, "최소 접속 사람 수: 이보다 적으면 그 시간은 건너뛴다(봇은 세지 않는다)", "명", "등장"));
        d.add(new Def("grace_seconds", 120, 10, 3600, "정각 뒤 이 시간 안에 서버가 켜져 있을 때만 나온다(서버를 다시 켠 직후 늦게 나오는 것을 막는다)", "초", "등장"));
        d.add(new Def("spawn_weight", 1, 0, 100, "정기 등장 비중: 같은 시각(주기·오프셋이 같은)에 나올 수 있는 켜진 보스 중 이 값에 비례해 한 마리가 무작위로 뽑힌다(0이면 정기 등장에서 뽑히지 않음, 관리자 즉시 소환은 된다)", null, "등장"));
        // ---- 체력(HP = 접속 사람 × 참여율 × 1인당 타격 수 × 기준 유행도 × 타격당 배수 × 여유)
        d.add(new Def("hp_participation_rate", 0.5, 0.05, 1, "체력 계산: 접속한 사람 중 보스를 치러 올 것으로 보는 비율", null, "체력"));
        d.add(new Def("hp_hits_per_player", 60, 1, 1000, "체력 계산: 참여자 한 명이 제한 시간 안에 칠 수 있다고 보는 횟수(부딪힌 뒤 튕겨 나갔다 다시 다가가는 시간과 `hit_interval_seconds`를 고려한 값)", "회", "체력"));
        d.add(new Def("hp_flame_ratio", 0.8, 0, 1, "체력 계산: 타격 중 불꽃 타격(유행도 × 불꽃 배수)의 비율. 나머지는 일반 타격(유행도 그대로)", null, "체력"));
        d.add(new Def("hp_top_weight", 0.3, 0, 1, "체력 계산: 기준 유행도 = 평균 × (1 − 이 값) + 1등 × 이 값. 1등이 클수록 보스가 단단해진다", null, "체력"));
        d.add(new Def("hp_margin", 0.9, 0.1, 3, "체력 계산: 기대 총 피해에 곱하는 여유. 1보다 작으면 제한 시간보다 일찍 잡고, 1이면 시간이 거의 꽉 찬다. 아슬아슬하게 하려면 0.9 안팎", null, "체력"));
        d.add(new Def("hp_min_players", 3, 1, 1000, "체력 계산에서 접속 사람 수의 최소값(관리자 즉시 소환 때 사람이 적어도 너무 약하지 않게)", "명", "체력"));
        d.add(new Def("hp_min", 2000, 1, 1000000000, "체력 하한", null, "체력"));
        d.add(new Def("hp_max", 5000000, 1, 1000000000, "체력 상한", null, "체력"));
        // ---- 몸·이동
        d.add(new Def("radius", radius, 100, 10000, "몸 반지름(고정). 1000이면 지름 2000px", "px", "몸·이동"));
        d.add(new Def("speed_ratio", 1, 0.05, 3, "이동 속도: 일반 사용자 최대 속도의 몇 배인지", "배", "몸·이동"));
        d.add(new Def("retarget_seconds", 3, 0.5, 60, "따라갈 사람을 이 시간마다 다시 정한다(가장 가까운 사람, 세이프존 안·은신 중인 사람은 제외)", "초", "몸·이동"));
        // ---- 싸움 규칙
        d.add(new Def("flame_damage_mult", 5, 1, 50, "불꽃 상태로 불꽃 없는 보스에 부딪히면 내 유행도의 이 배수만큼 피해(불꽃 보스에게는 불꽃 유무와 상관없이 1배)", "배", "싸움 규칙"));
        d.add(new Def("hit_interval_seconds", 1, 0, 30, "같은 사람이 보스에 다시 부딪혀 피해를 줄 수 있는 간격(무적이 아니라 연타만 막는다)", "초", "싸움 규칙"));
        d.add(new Def("hit_knockback", 3, 0, 10, "부딪힌 사람이 튕겨 나가는 세기(같은 크기끼리 부딪힐 때가 1)", "배", "싸움 규칙"));
        // ---- 분노
        d.add(new Def("rage_hp_percent", 20, 0, 100, "체력이 이 % 이하로 떨어지면 분노(0이면 이 조건 없음)", "%", "분노"));
        d.add(new Def("rage_remaining_seconds", 120, 0, 3600, "남은 시간이 이 시간 이하가 되면 분노(0이면 이 조건 없음)", "초", "분노"));
        d.add(new Def("rage_cooldown_mult", 0.6, 0.1, 1, "분노 중에는 패턴 쿨타임·공통 대기에 이 값을 곱한다(작을수록 자주 쓴다)", "배", "분노"));
        d.add(new Def("rage_speed_mult", 1, 0.5, 3, "분노 중 이동 속도에 곱하는 값", "배", "분노"));
        d.add(new Def("rage_damage_mult", 2, 1, 10, "분노 중에는 돌진·충격고리로 잃는 체력(%)에 이 값을 곱한다", "배", "분노"));
        d.add(new Def("rage_flame", 1, 0, 1, "분노하면 보스에 불꽃이 붙는지(1이면 붙음). 불꽃 보스에게 불꽃이 아닌 사용자가 부딪히면 피해를 주고 즉사하고, 불꽃 사용자는 즉사하지 않고 불꽃 없는 피해를 준다", null, "분노"));
        d.add(new Def("rage_flame_seconds", 0, 0, 3600, "분노의 불꽃이 유지되는 시간(0이면 끝까지). 지나면 불꽃이 꺼지고 분노만 남는다", "초", "분노"));
        // ---- 패턴 공통
        d.add(new Def("opening_delay_seconds", 8, 0, 120, "등장한 뒤 첫 패턴을 쓰기까지 기다리는 시간(모일 틈)", "초", "패턴 공통"));
        d.add(new Def("gcd_seconds", 5, 0, 60, "공통 대기: 어떤 패턴이든 끝난 뒤 이 시간이 지나야 다음 패턴을 쓴다", "초", "패턴 공통"));
        d.add(new Def("recover_seconds", 1, 0, 10, "패턴이 끝난 뒤 제자리에서 멈추는 시간(공략 틈)", "초", "패턴 공통"));
        return d;
    }

    /** 보상(모든 보스 공통) */
    private static List<Def> rewardDefs() {
        List<Def> d = new ArrayList<>();
        d.add(new Def("reward_min_share", 0.5, 0, 100, "참여로 인정하는 최소 피해(보스 최대 체력의 %)", "%", "보상"));
        d.add(new Def("reward_level_percent", 100, 0, 1000, "기여도에 비례한 레벨 진행: 내 몫이 100%일 때 '내 현재 레벨의 폭(그 레벨에 오를 때 필요했던 EXP)'의 몇 %를 EXP로 받는지(받는 사람 자신의 레벨 기준이라 사람마다 EXP가 다르다). 내 몫이 30%면 이 값의 30%", "%", "보상"));
        d.add(new Def("reward_keywords", 10, 0, 20, "처치 성공 때 참여자가 모두 받는 기본 🔥 활성 키워드 수(! 구슬과 같은 규칙: 무작위 🔥 키워드)", "개", "보상"));
        d.add(new Def("reward_keyword_pool", 0, 0, 20, "기여도에 비례해 더 받는 🔥 키워드 수: 내 몫이 100%면 이만큼(반올림), 25%면 이 값의 25%", "개", "보상"));
        d.add(new Def("reward_keywords_max", 10, 0, 20, "기본+기여도로 받는 🔥 키워드 수의 상한(1~3위 추가분은 별도)", "개", "보상"));
        return d;
    }

    /** 돌진 패턴(DashSkill)의 설정값. 돌진을 쓰는 보스가 그대로 가져다 쓴다 */
    public static List<Def> dashDefs() {
        List<Def> d = new ArrayList<>();
        d.add(new Def("dash_enabled", 1, 0, 1, "돌진 사용(1이면 씀, 0이면 이 패턴을 쓰지 않는다)", null, "돌진"));
        d.add(new Def("dash_weight", 3, 0, 100, "돌진 선택 가중치(쓸 수 있는 패턴 중 무작위로 고를 때의 비중, 0이면 쓰지 않음)", null, "돌진"));
        d.add(new Def("dash_cooldown", 20, 0, 600, "돌진 쿨타임(쓴 때부터)", "초", "돌진"));
        d.add(new Def("dash_windup", 1.5, 0.2, 10, "돌진 예고 시간(바닥에 붉은 길이 보인다)", "초", "돌진"));
        d.add(new Def("dash_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 방향을 고정한다(그 전에는 대상을 따라 돈다)", "초", "돌진"));
        d.add(new Def("dash_speed_mult", 2, 1, 10, "돌진 속도: 보스 기본 속도의 몇 배", "배", "돌진"));
        d.add(new Def("dash_seconds", 2, 0.2, 10, "돌진이 이어지는 시간", "초", "돌진"));
        d.add(new Def("wander_speed_ratio", 0.5, 0.05, 1, "쫓을 사람이 없을 때 돌아다니는 속도: 이동 속도의 몇 배인지", "배", "몸·이동"));
        d.add(new Def("wander_pause_seconds", 2, 0, 30, "돌아다니다 도착하면 쉬는 시간(실제로는 절반~1.5배 사이로 흔들린다)", "초", "몸·이동"));
        d.add(new Def("contact_hp_percent", 5, 0, 100, "보스 몸에 부딪힐 때마다(hit_interval_seconds 간격) 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "몸·이동"));
        d.add(new Def("dash_hp_percent", 10, 0, 100, "돌진에 맞으면 잃는 체력(최대 체력의 %, 한 번의 돌진에 한 번)", "%", "돌진"));
        d.add(new Def("dash_knock_mult", 1.5, 0.5, 5, "돌진 중에 부딪히면 튕김 세기에 곱하는 값", "배", "돌진"));
        return d;
    }

    /** 충격고리 패턴(RingSkill)의 설정값. 충격고리를 쓰는 보스가 그대로 가져다 쓴다 */
    public static List<Def> ringDefs() {
        List<Def> d = new ArrayList<>();
        d.add(new Def("ring_enabled", 1, 0, 1, "충격고리 사용(1이면 씀, 0이면 이 패턴을 쓰지 않는다)", null, "충격고리"));
        d.add(new Def("ring_weight", 2, 0, 100, "충격고리 선택 가중치(0이면 쓰지 않음)", null, "충격고리"));
        d.add(new Def("ring_cooldown", 35, 0, 600, "충격고리 쿨타임(쓴 때부터)", "초", "충격고리"));
        d.add(new Def("ring_windup", 3, 0.2, 10, "충격고리 예고 시간(보스가 멈추고 몸이 붉게 빛난다)", "초", "충격고리"));
        d.add(new Def("ring_speed", 1800, 100, 20000, "충격고리가 퍼지는 속도", "px/초", "충격고리"));
        d.add(new Def("ring_range_px", 0, 0, 100000, "충격고리가 퍼지는 거리와 맞는 피해가 줄어드는 거리(0이면 맵 끝까지)", "px", "충격고리"));
        d.add(new Def("ring_max_hp_percent", 33, 0, 100, "충격고리를 가장 가까이서 맞으면 잃는 체력(최대 체력의 %). 남은 체력이 이 값 이하면 죽는다", "%", "충격고리"));
        d.add(new Def("ring_min_hp_percent", 5, 0, 100, "충격고리를 가장 멀리서 맞으면 잃는 체력(최대 체력의 %). 멀수록 거리에 비례해 줄어든다", "%", "충격고리"));
        return d;
    }

    /** 깡충 패턴(HopSkill)의 설정값 */
    public static List<Def> hopDefs() {
        List<Def> d = new ArrayList<>();
        d.add(new Def("hop_enabled", 1, 0, 1, "깡충 사용(1이면 씀, 0이면 이 패턴을 쓰지 않는다)", null, "깡충"));
        d.add(new Def("hop_weight", 1, 0, 100, "깡충 선택 가중치(0이면 쓰지 않음)", null, "깡충"));
        d.add(new Def("hop_cooldown", 18, 0, 600, "깡충 쿨타임(쓴 때부터)", "초", "깡충"));
        d.add(new Def("hop_windup", 1.5, 0.2, 10, "깡충 예고 시간(대상 자리에 달 모양 그림자 원이 뜬다)", "초", "깡충"));
        d.add(new Def("hop_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 그림자 자리를 고정한다(그 전에는 대상을 따라간다)", "초", "깡충"));
        d.add(new Def("hop_air_seconds", 0.6, 0.1, 5, "점프해서 내려앉기까지 떠 있는 시간(떠 있는 동안은 몸이 없어 부딪히지 않는다)", "초", "깡충"));
        d.add(new Def("hop_radius_px", 500, 50, 5000, "착지 폭발 반지름(그림자 원의 크기)", "px", "깡충"));
        d.add(new Def("hop_hp_percent", 12, 0, 100, "착지 폭발에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "깡충"));
        d.add(new Def("hop_knock_mult", 2, 0, 10, "착지 폭발에 맞은 사람이 튕기는 세기(같은 크기끼리 부딪힐 때가 1)", "배", "깡충"));
        d.add(new Def("hop_stun_seconds", 1.5, 0, 10, "착지 뒤 무방비로 서 있는 시간(공략 틈: 이 동안 다른 패턴을 쓰지 못한다)", "초", "깡충"));
        d.add(new Def("hop_rage_count", 2, 1, 5, "분노 중에 한 번의 깡충으로 연달아 뛰는 횟수", "회", "깡충"));
        d.add(new Def("hop_chain_windup", 0.8, 0.2, 5, "연달아 뛸 때 다음 그림자를 예고하는 시간", "초", "깡충"));
        return d;
    }

    /** 끈끈 떡 패턴(MochiSkill)의 설정값 */
    public static List<Def> mochiDefs() {
        List<Def> d = new ArrayList<>();
        d.add(new Def("mochi_enabled", 1, 0, 1, "끈끈 떡 사용(1이면 씀, 0이면 이 패턴을 쓰지 않는다)", null, "끈끈 떡"));
        d.add(new Def("mochi_weight", 1, 0, 100, "끈끈 떡 선택 가중치(0이면 쓰지 않음)", null, "끈끈 떡"));
        d.add(new Def("mochi_cooldown", 28, 0, 600, "끈끈 떡 쿨타임(쓴 때부터)", "초", "끈끈 떡"));
        d.add(new Def("mochi_windup", 1.5, 0.2, 10, "끈끈 떡 예고 시간(떨어질 자리마다 점선 원이 뜬다)", "초", "끈끈 떡"));
        d.add(new Def("mochi_count", 5, 1, 20, "한 번에 던지는 달떡 장판 수(공격 대상 자리에 먼저, 다음은 다른 사람 자리, 남으면 대상 둘레)", "개", "끈끈 떡"));
        d.add(new Def("mochi_radius_px", 420, 30, 3000, "달떡 장판 반지름(보스가 쫓는 사람의 자리에 던진다)", "px", "끈끈 떡"));
        d.add(new Def("mochi_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 떡 자리를 고정한다(그 전에는 대상을 따라간다)", "초", "끈끈 떡"));
        d.add(new Def("mochi_scatter", 1.2, 0, 10, "남는 떡(사람 수보다 떡이 많을 때)을 공격 대상에서 떨어뜨리는 거리: 반지름의 몇 배인지", "배", "끈끈 떡"));
        d.add(new Def("mochi_seconds", 8, 1, 120, "달떡 장판이 바닥에 남는 시간", "초", "끈끈 떡"));
        d.add(new Def("mochi_slow_seconds", 1.5, 0.2, 30, "장판에 닿은 사람이 느려지는 시간(서 있는 동안 계속 이어지고 벗어나면 이 시간 뒤 풀린다). 세기는 슬로우 스킬의 slow_percent를 쓴다", "초", "끈끈 떡"));
        return d;
    }

    /** 당근 부채 패턴(CarrotSkill)의 설정값 */
    public static List<Def> carrotDefs() {
        List<Def> d = new ArrayList<>();
        d.add(new Def("carrot_enabled", 1, 0, 1, "당근 부채 사용(1이면 씀, 0이면 이 패턴을 쓰지 않는다)", null, "당근 부채"));
        d.add(new Def("carrot_weight", 1, 0, 100, "당근 부채 선택 가중치(0이면 쓰지 않음)", null, "당근 부채"));
        d.add(new Def("carrot_cooldown", 14, 0, 600, "당근 부채 쿨타임(쓴 때부터)", "초", "당근 부채"));
        d.add(new Def("carrot_windup", 1.2, 0.2, 10, "당근 부채 예고 시간(보스가 노려보고 부채꼴이 뜬다)", "초", "당근 부채"));
        d.add(new Def("carrot_lock_seconds", 0.4, 0, 5, "예고 마지막 이 시간 동안은 방향을 고정한다", "초", "당근 부채"));
        d.add(new Def("carrot_count", 7, 1, 30, "한 번에 쏘는 당근 수(부채꼴에 고르게 퍼진다)", "발", "당근 부채"));
        d.add(new Def("carrot_spread_deg", 60, 0, 360, "부채꼴 각도", "도", "당근 부채"));
        d.add(new Def("carrot_speed", 1400, 100, 20000, "당근이 날아가는 속도", "px/초", "당근 부채"));
        d.add(new Def("carrot_range_px", 3000, 100, 100000, "당근이 날아가는 거리(이 거리에서 사라진다)", "px", "당근 부채"));
        d.add(new Def("carrot_radius_px", 45, 5, 1000, "당근 하나의 맞는 범위 반지름", "px", "당근 부채"));
        d.add(new Def("carrot_hp_percent", 6, 0, 100, "당근에 맞으면 잃는 체력(최대 체력의 %, 한 번 쏜 묶음에서 한 사람에게 한 번). 분노 중에는 rage_damage_mult배", "%", "당근 부채"));
        return d;
    }
}
