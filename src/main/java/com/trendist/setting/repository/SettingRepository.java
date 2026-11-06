package com.trendist.setting.repository;

import com.trendist.setting.entity.Setting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettingRepository extends JpaRepository<Setting, Long> {
    Optional<Setting> findByName(String name);

    /** 관리자 화면용: 그룹 순서대로, 같은 그룹 안에서는 아무 순서(코드의 순서로 다시 정렬한다). 그룹을 함께 읽는다. */
    @Query("select s from Setting s join fetch s.group g order by g.sortOrder asc, s.id asc")
    List<Setting> findAllWithGroup();
}
