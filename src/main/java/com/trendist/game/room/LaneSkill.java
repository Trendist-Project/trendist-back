package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 번개 줄기(전기뱀장어, 4.35): 예고 동안 공격 대상을 지나는 가로 띠 `lane_count`줄(분노하면 `lane_rage_count`줄, 폭 `lane_width_px`, 간격 `lane_gap_px`, 방향은 보스에서 대상 쪽)이
 * 노랗게 깜빡이고 마지막 `lane_lock_seconds`는 고정된다. 번쩍하면 띠에 닿은 사람은 최대 체력의 `lane_hp_percent`%를 잃고 `lane_stun_seconds` 동안 기절한다.
 * 띠와 띠 사이·띠 밖·세이프존·무적 중이면 맞지 않는다.
 */
final class LaneSkill extends BossSkill {
    private double tx, ty, dir, until;
    private int n = 3;

    LaneSkill() { super("lane"); }

    @Override String windupPhase() { return "lane_windup"; }
    @Override String activePhase() { return "lane"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override
    void windupStart(SkillBoss b, Room room, double now, Room.Member t) {
        n = Math.max(1, (int) Math.round(b.p(room, b.raged() ? "lane_rage_count" : "lane_count")));
        if (t != null) aim(b, t);
    }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "lane_lock_seconds")) aim(b, t);
    }

    private void aim(SkillBoss b, Room.Member t) {
        tx = t.x; ty = t.y;
        if (Math.hypot(t.x - b.x, t.y - b.y) > 1) dir = Math.atan2(t.y - b.y, t.x - b.x);
    }

    private List<Map<String, Object>> lanes(SkillBoss b, Room room) {
        double gap = b.p(room, "lane_gap_px"), w = b.p(room, "lane_width_px"), len = room.half() * 3;
        List<Map<String, Object>> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double off = (k - (n - 1) / 2.0) * gap;
            out.add(BossUtil.lane(tx - Math.sin(dir) * off, ty + Math.cos(dir) * off, dir, len, w));
        }
        return out;
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("bolt");
        m.put("lanes", lanes(b, room));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        until = now + 0.45;
        List<Map<String, Object>> lanes = lanes(b, room);
        Map<String, Object> e = new HashMap<>();
        e.put("lanes", lanes); e.put("look", "bolt"); e.put("ms", 450);
        BossUtil.fx(room, b, "flash", e);
        double half = b.p(room, "lane_width_px") / 2, pct = b.p(room, "lane_hp_percent"), stun = b.p(room, "lane_stun_seconds");
        double c = Math.cos(dir), s = Math.sin(dir);
        for (Room.Member m : BossUtil.targets(room, now)) {
            for (int k = 0; k < n; k++) {
                double off = (k - (n - 1) / 2.0) * b.p(room, "lane_gap_px");
                double cx = tx - s * off, cy = ty + c * off;
                double perp = -(m.x - cx) * s + (m.y - cy) * c; // 띠 중심선까지의 거리(띠는 맵보다 길다)
                if (Math.abs(perp) >= half + m.displayRadius) continue;
                b.hurtPlayer(room, m, pct);
                if (!m.dead) room.status().stunFor(m, stun);
                break;
            }
        }
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return now >= until; }
}
