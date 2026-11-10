package com.trendist.monster.repository;

import com.trendist.monster.entity.MonsterSpawn;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterSpawnRepository extends JpaRepository<MonsterSpawn, Long> {
    /** 그 시각 이후에 이 종류가 이 나라에서 이미 나왔거나 건너뛰었는지(서버를 다시 켜도 같은 정각에 두 번 나오지 않게) */
    boolean existsByMonsterTypeIdAndCountryIdAndStartedAtGreaterThanEqual(Long monsterTypeId, Long countryId, Instant since);
    List<MonsterSpawn> findAllByOrderByIdDesc(Pageable page);
    List<MonsterSpawn> findByStatus(String status);
}
