package com.trendist.game.room;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스킬 설정(4.33)을 방에 넘기는 하나뿐인 통로. cooldowns는 스킬 코드 → 쿨타임(초, DB skills, 쓴 때부터 센다),
 * params는 설정 이름(settings.name) → 값이다. 서버는 1초마다 settings·skills에서 새로 만들어 넣고(RoomManagerImpl), 테스트는 DEFAULT에서 바꿔 쓴다.
 * 이름이 없으면 DEFAULT_PARAMS의 값을 쓴다. 이 기본값은 SettingKeys의 기본값과 같아야 한다(SettingKeysTest가 확인한다).
 */
public record SkillConfig(Map<String, Double> cooldowns, Map<String, Double> params) {

    /** 스킬 수치의 설정 이름과 기본값. 순서는 의미 없다 */
    public static final Map<String, Double> DEFAULT_PARAMS;
    static {
        Map<String, Double> p = new LinkedHashMap<>();
        // 반발(repel)·충격파(quake): 퍼지는 속도(최대 속도의 배수), 범위(원 가장자리에서 화면 기준 px), 밀어내는 세기
        p.put("repel_speed_multiplier", 4.0); p.put("repel_reach_px", 300.0); p.put("repel_knockback", 1.5);
        // 갈고리: 사거리(화면 기준 px), 뻗는 시간, 끌려오는 시간. 투사체·화염구의 화면 속도도 갈고리가 뻗는 속도와 같다
        p.put("hook_reach_px", 300.0); p.put("hook_seconds", 0.15); p.put("hook_pull_seconds", 0.3);
        // 투사체(독·암흑·슬로우·스턴)·화염구·흡수의 사거리(화면 기준 px)
        p.put("bolt_reach_px", 600.0);
        p.put("invincibility_seconds", 3.0); p.put("stealth_seconds", 5.0); p.put("skill_heal_percent", 50.0);
        // 독: poison_ticks번, poison_tick_seconds마다 겹 수만큼 키워드를 잃는다. 겹 상한
        p.put("poison_ticks", 3.0); p.put("poison_tick_seconds", 3.0); p.put("poison_max_stacks", 10.0);
        p.put("darkness_seconds", 3.0);
        // 정화 뒤 새 CC에 걸리지 않는 시간(0이면 면역 없음)
        p.put("cleanse_immune_seconds", 0.0);
        // 슬로우: 지속 시간, 한 겹의 이동 속도(%), 겹 상한
        p.put("slow_seconds", 3.0); p.put("slow_percent", 50.0); p.put("slow_max_stacks", 3.0);
        p.put("stun_seconds", 1.0);
        // 화염구(fireball): 발 수와 던지는 간격
        p.put("fireball_count", 3.0); p.put("fireball_interval_seconds", 0.15);
        // 충격파(quake): 닿은 원이 잃는 키워드 수
        p.put("quake_drop_keywords", 1.0);
        // 흡수: 선이 이어지는 시간과 빼앗는 간격
        p.put("absorb_seconds", 3.0); p.put("absorb_tick_seconds", 1.0);
        // 속박·침묵·혼란: 걸리는 시간(초)
        p.put("root_seconds", 2.0); p.put("silence_seconds", 3.0); p.put("confuse_seconds", 3.0);
        // 공격 증폭(내가 주는 체력 피해 배수)·피해 감소(받는 체력 피해의 %): 유지 시간(초)
        p.put("amplify_seconds", 10.0); p.put("amplify_multiplier", 2.0); p.put("fortify_seconds", 10.0); p.put("fortify_percent", 50.0);
        // 보호막: 유지 시간(초)과 쉴드량(최대 체력의 절대 %)
        p.put("barrier_seconds", 10.0); p.put("barrier_hp_percent", 50.0);
        // 광폭화: 켜 둔 동안 1초마다 잃는 최대 체력의 %, 이 % 아래로는 더 깎이지 않고 자동으로 꺼진다
        p.put("berserk_drain_percent", 1.0); p.put("berserk_min_hp_percent", 1.0);
        // 장판(zone): 범위(원 가장자리에서 화면 기준 px), 유지 시간, 피해 간격(쓰자마자 0초부터 한 번씩)
        p.put("zone_reach_px", 300.0); p.put("zone_seconds", 3.0); p.put("zone_tick_seconds", 1.0);
        // 터지는 총알(burst): 맞은 뒤 터질 때까지의 시간
        p.put("burst_fuse_seconds", 2.0);
        // 스킬 히트 하나가 주는 체력 피해: 몸싸움(hpDamage)이 계산하는 피해의 몇 %인가(모든 스킬이 같은 값, 히트 하나에 한 번)
        p.put("skill_hit_damage_percent", 10.0);
        DEFAULT_PARAMS = Map.copyOf(p);
    }

    /** DB에 쿨타임이 없을 때의 기본(초). 처음 네 스킬은 30초, 나중에 더한 스킬은 0(없음)이다 */
    public static final Map<String, Double> DEFAULT_COOLDOWNS = Map.of("repel", 30.0, "hook", 30.0, "invincibility", 30.0, "stealth", 30.0);

    public static final SkillConfig DEFAULT = new SkillConfig(DEFAULT_COOLDOWNS, DEFAULT_PARAMS);

    /** 설정 이름 목록(서버가 settings에서 읽어 올 이름) */
    public static List<String> paramNames() { return List.copyOf(DEFAULT_PARAMS.keySet()); }

    public SkillConfig {
        cooldowns = Map.copyOf(cooldowns);
        params = Map.copyOf(params);
    }

    /**
     * 스킬을 쓴 뒤 효과가 이어지는 시간(초). 스킬 칸에 "켜져 있음" 표시를 띄우는 기준이다(광폭화처럼 켜고 끄는 스킬은 효과가 따로라 0).
     * 지속되는 스킬(장판·무적·은신·증폭·감소·보호막·흡수)만 있고, 맞은 상대에게 걸리는 효과(슬로우·독 등)는 내 칸의 표시가 아니라서 0이다.
     */
    public double activeSeconds(String code) {
        String name = ACTIVE_PARAM.get(code);
        return name == null ? 0 : param(name);
    }

    private static final Map<String, String> ACTIVE_PARAM = Map.of("zone", "zone_seconds", "invincibility", "invincibility_seconds", "stealth", "stealth_seconds",
            "amplify", "amplify_seconds", "fortify", "fortify_seconds", "barrier", "barrier_seconds", "absorb", "absorb_seconds");

    /** 스킬의 쿨타임(초). 모르면 DEFAULT_COOLDOWNS, 그것도 없으면 0 */
    public double cooldown(String code) {
        Double v = cooldowns.get(code);
        return v != null ? v : DEFAULT_COOLDOWNS.getOrDefault(code, 0.0);
    }

    /** 스킬 수치. 없으면 기본값. 모르는 이름이면 코드 실수이므로 바로 알린다 */
    public double param(String name) {
        Double v = params.get(name);
        if (v != null) return v;
        Double d = DEFAULT_PARAMS.get(name);
        if (d == null) throw new IllegalArgumentException("unknown skill param: " + name);
        return d;
    }

    public int intParam(String name) { return (int) Math.round(param(name)); }

    public SkillConfig withCooldown(String code, double seconds) {
        Map<String, Double> c = new HashMap<>(cooldowns); c.put(code, seconds);
        return new SkillConfig(c, params);
    }

    public SkillConfig withParam(String name, double value) {
        param(name); // 모르는 이름이면 실패
        Map<String, Double> p = new HashMap<>(params); p.put(name, value);
        return new SkillConfig(cooldowns, p);
    }

    /** 모든 스킬의 쿨타임을 없앤다(테스트용) */
    public SkillConfig withoutCooldowns() {
        Map<String, Double> c = new HashMap<>();
        for (String code : Room.SKILLS) c.put(code, 0.0);
        return new SkillConfig(c, params);
    }
}
