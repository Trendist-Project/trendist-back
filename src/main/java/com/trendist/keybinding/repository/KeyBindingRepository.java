package com.trendist.keybinding.repository;

import com.trendist.keybinding.entity.KeyBinding;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeyBindingRepository extends JpaRepository<KeyBinding, Long> {
    @Query("select b from KeyBinding b join fetch b.action left join fetch b.key k left join fetch k.aliasOf where b.player.id = :playerId")
    List<KeyBinding> findAllByPlayerId(@Param("playerId") long playerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from KeyBinding b where b.player.id = :playerId")
    void deleteAllByPlayerId(@Param("playerId") long playerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from KeyBinding b where b.player.id = :playerId and b.action.id in :actionIds")
    void deleteByPlayerIdAndActionIds(@Param("playerId") long playerId, @Param("actionIds") Collection<Long> actionIds);
}
