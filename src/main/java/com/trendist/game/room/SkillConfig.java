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
        p.put("skill_shield_seconds", 3.0); p.put("stealth_seconds", 5.0); p.put("skill_heal_percent", 50.0);
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
        p.put("quake_drop_keywords", 3.0);
        // 흡수: 선이 이어지는 시간과 빼앗는 간격
        p.put("absorb_seconds", 3.0); p.put("absorb_tick_seconds", 1.0);
        DEFAULT_PARAMS = Map.copyOf(p);
    }

    /** DB에 쿨타임이 없을 때의 기본(초). 처음 네 스킬은 30초, 나중에 더한 스킬은 0(없음)이다 */
    public static final Map<String, Double> DEFAULT_COOLDOWNS = Map.of("repel", 30.0, "hook", 30.0, "shield", 30.0, "stealth", 30.0);

    public static final SkillConfig DEFAULT = new SkillConfig(DEFAULT_COOLDOWNS, DEFAULT_PARAMS);

    /** 설정 이름 목록(서버가 settings에서 읽어 올 이름) */
    public static List<String> paramNames() { return List.copyOf(DEFAULT_PARAMS.keySet()); }

    public SkillConfig {
        cooldowns = Map.copyOf(cooldowns);
        params = Map.copyOf(params);
    }

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
