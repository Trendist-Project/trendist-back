package com.trendist.game.room;

import java.util.HashMap;
import java.util.Map;

/**
 * 스킬 메시지(4.33)를 만드는 한 곳. 클라이언트의 `game/protocol.ts`(SkillFx, ServerMessage)와 필드 이름·타입이 짝이다:
 * 여기서 이름을 바꾸면 그 파일도 같이 바꾼다. 시간은 ms(long), 원 번호는 int, 좌표·길이는 세계 좌표(double).
 * fx…는 모두에게 보내는 연출(`type: skillFx`), 나머지는 맞은 본인에게만 보내는 알림이다.
 */
final class SkillFx {
    private SkillFx() {}

    private static Map<String, Object> fx(String id) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "skillFx"); m.put("id", id);
        return m;
    }
    private static Map<String, Object> msg(String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        return m;
    }
    static long ms(double seconds) { return Math.round(seconds * 1000); }

    // ---- 모두에게: 연출

    /** 반발("repel")·충격파("quake")가 (x, y)에서 반지름 r0로 시작해 speed로 rMax까지 퍼진다 */
    static Map<String, Object> wave(String id, int e, double x, double y, double r0, double rMax, double speed) {
        Map<String, Object> m = fx(id);
        m.put("e", e); m.put("x", x); m.put("y", y); m.put("r0", r0); m.put("rMax", rMax); m.put("speed", speed);
        return m;
    }
    /** 충격파가 원 t에 닿아 키워드 n개를 잃게 했다 */
    static Map<String, Object> quakeHit(int t, int n) { Map<String, Object> m = fx("quakeHit"); m.put("t", t); m.put("n", n); return m; }

    static Map<String, Object> hook(int e, double dx, double dy, double range, long ms) {
        Map<String, Object> m = fx("hook");
        m.put("e", e); m.put("dx", dx); m.put("dy", dy); m.put("range", range); m.put("ms", ms);
        return m;
    }
    static Map<String, Object> hookHit(int e, int t, long ms) { Map<String, Object> m = fx("hookHit"); m.put("e", e); m.put("t", t); m.put("ms", ms); return m; }
    static Map<String, Object> hookEnd(int e) { Map<String, Object> m = fx("hookEnd"); m.put("e", e); return m; }

    static Map<String, Object> stealth(int e, long ms) { Map<String, Object> m = fx("stealth"); m.put("e", e); m.put("ms", ms); return m; }
    static Map<String, Object> stealthEnd(int e) { Map<String, Object> m = fx("stealthEnd"); m.put("e", e); return m; }

    /** 투사체(kind: poison·darkness·slow·stun)가 날아간다 */
    static Map<String, Object> bolt(String kind, int bid, int e, double x, double y, double dx, double dy, double speed, double radius, double range) {
        Map<String, Object> m = fx("bolt");
        m.put("kind", kind); m.put("bid", bid); m.put("e", e); m.put("x", x); m.put("y", y); m.put("dx", dx); m.put("dy", dy);
        m.put("speed", speed); m.put("radius", radius); m.put("range", range);
        return m;
    }
    /** 투사체가 사거리 끝에서(또는 쓴 사람이 사라져) 없어졌다 */
    static Map<String, Object> boltEnd(int bid) { Map<String, Object> m = fx("boltEnd"); m.put("bid", bid); return m; }
    /** 투사체가 원 hit에 (x, y)에서 맞았다 */
    static Map<String, Object> boltHit(int bid, int hit, double x, double y) {
        Map<String, Object> m = boltEnd(bid);
        m.put("hit", hit); m.put("x", x); m.put("y", y);
        return m;
    }

    /** 상태 효과가 걸렸다(id: poison·slow는 겹 수가 있다) */
    static Map<String, Object> status(String id, int t, int stacks, long ms) { Map<String, Object> m = fx(id); m.put("t", t); m.put("stacks", stacks); m.put("ms", ms); return m; }
    /** 겹 수가 없는 상태 효과(darkness·stun) */
    static Map<String, Object> status(String id, int t, long ms) { Map<String, Object> m = fx(id); m.put("t", t); m.put("ms", ms); return m; }
    /** 상태 효과가 풀렸다(id: poisonEnd·slowEnd·stunEnd) */
    static Map<String, Object> statusEnd(String id, int t) { Map<String, Object> m = fx(id); m.put("t", t); return m; }

    static Map<String, Object> cleanse(int e) { Map<String, Object> m = fx("cleanse"); m.put("e", e); return m; }
    static Map<String, Object> heal(int e) { Map<String, Object> m = fx("heal"); m.put("e", e); return m; }

    /** 화염구 한 발이 (x, y)에서 원 t를 쫓아 speed로 날아간다 */
    static Map<String, Object> fireball(int fid, int e, int t, double x, double y, double speed) {
        Map<String, Object> m = fx("fireball");
        m.put("fid", fid); m.put("e", e); m.put("t", t); m.put("x", x); m.put("y", y); m.put("speed", speed);
        return m;
    }
    /** 화염구가 맞지 못하고 사라졌다 */
    static Map<String, Object> fireballEnd(int fid) { Map<String, Object> m = fx("fireballEnd"); m.put("fid", fid); m.put("hit", false); return m; }
    /** 화염구가 원 t에 닿았다(hit이 false면 세이프존·무적이라 꺼졌다) */
    static Map<String, Object> fireballEnd(int fid, boolean hit, int t, double x, double y) {
        Map<String, Object> m = fx("fireballEnd");
        m.put("fid", fid); m.put("hit", hit); m.put("t", t); m.put("x", x); m.put("y", y);
        return m;
    }

    static Map<String, Object> absorb(int e, int t, long ms, double range) { Map<String, Object> m = fx("absorb"); m.put("e", e); m.put("t", t); m.put("ms", ms); m.put("range", range); return m; }
    static Map<String, Object> absorbTick(int e, int t) { Map<String, Object> m = fx("absorbTick"); m.put("e", e); m.put("t", t); return m; }
    static Map<String, Object> absorbEnd(int e, int t) { Map<String, Object> m = fx("absorbEnd"); m.put("e", e); m.put("t", t); return m; }

    /** 무적(방패 스킬·키워드를 잃은 뒤)의 보호막과 카운트다운 */
    static Map<String, Object> shield(int e, long ms) { Map<String, Object> m = msg("shield"); m.put("e", e); m.put("ms", ms); return m; }

    // ---- 본인에게: 알림

    /** 스킬을 썼다: 다시 쓸 수 있을 때까지 */
    static Map<String, Object> used(String id, long cooldownMs) { Map<String, Object> m = msg("skill"); m.put("id", id); m.put("cooldownMs", cooldownMs); return m; }
    /** 스킬을 쓸 수 없었다(reason: safe·unequipped·hpFull·noTarget) */
    static Map<String, Object> fail(String id, String reason) { Map<String, Object> m = msg("skillFail"); m.put("id", id); m.put("reason", reason); return m; }
    /** 갈고리에 맞아 byE에게 끌려온다 */
    static Map<String, Object> pull(long ms, int byE) { Map<String, Object> m = msg("pull"); m.put("ms", ms); m.put("byE", byE); return m; }
    static Map<String, Object> pullEnd() { return msg("pullEnd"); }
    static Map<String, Object> dark(long ms) { Map<String, Object> m = msg("dark"); m.put("ms", ms); return m; }
    static Map<String, Object> darkEnd() { return msg("darkEnd"); }
    /** 이동 속도가 ms 동안 mult배가 된다 */
    static Map<String, Object> slow(long ms, double mult) { Map<String, Object> m = msg("slow"); m.put("ms", ms); m.put("mult", mult); return m; }
    static Map<String, Object> slowEnd() { return msg("slowEnd"); }
    static Map<String, Object> stun(long ms) { Map<String, Object> m = msg("stun"); m.put("ms", ms); return m; }
    static Map<String, Object> stunEnd() { return msg("stunEnd"); }
}
