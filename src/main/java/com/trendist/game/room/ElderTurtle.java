package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/**
 * 장로 거북이(4.35): 첫 번째 보스몹. 몸이 아주 크고 느리게 가장 가까운 사람을 따라다니며 돌진·충격고리를 쓴다.
 * 추적·패턴 선택·분노·충돌 규칙은 공통 틀(SkillBoss)이, 패턴은 DashSkill·RingSkill이 맡고 이 클래스는 체력 계산과 패턴 목록만 정한다.
 * 체력은 유행도 값이고, 부딪힌 사람의 유행도(불꽃이면 배수)만큼 줄어든다.
 */
final class ElderTurtle extends SkillBoss {
    private ElderTurtle(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(new DashSkill(), new RingSkill()));
    }

    /** 체력(HP)을 정하고 만든다(계산식은 SkillBoss.startFor, 모든 보스 공통) */
    static ElderTurtle spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = d(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new ElderTurtle(id, spec, radius, start, room.clock(), d(p, "duration_seconds"), at[0], at[1], d(p, "opening_delay_seconds"));
    }

    private static double d(Map<String, Double> p, String k) { Double v = p.get(k); return v == null ? 0 : v; }
}
