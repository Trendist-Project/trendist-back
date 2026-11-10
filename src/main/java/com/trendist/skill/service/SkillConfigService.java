package com.trendist.skill.service;

import com.trendist.audit.service.ConfigChangeRecorder;
import com.trendist.common.exception.ApiException;
import com.trendist.skill.entity.Skill;
import com.trendist.skill.repository.SkillRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 스킬의 쿨타임·해금 레벨·사용 여부를 바꾸는 한 곳(13.3). 관리자 화면과 변경 이력 되돌리기(13.17)가 함께 쓰고, 바뀐 항목마다 이력을 남긴다. 값이 null이면 바꾸지 않는다. */
@Service
@RequiredArgsConstructor
public class SkillConfigService {
    public static final double MAX_COOLDOWN = 3600;
    public static final int MAX_LEVEL = 100;

    private final SkillRepository skills;
    private final SkillCatalog catalog;
    private final ConfigChangeRecorder changes;

    @Transactional
    public Skill update(String code, Double cooldownSeconds, Integer requiredLevel, Boolean enabled, long adminUserId, Long revertedFromId) {
        Skill s = skills.findByCode(code).orElseThrow(() -> ApiException.notFound("SKILL_NOT_FOUND", "없는 스킬이에요"));
        if (cooldownSeconds != null) {
            double c = cooldownSeconds;
            if (Double.isNaN(c) || c < 0 || c > MAX_COOLDOWN) throw ApiException.badRequest("SKILL_COOLDOWN", "쿨타임은 0~" + (int) MAX_COOLDOWN + "초로 정해 주세요");
            String old = ConfigChangeRecorder.num(s.getCooldownSeconds());
            s.changeCooldown(BigDecimal.valueOf(c).setScale(2, RoundingMode.HALF_UP));
            changes.record(ConfigChangeRecorder.SKILL, code, "cooldown", old, ConfigChangeRecorder.num(s.getCooldownSeconds()), adminUserId, revertedFromId);
        }
        if (requiredLevel != null) {
            int l = requiredLevel;
            if (l < 1 || l > MAX_LEVEL) throw ApiException.badRequest("SKILL_LEVEL", "해금 레벨은 1~" + MAX_LEVEL + "로 정해 주세요");
            String old = String.valueOf(s.getRequiredLevel());
            s.changeRequiredLevel(l);
            changes.record(ConfigChangeRecorder.SKILL, code, "level", old, String.valueOf(l), adminUserId, revertedFromId);
        }
        if (enabled != null) {
            String old = String.valueOf(s.isEnabled());
            s.changeEnabled(enabled);
            changes.record(ConfigChangeRecorder.SKILL, code, "enabled", old, String.valueOf(enabled), adminUserId, revertedFromId);
        }
        skills.saveAndFlush(s);
        catalog.invalidate(); // 방이 1초마다 읽는 값을 바로 새로 읽게
        return s;
    }
}
