package com.trendist.game.room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 고래의 노래(대왕 고래, 4.35): 예고 동안 몸에서 동심원 파동이 퍼지는 모습이 보이고(`song_radius_px` 둘레가 점선), 이어 그 안의 사람이 혼란(방향 키 반대)에
 * `song_confuse_seconds` 동안 걸린다. 피해는 없다. 원 밖·세이프존·무적 중이면 걸리지 않고, 정화 스킬로 풀 수 있다.
 */
final class SongSkill extends BossSkill {
    private double until;

    SongSkill() { super("song"); }

    @Override String windupPhase() { return "song_windup"; }
    @Override String activePhase() { return "song"; }

    @Override
    Map<String, Object> telegraph(SkillBoss b, Room room) {
        Map<String, Object> m = BossUtil.tele("song");
        m.put("spots", List.of(BossUtil.spot(b.x, b.y, b.p(room, "song_radius_px"))));
        return m;
    }

    @Override
    void begin(SkillBoss b, Room room, double now) {
        until = now + 1.2;
        double r = b.p(room, "song_radius_px"), secs = b.p(room, "song_confuse_seconds");
        Map<String, Object> e = new HashMap<>();
        e.put("x", Math.round(b.x)); e.put("y", Math.round(b.y)); e.put("r", Math.round(r)); e.put("ms", 1200); e.put("look", "song");
        BossUtil.fx(room, b, "pulse", e);
        for (Room.Member m : BossUtil.targets(room, now)) {
            if (Math.hypot(m.x - b.x, m.y - b.y) - m.displayRadius < r) room.status().confuseFor(m, secs);
        }
    }

    @Override boolean activeTick(SkillBoss b, Room room, double now, double dt) { return now >= until; }
}
