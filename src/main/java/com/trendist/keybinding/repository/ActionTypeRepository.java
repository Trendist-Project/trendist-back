package com.trendist.keybinding.repository;

import com.trendist.keybinding.entity.ActionType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionTypeRepository extends JpaRepository<ActionType, Long> {
    Optional<ActionType> findByCode(String code);
}
