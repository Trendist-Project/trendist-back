package com.trendist.game.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 패턴을 쓰는 보스의 공통 틀(4.35). 새 보스는 이것을 이어받아 체력 계산(spawn)과 패턴 목록(BossSkill)만 정하면 된다.
 *
 * 패턴 쓰는 규칙(RPG 보스의 흔한 방식): ① 패턴마다 쿨타임이 있다 ② 어떤 패턴이든 끝난 뒤 공통 대기(GCD)가 지나야 다음 패턴을 쓴다
 * ③ 쓰기 전에 예고(바닥 표시·몸 빛남)가 있고 그동안 보스는 멈춘다 ④ 끝난 뒤에는 제자리에서 경직(공략 틈)한다 ⑤ 쓸 수 있는 패턴 중
 * 직전과 같은 패턴은 피해서 가중치로 무작위로 고른다 ⑥ 분노하면 쿨타임·공통 대기가 줄고 보스에 불꽃이 붙는다(`rage_flame_seconds` 동안만) ⑦ 등장 직후 첫 패턴까지 기다린다.
 *
 * 몸에 부딪히는 규칙: 부딪힌 사람은 보스 밖으로 튕겨 날 뿐 키워드·불꽃을 잃지 않고 무적도 되지 않으며 체력(HP)도 깎이지 않는다.
 * 보스는 사람의 공격력(유행도, 보스에 불꽃이 없고 사람에게 불꽃이 있으면 × `flame_damage_mult`)만큼 체력을 잃는다. 불꽃 보스에게는 불꽃 유무와
 * 상관없이 불꽃 없는 피해량이 들어가고, 불꽃이 아닌 사람은 그 피해를 주고 즉사한다. 스킬(갈고리·충격파·독 등)은 보스에게 통하지 않는다.
 */
abstract class SkillBoss extends Monster {
    private enum Ph { CHASE, WINDUP, ACTIVE, RECOVER }

    private final List<BossSkill> skills;
    private BossSkill current;
    private Ph ph = Ph.CHASE;
    private double phStart, phUntil;
    private final double openingUntil;
    private double gcdUntil;
    private String lastSkill = "";
    private Integer targetE;
    private double retargetAt;
    private double wanderX, wanderY, wanderIdleUntil; // 목표가 없을 때 돌아다니는 길(세이프존을 피한 곳)
    private boolean wandering;
    private boolean rage, flame;
    private double flameUntil;
    private double vx, vy;
    private final Map<Room.Member, Double> lastHit = new HashMap<>(); // 같은 사람을 다시 칠 수 있는 시각(연타 방지, 무적이 아니다)

    protected SkillBoss(int id, Spec spec, double radius, Start start, double now, double duration, double x, double y, double opening, List<BossSkill> skills) {
        super(id, spec, radius, start, now, duration);
        this.x = x; this.y = y; this.openingUntil = now + opening; this.skills = List.copyOf(skills);
        for (BossSkill s : skills) s.readyAt = now;
    }

    // ------------------------------------------------------------ 틱

    @Override
    void tick(Room room, double dt) {
        double now = room.clock();
        if (now >= endsAt) { room.endMonster(this, TIMEOUT); return; }
        checkRage(room, now);
        Room.Tuning tu = room.tuningNow();
        double px = x, py = y;
        switch (ph) {
            case CHASE -> {
                if (now >= retargetAt) { retarget(room); retargetAt = now + p(room, "retarget_seconds"); }
                Room.Member t = target(room);
                if (t != null) { wandering = false; stepToward(room, t.x, t.y, baseSpeed(room) * dt); }
                else wander(room, now, dt);
                if (now >= openingUntil && now >= gcdUntil) chooseSkill(room, now, t);
            }
            case WINDUP -> {
                current.windupTick(this, room, now, phUntil, target(room));
                if (now >= phUntil) { ph = Ph.ACTIVE; phStart = now; current.begin(this, room, now); }
            }
            case ACTIVE -> { if (current.activeTick(this, room, now, dt)) recover(room, now); }
            case RECOVER -> { if (now >= phUntil) ph = Ph.CHASE; }
        }
        vx = (x - px) / dt; vy = (y - py) / dt;
        bodyContacts(room, now, tu);
        if (hp <= 0 && !ended) room.endMonster(this, KILLED);
    }

    @Override
    boolean fastState() { return ph == Ph.ACTIVE && current != null && current.fast(); }

    // ------------------------------------------------------------ 분노·이동·대상

    private double rageMult(Room room) { return rage ? p(room, "rage_cooldown_mult") : 1; }

    /** 지금 이동 속도(px/초): 일반 사용자 최대 속도 × 속도 비율(분노하면 × 분노 배수). 패턴이 속도를 계산할 때도 쓴다 */
    double baseSpeed(Room room) {
        return room.tuningNow().maxSpeed() * p(room, "speed_ratio") * (rage ? p(room, "rage_speed_mult") : 1);
    }

    /** 패턴이 사람의 체력을 깎는다. 분노 중이면 깎는 %에 rage_damage_mult를 곱한다(패턴 공통) */
    void hurtPlayer(Room room, Room.Member m, double percent) {
        room.monsterHurt(m, spec.name(), percent * (rage ? p(room, "rage_damage_mult") : 1));
    }

    private void checkRage(Room room, double now) {
        if (flame && flameUntil > 0 && now >= flameUntil) { // 분노의 불꽃은 정해진 시간만 탄다
            flame = false;
            room.broadcast(fx("flameEnd", Map.of()));
        }
        if (rage) return;
        double hpPct = p(room, "rage_hp_percent"), secs = p(room, "rage_remaining_seconds");
        boolean byHp = hpPct > 0 && hp <= maxHp * hpPct / 100.0, byTime = secs > 0 && endsAt - now <= secs;
        if (!byHp && !byTime) return;
        rage = true;
        flame = p(room, "rage_flame") >= 1;
        double flameSec = p(room, "rage_flame_seconds");
        flameUntil = flameSec > 0 ? now + flameSec : 0; // 0이면 끝까지
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

    /**
     * 쫓을 사람이 없으면(모두 세이프존 안이거나 은신) 제자리에 서지 않고 맵을 천천히 돌아다닌다: 세이프존을 피한 무작위 곳을 골라 걸어가고,
     * 도착하면 잠깐 쉰 뒤 다음 곳을 고른다. 가는 길(직선)과 도착점이 세이프존(가운데·무작위 존)에 걸치면 그 곳은 고르지 않고, 걷다가 닿게 되면 새로 고른다.
     */
    private void wander(Room room, double now, double dt) {
        if (now < wanderIdleUntil) return;
        double step = baseSpeed(room) * p(room, "wander_speed_ratio") * dt;
        if (wandering) {
            double dx = wanderX - x, dy = wanderY - y, dist = Math.hypot(dx, dy);
            if (dist < Math.max(150, step * 2)) { wandering = false; wanderIdleUntil = now + p(room, "wander_pause_seconds") * (0.5 + Math.random()); return; }
            double nx = x + dx / dist * Math.min(step, dist), ny = y + dy / dist * Math.min(step, dist);
            if (room.nearSafeZone(nx, ny, radius * 0.25)) { wandering = false; return; } // 앞에 세이프존이 생겼다: 새 길을 고른다
            move(room, nx - x, ny - y);
            return;
        }
        double reach = Math.max(0, room.half() - radius);
        for (int tries = 0; tries < 16; tries++) {
            double tx = (Math.random() * 2 - 1) * reach, ty = (Math.random() * 2 - 1) * reach;
            if (Math.hypot(tx - x, ty - y) < radius) continue; // 너무 가까운 곳은 건너뛴다
            if (pathHitsSafeZone(room, tx, ty)) continue;
            wanderX = tx; wanderY = ty; wandering = true;
            return;
        }
        wanderIdleUntil = now + 1; // 갈 만한 곳이 없다: 잠시 뒤 다시 찾는다
    }

    /** (x, y)에서 (tx, ty)까지 직선이 세이프존에 걸치는지(약 반지름의 절반 간격으로 확인) */
    private boolean pathHitsSafeZone(Room room, double tx, double ty) {
        double dist = Math.hypot(tx - x, ty - y), gap = Math.max(100, radius * 0.5);
        int n = (int) Math.ceil(dist / gap);
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            if (room.nearSafeZone(x + (tx - x) * f, y + (ty - y) * f, radius * 0.25)) return true;
        }
        return false;
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

    /** 맵 안에서 이동한다(몸이 벗어나지 않게 막는다) */
    void move(Room room, double dx, double dy) {
        double reach = Math.max(0, room.half() - radius);
        x = Math.max(-reach, Math.min(reach, x + dx));
        y = Math.max(-reach, Math.min(reach, y + dy));
    }

    // ------------------------------------------------------------ 패턴 선택

    /** 패턴을 쓸 수 있게 켜 두었는지(설정 `<패턴>_enabled`, 없으면 켜짐) */
    private boolean skillEnabled(Room room, String id) {
        Map<String, Double> live = room.monsterParams(spec.code());
        Double v = live != null ? live.get(id + "_enabled") : null;
        if (v == null) v = spec.params().get(id + "_enabled");
        return v == null || v >= 1;
    }

    private void chooseSkill(Room room, double now, Room.Member t) {
        List<BossSkill> ready = new ArrayList<>();
        for (BossSkill s : skills) if (skillEnabled(room, s.id) && p(room, s.id + "_weight") > 0 && now >= s.readyAt && s.usable(this, room, now, t)) ready.add(s);
        if (ready.size() > 1) ready.removeIf(s -> s.id.equals(lastSkill)); // 직전과 같은 패턴은 피한다(다른 쓸 수 있는 패턴이 있을 때)
        if (ready.isEmpty()) return;
        double total = 0; for (BossSkill s : ready) total += p(room, s.id + "_weight");
        double roll = room.random().nextDouble(total);
        BossSkill pick = ready.get(ready.size() - 1);
        for (BossSkill s : ready) { roll -= p(room, s.id + "_weight"); if (roll < 0) { pick = s; break; } }
        current = pick; lastSkill = pick.id;
        ph = Ph.WINDUP; phStart = now; phUntil = now + p(room, pick.id + "_windup");
        pick.readyAt = now + p(room, pick.id + "_cooldown") * rageMult(room);
        pick.windupStart(this, room, now, t);
    }

    /** 패턴이 끝났다: 제자리 경직, 그 뒤 공통 대기가 지나야 다음 패턴 */
    private void recover(Room room, double now) {
        ph = Ph.RECOVER; phStart = now; phUntil = now + p(room, "recover_seconds");
        gcdUntil = phUntil + p(room, "gcd_seconds") * rageMult(room);
    }

    // ------------------------------------------------------------ 몸에 부딪히기

    private void bodyContacts(Room room, double now, Room.Tuning tu) {
        for (Room.Member m : new ArrayList<>(room.members())) {
            if (m.bot || m.dead || m.safe || m.hookedBy != null || m.protectUntil > now) continue;
            Double again = lastHit.get(m);
            if (again != null && now < again) continue; // 방금 쳤다: 연타되지 않게 잠깐 기다린다
            double dx = m.x - x, dy = m.y - y, dist = Math.hypot(dx, dy), min = radius + m.displayRadius;
            if (dist >= min) continue;
            if (dist < 0.001) { dx = 1; dy = 0; dist = 1; }
            contact(room, m, dx / dist, dy / dist, min - dist, now, tu);
        }
    }

    private void contact(Room room, Room.Member m, double nx, double ny, double overlap, double now, Room.Tuning tu) {
        boolean pFlame = room.isFlame(m);
        lastHit.put(m, now + p(room, "hit_interval_seconds"));
        if (lastHit.size() > 256) lastHit.keySet().removeIf(k -> k.dead || room.byEntity(k.entityNo) != k);
        // 공격력: 유행도. 불꽃 보스에게는 불꽃 유무와 상관없이 불꽃 없는 피해량, 아니면 불꽃일 때만 배수
        long dmg = Math.max(0, Math.round(m.score * (!flame && pFlame ? p(room, "flame_damage_mult") : 1)));
        double strength = p(room, "hit_knockback") * (ph == Ph.ACTIVE ? p(room, "dash_knock_mult") : 1);
        m.x += nx * overlap; m.y += ny * overlap;
        room.knockFrom(m, nx, ny, strength, tu);
        m.targetX = null;
        hp = Math.max(0, hp - dmg);
        record(m, dmg);
        Map<String, Object> hit = new HashMap<>();
        hit.put("type", "monsterHit"); hit.put("id", id); hit.put("e", m.entityNo); hit.put("dmg", dmg); hit.put("flame", pFlame && !flame);
        hit.put("x", m.x); hit.put("y", m.y); hit.put("hp", hp);
        room.broadcast(hit);
        if (flame && !pFlame) { room.monsterKill(m, spec.name()); return; } // 불꽃 보스에 불꽃 없이 덤비면 피해는 주고 즉사한다
        hurtPlayer(room, m, p(room, "contact_hp_percent")); // 몸에 닿으면 내 체력이 닳는다(분노 중이면 2배)
        if (ph == Ph.ACTIVE && current != null && !m.dead) current.onBodyContact(this, room, m, now);
        if (!m.dead) room.comboWin(m, false); // 보스를 치는 것도 연속 빼앗기 콤보(4.25)
    }

    // ------------------------------------------------------------ 메시지

    Map<String, Object> fx(String kind, Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>(extra);
        m.put("type", "monsterFx"); m.put("id", id); m.put("fx", kind);
        return m;
    }

    private String phaseName() {
        return switch (ph) {
            case CHASE -> "chase";
            case RECOVER -> "recover";
            case WINDUP -> current.windupPhase();
            case ACTIVE -> current.activePhase();
        };
    }

    @Override
    Map<String, Object> stateMessage(Room room) {
        double now = room.clock();
        Map<String, Object> m = new HashMap<>();
        m.put("type", "monster"); m.put("id", id); m.put("code", spec.code()); m.put("name", spec.name()); m.put("en", spec.nameEn());
        m.put("img", spec.image()); m.put("x", x); m.put("y", y); m.put("r", radius);
        m.put("hp", hp); m.put("max", maxHp); m.put("left", Math.max(0, Math.round((endsAt - now) * 1000)));
        m.put("flame", flame); m.put("rage", rage); m.put("ph", phaseName());
        m.put("tgt", targetE == null ? 0 : targetE);
        m.put("vx", Math.round(vx)); m.put("vy", Math.round(vy));
        if (ph == Ph.WINDUP) {
            Map<String, Object> tele = current.telegraph(this, room);
            if (tele != null) {
                tele = new HashMap<>(tele);
                tele.put("ms", Math.max(0, Math.round((phUntil - now) * 1000)));
                tele.put("total", Math.round(p(room, current.id + "_windup") * 1000));
                m.put("tele", tele);
            }
        }
        return m;
    }

    @Override
    Map<String, Object> viewMessage(Room room) {
        double now = room.clock();
        Map<String, Object> m = topList(5);
        m.put("id", id); m.put("spawnId", spec.spawnId()); m.put("code", spec.code()); m.put("name", spec.name());
        m.put("hp", hp); m.put("maxHp", maxHp); m.put("leftSeconds", Math.max(0, Math.round(endsAt - now)));
        m.put("phase", phaseName().toUpperCase()); m.put("rage", rage); m.put("flame", flame); m.put("x", Math.round(x)); m.put("y", Math.round(y));
        m.put("humans", start.humans()); m.put("avgScore", start.avgScore()); m.put("topScore", start.topScore());
        m.put("participants", ledger.size());
        Room.Member t = target(room);
        m.put("target", t == null ? null : t.nickname);
        for (BossSkill s : skills) m.put(s.id + "ReadyIn", Math.max(0, Math.round(s.readyAt - now)));
        return m;
    }
}
