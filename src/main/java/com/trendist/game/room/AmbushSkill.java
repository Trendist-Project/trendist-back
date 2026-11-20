package com.trendist.game.room;

import java.util.HashMap;
import java.util.Map;

/**
 * 급습(대형 상어, 4.35): 보스가 물속으로 잠겨(몸 판정 없음, 지느러미만 보인다) `ambush_dive_seconds` 동안 가장 가까운 사람 둘레를 빠르게 쫓는다. 마지막
 * `ambush_lock_seconds`에 그 사람 자리에 점선 원(반지름 `ambush_radius_px`)이 뜨고 자리가 고정된다. 이어 그 자리로 솟구쳐 물어 안의 사람이 최대 체력의
 * `ambush_hp_percent`%를 잃고 튕긴다. 직후 `ambush_stun_seconds`는 무방비(몸 판정이 돌아오고 다른 패턴은 못 쓴다: 딜 타임). 분노하면 `ambush_rage_count`번
 * 연달아(두 번째부터 잠수는 절반). 점선 원 밖·세이프존·무적 중이면 맞지 않는다.
 */
final class AmbushSkill extends BossSkill {
    private enum St { DIVE, STUN }

    private St st = St.DIVE;
    private double diveEnd, lockAt, spotX, spotY, until;
    private boolean locked;
    private int left;

    AmbushSkill() { super("ambush"); }

    @Override String windupPhase() { return "ambush_windup"; }
    @Override String activePhase() { return "ambush"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        left = b.raged() ? Math.max(1, (int) Math.round(b.p(room, "ambush_rage_count"))) : 1;
        dive(b, room, now, 1);
    }

    private void dive(SkillBoss b, Room room, double now, double scale) {
        st = St.DIVE; locked = false;
        double dive = b.p(room, "ambush_dive_seconds") * scale;
        diveEnd = now + dive; lockAt = diveEnd - Math.min(dive, b.p(room, "ambush_lock_seconds"));
        BossUtil.burst(room, b, b.x, b.y, b.radius * 0.8, "water"); // 잠수하는 물보라
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        if (st == St.STUN) return now >= until;
        Room.Member t = b.currentTarget(room);
        if (!locked) {
            if (t != null) { // 잠수 중에는 빠르게 쫓는다
                double dx = t.x - b.x, dy = t.y - b.y, d = Math.hypot(dx, dy), s = Math.min(d, b.baseSpeed(room) * b.p(room, "ambush_speed_mult") * dt);
                if (d > 1) b.move(room, dx / d * s, dy / d * s);
            }
            if (now >= lockAt) { // 자리를 고정하고 점선 원을 띄운다
                locked = true;
                spotX = t != null ? t.x : b.x; spotY = t != null ? t.y : b.y;
                BossUtil.mark(room, b, spotX, spotY, b.p(room, "ambush_radius_px"), Math.max(0.1, diveEnd - now), "bite");
            }
            return false;
        }
        if (now < diveEnd) return false;
        // 솟구쳐 문다
        double reach = Math.max(0, room.half() - b.radius);
        b.move(room, Math.max(-reach, Math.min(reach, spotX)) - b.x, Math.max(-reach, Math.min(reach, spotY)) - b.y);
        double r = b.p(room, "ambush_radius_px"), pct = b.p(room, "ambush_hp_percent"), knock = b.p(room, "ambush_knock_px");
        BossUtil.burst(room, b, spotX, spotY, r, "bite");
        for (Room.Member m : BossUtil.targets(room, now)) {
            if (Math.hypot(m.x - spotX, m.y - spotY) - m.displayRadius >= r) continue;
            b.hurtPlayer(room, m, pct);
            if (!m.dead) BossUtil.pushAway(room, m, spotX, spotY, knock);
        }
        if (--left > 0) { dive(b, room, now, 0.5); return false; }
        st = St.STUN; until = now + b.p(room, "ambush_stun_seconds");
        return false;
    }

    @Override boolean fast() { return st == St.DIVE; }
    @Override boolean bodyActive() { return st != St.DIVE; }

    @Override
    Map<String, Object> extraState(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("dive", st == St.DIVE); m.put("stun", st == St.STUN);
        return m;
    }
}
