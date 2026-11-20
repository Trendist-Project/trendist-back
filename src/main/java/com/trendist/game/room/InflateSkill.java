package com.trendist.game.room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 부풀기(복어 대왕, 4.35): 예고 동안 몸이 풍선처럼 부풀고 판정 범위(`inflate_radius_px`)가 점선 원으로 보인다. 터지듯 가시를 뿜어 그 원 안의 사람은 최대 체력의
 * `inflate_hp_percent`%를 잃고 크게 튕긴다. 뒤이어 쪼그라들어 `inflate_stun_seconds` 동안 무방비(몸 판정이 있고 다른 패턴은 못 쓴다: 모두 달려들 틈).
 * 분노하면 `inflate_rage_count`번 연달아 터진다(앞의 것은 원이 `inflate_small_ratio` 배로 작고, 사이마다 `inflate_chain_windup`초 예고한다). 세이프존 안·무적 중이면 맞지 않는다.
 */
final class InflateSkill extends BossSkill {
    private enum St { MARK, STUN }

    private St st = St.STUN;
    private int total, done;
    private double until;

    InflateSkill() { super("inflate"); }

    @Override String windupPhase() { return "inflate_windup"; }
    @Override String activePhase() { return "inflate"; }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("danger");
        m.put("spots", List.of(BossUtil.spot(b.x, b.y, b.p(room, "inflate_radius_px"))));
        m.put("swell", 1.7); // 예고가 진행될수록 몸이 부푼다
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        total = b.raged() ? Math.max(1, (int) Math.round(b.p(room, "inflate_rage_count"))) : 1;
        done = 0;
        burst(b, room, now);
    }

    private void burst(SkillBoss b, Room room, double now) {
        boolean last = done == total - 1;
        double r = b.p(room, "inflate_radius_px") * (last ? 1 : b.p(room, "inflate_small_ratio"));
        double pct = b.p(room, "inflate_hp_percent"), knock = b.p(room, "inflate_knock_px");
        BossUtil.burst(room, b, b.x, b.y, r, "thorn");
        for (Room.Member m : BossUtil.targets(room, now)) {
            if (Math.hypot(m.x - b.x, m.y - b.y) - m.displayRadius >= r) continue;
            b.hurtPlayer(room, m, pct);
            if (!m.dead) BossUtil.pushAway(room, m, b.x, b.y, knock);
        }
        done++;
        if (done < total) {
            double chain = Math.max(0.2, b.p(room, "inflate_chain_windup"));
            st = St.MARK; until = now + chain;
            BossUtil.mark(room, b, b.x, b.y, b.p(room, "inflate_radius_px"), chain, "danger");
        } else { st = St.STUN; until = now + b.p(room, "inflate_stun_seconds"); }
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        if (st == St.MARK) { if (now >= until) burst(b, room, now); return false; }
        return now >= until;
    }

    @Override
    Map<String, Object> extraState(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("stun", st == St.STUN);
        return m;
    }
}
