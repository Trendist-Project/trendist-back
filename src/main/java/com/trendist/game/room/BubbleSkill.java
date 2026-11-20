package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 거품 추적(복어 대왕, 4.35): 거품 `bubble_count`개가 서로 다른 사람을 하나씩 천천히(`bubble_speed`) 따라간다. 사람에게 닿거나 `bubble_seconds`가 지나면 터져서
 * 반지름 `bubble_radius_px` 안의 사람이 최대 체력의 `bubble_hp_percent`%를 잃는다(다른 사람을 끌고 가 같이 맞게 하는 것도 가능). 계속 움직이면 피할 수 있다.
 * 거품은 패턴이 끝난 뒤에도 남아 움직인다(passiveTick). 위치는 0.15초마다 모두에게 알린다. 세이프존 안·무적 중이면 맞지 않는다.
 */
final class BubbleSkill extends BossSkill {
    private static final class Bubble { double x, y, diesAt; Room.Member target; }
    private static final double SEND = 0.15;

    private final List<Bubble> bubbles = new ArrayList<>();
    private double sentAt;

    BubbleSkill() { super("bubble"); }

    @Override String windupPhase() { return "bubble_windup"; }
    @Override String activePhase() { return "bubble"; }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        int n = Math.max(1, (int) Math.round(b.p(room, "bubble_count")));
        List<Room.Member> people = BossUtil.targets(room, now);
        Collections.shuffle(people, new java.util.Random(room.random().nextLong()));
        double life = b.p(room, "bubble_seconds");
        for (int i = 0; i < n; i++) {
            Bubble bb = new Bubble();
            bb.target = people.isEmpty() ? null : people.get(i % people.size());
            // 거품은 쫓을 사람 쪽(입이 향한 쪽)에서 나온다(약간 흩뜨려서). 쫓을 사람이 없으면 아무 쪽이나
            double a = bb.target != null ? Math.atan2(bb.target.y - b.y, bb.target.x - b.x) + (room.random().nextDouble() - 0.5) * 0.8 : room.random().nextDouble(Math.PI * 2);
            bb.x = b.x + Math.cos(a) * b.radius * 0.8; bb.y = b.y + Math.sin(a) * b.radius * 0.8;
            bb.diesAt = now + life;
            bubbles.add(bb);
        }
        send(b, room, now);
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return true; } // 띄우면 끝, 움직임은 passiveTick이 맡는다

    @Override
    void passiveTick(SkillBoss b, Room room, double now, double dt) {
        if (bubbles.isEmpty()) return;
        double speed = b.p(room, "bubble_speed"), r = b.p(room, "bubble_radius_px"), pct = b.p(room, "bubble_hp_percent");
        for (Iterator<Bubble> it = bubbles.iterator(); it.hasNext(); ) {
            Bubble bb = it.next();
            Room.Member t = bb.target;
            if (t == null || t.dead || t.offline || room.byEntity(t.entityNo) != t) { // 쫓던 사람이 사라졌다: 가장 가까운 사람으로 바꾼다
                bb.target = null; double best = Double.MAX_VALUE;
                for (Room.Member m : BossUtil.targets(room, now)) { double d = Math.hypot(m.x - bb.x, m.y - bb.y); if (d < best) { best = d; bb.target = m; } }
                t = bb.target;
            }
            boolean pop = now >= bb.diesAt;
            if (t != null && !pop) {
                double dx = t.x - bb.x, dy = t.y - bb.y, d = Math.hypot(dx, dy);
                if (d - t.displayRadius < r * 0.5) pop = true; // 닿았다
                else if (d > 0.001) { double s = Math.min(speed * dt, d); bb.x += dx / d * s; bb.y += dy / d * s; }
            }
            if (!pop) continue;
            it.remove();
            BossUtil.burst(room, b, bb.x, bb.y, r, "water");
            for (Room.Member m : BossUtil.targets(room, now)) {
                if (Math.hypot(m.x - bb.x, m.y - bb.y) - m.displayRadius < r) b.hurtPlayer(room, m, pct);
            }
        }
        if (now - sentAt >= SEND || bubbles.isEmpty()) send(b, room, now);
    }

    private void send(SkillBoss b, Room room, double now) {
        sentAt = now;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Bubble bb : bubbles) list.add(BossUtil.spot(bb.x, bb.y, b.p(room, "bubble_radius_px") * 0.28));
        Map<String, Object> e = new HashMap<>();
        e.put("list", list);
        BossUtil.fx(room, b, "bubbles", e);
    }
}
