package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 감전 연쇄(전기뱀장어, 4.35): 예고 동안 공격 대상 머리 위에 번개 표식이 뜬다(마지막 `chain_lock_seconds`는 표식 자리를 고정). 번개가 표식 대상에게 꽂히고,
 * 그 사람에서 `chain_range_px` 안의 가장 가까운 다른 사람으로 튀며 최대 `chain_max`명(분노하면 `chain_rage_max`명)에게 각각 최대 체력의 `chain_hp_percent`%를 준다.
 * 모여 있으면 많이 맞고 흩어지면 덜 맞는다. 표식 대상이 세이프존 안·무적이면 번개가 닿지 않는다(연쇄도 시작되지 않는다).
 */
final class ChainSkill extends BossSkill {
    private Room.Member prey;
    private double px, py, until;

    ChainSkill() { super("chain"); }

    @Override String windupPhase() { return "chain_windup"; }
    @Override String activePhase() { return "chain"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override void windupStart(SkillBoss b, Room room, double now, Room.Member t) { prey = t; if (t != null) { px = t.x; py = t.y; } }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (prey != null && !prey.dead && now < windupEnd - b.p(room, "chain_lock_seconds")) { px = prey.x; py = prey.y; }
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("bolt");
        m.put("mark", BossUtil.spot(px, py, 260));
        m.put("spots", List.of(BossUtil.spot(px, py, b.p(room, "chain_range_px")))); // 튀는 거리(흩어지는 기준)
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        until = now + 0.6;
        List<Map<String, Object>> pts = new ArrayList<>();
        pts.add(BossUtil.spot(b.x, b.y, 0));
        Room.Member cur = prey != null && !prey.dead && BossUtil.hittable(prey, now) ? prey : null;
        if (cur == null) { pts.add(BossUtil.spot(px, py, 0)); sendChain(room, b, pts); return; } // 대상이 막혀 있다: 표식 자리에서 튀다 끝난다
        int max = Math.max(1, (int) Math.round(b.p(room, b.raged() ? "chain_rage_max" : "chain_max")));
        double range = b.p(room, "chain_range_px"), pct = b.p(room, "chain_hp_percent");
        List<Room.Member> chain = new ArrayList<>();
        chain.add(cur);
        while (chain.size() < max) {
            Room.Member next = null; double best = Double.MAX_VALUE;
            for (Room.Member m : BossUtil.targets(room, now)) {
                if (chain.contains(m)) continue;
                double d = Math.hypot(m.x - cur.x, m.y - cur.y);
                if (d <= range && d < best) { best = d; next = m; }
            }
            if (next == null) break;
            chain.add(next); cur = next;
        }
        for (Room.Member m : chain) { pts.add(BossUtil.spot(m.x, m.y, 0)); b.hurtPlayer(room, m, pct); }
        sendChain(room, b, pts);
    }

    private void sendChain(Room room, SkillBoss b, List<Map<String, Object>> pts) {
        Map<String, Object> e = new HashMap<>();
        e.put("pts", pts); e.put("ms", 600);
        BossUtil.fx(room, b, "chain", e);
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return now >= until; }
}
