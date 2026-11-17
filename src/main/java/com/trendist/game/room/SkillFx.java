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

    /** 장판: 원 e를 중심으로 따라다니는 반지름 r의 장판을 ms 동안 깔았다((x, y)는 깔 때의 e의 자리) */
    static Map<String, Object> zone(int e, double x, double y, double r, long ms, long tickMs) {
        Map<String, Object> m = fx("zone");
        m.put("e", e); m.put("x", x); m.put("y", y); m.put("r", r); m.put("ms", ms); m.put("tickMs", tickMs);
        return m;
    }
    /** 시전자 e의 장판이 일찍 끝났다(시전자가 나가거나 죽음) */
    static Map<String, Object> zoneEnd(int e) { Map<String, Object> m = fx("zoneEnd"); m.put("e", e); return m; }
    /** 터지는 총알에 맞은 원 t가 ms 뒤에 터진다 */
    static Map<String, Object> burstFuse(int t, long ms) { Map<String, Object> m = fx("burstFuse"); m.put("t", t); m.put("ms", ms); return m; }
    /** 터지는 총알이 원 t의 (x, y)에서 터졌다 */
    static Map<String, Object> burstBoom(int t, double x, double y) { Map<String, Object> m = fx("burstBoom"); m.put("t", t); m.put("x", x); m.put("y", y); return m; }

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

    /** 버프(amplify 공격 증폭·fortify 피해 감소)가 원 e에 ms 동안 걸렸다. 끝나면 amplifyEnd·fortifyEnd */
    static Map<String, Object> buff(String id, int e, long ms) { Map<String, Object> m = fx(id); m.put("e", e); m.put("ms", ms); return m; }
    static Map<String, Object> buffEnd(String id, int e) { Map<String, Object> m = fx(id + "End"); m.put("e", e); return m; }
    /** 광폭화가 켜졌거나 갱신됐다: pct는 잃은 체력 비율(0~100). 끌 때까지 이어진다 */
    static Map<String, Object> berserk(int e, double pct) { Map<String, Object> m = fx("berserk"); m.put("e", e); m.put("pct", pct); return m; }
    /** 보호막: pct는 지금 쉴드량(최대 체력 대비 %), max는 처음 값 */
    static Map<String, Object> barrier(int e, long ms, double pct, double max) {
        Map<String, Object> m = fx("barrier"); m.put("e", e); m.put("ms", ms); m.put("pct", pct); m.put("max", max); return m;
    }
    static Map<String, Object> barrierHit(int e, double pct) { Map<String, Object> m = fx("barrierHit"); m.put("e", e); m.put("pct", pct); return m; }
    static Map<String, Object> barrierEnd(int e, boolean broken) { Map<String, Object> m = fx("barrierEnd"); m.put("e", e); if (broken) m.put("broken", true); return m; }

    /** 무적(무적 스킬·키워드를 잃은 뒤)의 보호막과 카운트다운 */
    static Map<String, Object> invincibility(int e, long ms) { Map<String, Object> m = msg("invincibility"); m.put("e", e); m.put("ms", ms); return m; }

    // ---- 본인에게: 알림

    /** 스킬을 썼다: 다시 쓸 수 있을 때까지(cooldownMs)와 효과가 이어지는 시간(activeMs, 없으면 0: 칸에 "켜져 있음"을 띄우는 시간) */
    static Map<String, Object> used(String id, long cooldownMs, long activeMs) { Map<String, Object> m = msg("skill"); m.put("id", id); m.put("cooldownMs", cooldownMs); m.put("activeMs", activeMs); return m; }
    /** 스킬을 쓸 수 없었다(reason: safe·unequipped·hpFull·noTarget) */
    static Map<String, Object> fail(String id, String reason) { Map<String, Object> m = msg("skillFail"); m.put("id", id); m.put("reason", reason); return m; }
    /** 갈고리에 맞아 byE에게 끌려온다 */
    static Map<String, Object> pull(long ms, int byE) { Map<String, Object> m = msg("pull"); m.put("ms", ms); m.put("byE", byE); return m; }
    static Map<String, Object> pullEnd() { return msg("pullEnd"); }
    static Map<String, Object> dark(long ms) { Map<String, Object> m = msg("dark"); m.put("ms", ms); return m; }
    static Map<String, Object> darkEnd() { return msg("darkEnd"); }
    /** 이동 속도가 ms 동안 mult배가 된다 */
    static Map<String, Object> slow(long ms, double mult) { Map<String, Object> m = msg("slow"); m.put("ms", ms); m.put("mult", mult); return m; }
    /** 이동 속도 배수만 조용히 바꾼다(속박·광폭화 때문에 배수가 달라졌다. 토스트는 띄우지 않는다) */
    static Map<String, Object> slowQuiet(long ms, double mult) { Map<String, Object> m = slow(ms, mult); m.put("quiet", true); return m; }
    static Map<String, Object> slowEnd() { return msg("slowEnd"); }
    /** 혼란에 걸려 ms 동안 조작 방향이 반대다 */
    static Map<String, Object> confuse(long ms) { Map<String, Object> m = msg("confuse"); m.put("ms", ms); return m; }
    static Map<String, Object> confuseEnd() { return msg("confuseEnd"); }
    static Map<String, Object> stun(long ms) { Map<String, Object> m = msg("stun"); m.put("ms", ms); return m; }
    static Map<String, Object> stunEnd() { return msg("stunEnd"); }
}
