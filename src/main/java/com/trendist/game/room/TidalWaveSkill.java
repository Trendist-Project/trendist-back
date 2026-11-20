package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 거대 파도(대왕 고래, 4.35. 설정 이름은 `wave_*`): 예고 동안 몸을 젖히고 공격 대상 방향으로 폭 `wave_width_px`의 붉은 띠가 맵 끝까지 깜빡인다(마지막
 * `wave_lock_seconds`는 방향 고정). 파도 앞면이 `wave_speed`로 전진해 닿은 사람은 최대 체력의 `wave_hp_percent`%를 잃고 파도 방향으로 `wave_push_px`만큼 밀려난다.
 * 분노하면 서로 어긋난 방향(±`wave_rage_angle_deg`) 파도 둘이 동시에 간다. 띠 옆·세이프존·무적 중이면 맞지 않는다.
 */
final class TidalWaveSkill extends BossSkill {
    private double dir, startAt, x0, y0, len;
    private final List<Double> dirs = new ArrayList<>();
    private final List<Set<Room.Member>> hit = new ArrayList<>();

    TidalWaveSkill() { super("wave"); }

    @Override String windupPhase() { return "wave_windup"; }
    @Override String activePhase() { return "tidal"; }

    @Override boolean usable(SkillBoss b, Room room, double now, Room.Member t) { return t != null; }

    @Override void windupStart(SkillBoss b, Room room, double now, Room.Member t) { if (t != null) aim(b, t); }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (t != null && now < windupEnd - b.p(room, "wave_lock_seconds")) aim(b, t);
    }

    private void aim(SkillBoss b, Room.Member t) { if (Math.hypot(t.x - b.x, t.y - b.y) > 1) dir = Math.atan2(t.y - b.y, t.x - b.x); }

    private List<Double> waveDirs(SkillBoss b, Room room) {
        List<Double> out = new ArrayList<>();
        if (b.raged()) { double d = Math.toRadians(b.p(room, "wave_rage_angle_deg")); out.add(dir - d); out.add(dir + d); }
        else out.add(dir);
        return out;
    }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("wave");
        double length = room.half() * 3, w = b.p(room, "wave_width_px");
        List<Map<String, Object>> lanes = new ArrayList<>();
        for (double d : waveDirs(b, room)) lanes.add(BossUtil.lane(b.x + Math.cos(d) * length / 2, b.y + Math.sin(d) * length / 2, d, length, w));
        m.put("lanes", lanes);
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        startAt = now; x0 = b.x; y0 = b.y; len = room.half() * 3;
        dirs.clear(); dirs.addAll(waveDirs(b, room));
        hit.clear();
        for (double d : dirs) {
            hit.add(new HashSet<>());
            Map<String, Object> e = new HashMap<>();
            e.put("x", Math.round(x0)); e.put("y", Math.round(y0)); e.put("dir", Math.round(d * 1000) / 1000.0); e.put("w", b.p(room, "wave_width_px"));
            e.put("speed", b.p(room, "wave_speed")); e.put("len", len); e.put("band", b.p(room, "wave_band_px"));
            BossUtil.fx(room, b, "wave", e);
        }
    }

    @Override
    boolean activeTick(SkillBoss b, Room room, double now, double dt) {
        double front = b.p(room, "wave_speed") * (now - startAt), band = b.p(room, "wave_band_px"), w = b.p(room, "wave_width_px");
        double pct = b.p(room, "wave_hp_percent"), push = b.p(room, "wave_push_px");
        for (int i = 0; i < dirs.size(); i++) {
            double c = Math.cos(dirs.get(i)), s = Math.sin(dirs.get(i));
            for (Room.Member m : BossUtil.targets(room, now)) {
                if (hit.get(i).contains(m)) continue;
                double dx = m.x - x0, dy = m.y - y0, along = dx * c + dy * s, perp = -dx * s + dy * c;
                if (along > front + m.displayRadius || along < front - band - m.displayRadius || Math.abs(perp) >= w / 2 + m.displayRadius) continue;
                hit.get(i).add(m);
                b.hurtPlayer(room, m, pct);
                if (!m.dead) BossUtil.push(room, m, c, s, push);
            }
        }
        return front > len;
    }
}
