package com.trendist.setting.repository;

import com.trendist.setting.entity.SettingGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingGroupRepository extends JpaRepository<SettingGroup, Long> {
    Optional<SettingGroup> findByName(String name);
}
