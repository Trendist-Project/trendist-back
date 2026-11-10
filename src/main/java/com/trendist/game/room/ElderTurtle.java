package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 장로 거북이(4.35): 첫 번째 보스몹. 몸이 아주 크고 느리게 가장 가까운 사람을 따라다니며 패턴(돌진·충격고리)을 쓴다.
 *
 * 패턴 쓰는 규칙(RPG 보스의 흔한 방식): ① 패턴마다 쿨타임이 있다 ② 어떤 패턴이든 끝난 뒤 공통 대기(GCD)가 지나야 다음 패턴을 쓴다
 * ③ 쓰기 전에 예고(바닥 표시·몸 빛남)가 있고 그동안 보스는 멈춘다 ④ 끝난 뒤에는 제자리에서 경직(공략 틈)한다 ⑤ 쓸 수 있는 패턴 중
 * 직전과 같은 패턴은 피해서 가중치로 무작위로 고른다 ⑥ 분노하면 쿨타임·공통 대기가 줄어든다 ⑦ 등장 직후 첫 패턴까지 기다린다.
 * 체력은 유행도 값이고, 부딪힌 사람의 유행도(불꽃이면 배수)만큼 줄어든다. 스킬(갈고리·충격파·독 등)은 보스에게 통하지 않는다.
 */
final class ElderTurtle extends Monster {
    private enum Ph { CHASE, DASH_WINDUP, DASHING, RING_WINDUP, RING, RECOVER }

    private Ph ph = Ph.CHASE;
    private double phStart, phUntil;
    private final double openingUntil;
    private double gcdUntil, dashReadyAt, ringReadyAt;
    private String lastSkill = "";
    private double dirX = 1, dirY = 0;
    private Integer targetE;
    private double retargetAt;
    private boolean rage, flame;
    private double ringR, ringMax;
    private final Set<Room.Member> ringHit = new HashSet<>();

    private ElderTurtle(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening) {
        super(id, spec, radius, start, now, duration);
        this.x = x; this.y = y; this.openingUntil = now + opening;
        this.dashReadyAt = now; this.ringReadyAt = now;
    }

    /** 체력(HP)을 정하고 만든다. HP = 사람 수 × 참여율 × 1인당 타격 수 × 기준 유행도 × 타격당 배수 × 여유 */
    static ElderTurtle spawn(Room room, int id, Spec spec) {
        Map<String, Double> p = spec.params();
        int humans = 0; double sum = 0, top = 0;
        for (Room.Member m : room.members()) {
            if (m.bot || m.offline) continue;
            humans++; sum += m.score; top = Math.max(top, m.score);
        }
        double avg = humans == 0 ? 0 : sum / humans;
        double w = d(p, "hp_top_weight"), ref = avg * (1 - w) + top * w;
        double flameRatio = d(p, "hp_flame_ratio"), perHit = flameRatio * d(p, "flame_damage_mult") + (1 - flameRatio);
        double n = Math.max(humans, d(p, "hp_min_players"));
        double hp = n * d(p, "hp_participation_rate") * d(p, "hp_hits_per_player") * ref * perHit * d(p, "hp_margin");
        hp = Math.max(d(p, "hp_min"), Math.min(d(p, "hp_max"), hp));
        long maxHp = Math.round(hp);
        double now = room.clock(), half = room.half(), radius = d(p, "radius");
        double reach = Math.max(0, half - radius), ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double sx = Math.cos(ang) * reach * 0.6, sy = Math.sin(ang) * reach * 0.6; // 가운데(세이프존)에서 떨어진 곳에서 나온다
        return new ElderTurtle(id, spec, radius, new Start(maxHp, humans, Math.round(avg), Math.round(top)), now, d(p, "duration_seconds"), sx, sy, d(p, "opening_delay_seconds"));
    }

    private static double d(Map<String, Double> p, String k) { Double v = p.get(k); return v == null ? 0 : v; }

    private double rageMult(Room room) { return rage ? p(room, "rage_cooldown_mult") : 1; }

    @Override
    void tick(Room room, double dt) {
        double now = room.clock();
        if (now >= endsAt) { room.endMonster(this, TIMEOUT); return; }
        checkRage(room, now);
        Room.Tuning tu = room.tuningNow();
        double speed = tu.maxSpeed() * p(room, "speed_ratio") * (rage ? p(room, "rage_speed_mult") : 1);
        switch (ph) {
            case CHASE -> {
                if (now >= retargetAt) { retarget(room); retargetAt = now + p(room, "retarget_seconds"); }
                Room.Member t = target(room);
                if (t != null) stepToward(room, t.x, t.y, speed * dt);
                if (now >= openingUntil && now >= gcdUntil) chooseSkill(room, now, t);
            }
            case DASH_WINDUP -> {
                // 예고 동안은 멈춰서 대상을 향해 돈다. 마지막 dash_lock_seconds는 방향을 고정한다(피할 틈)
                Room.Member t = target(room);
                if (t != null && now < phUntil - p(room, "dash_lock_seconds")) aim(t);
                if (now >= phUntil) {
                    ph = Ph.DASHING; phStart = now; phUntil = now + p(room, "dash_seconds");
                    room.broadcast(fx("dash", Map.of("ms", Math.round(p(room, "dash_seconds") * 1000), "dx", dirX, "dy", dirY)));
                }
            }
            case DASHING -> {
                double s = speed * p(room, "dash_speed_mult") * dt;
                move(room, dirX * s, dirY * s);
                if (now >= phUntil) recover(room, now);
            }
            case RING_WINDUP -> {
                if (now >= phUntil) {
                    ph = Ph.RING; phStart = now; ringR = radius; ringMax = radius + ringRange(room); ringHit.clear();
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("x", x); extra.put("y", y); extra.put("r0", radius); extra.put("rMax", ringMax); extra.put("speed", p(room, "ring_speed"));
                    room.broadcast(fx("ring", extra));
                }
            }
            case RING -> {
                ringR += p(room, "ring_speed") * dt;
                ringHits(room, now);
                if (ringR >= ringMax) recover(room, now);
            }
            case RECOVER -> { if (now >= phUntil) ph = Ph.CHASE; }
        }
        bodyContacts(room, now, tu);
        if (hp <= 0 && !ended) room.endMonster(this, KILLED);
    }

    // ------------------------------------------------------------ 분노·이동·대상

    private void checkRage(Room room, double now) {
        if (rage) return;
        double hpPct = p(room, "rage_hp_percent"), secs = p(room, "rage_remaining_seconds");
        boolean byHp = hpPct > 0 && hp <= maxHp * hpPct / 100.0, byTime = secs > 0 && endsAt - now <= secs;
        if (!byHp && !byTime) return;
        rage = true;
        flame = p(room, "rage_flame") >= 1;
        room.broadcast(fx("rage", Map.of("flame", flame)));
    }

    private void retarget(Room room) {
        Room.Member best = null; double bestD = Double.MAX_VALUE;
        double now = room.clock();
        for (Room.Member m : room.members()) {
            if (m.bot || m.dead || m.offline || m.safe || m.stealthUntil > now) continue; // 세이프존 안·은신 중인 사람은 따라가지 않는다
            double dd = Math.hypot(m.x - x, m.y - y);
            if (dd < bestD) { bestD = dd; best = m; }
        }
        targetE = best == null ? null : best.entityNo;
    }

    private Room.Member target(Room room) {
        if (targetE == null) return null;
        Room.Member m = room.byEntity(targetE);
        return m == null || m.dead ? null : m;
    }

    private void stepToward(Room room, double tx, double ty, double step) {
        double dx = tx - x, dy = ty - y, dist = Math.hypot(dx, dy);
        if (dist < 1) return;
        double s = Math.min(step, dist);
        move(room, dx / dist * s, dy / dist * s);
    }

    private void move(Room room, double dx, double dy) {
        double reach = Math.max(0, room.half() - radius);
        x = Math.max(-reach, Math.min(reach, x + dx));
        y = Math.max(-reach, Math.min(reach, y + dy));
    }

    private void aim(Room.Member t) {
        double dx = t.x - x, dy = t.y - y, dist = Math.hypot(dx, dy);
        if (dist > 1) { dirX = dx / dist; dirY = dy / dist; }
    }

    private double ringRange(Room room) {
        double r = p(room, "ring_range_px");
        return r > 0 ? r : room.half() * 1.5;
    }

    // ------------------------------------------------------------ 패턴

    private void chooseSkill(Room room, double now, Room.Member t) {
        List<String> ready = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        if (p(room, "dash_weight") > 0 && now >= dashReadyAt && t != null && Math.hypot(t.x - x, t.y - y) - radius >= p(room, "dash_min_distance")) {
            ready.add("dash"); weights.add(p(room, "dash_weight"));
        }
        if (p(room, "ring_weight") > 0 && now >= ringReadyAt) { ready.add("ring"); weights.add(p(room, "ring_weight")); }
        if (ready.size() > 1) { // 직전과 같은 패턴은 피한다(다른 쓸 수 있는 패턴이 있을 때)
            int i = ready.indexOf(lastSkill);
            if (i >= 0) { ready.remove(i); weights.remove(i); }
        }
        if (ready.isEmpty()) return;
        double total = 0; for (double w : weights) total += w;
        double roll = ThreadLocalRandom.current().nextDouble(total);
        int pick = 0;
        for (int i = 0; i < weights.size(); i++) { roll -= weights.get(i); if (roll < 0) { pick = i; break; } }
        String skill = ready.get(pick);
        lastSkill = skill;
        double mult = rageMult(room);
        if ("dash".equals(skill)) {
            if (t != null) aim(t);
            ph = Ph.DASH_WINDUP; phStart = now; phUntil = now + p(room, "dash_windup");
            dashReadyAt = now + p(room, "dash_cooldown") * mult;
        } else {
            ph = Ph.RING_WINDUP; phStart = now; phUntil = now + p(room, "ring_windup");
            ringReadyAt = now + p(room, "ring_cooldown") * mult;
        }
    }

    /** 패턴이 끝났다: 제자리 경직, 그 뒤 공통 대기가 지나야 다음 패턴 */
    private void recover(Room room, double now) {
        ph = Ph.RECOVER; phStart = now; phUntil = now + p(room, "recover_seconds");
        gcdUntil = phUntil + p(room, "gcd_seconds") * rageMult(room);
    }

    /** 충격고리 앞면이 지나가는 사람: 보스에서 멀수록 적게 잃는다(최대 → 최소). 방패·세이프존·불꽃·무적 중이면 잃지 않는다 */
    private void ringHits(Room room, double now) {
        double range = ringMax - radius, maxKw = p(room, "ring_max_keywords"), minKw = p(room, "ring_min_keywords");
        for (Room.Member m : new ArrayList<>(room.members())) {
            if (m.bot || m.dead || ringHit.contains(m)) continue;
            double dist = Math.hypot(m.x - x, m.y - y);
            if (dist - m.displayRadius > ringR) continue; // 아직 앞면이 닿지 않았다
            ringHit.add(m);
            if (m.safe || m.protectUntil > now || room.isFlame(m)) continue;
            double travel = Math.max(0, dist - radius), f = range <= 0 ? 1 : Math.min(1, travel / range);
            int count = (int) Math.round(maxKw - (maxKw - minKw) * f);
            if (count <= 0 || m.keywords.isEmpty()) continue;
            m.protectUntil = now + p(room, "hit_protect_seconds");
            room.shieldFor(m, p(room, "hit_protect_seconds"));
            room.monsterLose(m, spec.name(), count);
        }
    }

    // ------------------------------------------------------------ 몸에 부딪히기

    private void bodyContacts(Room room, double now, Room.Tuning tu) {
        for (Room.Member m : new ArrayList<>(room.members())) {
            if (m.bot || m.dead || m.safe || m.hookedBy != null || m.protectUntil > now) continue;
            double dx = m.x - x, dy = m.y - y, dist = Math.hypot(dx, dy), min = radius + m.displayRadius;
            if (dist >= min) continue;
            if (dist < 0.001) { dx = 1; dy = 0; dist = 1; }
            contact(room, m, dx / dist, dy / dist, min - dist, now, tu);
        }
    }

    /**
     * 사람이 보스에 부딪혔다. 보스에 불꽃이 없으면: 일반이면 내 유행도만큼 피해 + 키워드 잃기, 불꽃이면 유행도 × 배수 피해 + 키워드는 그대로.
     * 보스에 불꽃이 있으면: 불꽃 사용자는 피해를 주지만 불꽃이 꺼지고 키워드 1개를 잃고, 불꽃이 아닌 사용자는 키워드를 잃지 않고 즉사한다.
     * 맞은 사람은 보스 밖으로 밀려 나가 튕기고 hit_protect_seconds 동안 무적(유령)이다. 부딪히는 것도 콤보로 센다.
     */
    private void contact(Room room, Room.Member m, double nx, double ny, double overlap, double now, Room.Tuning tu) {
        boolean pFlame = room.isFlame(m);
        if (flame && !pFlame) { room.monsterKill(m, spec.name()); return; }
        long dmg = Math.max(0, Math.round(m.score * (pFlame ? p(room, "flame_damage_mult") : 1)));
        double strength = p(room, "hit_knockback") * (ph == Ph.DASHING ? p(room, "dash_knock_mult") : 1);
        m.x += nx * overlap; m.y += ny * overlap;
        room.knockFrom(m, nx, ny, strength, tu);
        m.targetX = null;
        double protect = p(room, "hit_protect_seconds");
        m.protectUntil = now + protect;
        room.shieldFor(m, protect);
        hp = Math.max(0, hp - dmg);
        record(m, dmg);
        Map<String, Object> hit = new HashMap<>();
        hit.put("type", "monsterHit"); hit.put("id", id); hit.put("e", m.entityNo); hit.put("dmg", dmg); hit.put("flame", pFlame);
        hit.put("x", m.x); hit.put("y", m.y); hit.put("hp", hp);
        room.broadcast(hit);
        if (flame) {
            room.monsterFlameOut(m, spec.name()); // 불꽃이 꺼진다
            if (!m.keywords.isEmpty()) room.monsterLose(m, spec.name(), 1);
        } else {
            if (!pFlame) {
                int n = (int) Math.round(p(room, "hit_lose_keywords"));
                if (n > 0 && !m.keywords.isEmpty()) room.monsterLose(m, spec.name(), n);
            }
            room.comboWin(m, false); // 보스를 치는 것도 연속 빼앗기 콤보(키워드를 잃어도 콤보는 끊기지 않는다)
        }
    }

    // ------------------------------------------------------------ 메시지

    private Map<String, Object> fx(String kind, Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>(extra);
        m.put("type", "monsterFx"); m.put("id", id); m.put("fx", kind);
        return m;
    }

    @Override
    Map<String, Object> stateMessage(Room room) {
        double now = room.clock();
        Map<String, Object> m = new HashMap<>();
        m.put("type", "monster"); m.put("id", id); m.put("code", spec.code()); m.put("name", spec.name()); m.put("en", spec.nameEn());
        m.put("img", spec.image()); m.put("x", x); m.put("y", y); m.put("r", radius);
        m.put("hp", hp); m.put("max", maxHp); m.put("left", Math.max(0, Math.round((endsAt - now) * 1000)));
        m.put("flame", flame); m.put("rage", rage); m.put("ph", ph.name().toLowerCase());
        m.put("tgt", targetE == null ? 0 : targetE);
        if (ph == Ph.DASH_WINDUP) {
            double dist = tuTotalDash(room);
            m.put("tele", Map.of("k", "dash", "dx", dirX, "dy", dirY, "len", dist, "ms", Math.max(0, Math.round((phUntil - now) * 1000)),
                    "total", Math.round(p(room, "dash_windup") * 1000)));
        } else if (ph == Ph.RING_WINDUP) {
            m.put("tele", Map.of("k", "ring", "ms", Math.max(0, Math.round((phUntil - now) * 1000)), "total", Math.round(p(room, "ring_windup") * 1000)));
        }
        return m;
    }

    /** 돌진이 가는 거리(px) = 최대 속도 × 속도 비율 × 돌진 배수 × 돌진 시간 */
    private double tuTotalDash(Room room) {
        double speed = room.tuningNow().maxSpeed() * p(room, "speed_ratio") * (rage ? p(room, "rage_speed_mult") : 1);
        return speed * p(room, "dash_speed_mult") * p(room, "dash_seconds");
    }

    @Override
    Map<String, Object> viewMessage(Room room) {
        double now = room.clock();
        Map<String, Object> m = topList(5);
        m.put("id", id); m.put("spawnId", spec.spawnId()); m.put("code", spec.code()); m.put("name", spec.name());
        m.put("hp", hp); m.put("maxHp", maxHp); m.put("leftSeconds", Math.max(0, Math.round(endsAt - now)));
        m.put("phase", ph.name()); m.put("rage", rage); m.put("flame", flame); m.put("x", Math.round(x)); m.put("y", Math.round(y));
        m.put("humans", start.humans()); m.put("avgScore", start.avgScore()); m.put("topScore", start.topScore());
        m.put("participants", ledger.size());
        Room.Member t = target(room);
        m.put("target", t == null ? null : t.nickname);
        m.put("dashReadyIn", Math.max(0, Math.round(dashReadyAt - now))); m.put("ringReadyIn", Math.max(0, Math.round(ringReadyAt - now)));
        return m;
    }
}
