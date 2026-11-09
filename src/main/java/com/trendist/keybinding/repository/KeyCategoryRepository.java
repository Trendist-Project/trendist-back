package com.trendist.keybinding.repository;

import com.trendist.keybinding.entity.KeyCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyCategoryRepository extends JpaRepository<KeyCategory, Long> {
    Optional<KeyCategory> findByCode(String code);
}
