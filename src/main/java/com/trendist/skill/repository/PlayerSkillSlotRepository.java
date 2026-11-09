package com.trendist.skill.repository;

import com.trendist.skill.entity.PlayerSkillSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerSkillSlotRepository extends JpaRepository<PlayerSkillSlot, Long> {
    @Query("select s from PlayerSkillSlot s join fetch s.action left join fetch s.skill where s.player.id = :playerId")
    List<PlayerSkillSlot> findAllByPlayerId(@Param("playerId") long playerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlayerSkillSlot s where s.player.id = :playerId")
    void deleteAllByPlayerId(@Param("playerId") long playerId);
}
