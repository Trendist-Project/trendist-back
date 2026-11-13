package com.trendist.game.room;

/**
 * 상태 효과(4.33): 독·암흑·슬로우·스턴을 거는 것, 푸는 것, 시간을 세는 것을 한 곳에서 한다. 효과는 원(Member.effects)에 종류마다 하나씩 붙고
 * (종류, 겹 수, 끝 시각, 건 사람), 걸릴 때와 풀릴 때 모두에게(연출) 그리고 맞은 본인에게(화면·예측) 알린다.
 * 새 효과는 Kind에 더하고 거는 메서드 하나와 clear의 알림 한 갈래를 더한다. 수치는 스킬 설정(SkillConfig)에서 읽는다.
 * 갈고리에 끌려오는 것은 위치를 움직이는 일이라 HookSkill이 따로 맡는다.
 */
final class StatusEffects {
    /** 효과 종류. POISON은 남은 틱 수로 끝나고, 나머지는 until(방 시계 초)에 끝난다 */
    enum Kind { POISON, DARK, SLOW, STUN }

    /** 원에 붙은 효과 하나: 겹 수, 끝나는 시각, (독) 다음 틱 시각·남은 틱 수, 건 사람 */
    static final class Effect {
        int stacks;
        double until, nextAt;
        int ticksLeft;
        Room.Member owner;
    }

    /** 암흑에 걸린 사람의 화면은 자기 원 중심에서 반지름의 이 배수 안만 보인다(클라이언트 `drawDarkness`와 같은 값). 봇의 시야도 같다 */
    static final double DARK_SIGHT_RATIO = 1.5;

    private final Room room;

    StatusEffects(Room room) { this.room = room; }

    /** 그 효과가 지금 걸려 있는지 */
    boolean has(Room.Member m, Kind kind) {
        Effect e = m.effects.get(kind);
        if (e == null) return false;
        return kind == Kind.POISON ? e.stacks > 0 : room.now() < e.until;
    }

    /** 걸려 있는 겹 수(없으면 0). 독·슬로우만 겹친다 */
    int stacks(Room.Member m, Kind kind) { return has(m, kind) ? m.effects.get(kind).stacks : 0; }

    boolean stunned(Room.Member m) { return has(m, Kind.STUN); }

    /** 지금 이동 속도 배수: 슬로우 겹 수만큼 slow_percent를 거듭 곱한다(없으면 1) */
    double slowMult(Room.Member m) {
        int n = stacks(m, Kind.SLOW);
        return n > 0 ? Math.pow(room.skillConfig().param("slow_percent") / 100.0, n) : 1;
    }

    /** 투사체가 맞힌 효과를 건다(kind: 스킬 코드 poison·darkness·slow·stun) */
    void applyBolt(String kind, Room.Member t, Room.Member owner) {
        switch (kind) {
            case "poison" -> poison(t, owner);
            case "slow" -> slow(t, owner);
            case "stun" -> stun(t, owner);
            default -> dark(t, owner);
        }
    }

    /** 독: 이미 중독이면 겹이 오르고(상한 poison_max_stacks) 틱을 처음부터 다시 센다. 틱마다 겹 수만큼 잃는다 */
    void poison(Room.Member t, Room.Member owner) {
        SkillConfig c = room.skillConfig();
        Effect e = effect(t, Kind.POISON, owner);
        e.stacks = e.stacks > 0 ? Math.min(c.intParam("poison_max_stacks"), e.stacks + 1) : 1;
        e.ticksLeft = c.intParam("poison_ticks");
        e.nextAt = room.now() + c.param("poison_tick_seconds");
        room.broadcast(SkillFx.status("poison", t.entityNo, e.stacks, SkillFx.ms(e.ticksLeft * c.param("poison_tick_seconds"))));
    }

    /**
     * 암흑: 맞은 사람의 화면이 darkness_seconds 동안 자기 원 둘레만 보이게 어두워진다(그리는 것은 클라이언트). 봇도 같은 시간 동안
     * 자기 원 중심에서 반지름 DARK_SIGHT_RATIO배 안의 원·구슬만 본다(Room.thinkBots). 다른 사람에게도 걸린 원이 표시된다.
     */
    void dark(Room.Member t, Room.Member owner) {
        double seconds = room.skillConfig().param("darkness_seconds");
        effect(t, Kind.DARK, owner).until = room.now() + seconds;
        room.tell(t, SkillFx.dark(SkillFx.ms(seconds)));
        room.broadcast(SkillFx.status("darkness", t.entityNo, SkillFx.ms(seconds))); // 풀리면(정화) cleanse 신호로 지운다
    }

    /** 슬로우: 이미 걸려 있으면 겹이 오르고(최대 slow_max_stacks) 시간은 처음부터 다시 센다 */
    void slow(Room.Member t, Room.Member owner) {
        SkillConfig c = room.skillConfig();
        boolean on = has(t, Kind.SLOW);
        Effect e = effect(t, Kind.SLOW, owner);
        e.stacks = on ? Math.min(c.intParam("slow_max_stacks"), e.stacks + 1) : 1;
        double seconds = c.param("slow_seconds");
        e.until = room.now() + seconds;
        room.tell(t, SkillFx.slow(SkillFx.ms(seconds), slowMult(t)));
        room.broadcast(SkillFx.status("slow", t.entityNo, e.stacks, SkillFx.ms(seconds)));
    }

    /** 스턴: stun_seconds 동안 움직이지도 스킬을 쓰지도 못한다. 이미 스턴이면 시간을 더하지 않고 더 긴 쪽을 쓴다 */
    void stun(Room.Member t, Room.Member owner) {
        double seconds = room.skillConfig().param("stun_seconds");
        Effect e = effect(t, Kind.STUN, owner);
        e.until = Math.max(e.until, room.now() + seconds);
        t.vx = 0; t.vy = 0; t.targetX = null; t.targetY = null;
        room.tell(t, SkillFx.stun(SkillFx.ms(seconds)));
        room.broadcast(SkillFx.status("stun", t.entityNo, SkillFx.ms(seconds)));
    }

    private Effect effect(Room.Member t, Kind kind, Room.Member owner) {
        Effect e = t.effects.computeIfAbsent(kind, k -> new Effect());
        if (!has(t, kind)) { e.stacks = 0; e.until = 0; } // 끝난 효과가 남아 있으면 새로 건다
        e.owner = owner;
        return e;
    }

    /** 효과 하나를 푼다(걸려 있지 않으면 아무 일도 없다). 본인과 모두에게 알린다 */
    void clear(Room.Member m, Kind kind) {
        boolean on = has(m, kind);
        m.effects.remove(kind);
        if (!on) return;
        switch (kind) {
            case POISON -> room.broadcast(SkillFx.statusEnd("poisonEnd", m.entityNo));
            case DARK -> room.tell(m, SkillFx.darkEnd()); // 다른 사람 화면의 표시는 정화 연출(cleanse)이 지운다
            case SLOW -> { room.tell(m, SkillFx.slowEnd()); room.broadcast(SkillFx.statusEnd("slowEnd", m.entityNo)); }
            case STUN -> { room.tell(m, SkillFx.stunEnd()); room.broadcast(SkillFx.statusEnd("stunEnd", m.entityNo)); }
        }
    }

    /** 걸려 있는 효과를 모두 푼다(정화) */
    void clearAll(Room.Member m) { for (Kind k : Kind.values()) clear(m, k); }

    /**
     * 한 틱: 독은 다음 틱 시각이 됐으면 겹 수만큼 키워드를 잃게 하고(히트 하나, Room.skillHit) 마지막 틱이면 끝난다.
     * 잃은 뒤 무적은 독을 막지 않고 독도 무적을 주지 않는다. 슬로우·스턴은 시간이 다 되면 풀렸다고 알린다(암흑은 클라이언트가 스스로 걷는다).
     */
    void tick() {
        SkillConfig c = room.skillConfig();
        for (Room.Member m : room.all()) {
            if (m.effects.isEmpty()) continue;
            Effect p = m.effects.get(Kind.POISON);
            if (p != null && p.stacks > 0) {
                if (m.dead) clear(m, Kind.POISON);
                else if (room.now() + 1e-9 >= p.nextAt) {
                    room.skillHit(p.owner, m, "poison", p.stacks); // 틱마다 히트 한 번, 겹 수만큼 잃는다
                    p.ticksLeft--;
                    p.nextAt += c.param("poison_tick_seconds");
                    if (p.ticksLeft <= 0) clear(m, Kind.POISON);
                }
            }
            for (Kind k : new Kind[]{Kind.SLOW, Kind.STUN}) {
                Effect e = m.effects.get(k);
                if (e == null || room.now() < e.until) continue;
                m.effects.remove(k);
                if (k == Kind.SLOW) { room.tell(m, SkillFx.slowEnd()); room.broadcast(SkillFx.statusEnd("slowEnd", m.entityNo)); }
                // 스턴은 시간이 다 되면 클라이언트가 스스로 푼다(stunEnd는 정화로 일찍 풀릴 때만 보낸다)
            }
        }
    }
}
