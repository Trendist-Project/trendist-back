package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 장판(4.33): **시전자 원을 중심으로 따라다니는** 원형 장판을 깐다(시전자가 움직이면 장판도 같이 움직이고 판정도 시전자의 지금 자리 기준이다).
 * 반지름은 쓴 순간의 내 원 반지름에 가장자리에서 화면 기준 zone_reach_px를 더한 값으로 고정이고,
 * zone_seconds(기본 3초) 동안 남아 있다. **쓰자마자(0초) 그 위에 있던 원부터** zone_tick_seconds(기본 1초)마다 장판 위의 원에게 히트 하나(Room.skillHit: 콤보 +1,
 * 체력 피해 skill_hit_damage_percent, 키워드 1개 잃기)를 준다(3초 = 0·1·2초 세 번).
 * 장판에 걸치기만 해도(원 가장자리가 닿으면) 맞고, 세이프존·무적·정화 직후 면역인 원과 쓴 사람은 맞지 않는다. 쓴 사람이 방에서 나가면 장판도 사라진다.
 */
final class ZoneSkill implements PlayerSkill {
    private static final class Zone {
        final Room.Member owner; final double r, until, tick; double nextAt; int ticksLeft;
        Zone(Room.Member owner, double r, double until, double tick, double nextAt, int ticksLeft) {
            this.owner = owner; this.r = r; this.until = until; this.tick = tick; this.nextAt = nextAt; this.ticksLeft = ticksLeft;
        }
    }

    private final Room room;
    private final List<Zone> zones = new ArrayList<>();

    ZoneSkill(Room room) { this.room = room; }

    public String code() { return "zone"; }
    public boolean offensive() { return true; }

    public boolean cast(Room.Member m, double ax, double ay) {
        SkillConfig c = room.skillConfig();
        double r0 = m.displayRadius, r = r0 + room.reachWorld(Math.max(1, c.param("zone_reach_px")), r0);
        double seconds = c.param("zone_seconds"), tick = Math.max(0.1, c.param("zone_tick_seconds"));
        int ticks = Math.max(1, (int) Math.round(seconds / tick));
        Zone z = new Zone(m, r, room.now() + seconds, tick, room.now(), ticks);
        zones.add(z);
        room.broadcast(SkillFx.zone(m.entityNo, m.x, m.y, r, SkillFx.ms(seconds), SkillFx.ms(tick)));
        pulse(z); // 쓰자마자 0초 판정 // 화면은 e(시전자)의 지금 자리를 따라 그린다(x, y는 처음 자리)
        return true;
    }

    public void update(double dt) {
        for (Iterator<Zone> it = zones.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            if (!room.present(z.owner)) { it.remove(); room.broadcast(SkillFx.zoneEnd(z.owner.entityNo)); continue; } // 시전자가 나가거나 죽었다: 화면의 장판도 바로 걷는다
            if (z.ticksLeft > 0 && room.now() + 1e-9 >= z.nextAt) pulse(z); // 틱이 됐으면 먼저 판정하고, 그 뒤에 끝났는지 본다
            if (room.now() + 1e-9 >= z.until) it.remove();
        }
    }

    /** 시전자 둘레 장판 위(원 가장자리가 닿는)의 맞힐 수 있는 원 모두에게 히트 하나씩 */
    private void pulse(Zone z) {
        z.ticksLeft--;
        z.nextAt += z.tick;
        for (Room.Member t : room.all()) {
            if (!room.targetable(z.owner, t)) continue;
            if (Math.hypot(t.x - z.owner.x, t.y - z.owner.y) - t.displayRadius > z.r) continue;
            room.skillHit(z.owner, t, code(), 1);
        }
    }
}
