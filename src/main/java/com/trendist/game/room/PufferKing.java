package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/** 복어 대왕(4.35 바다 보스): 둥글고 귀여운 복어. 부풀기(원 밖으로)·가시 발사(빈틈으로)·거품 추적(몰고 가 터뜨리기)을 쓴다. 공통 틀은 SkillBoss. */
final class PufferKing extends SkillBoss {
    private PufferKing(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(new InflateSkill(), new SpikeSkill(), new BubbleSkill()));
    }

    static PufferKing spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = param(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new PufferKing(id, spec, radius, start, room.clock(), param(p, "duration_seconds"), at[0], at[1], param(p, "opening_delay_seconds"));
    }
}
