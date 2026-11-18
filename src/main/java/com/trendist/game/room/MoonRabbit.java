package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/**
 * 달토끼(4.35): 두 번째 보스몹. 통통한 토끼가 가장 가까운 사람을 쫓아다니며 깡충(달 모양 그림자 자리에 내려앉기)·끈끈 떡(바닥 장판)·당근 부채(부채꼴 투사체)를 쓴다.
 * 추적·패턴 선택·분노·충돌 규칙은 공통 틀(SkillBoss)이, 패턴은 HopSkill·MochiSkill·CarrotSkill이 맡고 이 클래스는 패턴 목록만 정한다
 * (체력 계산·시작 자리는 장로 거북이와 같은 SkillBoss.startFor·spawnPoint).
 */
final class MoonRabbit extends SkillBoss {
    private MoonRabbit(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(new HopSkill(), new MochiSkill(), new CarrotSkill()));
    }

    static MoonRabbit spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = param(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new MoonRabbit(id, spec, radius, start, room.clock(), param(p, "duration_seconds"), at[0], at[1], param(p, "opening_delay_seconds"));
    }
}
