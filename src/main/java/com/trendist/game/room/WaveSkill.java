package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 퍼지는 고리(4.33): 쓴 자리에서 쓴 순간의 내 원 크기로 시작해 화면 기준 repel_reach_px까지 퍼진다(범위·속도 모두 줌의 역수를 곱한 월드 길이).
 * 반발(코드 repel, drop=false)은 닿은 원을 바깥으로 밀고, 충격파(코드 quake, drop=true)는 밀지 않고 키워드 quake_drop_keywords개를 잃게 한다.
 * 한 고리에 한 번만 맞고, 세이프존·무적·정화 면역·끌려오는 원은 맞지 않는다.
 */
final class WaveSkill implements PlayerSkill {
    private record Wave(Room.Member owner, double x, double y, double r0, double rMax, double speed, double startedAt, Set<Room.Member> hit) {}

    private final Room room;
    private final String code;
    private final boolean drop;
    private final List<Wave> waves = new ArrayList<>();

    WaveSkill(Room room, String code, boolean drop) { this.room = room; this.code = code; this.drop = drop; }

    public String code() { return code; }
    public boolean offensive() { return true; }

    public boolean cast(Room.Member m, double ax, double ay) {
        SkillConfig c = room.skillConfig();
        double r0 = m.displayRadius, rMax = r0 + room.reachWorld(Math.max(1, c.param("repel_reach_px")), r0);
        double speed = room.reachWorld(room.tuning().maxSpeed() * c.param("repel_speed_multiplier"), r0);
        waves.add(new Wave(m, m.x, m.y, r0, rMax, speed, room.now(), new HashSet<>()));
        room.broadcast(SkillFx.wave(code, m.entityNo, m.x, m.y, r0, rMax, speed));
        return true;
    }

    public void update(double dt) {
        int n = room.skillConfig().intParam("quake_drop_keywords");
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            double radius = Math.min(w.rMax(), w.r0() + w.speed() * (room.now() - w.startedAt()));
            for (Room.Member t : room.all()) {
                if (!room.targetable(w.owner(), t) || t.hookedBy != null || w.hit().contains(t)) continue;
                double dx = t.x - w.x(), dy = t.y - w.y(), d = Math.hypot(dx, dy);
                if (d - t.displayRadius > radius) continue;
                w.hit().add(t);
                if (drop) {
                    room.skillHit(w.owner(), t, code, n);
                    room.broadcast(SkillFx.quakeHit(t.entityNo, n)); // 맞은 원 위에 표시
                    continue;
                }
                room.knock(t, d < 0.001 ? 1 : dx / d, d < 0.001 ? 0 : dy / d, room.skillConfig().param("repel_knockback"), room.tuning());
                t.targetX = null;
            }
            if (radius >= w.rMax()) it.remove();
        }
    }
}
