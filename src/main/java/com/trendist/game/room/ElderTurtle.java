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

    /** 체력(HP)을 정하고 만든다. HP = 사람 수 × 참여율 × 1인당 타격 수 × 기준 유행도 × 타격당 배수 × 여유 */
    static ElderTurtle spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        int humans = 0; double sum = 0, top = 0;
        for (Room.Member m : room.members()) {
            if (m.bot || m.offline) continue;
            humans++; sum += m.score; top = Math.max(top, m.score);
        }
        double avg = humans == 0 ? 0 : sum / humans;
        double w = d(p, "hp_top_weight"), ref = avg * (1 - w) + top * w;
        double flameRatio = d(p, "hp_flame_ratio"), perHit = flameRatio * d(p, "flame_damage_mult") + (1 - flameRatio);
        double n = Math.max(humans, d(p, "hp_min_players"));
        double hp = n * d(p, "hp_participation_rate") * d(p, "hp_hits_per_player") * ref * perHit * d(p, "hp_margin");
        hp = Math.max(d(p, "hp_min"), Math.min(d(p, "hp_max"), hp));
        long maxHp = Math.round(hp);
        double now = room.clock(), half = room.half(), radius = d(p, "radius");
        double reach = Math.max(0, half - radius), ang = room.random().nextDouble(Math.PI * 2);
        double sx = Math.cos(ang) * reach * 0.6, sy = Math.sin(ang) * reach * 0.6; // 가운데(세이프존)에서 떨어진 곳에서 나온다
        return new ElderTurtle(id, spec, radius, new Start(maxHp, humans, Math.round(avg), Math.round(top)), now, d(p, "duration_seconds"), sx, sy, d(p, "opening_delay_seconds"));
    }

    private static double d(Map<String, Double> p, String k) { Double v = p.get(k); return v == null ? 0 : v; }
}
