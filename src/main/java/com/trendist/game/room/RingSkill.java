package com.trendist.game.room;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 충격고리: 예고 동안 몸이 붉게 맥동하다가 몸 가장자리에서 맵 전체로 퍼진다. 앞면이 닿은 사람은 보스에서 가까울수록 많이
 * (`ring_max_hp_percent`) 멀수록 적게(`ring_min_hp_percent`) 최대 체력의 %를 잃는다. 세이프존 안이거나 무적(방패 스킬 등) 상태가 아니면 불꽃이어도 맞는다.
 */
final class RingSkill extends BossSkill {
    private double ringR, ringMax;
    private final Set<Room.Member> hit = new HashSet<>();

    RingSkill() { super("ring"); }

    @Override String windupPhase() { return "ring_windup"; }
    @Override String activePhase() { return "ring"; }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        ringR = b.radius; ringMax = b.radius + range(b, room); hit.clear();
        Map<String, Object> extra = new HashMap<>();
        extra.put("x", b.x); extra.put("y", b.y); extra.put("r0", b.radius); extra.put("rMax", ringMax); extra.put("speed", b.p(room, "ring_speed"));
        room.broadcast(b.fx("ring", extra));
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        ringR += b.p(room, "ring_speed") * dt;
        double range = ringMax - b.radius, maxPct = b.p(room, "ring_max_hp_percent"), minPct = b.p(room, "ring_min_hp_percent");
        for (Room.Member m : new java.util.ArrayList<>(room.members())) {
            if (m.bot || m.dead || hit.contains(m)) continue;
            double dist = Math.hypot(m.x - b.x, m.y - b.y);
            if (dist - m.displayRadius > ringR) continue; // 아직 앞면이 닿지 않았다
            hit.add(m);
            if (m.safe || m.protectUntil > now) continue; // 세이프존 안·무적 중이면 맞지 않는다(불꽃이어도 맞는다)
            double f = range <= 0 ? 1 : Math.min(1, Math.max(0, dist - b.radius) / range); // 0(바로 옆) ~ 1(가장 먼 곳)
            b.hurtPlayer(room, m, maxPct - (maxPct - minPct) * f);
        }
        return ringR >= ringMax;
    }

    private double range(SkillBoss b, Room room) {
        double r = b.p(room, "ring_range_px");
        return r > 0 ? r : room.half() * 1.5;
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) { return Map.of("k", "ring"); }
}
