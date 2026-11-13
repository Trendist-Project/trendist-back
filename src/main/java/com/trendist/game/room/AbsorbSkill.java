package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 흡수(4.33): 조준 없이 사거리(화면 기준 bolt_reach_px) 안의 모든 원과 선을 잇고, 쓰는 순간 1개, 그 뒤 absorb_tick_seconds마다 1개씩
 * absorb_seconds 동안 키워드를 빼앗는다(기본 원마다 3개, 한 번이 히트 하나). 이미 이은 선이 있으면 끊고 새로 잇는다.
 * 쓴 사람이나 상대가 사라지거나 죽거나 상대가 세이프존에 들어가거나, 사거리를 벗어나거나, 쓴 사람이 끌려오면 그 선만 끊긴다. 사거리 안에 아무도 없으면 쓰지 않는다.
 */
final class AbsorbSkill implements PlayerSkill {
    private static final class Tether {
        final Room.Member owner, target; final double range, until; double nextAt; int left;
        Tether(Room.Member owner, Room.Member target, double range, double nextAt, int left, double until) {
            this.owner = owner; this.target = target; this.range = range; this.nextAt = nextAt; this.left = left; this.until = until;
        }
    }

    private final Room room;
    private final List<Tether> tethers = new ArrayList<>();

    AbsorbSkill(Room room) { this.room = room; }

    public String code() { return "absorb"; }
    public boolean offensive() { return true; }

    public boolean cast(Room.Member m, double ax, double ay) {
        SkillConfig c = room.skillConfig();
        double range = room.reachWorld(c.param("bolt_reach_px"), m.displayRadius);
        List<Room.Member> targets = room.targetsInReach(m, range);
        if (targets.isEmpty()) { room.tell(m, SkillFx.fail(code(), "noTarget")); return false; }
        endWhere(b -> b.owner == m);
        double seconds = c.param("absorb_seconds"), every = Math.max(0.05, c.param("absorb_tick_seconds"));
        int steals = Math.max(1, (int) Math.round(seconds / every));
        for (Room.Member t : targets) {
            Tether b = new Tether(m, t, range, room.now(), steals, room.now() + seconds);
            tethers.add(b);
            room.broadcast(SkillFx.absorb(m.entityNo, t.entityNo, SkillFx.ms(seconds), range));
            steal(b, every); // 0초에 1개
        }
        return true;
    }

    private void steal(Tether b, double every) {
        room.skillHit(b.owner, b.target, code(), 1);
        room.broadcast(SkillFx.absorbTick(b.owner.entityNo, b.target.entityNo));
        b.left--; b.nextAt += every;
    }

    public void update(double dt) {
        double every = Math.max(0.05, room.skillConfig().param("absorb_tick_seconds"));
        for (Iterator<Tether> it = tethers.iterator(); it.hasNext(); ) {
            Tether b = it.next();
            Room.Member o = b.owner, t = b.target;
            boolean broken = !room.present(o) || !room.present(t) || t.safe || o.hookedBy != null
                    || Math.hypot(t.x - o.x, t.y - o.y) - o.displayRadius - t.displayRadius > b.range;
            if (broken || room.now() + 1e-9 >= b.until) { it.remove(); room.broadcast(SkillFx.absorbEnd(o.entityNo, t.entityNo)); continue; }
            if (b.left > 0 && room.now() + 1e-9 >= b.nextAt) steal(b, every);
        }
    }

    /** 정화: 나(target)를 향한 선을 끊는다 */
    public void release(Room.Member target) { endWhere(b -> b.target == target); }

    private void endWhere(java.util.function.Predicate<Tether> which) {
        for (Iterator<Tether> it = tethers.iterator(); it.hasNext(); ) {
            Tether b = it.next();
            if (!which.test(b)) continue;
            it.remove();
            room.broadcast(SkillFx.absorbEnd(b.owner.entityNo, b.target.entityNo));
        }
    }
}
