package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/** 전기뱀장어(4.35 바다 보스): 작고 가장 빠른 보스. 번개 줄기(줄 사이로)·감전 연쇄(흩어지기)·번개 폭풍(계속 움직이기)을 쓴다. 공통 틀은 SkillBoss. */
final class ElectricEel extends SkillBoss {
    private ElectricEel(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(new LaneSkill(), new ChainSkill(), new StormSkill()));
    }

    static ElectricEel spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = param(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new ElectricEel(id, spec, radius, start, room.clock(), param(p, "duration_seconds"), at[0], at[1], param(p, "opening_delay_seconds"));
    }
}
