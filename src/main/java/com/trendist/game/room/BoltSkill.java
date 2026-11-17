package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 투사체(4.33): 독·암흑·슬로우·스턴. 내 원 가장자리에서 겨눈 방향으로 날아가고(논타겟) 처음 닿은 원 하나가 맞아 그 상태 효과(StatusEffects)에 걸린다.
 * 사거리(화면 기준 bolt_reach_px) 끝까지 빗나가면 사라진다. 화면 속도는 갈고리가 뻗는 속도(hook_reach_px ÷ hook_seconds)와 같다.
 * 반지름 = 쓴 원 반지름 × RADIUS_RATIO(최소 MIN_RADIUS px)이고 판정과 화면 그림이 같은 값을 쓴다(서버가 radius를 보낸다).
 * 세이프존·무적 스킬·정화 직후 면역인 원은 지나간다.
 */
final class BoltSkill implements PlayerSkill {
    static final double RADIUS_RATIO = 0.3, MIN_RADIUS = 10;

    private static final class Bolt {
        final int id; final Room.Member owner; double x, y, left; final double dx, dy, speed, radius;
        Bolt(int id, Room.Member owner, double x, double y, double dx, double dy, double speed, double radius, double range) {
            this.id = id; this.owner = owner; this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.speed = speed; this.radius = radius; this.left = range;
        }
    }

    private final Room room;
    private final BoltKind kind;
    private final String code;
    private final List<Bolt> bolts = new ArrayList<>();
    /** 터지는 총알(burst)에 맞아 곧 터질 원: 맞은 원, 쏜 사람, 터질 시각 */
    private record Fuse(Room.Member victim, Room.Member owner, double at) {}
    private final List<Fuse> fuses = new ArrayList<>();

    BoltSkill(Room room, BoltKind kind) { this.room = room; this.kind = kind; this.code = kind.code; }

    public String code() { return code; }
    public boolean offensive() { return true; }

    int flying() { return bolts.size(); }

    public boolean cast(Room.Member m, double ax, double ay) {
        SkillConfig c = room.skillConfig();
        double dx = ax - m.x, dy = ay - m.y, len = Math.hypot(dx, dy);
        if (!Double.isFinite(len) || len < 1e-6) { dx = m.vx; dy = m.vy; len = Math.hypot(dx, dy); }
        if (len < 1e-6) { dx = 0; dy = -1; len = 1; }
        dx /= len; dy /= len;
        double speed = room.reachWorld(c.param("hook_reach_px") / Math.max(0.05, c.param("hook_seconds")), m.displayRadius);
        double radius = Math.max(MIN_RADIUS, m.displayRadius * RADIUS_RATIO), range = room.reachWorld(c.param("bolt_reach_px"), m.displayRadius);
        Bolt b = new Bolt(room.nextBoltId(), m, m.x + dx * m.displayRadius, m.y + dy * m.displayRadius, dx, dy, speed, radius, range);
        bolts.add(b);
        room.broadcast(SkillFx.bolt(code, b.id, m.entityNo, b.x, b.y, dx, dy, speed, radius, range));
        return true;
    }

    /** 터지는 총알에 맞은 원에 도화선을 단다(같은 원에 다시 맞으면 새로 단다) */
    private void armFuse(Room.Member owner, Room.Member victim) {
        double fuse = room.skillConfig().param("burst_fuse_seconds");
        fuses.removeIf(f -> f.victim() == victim);
        fuses.add(new Fuse(victim, owner, room.now() + fuse));
        room.broadcast(SkillFx.burstFuse(victim.entityNo, SkillFx.ms(fuse)));
    }

    /** 터지는 총알: 맞은 원이 burst_fuse_seconds 뒤에 터진다. 터질 때 맞힐 수 있는 상태면 두 번째 히트(체력 피해 burst_explode_damage_percent)를 주고 무작위 방향으로 튕겨 낸다 */
    private void explodeDue() {
        for (Iterator<Fuse> it = fuses.iterator(); it.hasNext(); ) {
            Fuse f = it.next();
            if (!room.present(f.victim())) { it.remove(); continue; }
            if (room.now() + 1e-9 < f.at()) continue;
            it.remove();
            Room.Member v = f.victim();
            if (!room.targetable(f.owner(), v)) continue; // 세이프존·무적이 되었으면 불발
            room.broadcast(SkillFx.burstBoom(v.entityNo, v.x, v.y));
            room.skillHit(f.owner(), v, "burst", 1);
            if (!room.present(v)) continue; // 두 번째 히트로 죽었다
            double strength = room.collisionKnock(f.owner(), v); // 충돌의 튕김과 같은 세기
            if (strength <= 0) continue;
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            room.knockFrom(v, Math.cos(a), Math.sin(a), strength, room.tuning());
            v.targetX = null;
        }
    }

    /** 투사체를 한 틱 나아가게 하고, 지난 선분에 처음 닿은 원이 있으면 맞힌다 */
    public void update(double dt) {
        if (!fuses.isEmpty()) explodeDue();
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            Room.Member o = b.owner;
            if (!room.inRoom(o)) { it.remove(); room.broadcast(SkillFx.boltEnd(b.id)); continue; }
            double step = Math.min(b.speed * dt, b.left), nx = b.x + b.dx * step, ny = b.y + b.dy * step;
            Room.Member best = null; double bestT = Double.MAX_VALUE;
            for (Room.Member t : room.all()) {
                if (!room.targetable(o, t)) continue;
                double sx = nx - b.x, sy = ny - b.y, len2 = sx * sx + sy * sy;
                double u = len2 > 0 ? Math.max(0, Math.min(1, ((t.x - b.x) * sx + (t.y - b.y) * sy) / len2)) : 0;
                if (Math.hypot(b.x + sx * u - t.x, b.y + sy * u - t.y) <= t.displayRadius + b.radius && u < bestT) { best = t; bestT = u; }
            }
            if (best != null) {
                it.remove();
                room.broadcast(SkillFx.boltHit(b.id, best.entityNo, b.x + (nx - b.x) * bestT, b.y + (ny - b.y) * bestT));
                // 맞히면 히트 하나(콤보 +1, 체력 피해, 키워드 1개 잃기)인 종류는 먼저 히트를 주고, 그 히트로 죽었으면 효과를 걸지 않는다
                if (kind.hitsOnImpact) room.skillHit(o, best, code, 1);
                if (room.present(best)) {
                    if (kind == BoltKind.BURST) armFuse(o, best);  // 터지는 총알: 상태 효과 대신 도화선을 단다
                    else room.status().applyBolt(code, best, o);
                }
                continue;
            }
            b.x = nx; b.y = ny; b.left -= step;
            if (b.left <= 1e-6) { it.remove(); room.broadcast(SkillFx.boltEnd(b.id)); }
        }
    }
}
