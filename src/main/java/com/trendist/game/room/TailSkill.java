package com.trendist.game.room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 꼬리 치기(대형 상어, 4.35): 예고 동안 보스 뒤쪽(대상이 있는 쪽의 반대)의 반원(길이 `tail_range_px`)이 붉게 표시되고, 꼬리를 쓸어 안의 사람이 최대 체력의
 * `tail_hp_percent`%를 잃고 크게 튕긴다. 분노하면 전방위(원 전체)다. 앞·옆에서 때리면 맞지 않는다. 세이프존 안·무적 중이면 맞지 않는다.
 */
final class TailSkill extends BossSkill {
    private double rear, until;
    private boolean full;

    TailSkill() { super("tail"); }

    @Override String windupPhase() { return "tail_windup"; }
    @Override String activePhase() { return "tail"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override
    void windupStart(SkillBoss b, Room room, double now, Room.Member t) {
        full = b.raged();
        rear = t == null ? 0 : Math.atan2(b.y - t.y, b.x - t.x); // 대상이 보이는 쪽의 반대가 뒤쪽이다
    }

    private List<List<Double>> arcs() { return List.of(full ? BossUtil.arc(-Math.PI, Math.PI) : BossUtil.arc(rear - Math.PI / 2, rear + Math.PI / 2)); }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("tail");
        m.put("x", b.x); m.put("y", b.y); m.put("items", arcs()); m.put("range", b.p(room, "tail_range_px"));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        until = now + 0.45;
        double range = b.p(room, "tail_range_px"), pct = b.p(room, "tail_hp_percent"), knock = b.p(room, "tail_knock_px");
        Map<String, Object> e = new HashMap<>();
        e.put("x", b.x); e.put("y", b.y); e.put("items", arcs()); e.put("range", range); e.put("look", "tail"); e.put("ms", 450);
        BossUtil.fx(room, b, "flash", e);
        for (Room.Member m : BossUtil.targets(room, now)) {
            double dx = m.x - b.x, dy = m.y - b.y;
            if (Math.hypot(dx, dy) - m.displayRadius >= range) continue;
            if (!full && Math.abs(BossUtil.norm(Math.atan2(dy, dx) - rear)) > Math.PI / 2) continue;
            b.hurtPlayer(room, m, pct);
            if (!m.dead) BossUtil.pushAway(room, m, b.x, b.y, knock);
        }
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return now >= until; }
}
