package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/** 대왕 고래(4.35 바다 보스): 가장 크고 느린 주말 레이드 보스. 물기둥(점선 원)·거대 파도(띠 옆으로)·고래의 노래(혼란)를 쓴다. 공통 틀은 SkillBoss. */
final class GreatWhale extends SkillBoss {
    private GreatWhale(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration, x, y, opening, List.of(new SpoutSkill(), new TidalWaveSkill(), new SongSkill()));
    }

    static GreatWhale spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        Start start = startFor(room, spec);
        double radius = param(p, "radius");
        double[] at = spawnPoint(room, radius);
        return new GreatWhale(id, spec, radius, start, room.clock(), param(p, "duration_seconds"), at[0], at[1], param(p, "opening_delay_seconds"));
    }
}
