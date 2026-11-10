package com.trendist.game.room;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 돌진: 예고 동안 대상을 향해 돌다가(마지막 `dash_lock_seconds`는 방향 고정) 그 방향으로 일직선으로 달린다. 속도는 서서히 붙었다 서서히 줄고
 * 가는 거리는 일정 속도로 갈 때와 같다. 돌진에 맞은 사람은 한 번의 돌진에 한 번만 최대 체력의 `dash_hp_percent`%를 잃는다.
 */
final class DashSkill extends BossSkill {
    /** 앞 EASE_IN은 서서히 빨라지고 뒤 EASE_OUT은 서서히 느려진다. AREA는 곡선 아래 넓이(그만큼 최고 속도를 올려 가는 거리를 같게 한다) */
    private static final double EASE_IN = 0.25, EASE_OUT = 0.35, AREA = 1 - (EASE_IN + EASE_OUT) / 2;

    private double dirX = 1, dirY = 0, startAt, until;
    private final Set<Room.Member> hit = new HashSet<>();

    DashSkill() { super("dash"); }

    @Override String windupPhase() { return "dash_windup"; }
    @Override String activePhase() { return "dashing"; }

    @Override
    boolean usable(SkillBoss b, Room room, double now, Room.Member t) {
        return t != null; // 거리 조건은 없다(대상만 있으면 쓴다)
    }

    @Override void windupStart(SkillBoss b, Room room, double now, Room.Member t) { if (t != null) aim(b, t); }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "dash_lock_seconds")) aim(b, t); // 마지막 잠깐은 방향을 고정한다(피할 틈)
    }

    private void aim(SkillBoss b, Room.Member t) {
        double dx = t.x - b.x, dy = t.y - b.y, dist = Math.hypot(dx, dy);
        if (dist > 1) { dirX = dx / dist; dirY = dy / dist; }
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        double sec = b.p(room, "dash_seconds");
        startAt = now; until = now + sec;
        hit.clear();
        room.broadcast(b.fx("dash", Map.of("ms", Math.round(sec * 1000), "dx", dirX, "dy", dirY)));
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        double sec = Math.max(0.001, b.p(room, "dash_seconds"));
        double u = Math.min(1, Math.max(0, (now - startAt) / sec));
        double s = b.baseSpeed(room) * b.p(room, "dash_speed_mult") * ease(u) / AREA * dt;
        b.move(room, dirX * s, dirY * s);
        return now >= until;
    }

    /** 속도 비율(0~1, smoothstep) */
    private static double ease(double u) {
        double in = Math.min(1, u / EASE_IN), out = Math.min(1, (1 - u) / EASE_OUT);
        return Math.min(in * in * (3 - 2 * in), out * out * (3 - 2 * out));
    }

    @Override boolean fast() { return true; }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> t = new HashMap<>();
        t.put("k", "dash"); t.put("dx", dirX); t.put("dy", dirY);
        t.put("len", b.baseSpeed(room) * b.p(room, "dash_speed_mult") * b.p(room, "dash_seconds")); // 가는 거리 = 속도 × 돌진 배수 × 시간
        return t;
    }

    @Override
    void onBodyContact(SkillBoss b, Room room, Room.Member m, double now) {
        if (hit.add(m)) b.hurtPlayer(room, m, b.p(room, "dash_hp_percent"));
    }
}
