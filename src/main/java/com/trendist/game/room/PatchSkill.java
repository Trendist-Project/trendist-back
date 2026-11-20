package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 바닥 장판 패턴의 공통 틀(달토끼 끈끈 떡·심해 문어 먹물, 4.35). 설정 이름은 패턴 id를 앞에 붙인다(`<id>_count` 등). 닿았을 때 거는 효과(슬로우·암흑)와 모양(look)만 다르다.
 * 아래는 끈끈 떡 기준 설명이다:
 *
 * 끈끈 떡(달토끼, 4.35): 보스가 쫓는 **공격 대상의 자리**에 떡을 던진다. 예고 동안(`mochi_windup`) 떨어질 자리 `mochi_count`곳이 점선 원으로 보이고,
 * 첫 번째는 공격 대상, 다음은 접속한 다른 사람들의 자리, 남으면 공격 대상 둘레(`mochi_scatter` × 반지름 거리)에 놓는다. 자리는 마지막 `mochi_lock_seconds`
 * 전까지는 그 사람을 따라가고 그 뒤로 고정된다(피할 틈). 그 뒤 달떡 장판(반지름 `mochi_radius_px`)이 `mochi_seconds` 동안 바닥에 남고, 닿은 사람은
 * 이동이 느려진다(슬로우 스킬과 같은 상태, 겹치지 않고 벗어나면 `mochi_slow_seconds` 뒤에 풀린다). 피해는 없다. 세이프존·무적 중이면 걸리지 않는다.
 * 장판은 패턴이 끝난 뒤에도 남는다(passiveTick).
 */
class PatchSkill extends BossSkill {
    private record Spot(double x, double y, double r) {}
    private static final class Zone { final Spot spot; final double until; Zone(Spot spot, double until) { this.spot = spot; this.until = until; } }

    private final List<Spot> spots = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();

    /** 장판에 닿은 사람에게 거는 효과: (방, 사람, 효과를 유지하는 초) */
    @FunctionalInterface interface Effect { void apply(Room room, Room.Member m, double hold); }

    private final String holdKey, look;
    private final Effect effect;

    /** id: 패턴 이름(설정 이름 앞머리), holdKey: 효과 유지 시간 설정 이름, look: 화면 모양("mochi" | "ink") */
    PatchSkill(String id, String holdKey, String look, Effect effect) { super(id); this.holdKey = holdKey; this.look = look; this.effect = effect; }

    @Override String windupPhase() { return id + "_windup"; }
    @Override String activePhase() { return id; }

    /** 자리 하나: 사람을 따라가면(owner) 그 사람 자리, 아니면 공격 대상(또는 처음 자리)에서 정한 방향·거리만큼 떨어진 곳 */
    private static final class Plan { Room.Member owner; double ang, dist, x, y; }
    private final List<Plan> plans = new ArrayList<>();
    private Room.Member anchor;
    private double anchorX, anchorY;

    @Override
    void windupStart(SkillBoss b, Room room, double now, Room.Member t) {
        spots.clear(); plans.clear();
        int n = Math.max(1, (int) Math.round(b.p(room, id + "_count")));
        double rageN = b.p(room, id + "_rage_count"); // 분노하면 늘리는 장판 수(설정이 없거나 0이면 그대로)
        if (b.raged() && rageN > 0) n = Math.max(1, (int) Math.round(rageN));
        double r = b.p(room, id + "_radius_px"), scatter = b.p(room, id + "_scatter");
        List<Room.Member> people = new ArrayList<>();
        for (Room.Member m : room.members()) if (!m.bot && !m.dead && !m.offline && !m.safe && m != t) people.add(m);
        java.util.Collections.shuffle(people, new java.util.Random(room.random().nextLong()));
        anchor = t != null ? t : (people.isEmpty() ? null : people.remove(0));
        anchorX = anchor != null ? anchor.x : 0; anchorY = anchor != null ? anchor.y : 0;
        for (int i = 0; i < n; i++) {
            Plan pl = new Plan();
            if (i == 0 && anchor != null) pl.owner = anchor;                       // 첫 번째: 공격 대상 바로 그 자리
            else if (i - (anchor != null ? 1 : 0) < people.size()) pl.owner = people.get(i - (anchor != null ? 1 : 0)); // 다음: 다른 사람의 자리
            else {                                                                 // 남는 것: 공격 대상 둘레
                for (int tries = 0; tries < 12; tries++) {
                    pl.ang = room.random().nextDouble(Math.PI * 2); pl.dist = r * scatter * (0.8 + 0.6 * room.random().nextDouble());
                    if (!room.nearSafeZone(clampX(room, r, anchorX + Math.cos(pl.ang) * pl.dist), clampX(room, r, anchorY + Math.sin(pl.ang) * pl.dist), r * 0.5)) break;
                }
            }
            plans.add(pl);
        }
        refresh(room, r);
    }

    @Override
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member t) {
        if (now < windupEnd - b.p(room, id + "_lock_seconds")) refresh(room, b.p(room, id + "_radius_px")); // 마지막 잠깐은 자리를 고정한다
    }

    /** 자리들을 지금 사람 위치에 맞춘다(죽거나 나간 사람은 마지막 자리 그대로) */
    private void refresh(Room room, double r) {
        if (anchor != null && !anchor.dead && !anchor.offline) { anchorX = anchor.x; anchorY = anchor.y; }
        spots.clear();
        for (Plan pl : plans) {
            if (pl.owner != null) { if (!pl.owner.dead && !pl.owner.offline) { pl.x = pl.owner.x; pl.y = pl.owner.y; } }
            else { pl.x = anchorX + Math.cos(pl.ang) * pl.dist; pl.y = anchorY + Math.sin(pl.ang) * pl.dist; }
            spots.add(new Spot(clampX(room, r, pl.x), clampX(room, r, pl.y), r));
        }
    }

    private static double clampX(Room room, double r, double v) { double reach = Math.max(0, room.half() - r); return Math.max(-reach, Math.min(reach, v)); }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = new HashMap<>();
        m.put("k", "mochi"); m.put("look", look); m.put("spots", spotList());
        return m;
    }

    private List<Map<String, Object>> spotList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Spot s : spots) list.add(Map.of("x", Math.round(s.x), "y", Math.round(s.y), "r", Math.round(s.r)));
        return list;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        double seconds = b.p(room, id + "_seconds");
        for (Spot s : spots) zones.add(new Zone(s, now + seconds));
        Map<String, Object> e = new HashMap<>();
        e.put("spots", spotList()); e.put("ms", Math.round(seconds * 1000)); e.put("look", look);
        room.broadcast(b.fx("mochi", e));
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return true; } // 떨어뜨리면 끝, 장판은 passiveTick이 맡는다

    @Override
    void passiveTick(SkillBoss b, Room room, double now, double dt) {
        if (zones.isEmpty()) return;
        zones.removeIf(z -> now >= z.until);
        if (zones.isEmpty()) return;
        double hold = b.p(room, holdKey);
        for (Room.Member m : room.members()) {
            if (m.bot || m.dead || m.safe || m.protectUntil > now) continue;
            for (Zone z : zones) {
                if (Math.hypot(m.x - z.spot.x, m.y - z.spot.y) < z.spot.r + m.displayRadius * 0.5) { effect.apply(room, m, hold); break; }
            }
        }
    }
}
