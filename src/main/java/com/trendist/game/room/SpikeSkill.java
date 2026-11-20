package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 가시 발사(복어 대왕, 4.35): 예고 동안 몸에서 방사형 가이드 선이 비치고, 균등한 각도로 가시 `spike_count`발이 한꺼번에 날아가 `spike_range_px`까지 간다.
 * 분노하면 `spike_gap_seconds` 뒤 각도를 반 칸 어긋나게 한 묶음이 더 나간다. 맞은 사람은 최대 체력의 `spike_hp_percent`%를 잃고 독에 걸려 `spike_poison_seconds` 동안
 * 1초마다 최대 체력의 `spike_poison_percent`%를 잃는다(체력만 닳고 키워드는 잃지 않는다, 정화 스킬로 풀 수 있다). 한 묶음에서 한 사람은 한 번만 맞는다.
 * 날아가는 가시와 독은 패턴이 끝난 뒤에도 계속된다(passiveTick). 세이프존 안·무적 중이면 맞지 않는다.
 */
final class SpikeSkill extends BossSkill {
    private static final class Volley {
        final double x, y, speed, range, r, startAt; final double[] angles; final Set<Room.Member> hit = new HashSet<>();
        Volley(double x, double y, double speed, double range, double r, double startAt, double[] angles) {
            this.x = x; this.y = y; this.speed = speed; this.range = range; this.r = r; this.startAt = startAt; this.angles = angles;
        }
    }
    private static final class Poison { double until, nextAt; }

    private final List<Volley> volleys = new ArrayList<>();
    private final Map<Room.Member, Poison> poisoned = new HashMap<>();
    private double rot, pendingAt;
    private boolean pending;

    SpikeSkill() { super("spike"); }

    @Override String windupPhase() { return "spike_windup"; }
    @Override String activePhase() { return "spike"; }

    @Override void windupStart(SkillBoss b, Room room, double now, Room.Member t) { rot = room.random().nextDouble(Math.PI * 2); }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("thorn");
        Map<String, Object> rad = new HashMap<>();
        rad.put("x", b.x); rad.put("y", b.y); rad.put("count", Math.round(b.p(room, "spike_count"))); rad.put("range", b.p(room, "spike_range_px")); rad.put("rot", Math.round(rot * 1000) / 1000.0);
        m.put("radial", rad);
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        fire(b, room, now, 0);
        pending = b.raged();
        pendingAt = now + b.p(room, "spike_gap_seconds");
    }

    private void fire(SkillBoss b, Room room, double now, double offsetSteps) {
        int n = Math.max(1, (int) Math.round(b.p(room, "spike_count")));
        double step = Math.PI * 2 / n;
        double[] angles = new double[n];
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < n; i++) { angles[i] = rot + (i + offsetSteps) * step; out.add(Math.round(angles[i] * 1000) / 1000.0); }
        double speed = Math.max(1, b.p(room, "spike_speed")), range = b.p(room, "spike_range_px"), r = b.p(room, "spike_radius_px");
        volleys.add(new Volley(b.x, b.y, speed, range, r, now, angles));
        Map<String, Object> e = new HashMap<>();
        e.put("x", b.x); e.put("y", b.y); e.put("speed", speed); e.put("range", range); e.put("r", r); e.put("angles", out); e.put("look", "spike");
        BossUtil.fx(room, b, "carrot", e);
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        if (pending && now >= pendingAt) { pending = false; fire(b, room, now, 0.5); }
        return !pending;
    }

    @Override
    void passiveTick(SkillBoss b, Room room, double now, double dt) {
        if (!volleys.isEmpty()) {
            double pct = b.p(room, "spike_hp_percent"), secs = b.p(room, "spike_poison_seconds");
            for (Iterator<Volley> it = volleys.iterator(); it.hasNext(); ) {
                Volley v = it.next();
                double d = v.speed * (now - v.startAt);
                if (d > v.range + v.r) { it.remove(); continue; }
                for (double a : v.angles) {
                    double cx = v.x + Math.cos(a) * d, cy = v.y + Math.sin(a) * d;
                    for (Room.Member m : BossUtil.targets(room, now)) {
                        if (v.hit.contains(m) || Math.hypot(m.x - cx, m.y - cy) >= v.r + m.displayRadius) continue;
                        v.hit.add(m);
                        b.hurtPlayer(room, m, pct);
                        if (!m.dead && secs > 0) {
                            Poison p = poisoned.computeIfAbsent(m, k -> new Poison());
                            p.until = now + secs; p.nextAt = now + 1;
                            room.status().bossPoison(m, secs);
                        }
                    }
                }
            }
        }
        if (poisoned.isEmpty()) return;
        double tick = b.p(room, "spike_poison_percent");
        for (Iterator<Map.Entry<Room.Member, Poison>> it = poisoned.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Room.Member, Poison> e = it.next();
            Room.Member m = e.getKey(); Poison p = e.getValue();
            if (m.dead || room.byEntity(m.entityNo) != m || !room.status().bossPoisoned(m)) { it.remove(); continue; } // 죽었거나 나갔거나 정화로 풀렸다
            if (now >= p.until) { room.status().bossPoisonEnd(m); it.remove(); continue; }
            if (now >= p.nextAt) { p.nextAt += 1; b.hurtPlayer(room, m, tick); }
        }
    }
}
