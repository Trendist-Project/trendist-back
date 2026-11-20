package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/** 대형 상어(4.35 바다 보스): 약한 사람을 노리는 포식자. 사냥 돌진(표식 대상)·급습(점선 원)·꼬리 치기(뒤는 위험)를 쓴다. 공통 틀은 SkillBoss. */
final class GreatShark extends SkillBoss {
    private GreatShark(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(new HuntSkill(), new AmbushSkill(), new TailSkill()));
    }

    static GreatShark spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = param(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new GreatShark(id, spec, radius, start, room.clock(), param(p, "duration_seconds"), at[0], at[1], param(p, "opening_delay_seconds"));
    }
}
