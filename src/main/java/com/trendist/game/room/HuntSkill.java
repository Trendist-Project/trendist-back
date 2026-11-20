package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사냥 돌진(대형 상어, 4.35): "피 냄새" — 체력이 가장 낮은 사람(같으면 가까운 사람, 세이프존·은신·봇 제외)에게 표식이 붙는다. 예고 동안 표식 대상에게 붉은 길이 이어지고
 * (마지막 `hunt_lock_seconds`는 방향 고정), 보스 속도의 `hunt_speed_mult`배로 `hunt_seconds`씩 `hunt_count`번(분노하면 `hunt_rage_count`번) 연달아 돌진한다
 * (돌진마다 표식 대상 쪽으로 다시 겨눈다, 사이는 `hunt_gap_seconds`). 닿은 사람은 한 번의 돌진에 한 번 최대 체력의 `hunt_hp_percent`%를 잃는다.
 * 붉은 길(폭 = 몸 지름) 밖·세이프존·무적 중이면 맞지 않는다.
 */
final class HuntSkill extends BossSkill {
    private enum St { DASH, GAP }
    private static final double EASE_IN = 0.25, EASE_OUT = 0.35, AREA = 1 - (EASE_IN + EASE_OUT) / 2;

    private Room.Member prey;
    private double dirX = 1, dirY = 0, startAt, until;
    private St st = St.GAP;
    private int idx, total;
    private final Set<Room.Member> hit = new HashSet<>();

    HuntSkill() { super("hunt"); }

    @Override String windupPhase() { return "hunt_windup"; }
    @Override String activePhase() { return "hunting"; }

    /** 피 냄새: 체력이 가장 낮은 사람(체력 규칙이 꺼져 모두 같으면 가장 가까운 사람) */
    private Room.Member choose(SkillBoss b, Room room, double now) {
        Room.Member best = null;
        for (Room.Member m : BossUtil.humans(room)) {
            if (m.safe || m.stealthUntil > now) continue;
            if (best == null || m.hp < best.hp || (m.hp == best.hp && Math.hypot(m.x - b.x, m.y - b.y) < Math.hypot(best.x - b.x, best.y - b.y))) best = m;
        }
        return best;
    }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return choose(b, room, now) != null; }

    @Override
    void windupStart(SkillBoss b, Room room, double now, Room.Member t) {
        prey = choose(b, room, now);
        total = Math.max(1, (int) Math.round(b.p(room, b.raged() ? "hunt_rage_count" : "hunt_count")));
        aim(b);
    }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (now < windupEnd - b.p(room, "hunt_lock_seconds")) aim(b);
    }

    private void aim(SkillBoss b) {
        if (prey == null || prey.dead) return;
        double dx = prey.x - b.x, dy = prey.y - b.y, d = Math.hypot(dx, dy);
        if (d > 1) { dirX = dx / d; dirY = dy / d; }
    }

    private double reach(SkillBoss b, Room room) { return b.baseSpeed(room) * b.p(room, "hunt_speed_mult") * b.p(room, "hunt_seconds"); }

    private Map<String, Object> laneNow(SkillBoss b, Room room) {
        double len = reach(b, room) + b.radius;
        return BossUtil.lane(b.x + dirX * len / 2, b.y + dirY * len / 2, Math.atan2(dirY, dirX), len, b.radius * 2);
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("hunt");
        m.put("lanes", List.of(laneNow(b, room)));
        if (prey != null && !prey.dead) m.put("mark", BossUtil.spot(prey.x, prey.y, 260));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) { idx = 0; dash(b, room, now); }

    private void dash(SkillBoss b, Room room, double now) {
        aim(b);
        st = St.DASH; startAt = now; until = now + b.p(room, "hunt_seconds"); hit.clear();
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        if (st == St.GAP) {
            if (now >= until) dash(b, room, now);
            return false;
        }
        double sec = Math.max(0.001, until - startAt), u = Math.min(1, Math.max(0, (now - startAt) / sec));
        double s = b.baseSpeed(room) * b.p(room, "hunt_speed_mult") * ease(u) / AREA * dt;
        b.move(room, dirX * s, dirY * s);
        if (now < until) return false;
        if (++idx >= total) return true;
        st = St.GAP; until = now + b.p(room, "hunt_gap_seconds");
        aim(b);
        List<Map<String, Object>> next = new ArrayList<>(); next.add(laneNow(b, room)); // 다음 돌진의 길을 잠깐 미리 보여 준다
        Map<String, Object> e = new HashMap<>();
        e.put("lanes", next); e.put("look", "hunt"); e.put("ms", Math.round(b.p(room, "hunt_gap_seconds") * 1000));
        BossUtil.fx(room, b, "flash", e);
        return false;
    }

    private static double ease(double u) {
        double in = Math.min(1, u / EASE_IN), out = Math.min(1, (1 - u) / EASE_OUT);
        return Math.min(in * in * (3 - 2 * in), out * out * (3 - 2 * out));
    }

    @Override boolean fast() { return st == St.DASH; }

    @Override
    void onBodyContact(SkillBoss b, Room room, Room.Member m, double now) {
        if (st == St.DASH && hit.add(m)) b.hurtPlayer(room, m, b.p(room, "hunt_hp_percent"));
    }
}
