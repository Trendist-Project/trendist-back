package com.trendist.game.room;

import java.util.HashMap;
import java.util.Map;

/**
 * 빨판 끌기(심해 문어, 4.35): 예고 동안 몸이 움츠러들고(`pull_range_px` 둘레가 보인다), 이어 `pull_seconds` 동안 범위 안의 사람을 `pull_speed`로 보스 쪽으로 끌어당긴다
 * (사람 최대 속도보다 빨라 걸어서는 못 벗어난다). 끌기가 끝날 때 몸 가까이(`pull_contact_px` 안) 끌려온 사람은 최대 체력의 `pull_hp_percent`%를 잃고 튕긴다.
 * 끝난 뒤 `pull_stun_seconds`는 무방비(모여 있는 사람들이 때릴 틈). 세이프존 안·무적 중이면 끌리지 않는다.
 */
final class PullSkill extends BossSkill {
    private enum St { PULL, STUN }
    private static final double STEP = 0.2; // 끌어당기는 간격(초)

    private St st = St.PULL;
    private double until, nextAt;

    PullSkill() { super("pull"); }

    @Override String windupPhase() { return "pull_windup"; }
    @Override String activePhase() { return "pull"; }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("pull");
        m.put("spots", java.util.List.of(BossUtil.spot(b.x, b.y, b.p(room, "pull_range_px"))));
        m.put("swell", 0.88); // 몸이 움츠러든다
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        st = St.PULL; until = now + b.p(room, "pull_seconds"); nextAt = now;
        Map<String, Object> e = new HashMap<>();
        e.put("x", b.x); e.put("y", b.y); e.put("range", b.p(room, "pull_range_px")); e.put("ms", Math.round(b.p(room, "pull_seconds") * 1000));
        BossUtil.fx(room, b, "pull", e);
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        if (st == St.STUN) return now >= until;
        if (now >= nextAt) {
            nextAt = now + STEP;
            double range = b.p(room, "pull_range_px"), step = b.p(room, "pull_speed") * STEP;
            for (Room.Member m : BossUtil.targets(room, now)) {
                double dx = b.x - m.x, dy = b.y - m.y, d = Math.hypot(dx, dy), gap = d - b.radius - m.displayRadius;
                if (d > range || gap <= 0) continue;
                BossUtil.push(room, m, dx / d, dy / d, Math.min(step, gap)); // 몸 안으로 파고들지는 않는다
            }
        }
        if (now >= until) {
            double reach = b.radius + b.p(room, "pull_contact_px"), pct = b.p(room, "pull_hp_percent"), knock = b.p(room, "pull_knock_px");
            for (Room.Member m : BossUtil.targets(room, now)) {
                if (Math.hypot(m.x - b.x, m.y - b.y) - m.displayRadius >= reach) continue;
                b.hurtPlayer(room, m, pct);
                if (!m.dead) BossUtil.pushAway(room, m, b.x, b.y, knock);
            }
            BossUtil.burst(room, b, b.x, b.y, reach, "ink");
            st = St.STUN; until = now + b.p(room, "pull_stun_seconds");
        }
        return false;
    }

    @Override boolean fast() { return false; }

    @Override
    Map<String, Object> extraState(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("stun", st == St.STUN);
        return m;
    }
}
