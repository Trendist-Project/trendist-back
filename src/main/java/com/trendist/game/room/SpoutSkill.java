package com.trendist.game.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 물기둥(대왕 고래, 4.35): 예고 동안 공격 대상 둘레 `spout_count`곳(분노하면 `spout_rage_count`곳)에 점선 원(반지름 `spout_radius_px`)이 뜬다(첫 번째는 대상 바로 그 자리,
 * 나머지는 둘레 무작위, 마지막 `spout_lock_seconds`는 자리 고정). 물줄기가 솟아 안의 사람이 최대 체력의 `spout_hp_percent`%를 잃고 크게 튕긴다.
 * 점선 원 밖·세이프존·무적 중이면 맞지 않는다.
 */
final class SpoutSkill extends BossSkill {
    private final List<double[]> offsets = new ArrayList<>(); // 대상 자리에서 떨어진 (dx, dy)
    private double ax, ay, until;
    private final List<double[]> spots = new ArrayList<>();

    SpoutSkill() { super("spout"); }

    @Override String windupPhase() { return "spout_windup"; }
    @Override String activePhase() { return "spout"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override
    void windupStart(SkillBoss b, Room room, double now, Room.Member t) {
        int n = Math.max(1, (int) Math.round(b.p(room, b.raged() ? "spout_rage_count" : "spout_count")));
        double r = b.p(room, "spout_radius_px");
        offsets.clear();
        offsets.add(new double[]{0, 0});
        for (int i = 1; i < n; i++) {
            double a = room.random().nextDouble(Math.PI * 2), d = r * (1.0 + room.random().nextDouble() * 2.2);
            offsets.add(new double[]{Math.cos(a) * d, Math.sin(a) * d});
        }
        if (t != null) { ax = t.x; ay = t.y; }
        refresh(room, r);
    }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "spout_lock_seconds")) { ax = t.x; ay = t.y; refresh(room, b.p(room, "spout_radius_px")); }
    }

    private void refresh(Room room, double r) {
        spots.clear();
        for (double[] o : offsets) spots.add(new double[]{BossUtil.clampTo(room, r, ax + o[0]), BossUtil.clampTo(room, r, ay + o[1])});
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("spout");
        List<Map<String, Object>> list = new ArrayList<>();
        for (double[] s : spots) list.add(BossUtil.spot(s[0], s[1], b.p(room, "spout_radius_px")));
        m.put("spots", list);
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        until = now + 0.5;
        double r = b.p(room, "spout_radius_px"), pct = b.p(room, "spout_hp_percent"), knock = b.p(room, "spout_knock_px");
        for (double[] s : spots) {
            BossUtil.burst(room, b, s[0], s[1], r, "spout");
            for (Room.Member m : BossUtil.targets(room, now)) {
                if (Math.hypot(m.x - s[0], m.y - s[1]) - m.displayRadius >= r) continue;
                b.hurtPlayer(room, m, pct);
                if (!m.dead) BossUtil.pushAway(room, m, s[0], s[1], knock);
            }
        }
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return now >= until; }
}
