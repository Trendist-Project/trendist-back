package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 당근 부채(달토끼, 4.35): 예고 동안 대상 쪽을 노려보다가(마지막 `carrot_lock_seconds`는 방향 고정) 부채꼴(`carrot_spread_deg`)로 당근 `carrot_count`발을 쏜다.
 * 당근은 직선으로 `carrot_speed`로 날아가 `carrot_range_px`에서 사라지고, 한 사람은 한 번 쏜 묶음에서 한 번만 맞는다(최대 체력의 `carrot_hp_percent`%).
 * 쏜 뒤에도 날아가는 중이라 보스가 다음 동작으로 넘어가도 계속 날아가고(passiveTick), 세이프존 안·무적 중이면 맞지 않는다.
 * 그림은 시작한 시각과 방향만으로 계산한다(서버·화면이 같은 식: 위치 = 시작점 + 방향 × 속도 × 지난 시간).
 */
final class CarrotSkill extends BossSkill {
    private static final class Volley {
        final double x, y, speed, range, r, startAt; final double[] angles; final double hp;
        final Set<Room.Member> hit = new HashSet<>();
        Volley(double x, double y, double speed, double range, double r, double startAt, double[] angles, double hp) {
            this.x = x; this.y = y; this.speed = speed; this.range = range; this.r = r; this.startAt = startAt; this.angles = angles; this.hp = hp;
        }
    }

    private double dir;
    private final List<Volley> volleys = new ArrayList<>();

    CarrotSkill() { super("carrot"); }

    @Override String windupPhase() { return "carrot_windup"; }
    @Override String activePhase() { return "carrot"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override void windupStart(SkillBoss b, Room room, double now, Room.Member t) { if (t != null) aim(b, t); }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "carrot_lock_seconds")) aim(b, t); // 마지막 잠깐은 방향을 고정한다
    }

    private void aim(SkillBoss b, Room.Member t) {
        double dx = t.x - b.x, dy = t.y - b.y;
        if (Math.hypot(dx, dy) > 1) dir = Math.atan2(dy, dx);
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("k", "carrot"); m.put("x", b.x); m.put("y", b.y); m.put("dir", dir);
        m.put("spread", b.p(room, "carrot_spread_deg")); m.put("range", b.p(room, "carrot_range_px"));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        int n = Math.max(1, (int) Math.round(b.p(room, "carrot_count")));
        double spread = Math.toRadians(b.p(room, "carrot_spread_deg"));
        double[] angles = new double[n];
        for (int i = 0; i < n; i++) angles[i] = n == 1 ? dir : dir - spread / 2 + spread * i / (n - 1);
        double speed = Math.max(1, b.p(room, "carrot_speed")), range = b.p(room, "carrot_range_px"), r = b.p(room, "carrot_radius_px");
        volleys.add(new Volley(b.x, b.y, speed, range, r, now, angles, b.p(room, "carrot_hp_percent")));
        double[] list = angles;
        Map<String, Object> e = new HashMap<>();
        e.put("x", b.x); e.put("y", b.y); e.put("speed", speed); e.put("range", range); e.put("r", r);
        List<Double> out = new ArrayList<>(); for (double a : list) out.add(Math.round(a * 1000) / 1000.0);
        e.put("angles", out);
        room.broadcast(b.fx("carrot", e));
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return true; } // 쏘면 끝, 날아가는 것은 passiveTick이 맡는다

    @Override
    void passiveTick(SkillBoss b, Room room, double now, double dt) {
        if (volleys.isEmpty()) return;
        for (Iterator<Volley> it = volleys.iterator(); it.hasNext(); ) {
            Volley v = it.next();
            double d = v.speed * (now - v.startAt);
            if (d > v.range + v.r) { it.remove(); continue; }
            for (double a : v.angles) {
                double cx = v.x + Math.cos(a) * d, cy = v.y + Math.sin(a) * d;
                for (Room.Member m : new ArrayList<>(room.members())) {
                    if (m.bot || m.dead || m.safe || m.protectUntil > now || v.hit.contains(m)) continue;
                    if (Math.hypot(m.x - cx, m.y - cy) >= v.r + m.displayRadius) continue;
                    v.hit.add(m);
                    b.hurtPlayer(room, m, v.hp);
                }
            }
        }
    }
}
