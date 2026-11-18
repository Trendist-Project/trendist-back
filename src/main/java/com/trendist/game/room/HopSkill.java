package com.trendist.game.room;

import java.util.HashMap;
import java.util.Map;

/**
 * 깡충(달토끼, 4.35): 대상 자리에 달 모양 그림자 원이 뜬다(예고, 마지막 `hop_lock_seconds`는 자리를 고정한다). 보스가 떠올라(점프 중에는 몸이 없어 부딪히지도 부딪히지도 않는다)
 * 그림자 위에 내려앉고, 착지할 때 원(`hop_radius_px`) 안의 사람은 최대 체력의 `hop_hp_percent`%를 잃고 튕긴다. 착지 뒤 `hop_stun_seconds`는 무방비
 * (몸 판정이 돌아오고 다른 패턴은 못 쓴다: 모두 달려들 틈). 분노하면 `hop_rage_count`번 연달아 뛴다(다음 자리는 `hop_chain_windup` 동안 새로 예고한다).
 * 세이프존 안·무적 중이면 맞지 않는다.
 */
final class HopSkill extends BossSkill {
    private enum St { AIR, MARK, STUN }

    private double spotX, spotY;
    private St st = St.AIR;
    private double fromX, fromY, startAt, until;
    private int left;

    HopSkill() { super("hop"); }

    @Override String windupPhase() { return "hop_windup"; }
    @Override String activePhase() { return "hop"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override void windupStart(SkillBoss b, Room room, double now, Room.Member t) { if (t != null) aim(b, room, t.x, t.y); }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "hop_lock_seconds")) aim(b, room, t.x, t.y); // 마지막 잠깐은 자리를 고정한다(피할 틈)
    }

    /** 그림자 자리: 그 자리에서 보스 몸이 맵 밖으로 나가지 않게 맞춘다 */
    private void aim(SkillBoss b, Room room, double x, double y) {
        double reach = Math.max(0, room.half() - b.radius);
        spotX = Math.max(-reach, Math.min(reach, x)); spotY = Math.max(-reach, Math.min(reach, y));
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("k", "hop"); m.put("x", spotX); m.put("y", spotY); m.put("r", b.p(room, "hop_radius_px"));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        left = b.raged() ? Math.max(1, (int) Math.round(b.p(room, "hop_rage_count"))) : 1;
        jump(b, room, now);
    }

    private void jump(SkillBoss b, Room room, double now) {
        st = St.AIR; fromX = b.x; fromY = b.y; startAt = now;
        double air = Math.max(0.1, b.p(room, "hop_air_seconds"));
        until = now + air;
        Map<String, Object> e = new HashMap<>();
        e.put("x0", fromX); e.put("y0", fromY); e.put("x", spotX); e.put("y", spotY); e.put("r", b.p(room, "hop_radius_px")); e.put("ms", Math.round(air * 1000));
        room.broadcast(b.fx("hopJump", e));
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        switch (st) {
            case AIR -> {
                double air = Math.max(0.1, until - startAt), u = Math.min(1, (now - startAt) / air);
                double e = u * u * (3 - 2 * u); // smoothstep: 떠올랐다 내려앉는 느낌
                b.move(room, fromX + (spotX - fromX) * e - b.x, fromY + (spotY - fromY) * e - b.y);
                if (now >= until) land(b, room, now);
            }
            case MARK -> { if (now >= until) jump(b, room, now); }
            case STUN -> { return now >= until; }
        }
        return false;
    }

    private void land(SkillBoss b, Room room, double now) {
        double r = b.p(room, "hop_radius_px"), pct = b.p(room, "hop_hp_percent"), knock = b.p(room, "hop_knock_mult");
        Map<String, Object> e = new HashMap<>();
        e.put("x", spotX); e.put("y", spotY); e.put("r", r);
        room.broadcast(b.fx("hopLand", e));
        Room.Tuning tu = room.tuningNow();
        for (Room.Member m : new java.util.ArrayList<>(room.members())) {
            if (m.bot || m.dead || m.safe || m.protectUntil > now) continue; // 세이프존 안·무적 중이면 맞지 않는다
            double dx = m.x - spotX, dy = m.y - spotY, dist = Math.hypot(dx, dy);
            if (dist - m.displayRadius >= r) continue;
            if (dist < 0.001) { dx = 1; dy = 0; dist = 1; }
            room.knockFrom(m, dx / dist, dy / dist, knock, tu);
            b.hurtPlayer(room, m, pct);
        }
        left--;
        if (left > 0) { // 분노: 다음 그림자를 새로 예고하고 다시 뛴다
            Room.Member t = b.currentTarget(room);
            if (t != null) aim(b, room, t.x, t.y);
            double chain = Math.max(0.2, b.p(room, "hop_chain_windup"));
            st = St.MARK; until = now + chain;
            Map<String, Object> mk = new HashMap<>();
            mk.put("x", spotX); mk.put("y", spotY); mk.put("r", r); mk.put("ms", Math.round(chain * 1000));
            room.broadcast(b.fx("hopMark", mk));
        } else { st = St.STUN; until = now + b.p(room, "hop_stun_seconds"); }
    }

    @Override boolean fast() { return st == St.AIR; }
    @Override boolean bodyActive() { return st != St.AIR; }

    @Override
    Map<String, Object> extraState(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("air", st == St.AIR); m.put("stun", st == St.STUN);
        return m;
    }
}
