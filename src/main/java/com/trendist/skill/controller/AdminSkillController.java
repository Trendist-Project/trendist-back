package com.trendist.skill.controller;

import com.trendist.auth.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.trendist.skill.entity.Skill;
import com.trendist.skill.repository.SkillRepository;
import com.trendist.skill.service.SkillCatalog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 스킬 설정(13.3, 4.33): 스킬마다 쿨타임·해금 레벨·사용 여부를 따로 고친다. 저장하면 바로 DB에 들어가고
 * 게임 방에는 1초 안에 반영된다(서버가 쿨타임·해금 레벨을 이 값으로 판정한다). 스킬을 늘리거나 키·아이콘을 바꾸는 것은 코드·DB 작업이다.
 */
@RestController
@RequestMapping("/api/admin/skills")
@RequiredArgsConstructor
public class AdminSkillController {
    private final SkillRepository skills;
    private final SkillCatalog catalog;
    private final com.trendist.skill.service.SkillConfigService config;

    public record Item(String code, String icon, String category, String defaultKey, double cooldownSeconds, int requiredLevel,
                       boolean enabled, double defaultCooldown, java.time.Instant updatedAt) {}

    /** 값은 null이면 바꾸지 않는다. cooldownSeconds: 0~3600(0이면 쿨타임 없음, 소수 둘째 자리까지), requiredLevel: 1~100 */
    public record UpdateRequest(Double cooldownSeconds, Integer requiredLevel, Boolean enabled) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<Item> list() {
        return skills.findAllByOrderBySortOrderAscIdAsc().stream().map(AdminSkillController::toItem).toList();
    }

    @PatchMapping("/{code}")
    public Item update(@AuthenticationPrincipal AuthUser user, @PathVariable String code, @RequestBody UpdateRequest req) {
        return toItem(config.update(code, req.cooldownSeconds(), req.requiredLevel(), req.enabled(), user.userId(), null));
    }

    private static Item toItem(Skill s) {
        return new Item(s.getCode(), s.getIcon(), s.getCategory(), s.getDefaultKey(), s.getCooldownSeconds().doubleValue(), s.getRequiredLevel(),
                s.isEnabled(), 30.0, s.getUpdatedAt());
    }
}
