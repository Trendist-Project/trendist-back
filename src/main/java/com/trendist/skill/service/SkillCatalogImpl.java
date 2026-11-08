package com.trendist.skill.service;

import com.trendist.skill.dto.SkillDtos;
import com.trendist.skill.entity.Skill;
import com.trendist.skill.repository.SkillRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SkillCatalogImpl implements SkillCatalog {
    private static final long TTL_MS = 10_000;

    private final SkillRepository skills;
    private volatile List<Skill> cache = List.of();
    private volatile long loadedAt;

    private List<Skill> rows() {
        long now = System.currentTimeMillis();
        if (loadedAt == 0 || now - loadedAt >= TTL_MS) {
            synchronized (this) {
                if (loadedAt == 0 || now - loadedAt >= TTL_MS) {
                    cache = List.copyOf(skills.findAllByOrderBySortOrderAscIdAsc());
                    loadedAt = cache.isEmpty() ? 0 : now; // 시더가 채우기 전에 읽은 빈 목록은 기억하지 않는다
                }
            }
        }
        return cache;
    }

    @Override
    public List<SkillDtos.Item> list() {
        return rows().stream().filter(Skill::isEnabled)
                .map(s -> new SkillDtos.Item(s.getCode(), s.getIcon(), s.getCategory(), s.getDefaultKey(), s.getCooldownSeconds().doubleValue(), s.getRequiredLevel()))
                .toList();
    }

    @Override
    public double cooldownSeconds(String code, double fallback) {
        return rows().stream().filter(s -> s.getCode().equals(code)).findFirst().map(s -> s.getCooldownSeconds().doubleValue()).orElse(fallback);
    }

    @Override
    public java.util.Map<String, Integer> requiredLevels() {
        return rows().stream().filter(s -> s.getRequiredLevel() > 1).collect(Collectors.toMap(Skill::getCode, Skill::getRequiredLevel));
    }

    @Override
    public Set<String> disabledCodes() {
        return rows().stream().filter(s -> !s.isEnabled()).map(Skill::getCode).collect(Collectors.toSet());
    }

    @Override
    public synchronized void invalidate() { loadedAt = 0; }
}
