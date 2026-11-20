package com.trendist.game.room;

import java.util.ArrayList;
import java.util.List;

/**
 * 번개 폭풍(전기뱀장어, 4.35): 예고 뒤 번개 `storm_count`곳(분노하면 `storm_rage_count`곳)이 `storm_interval`초 간격으로 차례로 표시되고, 표시된 지 `storm_warn_seconds` 뒤에 떨어진다.
 * 자리는 접속한 사람 둘레에서 무작위로 정한다(사람이 없으면 맵 안 아무 곳). 반지름 `storm_radius_px` 안의 사람은 최대 체력의 `storm_hp_percent`%를 잃는다.
 * 표시된 원 밖으로 계속 움직이면 맞지 않는다. 세이프존 안·무적 중이면 맞지 않는다.
 */
final class StormSkill extends BossSkill {
    private static final class Strike {
        final double x, y, markAt, hitAt; boolean marked, done;
        Strike(double x, double y, double markAt, double hitAt) { this.x = x; this.y = y; this.markAt = markAt; this.hitAt = hitAt; }
    }

    private final List<Strike> strikes = new ArrayList<>();

    StormSkill() { super("storm"); }

    @Override String windupPhase() { return "storm_windup"; }
    @Override String activePhase() { return "storm"; }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        strikes.clear();
        int n = Math.max(1, (int) Math.round(b.p(room, b.raged() ? "storm_rage_count" : "storm_count")));
        double gap = b.p(room, "storm_interval"), warn = b.p(room, "storm_warn_seconds"), r = b.p(room, "storm_radius_px");
        List<Room.Member> people = BossUtil.targets(room, now);
        for (int i = 0; i < n; i++) {
            double x, y;
            if (people.isEmpty()) { x = (room.random().nextDouble() * 2 - 1) * room.half() * 0.8; y = (room.random().nextDouble() * 2 - 1) * room.half() * 0.8; }
            else {
                Room.Member m = people.get(room.random().nextInt(people.size()));
                double a = room.random().nextDouble(Math.PI * 2), d = room.random().nextDouble() * r * 2.5;
                x = BossUtil.clampTo(room, r, m.x + Math.cos(a) * d); y = BossUtil.clampTo(room, r, m.y + Math.sin(a) * d);
            }
            strikes.add(new Strike(x, y, now + i * gap, now + i * gap + warn));
        }
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        double r = b.p(room, "storm_radius_px"), pct = b.p(room, "storm_hp_percent"), warn = b.p(room, "storm_warn_seconds");
        boolean all = true;
        for (Strike s : strikes) {
            if (s.done) continue;
            all = false;
            if (!s.marked && now >= s.markAt) { s.marked = true; BossUtil.mark(room, b, s.x, s.y, r, warn, "bolt"); }
            if (now >= s.hitAt) {
                s.done = true;
                BossUtil.burst(room, b, s.x, s.y, r, "bolt");
                for (Room.Member m : BossUtil.targets(room, now)) {
                    if (Math.hypot(m.x - s.x, m.y - s.y) - m.displayRadius < r) b.hurtPlayer(room, m, pct);
                }
            }
        }
        return all;
    }
}
