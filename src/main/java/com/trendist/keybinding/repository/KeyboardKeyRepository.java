package com.trendist.keybinding.repository;

import com.trendist.keybinding.entity.KeyboardKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KeyboardKeyRepository extends JpaRepository<KeyboardKey, Long> {
    Optional<KeyboardKey> findByCode(String code);

    /** 쓸 수 있는 키(사용 여부가 켜진 것), 분류·짝까지 한 번에 */
    @Query("select k from KeyboardKey k join fetch k.category left join fetch k.aliasOf where k.enabled = true order by k.id")
    List<KeyboardKey> findAllEnabledWithCategory();
}
