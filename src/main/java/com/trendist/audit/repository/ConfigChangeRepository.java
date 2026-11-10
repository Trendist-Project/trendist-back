package com.trendist.audit.repository;

import com.trendist.audit.entity.ConfigChange;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigChangeRepository extends JpaRepository<ConfigChange, Long> {
    /** 최신순. scope·검색어(대상 이름에 포함)·beforeId(이 번호보다 앞선 것만, 더 보기)는 비우면 조건 없음 */
    @Query("select c from ConfigChange c where (:scope = '' or c.scope = :scope) and (:q = '' or lower(c.target) like concat('%', :q, '%')) "
            + "and (:beforeId = 0 or c.id < :beforeId) order by c.id desc")
    List<ConfigChange> search(@Param("scope") String scope, @Param("q") String q, @Param("beforeId") long beforeId, Pageable page);
}
