package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/**
 * 심해 문어(4.35 바다 보스): 깊은 바다에서 깨어난 보라색 문어. 가장 가까운 사람을 쫓아다니며 촉수 휘두르기(빈 호로 피하기)·먹물(암흑 장판)·빨판 끌기(끌려와 딜 타임)를 쓴다.
 * 추적·패턴 선택·분노·충돌 규칙은 공통 틀(SkillBoss)이 맡고 이 클래스는 패턴 목록만 정한다.
 */
final class DeepOctopus extends SkillBoss {
    private DeepOctopus(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(
                new SweepSkill(),
                new PatchSkill("ink", "ink_blind_seconds", "ink", (room, m, hold) -> room.status().darkZone(m, hold)),
                new PullSkill()));
    }

    static DeepOctopus spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = param(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new DeepOctopus(id, spec, radius, start, room.clock(), param(p, "duration_seconds"), at[0], at[1], param(p, "opening_delay_seconds"));
    }
}
