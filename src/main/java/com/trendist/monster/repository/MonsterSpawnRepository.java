package com.trendist.monster.repository;

import com.trendist.monster.entity.MonsterSpawn;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterSpawnRepository extends JpaRepository<MonsterSpawn, Long> {
    /** 그 시각 이후에 이 종류가 이 나라에서 이미 나왔거나 건너뛰었는지(서버를 다시 켜도 같은 정각에 두 번 나오지 않게) */
    boolean existsByMonsterTypeIdAndCountryIdAndStartedAtGreaterThanEqual(Long monsterTypeId, Long countryId, Instant since);
    List<MonsterSpawn> findAllByOrderByIdDesc(Pageable page);
    /** 한 종류의 등장 기록(최신순은 호출하는 쪽이 Pageable의 정렬로 정한다) */
    Page<MonsterSpawn> findByMonsterTypeId(Long monsterTypeId, Pageable page);
    List<MonsterSpawn> findByStatus(String status);
    /** 목록: 예전에 남긴 건너뜀(SKIPPED) 행은 빼고 보여 준다 */
    Page<MonsterSpawn> findByStatusNot(String status, Pageable page);
    Page<MonsterSpawn> findByMonsterTypeIdAndStatusNot(Long monsterTypeId, String status, Pageable page);
    /** 출현표: 기간 안에 시작한 등장(건너뜀 제외, 시작 시각순) */
    List<MonsterSpawn> findByStartedAtGreaterThanEqualAndStartedAtLessThanAndStatusNotOrderByStartedAtAsc(Instant from, Instant to, String status);
}
