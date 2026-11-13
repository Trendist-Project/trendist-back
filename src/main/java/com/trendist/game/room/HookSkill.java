package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 갈고리(4.33): 내 원 중심에서 겨눈 방향으로 가장자리에서 화면 기준 hook_reach_px까지 hook_seconds에 걸쳐 뻗는다(논타겟). 처음 닿은 원 하나가
 * 키워드 하나를 잃고(쓴 사람이 빼앗는다, 없으면 끌려오기만 한다) hook_pull_seconds 동안 쓴 사람에게 끌려온다.
 * 끌려오는 동안(Member.hookedBy) 맞은 원은 움직이지도 스킬을 쓰지도 못하고 쓴 사람 말고는 부딪히지 않으며, 끌려와서 쓴 사람과는 무조건 부딪혀
 * 보통 충돌(4.3)로 가려진다. 잃은 뒤 무적은 끌려온 뒤에 시작한다(finish).
 * 끌어당기기(pull)는 모든 원이 움직인 뒤, 풀어 주기(finish)는 충돌이 끝난 뒤라서 Room.tick이 그 자리에서 직접 부른다.
 */
final class HookSkill implements PlayerSkill {
    private record Hook(Room.Member owner, double dirX, double dirY, double range, double startedAt, double seconds) {}

    private final Room room;
    private final List<Hook> hooks = new ArrayList<>();

    HookSkill(Room room) { this.room = room; }

    public String code() { return "hook"; }
    public boolean offensive() { return true; }

    public boolean cast(Room.Member m, double ax, double ay) {
        SkillConfig c = room.skillConfig();
        double dx = ax - m.x, dy = ay - m.y, len = Math.hypot(dx, dy);
        if (!Double.isFinite(len) || len < 1e-6) { dx = m.vx; dy = m.vy; len = Math.hypot(dx, dy); } // 겨누지 않았으면 가던 방향
        if (len < 1e-6) { dx = 0; dy = -1; len = 1; }
        double seconds = Math.max(0.05, c.param("hook_seconds")), range = m.displayRadius + room.reachWorld(Math.max(1, c.param("hook_reach_px")), m.displayRadius);
        hooks.add(new Hook(m, dx / len, dy / len, range, room.now(), seconds));
        room.broadcast(SkillFx.hook(m.entityNo, dx / len, dy / len, range, SkillFx.ms(seconds)));
        return true;
    }

    /** 뻗는 갈고리가 처음 닿은 원 하나를 붙잡는다. 쓴 사람이 사라지거나 끌려오는 중이면 거둔다. 끝까지 뻗어도 못 맞히면 거둔다 */
    public void update(double dt) {
        for (Iterator<Hook> it = hooks.iterator(); it.hasNext(); ) {
            Hook h = it.next();
            Room.Member o = h.owner();
            if (!room.present(o) || o.hookedBy != null) { it.remove(); room.broadcast(SkillFx.hookEnd(o.entityNo)); continue; }
            double elapsed = room.now() - h.startedAt() + 1e-9, head = h.range() * Math.min(1, elapsed / h.seconds()); // 부동소수 오차로 끝까지 못 뻗지 않게
            Room.Member best = null;
            double bestAt = Double.MAX_VALUE;
            for (Room.Member t : room.all()) {
                if (!room.targetable(o, t) || t.hookedBy != null) continue;
                double vx = t.x - o.x, vy = t.y - o.y, proj = vx * h.dirX() + vy * h.dirY(), perp = Math.abs(vx * h.dirY() - vy * h.dirX());
                double tr = t.displayRadius, disc = tr * tr - perp * perp;
                if (disc < 0) continue; // 빗나감
                double entry = proj - Math.sqrt(disc); // 갈고리 선이 그 원에 처음 닿는 거리
                if (entry < 0 && Math.hypot(vx, vy) > tr) continue; // 원이 뒤쪽에 있다
                entry = Math.max(0, entry);
                if (entry > head || entry > h.range()) continue; // 아직 거기까지 안 뻗었다
                if (entry < bestAt) { best = t; bestAt = entry; }
            }
            if (best != null) { hit(o, best); it.remove(); }
            else if (elapsed >= h.seconds()) { it.remove(); room.broadcast(SkillFx.hookEnd(o.entityNo)); }
        }
    }

    private void hit(Room.Member o, Room.Member v) {
        v.hookedBy = o; v.pullLeft = Math.max(0.05, room.skillConfig().param("hook_pull_seconds")); v.hookLost = false;
        v.kbTime = 0; v.vx = 0; v.vy = 0; v.targetX = null;
        long ms = SkillFx.ms(v.pullLeft);
        room.tell(v, SkillFx.pull(ms, o.entityNo));
        room.broadcast(SkillFx.hookHit(o.entityNo, v.entityNo, ms));
        v.hookLost = room.skillHit(o, v, code(), 1);
    }

    /** 끌려오는 원을 쓴 사람 쪽으로 남은 시간에 맞춰 당긴다(원이 서로 닿는 자리까지). 쓴 사람이 사라졌으면 멈춘다 */
    void pull(double dt, double half) {
        for (Room.Member m : room.all()) {
            Room.Member o = m.hookedBy;
            if (o == null || m.pullLeft <= 0 || !room.present(o)) continue;
            double dx = o.x - m.x, dy = o.y - m.y, d = Math.hypot(dx, dy), gap = d - (m.displayRadius + o.displayRadius) * 0.98;
            if (gap > 0 && d > 1e-6) {
                double k = m.pullLeft <= dt ? 1 : dt / m.pullLeft;
                m.x += dx / d * gap * k; m.y += dy / d * gap * k;
            }
            m.pullLeft = Math.max(0, m.pullLeft - dt);
            m.vx = 0; m.vy = 0; m.kbTime = 0;
            double r = m.displayRadius;
            m.x = Room.clamp(m.x, -half + r, half - r); m.y = Room.clamp(m.y, -half + r, half - r);
        }
    }

    /** 충돌 처리가 끝난 뒤, 다 끌려왔거나 쓴 사람이 사라진 원을 풀어 준다. 맞을 때 키워드를 잃었으면 이제 잃은 뒤 무적(4.3)을 준다 */
    void finish() {
        for (Room.Member m : room.all()) {
            Room.Member o = m.hookedBy;
            if (o == null) continue;
            if (m.pullLeft > 0 && room.present(o)) continue;
            unhook(m);
        }
    }

    /** 정화: 끌려오는 중이면 바로 풀고, 맞을 때 잃었으면 받을 무적을 지금 준다 */
    public void release(Room.Member m) {
        if (m.hookedBy == null) return;
        unhook(m);
        room.tell(m, SkillFx.pullEnd());
    }

    private void unhook(Room.Member m) {
        m.hookedBy = null; m.pullLeft = 0;
        if (m.hookLost) { m.hookLost = false; room.protectAfterLoss(m); }
    }
}
