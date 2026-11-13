package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 화염구(코드 fireball, 4.33): 조준점에 가장 가까운 상대를 노려(확정 타겟팅) fireball_count발을 fireball_interval_seconds 간격으로 던진다.
 * 한 발은 상대를 끝까지 따라가 닿으면 키워드 1개를 잃게 한다(히트 하나). 닿는 순간 상대가 세이프존·무적·정화 면역이면 꺼진다.
 * 상대나 쓴 사람이 사라지거나 MAX_FLIGHT초 넘게 날면 거둔다. 대상이 없으면 쓰지 않는다({skillFail: noTarget}).
 */
final class FireballSkill implements PlayerSkill {
    /** 한 발이 날 수 있는 가장 긴 시간(초). 상대가 계속 도망쳐도 끝없이 쫓지 않게 하는 안전장치다 */
    static final double MAX_FLIGHT = 3;

    private static final class Shot {
        final int id; final Room.Member owner, target; final double launchAt; boolean launched; double x, y, flown;
        Shot(int id, Room.Member owner, Room.Member target, double launchAt) { this.id = id; this.owner = owner; this.target = target; this.launchAt = launchAt; }
    }

    private final Room room;
    private final List<Shot> shots = new ArrayList<>();
    private int nextId = 1;

    FireballSkill(Room room) { this.room = room; }

    public String code() { return "fireball"; }
    public boolean offensive() { return true; }

    public boolean cast(Room.Member m, double ax, double ay) {
        SkillConfig c = room.skillConfig();
        Room.Member t = room.pickTarget(m, ax, ay);
        if (t == null) { room.tell(m, SkillFx.fail(code(), "noTarget")); return false; }
        int count = Math.max(1, c.intParam("fireball_count"));
        for (int i = 0; i < count; i++) shots.add(new Shot(nextId++, m, t, room.now() + i * c.param("fireball_interval_seconds")));
        return true;
    }

    public void update(double dt) {
        SkillConfig c = room.skillConfig();
        for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
            Shot f = it.next();
            Room.Member o = f.owner, t = f.target;
            if (!room.present(o) || !room.present(t)) { it.remove(); if (f.launched) room.broadcast(SkillFx.fireballEnd(f.id)); continue; }
            if (room.now() < f.launchAt) continue;
            double speed = room.reachWorld(c.param("hook_reach_px") / Math.max(0.05, c.param("hook_seconds")), o.displayRadius);
            if (!f.launched) {
                double dx = t.x - o.x, dy = t.y - o.y, d = Math.hypot(dx, dy);
                f.x = o.x + (d < 1e-6 ? 0 : dx / d) * o.displayRadius; f.y = o.y + (d < 1e-6 ? -1 : dy / d) * o.displayRadius;
                f.launched = true;
                room.broadcast(SkillFx.fireball(f.id, o.entityNo, t.entityNo, f.x, f.y, speed));
            }
            double dx = t.x - f.x, dy = t.y - f.y, d = Math.hypot(dx, dy), step = speed * dt;
            if (d <= step + t.displayRadius * 0.6) {
                it.remove();
                boolean ok = room.targetable(o, t);
                room.broadcast(SkillFx.fireballEnd(f.id, ok, t.entityNo, t.x, t.y));
                if (ok) room.skillHit(o, t, code(), 1);
                continue;
            }
            f.x += dx / d * step; f.y += dy / d * step; f.flown += dt;
            if (f.flown >= MAX_FLIGHT) { it.remove(); room.broadcast(SkillFx.fireballEnd(f.id)); }
        }
    }
}
