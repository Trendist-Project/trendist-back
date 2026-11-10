package com.trendist.monster.service;

import com.trendist.game.room.Monster;
import com.trendist.monster.entity.MonsterDamage;
import com.trendist.monster.entity.MonsterSpawn;
import com.trendist.monster.repository.MonsterDamageRepository;
import com.trendist.monster.repository.MonsterSpawnRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 몬스터 등장 기록과 플레이어별 피해를 DB에 남긴다(4.35). 방(틱) 밖의 작업 스레드나 스케줄러에서 부른다. */
@Service
@RequiredArgsConstructor
public class MonsterRecorder {
    private final MonsterSpawnRepository spawns;
    private final MonsterDamageRepository damages;

    /** 등장 기록을 만든다(체력은 방이 정한 뒤 started로 채운다). 건너뛴 것은 SKIPPED로 바로 끝낸다 */
    @Transactional
    public long open(long typeId, long countryId, String source, String status) {
        MonsterSpawn s = spawns.save(new MonsterSpawn(typeId, countryId, source, status, Instant.now()));
        if ("SKIPPED".equals(status)) s.finish("SKIPPED", 0, 0, null, 0);
        return s.getId();
    }

    @Transactional
    public void started(long spawnId, Monster.Start start) {
        spawns.findById(spawnId).ifPresent(s -> s.started(start.maxHp(), start.humans(), start.avgScore(), start.topScore()));
    }

    /** 끝난 결과를 기록한다: 상태·남은 체력·참여자 수·피해 1위, 플레이어별 피해 */
    @Transactional
    public void finish(Monster.Result r) {
        long spawnId = r.spec().spawnId();
        MonsterSpawn s = spawns.findById(spawnId).orElse(null);
        if (s == null) return;
        Monster.Part top = r.parts().isEmpty() ? null : r.parts().get(0);
        s.finish(r.status(), r.finalHp(), r.parts().size(), top == null ? null : top.playerId(), top == null ? 0 : top.damage());
        for (Monster.Part p : r.parts()) damages.save(new MonsterDamage(spawnId, p.playerId(), p.damage(), p.hits()));
    }

    /** 서버가 꺼질 때 싸우는 중이던 기록은 끝내지 못했으므로 다음에 켤 때 REMOVED로 닫는다 */
    @Transactional
    public int closeStale() {
        var stale = spawns.findByStatus("ACTIVE");
        for (MonsterSpawn s : stale) s.finish("REMOVED", s.getFinalHp(), 0, null, 0);
        return stale.size();
    }
}
