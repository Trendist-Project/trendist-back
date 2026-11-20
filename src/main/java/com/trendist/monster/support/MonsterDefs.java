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
    public static final String DEEP_OCTOPUS = "deep_octopus";
    public static final String ELECTRIC_EEL = "electric_eel";
    public static final String PUFFER_KING = "puffer_king";
    public static final String GREAT_SHARK = "great_shark";
    public static final String GREAT_WHALE = "great_whale";


    /** 정기 등장은 처음에는 꺼 둔다(운영자가 [정기 등장]을 켜야 한다). 매시 같은 시각(같은 주기·오프셋)에 나오는 켜진 보스 중 하나가 무작위로 나온다(`spawn_weight` 비중) */
    public static List<Type> types() {
        return List.of(
            new Type(ELDER_TURTLE, "장로 거북이", "Elder Turtle", "BOSS", "ELDER_TURTLE", "elder-turtle", false, elderTurtle()),
            new Type(MOON_RABBIT, "달토끼", "Moon Rabbit", "BOSS", "MOON_RABBIT", "moon-rabbit", false, moonRabbit()),
            new Type(ELECTRIC_EEL, "전기뱀장어", "Electric Eel", "BOSS", "ELECTRIC_EEL", "electric-eel", false, electricEel()),
            new Type(DEEP_OCTOPUS, "심해 문어", "Abyssal Octopus", "BOSS", "DEEP_OCTOPUS", "deep-octopus", false, deepOctopus()),
            new Type(PUFFER_KING, "복어 대왕", "Puffer King", "BOSS", "PUFFER_KING", "puffer-king", false, pufferKing()),
            new Type(GREAT_SHARK, "대형 상어", "Great Shark", "BOSS", "GREAT_SHARK", "great-shark", false, greatShark()),
            new Type(GREAT_WHALE, "대왕 고래", "Great Whale", "BOSS", "GREAT_WHALE", "great-whale", false, greatWhale()));
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
        d.add(new Def("spawn_weight", 1, 0, 10, "사이클당 등장 횟수: 같은 시각(주기·오프셋이 같은)에 나올 수 있는 켜진 보스를 한 사이클(켜진 보스 수만큼의 정각)로 묶어 사이클마다 순서를 섞어 낸다. 이 값은 한 사이클에서 이 보스가 나오는 횟수(반올림, 최소 1)다. 모두 1이면 한 사이클에 보스마다 정확히 한 번씩 나온다(0이면 정기 등장에서 제외, 관리자 즉시 소환은 된다)", "회", "등장"));
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

    // ================================================================ 바다 보스 다섯 마리(229판)

    /** 짧게 쓰는 설정 한 줄 */
    private static Def d(String name, double value, double min, double max, String desc, String unit, String group) { return new Def(name, value, min, max, desc, unit, group); }

    /** 이미 만든 목록에서 설정 하나의 기본값을 바꾼다(보스마다 다른 값) */
    private static void set(List<Def> list, String name, double value) {
        for (int i = 0; i < list.size(); i++) {
            Def x = list.get(i);
            if (x.name().equals(name)) { list.set(i, new Def(x.name(), value, x.min(), x.max(), x.description(), x.unit(), x.group())); return; }
        }
        throw new IllegalArgumentException("no setting " + name);
    }

    /** 패턴 공통 네 가지(`<id>_enabled`·`_weight`·`_cooldown`·`_windup`, 그룹 = 패턴 이름) */
    private static List<Def> pattern(String group, String id, String label, double cooldown, double windup, String windupDesc) {
        List<Def> l = new ArrayList<>();
        l.add(d(id + "_enabled", 1, 0, 1, label + " 사용(1이면 씀, 0이면 이 패턴을 쓰지 않는다)", null, group));
        l.add(d(id + "_weight", 1, 0, 100, label + " 선택 가중치(0이면 쓰지 않음)", null, group));
        l.add(d(id + "_cooldown", cooldown, 0, 600, label + " 쿨타임(쓴 때부터)", "초", group));
        l.add(d(id + "_windup", windup, 0.2, 10, label + " 예고 시간(" + windupDesc + ")", "초", group));
        return l;
    }

    /** 몸·이동(바다 보스 공통): 접촉 피해와 돌아다니기 */
    private static List<Def> bodyDefs() {
        List<Def> l = new ArrayList<>();
        l.add(d("contact_hp_percent", 5, 0, 100, "보스 몸에 부딪힐 때마다(hit_interval_seconds 간격) 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "몸·이동"));
        l.add(d("wander_speed_ratio", 0.5, 0.05, 1, "쫓을 사람이 없을 때 돌아다니는 속도: 이동 속도의 몇 배인지", "배", "몸·이동"));
        l.add(d("wander_pause_seconds", 2, 0, 30, "돌아다니다 도착하면 쉬는 시간(실제로는 절반~1.5배 사이로 흔들린다)", "초", "몸·이동"));
        return l;
    }

    private static List<Def> sea(double radius, double speed, double hits, List<List<Def>> patterns) {
        List<Def> l = commonDefs(radius);
        set(l, "speed_ratio", speed);
        set(l, "hp_hits_per_player", hits);
        for (List<Def> p : patterns) l.addAll(p);
        l.addAll(bodyDefs());
        l.addAll(rewardDefs());
        return l;
    }

    /** 심해 문어 */
    private static List<Def> deepOctopus() {
        List<Def> sweep = pattern("촉수 휘두르기", "sweep", "촉수 휘두르기", 16, 1.5, "촉수가 쓸고 갈 부채꼴이 붉게 보인다");
        sweep.add(d("sweep_lock_seconds", 0.4, 0, 5, "예고 마지막 이 시간 동안은 방향을 고정한다", "초", "촉수 휘두르기"));
        sweep.add(d("sweep_arms", 3, 1, 8, "촉수 수", "개", "촉수 휘두르기"));
        sweep.add(d("sweep_rage_arms", 4, 1, 8, "분노 중 촉수 수", "개", "촉수 휘두르기"));
        sweep.add(d("sweep_angle_deg", 60, 5, 360, "한 번 휘두를 때 촉수가 도는 각도(이 호가 위험 구역이고 호 사이는 빈 곳)", "도", "촉수 휘두르기"));
        sweep.add(d("sweep_seconds", 2.5, 0.2, 10, "촉수가 도는 시간", "초", "촉수 휘두르기"));
        sweep.add(d("sweep_range_px", 1800, 100, 20000, "촉수 길이(몸 중심에서 잰다)", "px", "촉수 휘두르기"));
        sweep.add(d("sweep_width_px", 240, 20, 3000, "촉수 폭", "px", "촉수 휘두르기"));
        sweep.add(d("sweep_hp_percent", 8, 0, 100, "촉수에 맞으면 잃는 체력(최대 체력의 %, 한 번 휘두르는 동안 한 번). 분노 중에는 rage_damage_mult배", "%", "촉수 휘두르기"));
        sweep.add(d("sweep_knock_px", 600, 0, 10000, "촉수에 맞은 사람이 튕기는 거리", "px", "촉수 휘두르기"));
        List<Def> ink = pattern("먹물", "ink", "먹물", 20, 1.5, "떨어질 자리마다 점선 원이 뜬다");
        ink.add(d("ink_count", 3, 1, 20, "한 번에 던지는 먹물 장판 수(공격 대상 자리에 먼저, 다음은 다른 사람 자리, 남으면 대상 둘레)", "개", "먹물"));
        ink.add(d("ink_rage_count", 5, 0, 20, "분노 중 먹물 장판 수(0이면 그대로)", "개", "먹물"));
        ink.add(d("ink_radius_px", 380, 30, 3000, "먹물 장판 반지름", "px", "먹물"));
        ink.add(d("ink_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 자리를 고정한다(그 전에는 대상을 따라간다)", "초", "먹물"));
        ink.add(d("ink_scatter", 1.2, 0, 10, "남는 먹물을 공격 대상에서 떨어뜨리는 거리: 반지름의 몇 배인지", "배", "먹물"));
        ink.add(d("ink_seconds", 8, 1, 120, "먹물 장판이 바닥에 남는 시간", "초", "먹물"));
        ink.add(d("ink_blind_seconds", 2, 0.2, 30, "장판에 닿은 사람이 암흑(시야가 자기 원 둘레로 좁아짐)에 걸리는 시간(서 있는 동안 계속 이어지고 벗어나면 이 시간 뒤 풀린다). 피해는 없다", "초", "먹물"));
        List<Def> pull = pattern("빨판 끌기", "pull", "빨판 끌기", 30, 2, "몸이 움츠러든다");
        pull.add(d("pull_range_px", 2400, 100, 30000, "끌어당기는 범위(몸 중심에서 잰다)", "px", "빨판 끌기"));
        pull.add(d("pull_seconds", 1.5, 0.2, 10, "끌어당기는 시간", "초", "빨판 끌기"));
        pull.add(d("pull_speed", 600, 50, 10000, "끌려오는 속도(사람 최대 속도보다 빠르면 걸어서는 못 벗어난다)", "px/초", "빨판 끌기"));
        pull.add(d("pull_contact_px", 300, 0, 5000, "끌기가 끝날 때 몸 가장자리에서 이 거리 안으로 끌려온 사람이 맞는다", "px", "빨판 끌기"));
        pull.add(d("pull_hp_percent", 10, 0, 100, "끌려와서 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "빨판 끌기"));
        pull.add(d("pull_knock_px", 500, 0, 10000, "끌기 끝에 맞은 사람이 튕기는 거리", "px", "빨판 끌기"));
        pull.add(d("pull_stun_seconds", 2, 0, 10, "끌기가 끝난 뒤 무방비로 서 있는 시간(모여 있는 사람들이 때릴 틈)", "초", "빨판 끌기"));
        return sea(900, 0.9, 60, List.of(sweep, ink, pull));
    }

    /** 전기뱀장어 */
    private static List<Def> electricEel() {
        List<Def> lane = pattern("번개 줄기", "lane", "번개 줄기", 14, 1.5, "대상을 지나는 가로 띠가 노랗게 깜빡인다");
        lane.add(d("lane_lock_seconds", 0.4, 0, 5, "예고 마지막 이 시간 동안은 띠를 고정한다", "초", "번개 줄기"));
        lane.add(d("lane_count", 3, 1, 12, "띠 수", "줄", "번개 줄기"));
        lane.add(d("lane_rage_count", 5, 1, 12, "분노 중 띠 수", "줄", "번개 줄기"));
        lane.add(d("lane_width_px", 300, 20, 5000, "띠 폭", "px", "번개 줄기"));
        lane.add(d("lane_gap_px", 700, 50, 10000, "띠 사이 간격(가운데에서 가운데)", "px", "번개 줄기"));
        lane.add(d("lane_hp_percent", 10, 0, 100, "띠에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "번개 줄기"));
        lane.add(d("lane_stun_seconds", 0.8, 0, 10, "띠에 맞은 사람이 기절하는 시간", "초", "번개 줄기"));
        List<Def> chain = pattern("감전 연쇄", "chain", "감전 연쇄", 18, 2, "공격 대상 머리 위에 번개 표식이 뜬다");
        chain.add(d("chain_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 표식 자리를 고정한다", "초", "감전 연쇄"));
        chain.add(d("chain_range_px", 800, 50, 10000, "번개가 튀는 거리(서로 이 거리보다 멀면 이어지지 않는다)", "px", "감전 연쇄"));
        chain.add(d("chain_max", 4, 1, 30, "번개에 맞는 최대 사람 수(표식 대상 포함)", "명", "감전 연쇄"));
        chain.add(d("chain_rage_max", 6, 1, 30, "분노 중 번개에 맞는 최대 사람 수", "명", "감전 연쇄"));
        chain.add(d("chain_hp_percent", 8, 0, 100, "번개에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "감전 연쇄"));
        List<Def> storm = pattern("번개 폭풍", "storm", "번개 폭풍", 22, 1, "보스가 멈춰 전기를 모은다");
        storm.add(d("storm_count", 8, 1, 60, "번개가 떨어지는 곳 수", "곳", "번개 폭풍"));
        storm.add(d("storm_rage_count", 12, 1, 60, "분노 중 번개가 떨어지는 곳 수", "곳", "번개 폭풍"));
        storm.add(d("storm_interval", 0.25, 0.05, 5, "번개를 하나씩 표시하는 간격", "초", "번개 폭풍"));
        storm.add(d("storm_warn_seconds", 1, 0.2, 5, "표시된 뒤 번개가 떨어지기까지(피할 시간)", "초", "번개 폭풍"));
        storm.add(d("storm_radius_px", 260, 30, 3000, "번개 하나의 맞는 범위 반지름", "px", "번개 폭풍"));
        storm.add(d("storm_hp_percent", 7, 0, 100, "번개에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "번개 폭풍"));
        return sea(700, 1.3, 60, List.of(lane, chain, storm));
    }

    /** 복어 대왕 */
    private static List<Def> pufferKing() {
        List<Def> inflate = pattern("부풀기", "inflate", "부풀기", 24, 2, "몸이 풍선처럼 부풀고 판정 범위가 점선 원으로 보인다");
        inflate.add(d("inflate_radius_px", 2200, 100, 30000, "터질 때 맞는 범위 반지름(몸 중심에서 잰다)", "px", "부풀기"));
        inflate.add(d("inflate_hp_percent", 14, 0, 100, "터지는 가시에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "부풀기"));
        inflate.add(d("inflate_knock_px", 1500, 0, 20000, "맞은 사람이 튕기는 거리", "px", "부풀기"));
        inflate.add(d("inflate_stun_seconds", 2, 0, 10, "터진 뒤 쪼그라들어 무방비로 서 있는 시간(딜 타임)", "초", "부풀기"));
        inflate.add(d("inflate_rage_count", 2, 1, 5, "분노 중 연달아 터지는 횟수(마지막만 전체 크기)", "회", "부풀기"));
        inflate.add(d("inflate_small_ratio", 0.6, 0.1, 1, "분노 중 앞선 부풀기의 범위: 전체 크기의 몇 배인지", "배", "부풀기"));
        inflate.add(d("inflate_chain_windup", 0.8, 0.2, 5, "연달아 터질 때 다음 부풀기를 예고하는 시간", "초", "부풀기"));
        List<Def> spike = pattern("가시 발사", "spike", "가시 발사", 15, 1.2, "몸에서 방사형 가이드 선이 비친다");
        spike.add(d("spike_count", 16, 4, 64, "한 묶음의 가시 수(각도가 고르게 퍼진다). 분노하면 반 칸 어긋난 묶음이 한 번 더 나간다", "발", "가시 발사"));
        spike.add(d("spike_gap_seconds", 0.5, 0.1, 5, "분노 중 두 번째 묶음이 나가기까지의 간격", "초", "가시 발사"));
        spike.add(d("spike_speed", 1500, 100, 20000, "가시가 날아가는 속도", "px/초", "가시 발사"));
        spike.add(d("spike_range_px", 3000, 100, 100000, "가시가 날아가는 거리", "px", "가시 발사"));
        spike.add(d("spike_radius_px", 50, 5, 1000, "가시 하나의 맞는 범위 반지름", "px", "가시 발사"));
        spike.add(d("spike_hp_percent", 5, 0, 100, "가시에 맞으면 잃는 체력(최대 체력의 %, 한 묶음에서 한 사람에게 한 번). 분노 중에는 rage_damage_mult배", "%", "가시 발사"));
        spike.add(d("spike_poison_seconds", 4, 0, 60, "가시에 맞으면 독에 걸리는 시간(0이면 독 없음). 정화 스킬로 풀 수 있다", "초", "가시 발사"));
        spike.add(d("spike_poison_percent", 1.5, 0, 100, "독이 1초마다 깎는 체력(최대 체력의 %). 체력만 닳고 키워드는 잃지 않는다", "%", "가시 발사"));
        List<Def> bubble = pattern("거품 추적", "bubble", "거품 추적", 20, 1, "보스가 숨을 들이쉰다");
        bubble.add(d("bubble_count", 5, 1, 30, "거품 수(서로 다른 사람을 하나씩 따라간다)", "개", "거품 추적"));
        bubble.add(d("bubble_speed", 280, 20, 5000, "거품이 쫓아가는 속도", "px/초", "거품 추적"));
        bubble.add(d("bubble_seconds", 6, 1, 60, "거품이 닿지 않아도 터지는 시간", "초", "거품 추적"));
        bubble.add(d("bubble_radius_px", 320, 30, 5000, "거품이 터질 때 맞는 범위 반지름", "px", "거품 추적"));
        bubble.add(d("bubble_hp_percent", 6, 0, 100, "터지는 거품에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "거품 추적"));
        return sea(600, 1.1, 60, List.of(inflate, spike, bubble));
    }

    /** 대형 상어 */
    private static List<Def> greatShark() {
        List<Def> hunt = pattern("사냥 돌진", "hunt", "사냥 돌진", 22, 1.2, "체력이 가장 낮은 사람에게 붉은 길이 이어진다");
        hunt.add(d("hunt_lock_seconds", 0.3, 0, 5, "예고 마지막 이 시간 동안은 방향을 고정한다", "초", "사냥 돌진"));
        hunt.add(d("hunt_speed_mult", 2.2, 1, 10, "돌진 속도: 보스 기본 속도의 몇 배", "배", "사냥 돌진"));
        hunt.add(d("hunt_seconds", 1, 0.2, 10, "돌진 한 번이 이어지는 시간", "초", "사냥 돌진"));
        hunt.add(d("hunt_count", 3, 1, 10, "연달아 돌진하는 횟수(돌진마다 표식 대상 쪽으로 다시 겨눈다)", "회", "사냥 돌진"));
        hunt.add(d("hunt_rage_count", 5, 1, 10, "분노 중 연달아 돌진하는 횟수", "회", "사냥 돌진"));
        hunt.add(d("hunt_gap_seconds", 0.4, 0, 5, "돌진과 돌진 사이 멈추는 시간(다음 길이 잠깐 보인다)", "초", "사냥 돌진"));
        hunt.add(d("hunt_hp_percent", 10, 0, 100, "돌진에 맞으면 잃는 체력(최대 체력의 %, 한 번의 돌진에 한 번). 분노 중에는 rage_damage_mult배", "%", "사냥 돌진"));
        List<Def> ambush = pattern("급습", "ambush", "급습", 26, 0.8, "보스가 물속으로 잠기려 한다");
        ambush.add(d("ambush_dive_seconds", 3, 0.5, 20, "물속에서 사람 둘레를 쫓는 시간(떠 있는 동안은 몸 판정이 없다)", "초", "급습"));
        ambush.add(d("ambush_lock_seconds", 0.6, 0.1, 5, "물기 직전 점선 원이 뜨고 자리가 고정되는 시간(그 사이에 피할 수 있다)", "초", "급습"));
        ambush.add(d("ambush_speed_mult", 1.6, 0.5, 10, "잠수 중 쫓는 속도: 보스 기본 속도의 몇 배", "배", "급습"));
        ambush.add(d("ambush_radius_px", 450, 50, 5000, "무는 범위(점선 원) 반지름", "px", "급습"));
        ambush.add(d("ambush_hp_percent", 15, 0, 100, "물리면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "급습"));
        ambush.add(d("ambush_knock_px", 1200, 0, 20000, "물린 사람이 튕기는 거리", "px", "급습"));
        ambush.add(d("ambush_stun_seconds", 2, 0, 10, "문 뒤 무방비로 서 있는 시간(딜 타임)", "초", "급습"));
        ambush.add(d("ambush_rage_count", 2, 1, 5, "분노 중 연달아 무는 횟수(두 번째부터 잠수는 절반)", "회", "급습"));
        List<Def> tail = pattern("꼬리 치기", "tail", "꼬리 치기", 16, 1, "보스 뒤쪽 반원이 붉게 표시된다");
        tail.add(d("tail_range_px", 1500, 100, 20000, "꼬리가 닿는 거리(몸 중심에서 잰다)", "px", "꼬리 치기"));
        tail.add(d("tail_hp_percent", 9, 0, 100, "꼬리에 맞으면 잃는 체력(최대 체력의 %). 분노하면 전방위. 분노 중에는 rage_damage_mult배", "%", "꼬리 치기"));
        tail.add(d("tail_knock_px", 1500, 0, 20000, "꼬리에 맞은 사람이 튕기는 거리", "px", "꼬리 치기"));
        return sea(850, 1.2, 60, List.of(hunt, ambush, tail));
    }

    /** 대왕 고래 */
    private static List<Def> greatWhale() {
        List<Def> spout = pattern("물기둥", "spout", "물기둥", 14, 1.5, "대상 둘레에 점선 원이 뜬다");
        spout.add(d("spout_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 자리를 고정한다", "초", "물기둥"));
        spout.add(d("spout_count", 4, 1, 30, "물기둥 수(첫 번째는 대상 바로 그 자리)", "개", "물기둥"));
        spout.add(d("spout_rage_count", 8, 1, 30, "분노 중 물기둥 수", "개", "물기둥"));
        spout.add(d("spout_radius_px", 350, 30, 5000, "물기둥 하나의 맞는 범위 반지름", "px", "물기둥"));
        spout.add(d("spout_hp_percent", 8, 0, 100, "물기둥에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "물기둥"));
        spout.add(d("spout_knock_px", 1200, 0, 20000, "물기둥에 맞은 사람이 튕기는 거리", "px", "물기둥"));
        List<Def> wave = pattern("거대 파도", "wave", "거대 파도", 24, 2.5, "몸을 젖히고 대상 쪽에 붉은 띠가 깜빡인다");
        wave.add(d("wave_lock_seconds", 0.5, 0, 5, "예고 마지막 이 시간 동안은 방향을 고정한다", "초", "거대 파도"));
        wave.add(d("wave_width_px", 1800, 100, 20000, "파도 띠 폭", "px", "거대 파도"));
        wave.add(d("wave_speed", 1400, 100, 20000, "파도 앞면이 전진하는 속도", "px/초", "거대 파도"));
        wave.add(d("wave_band_px", 500, 50, 5000, "파도 앞면의 두께(앞면이 이 두께만큼 지나는 동안 닿으면 맞는다)", "px", "거대 파도"));
        wave.add(d("wave_hp_percent", 10, 0, 100, "파도에 맞으면 잃는 체력(최대 체력의 %). 분노 중에는 rage_damage_mult배", "%", "거대 파도"));
        wave.add(d("wave_push_px", 1500, 0, 20000, "파도에 맞은 사람이 파도 방향으로 밀려나는 거리", "px", "거대 파도"));
        wave.add(d("wave_rage_angle_deg", 25, 0, 90, "분노 중 파도 둘이 대상 방향에서 벌어지는 각도(±)", "도", "거대 파도"));
        List<Def> song = pattern("고래의 노래", "song", "고래의 노래", 28, 2, "동심원 파동이 퍼진다");
        song.add(d("song_radius_px", 1800, 100, 30000, "노래가 닿는 범위 반지름(몸 중심에서 잰다)", "px", "고래의 노래"));
        song.add(d("song_confuse_seconds", 3, 0.5, 30, "범위 안의 사람이 혼란(방향 키 반대)에 걸리는 시간. 피해는 없다", "초", "고래의 노래"));
        return sea(1300, 0.6, 90, List.of(spout, wave, song));
    }
}
