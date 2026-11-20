package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 촉수 휘두르기(심해 문어, 4.35): 예고 동안 촉수 `sweep_arms`개(분노하면 `sweep_rage_arms`개)가 쓸고 갈 부채꼴이 붉게 보이고(마지막 `sweep_lock_seconds`는 방향 고정),
 * 이어 촉수가 `sweep_seconds` 동안 `sweep_angle_deg`만큼 돈다(길이 `sweep_range_px`, 폭 `sweep_width_px`, 몸 중심에서 잰다). 닿은 사람은 한 번의 휘두름에 한 번
 * 최대 체력의 `sweep_hp_percent`%를 잃고 튕긴다. 촉수 사이의 빈 호·세이프존·무적 중이면 맞지 않는다.
 */
final class SweepSkill extends BossSkill {
    private double base, dir = 1, startAt, endAt;
    private int arms = 3;
    private final Set<Room.Member> hit = new HashSet<>();

    SweepSkill() { super("sweep"); }

    @Override String windupPhase() { return "sweep_windup"; }
    @Override String activePhase() { return "sweep"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override
    void windupStart(SkillBoss b, Room room, double now, Room.Member t) {
        arms = Math.max(1, (int) Math.round(b.p(room, b.raged() ? "sweep_rage_arms" : "sweep_arms")));
        dir = room.random().nextBoolean() ? 1 : -1;
        if (t != null) aim(b, t);
    }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "sweep_lock_seconds")) aim(b, t);
    }

    private void aim(SkillBoss b, Room.Member t) { base = Math.atan2(t.y - b.y, t.x - b.x); }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("sweep");
        double span = Math.toRadians(b.p(room, "sweep_angle_deg")), step = Math.PI * 2 / arms;
        List<List<Double>> items = new ArrayList<>();
        for (int i = 0; i < arms; i++) {
            double a0 = base + i * step;
            items.add(dir > 0 ? BossUtil.arc(a0, a0 + span) : BossUtil.arc(a0 - span, a0));
        }
        m.put("x", b.x); m.put("y", b.y); m.put("items", items); m.put("range", b.p(room, "sweep_range_px"));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        double sec = Math.max(0.2, b.p(room, "sweep_seconds"));
        startAt = now; endAt = now + sec; hit.clear();
        Map<String, Object> e = new HashMap<>();
        e.put("x", b.x); e.put("y", b.y); e.put("base", Math.round(base * 1000) / 1000.0); e.put("dir", dir); e.put("arms", arms);
        e.put("span", Math.round(Math.toRadians(b.p(room, "sweep_angle_deg")) * 1000) / 1000.0);
        e.put("range", b.p(room, "sweep_range_px")); e.put("width", b.p(room, "sweep_width_px")); e.put("ms", Math.round(sec * 1000));
        BossUtil.fx(room, b, "sweep", e);
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        double sec = Math.max(0.2, endAt - startAt), u = Math.min(1, (now - startAt) / sec);
        double span = Math.toRadians(b.p(room, "sweep_angle_deg")), range = b.p(room, "sweep_range_px"), width = b.p(room, "sweep_width_px");
        double pct = b.p(room, "sweep_hp_percent"), knock = b.p(room, "sweep_knock_px");
        for (Room.Member m : BossUtil.targets(room, now)) {
            if (hit.contains(m)) continue;
            double dx = m.x - b.x, dy = m.y - b.y;
            for (int i = 0; i < arms; i++) {
                double a = base + i * Math.PI * 2 / arms + dir * span * u, c = Math.cos(a), s = Math.sin(a);
                double along = dx * c + dy * s, perp = -dx * s + dy * c;
                if (along > 0 && along < range + m.displayRadius && Math.abs(perp) < width / 2 + m.displayRadius) {
                    hit.add(m);
                    b.hurtPlayer(room, m, pct);
                    if (!m.dead) BossUtil.pushAway(room, m, b.x, b.y, knock);
                    break;
                }
            }
        }
        return now >= endAt;
    }
}
