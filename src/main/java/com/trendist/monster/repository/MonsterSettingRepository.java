package com.trendist.monster.repository;

import com.trendist.monster.entity.MonsterSetting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterSettingRepository extends JpaRepository<MonsterSetting, Long> {
    List<MonsterSetting> findByMonsterTypeIdOrderBySortOrderAscIdAsc(Long monsterTypeId);
    Optional<MonsterSetting> findByMonsterTypeIdAndName(Long monsterTypeId, String name);
}
