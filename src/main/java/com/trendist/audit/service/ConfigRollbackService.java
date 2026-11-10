package com.trendist.audit.service;

import com.trendist.audit.entity.ConfigChange;
import com.trendist.audit.repository.ConfigChangeRepository;
import com.trendist.common.exception.ApiException;
import com.trendist.monster.entity.MonsterSetting;
import com.trendist.monster.entity.MonsterType;
import com.trendist.monster.repository.MonsterSettingRepository;
import com.trendist.monster.repository.MonsterTypeRepository;
import com.trendist.monster.service.MonsterConfigService;
import com.trendist.setting.entity.Setting;
import com.trendist.setting.repository.SettingRepository;
import com.trendist.setting.service.SettingService;
import com.trendist.skill.entity.Skill;
import com.trendist.skill.repository.SkillRepository;
import com.trendist.skill.service.SkillConfigService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설정 변경 이력 보기·메모·되돌리기(13.17). 되돌리기는 그 줄의 "이전 값"을 지금 값으로 다시 넣는 새 변경이고(범위 검사·방 반영은 일반 변경과 같다),
 * 새 이력 줄에 되돌린 이력 번호(revertedFromId)가 남는다. 이후에 값이 또 바뀌었어도 이전 값으로 덮는다(화면이 먼저 확인을 받는다).
 */
@Service
@RequiredArgsConstructor
public class ConfigRollbackService {
    private final ConfigChangeRepository repository;
    private final SettingService settingService;
    private final SettingRepository settingRepository;
    private final MonsterConfigService monsterConfig;
    private final MonsterTypeRepository monsterTypes;
    private final MonsterSettingRepository monsterSettings;
    private final SkillConfigService skillConfig;
    private final SkillRepository skills;

    public record Item(Long id, String scope, String target, String field, String label, String oldValue, String newValue, String currentValue,
                       Long adminUserId, String adminName, String memo, Long revertedFromId, Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<Item> list(String scope, String q, long beforeId, int limit) {
        String sc = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT), query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ConfigChange> rows = repository.search(sc, query, beforeId, PageRequest.of(0, Math.max(1, Math.min(100, limit))));
        // 지금 값과 이름(설명)은 한 번에 읽어 둔다
        Map<String, Setting> settingByName = new HashMap<>();
        settingRepository.findAll().forEach(s -> settingByName.put(s.getName(), s));
        Map<Long, MonsterType> typeById = new HashMap<>(); Map<String, MonsterType> typeByCode = new HashMap<>();
        monsterTypes.findAll().forEach(t -> { typeById.put(t.getId(), t); typeByCode.put(t.getCode(), t); });
        Map<String, MonsterSetting> monsterByKey = new HashMap<>();
        monsterSettings.findAll().forEach(s -> { MonsterType t = typeById.get(s.getMonsterTypeId()); if (t != null) monsterByKey.put(t.getCode() + "/" + s.getName(), s); });
        Map<String, Skill> skillByCode = new HashMap<>();
        skills.findAll().forEach(s -> skillByCode.put(s.getCode(), s));
        return rows.stream().map(c -> {
            String label = null, current = null;
            switch (c.getScope()) {
                case ConfigChangeRecorder.SETTING -> {
                    Setting s = settingByName.get(c.getTarget());
                    if (s != null) { label = s.getDescription(); current = ConfigChangeRecorder.num(new BigDecimal(s.getValue().trim())); }
                }
                case ConfigChangeRecorder.MONSTER -> {
                    if ("enabled".equals(c.getField())) {
                        MonsterType t = typeByCode.get(c.getTarget());
                        if (t != null) { label = t.getName() + " 정기 등장"; current = String.valueOf(t.isEnabled()); }
                    } else {
                        MonsterSetting s = monsterByKey.get(c.getTarget());
                        if (s != null) { label = typeById.get(s.getMonsterTypeId()).getName() + " · " + s.getDescription(); current = ConfigChangeRecorder.num(s.getValue()); }
                    }
                }
                case ConfigChangeRecorder.SKILL -> {
                    Skill s = skillByCode.get(c.getTarget());
                    if (s != null) {
                        label = "스킬 " + s.getCode();
                        current = switch (c.getField()) {
                            case "cooldown" -> ConfigChangeRecorder.num(s.getCooldownSeconds());
                            case "level" -> String.valueOf(s.getRequiredLevel());
                            default -> String.valueOf(s.isEnabled());
                        };
                    }
                }
                default -> { }
            }
            return new Item(c.getId(), c.getScope(), c.getTarget(), c.getField(), label, c.getOldValue(), c.getNewValue(), current,
                    c.getAdminUserId(), c.getAdminName(), c.getMemo(), c.getRevertedFromId(), c.getCreatedAt());
        }).toList();
    }

    @Transactional
    public void memo(long id, String memo) {
        ConfigChange c = repository.findById(id).orElseThrow(() -> ApiException.notFound("CONFIG_CHANGE_NOT_FOUND", "없는 이력이에요"));
        String m = memo == null ? null : memo.trim();
        if (m != null && m.length() > 300) throw ApiException.badRequest("CONFIG_MEMO_LENGTH", "메모는 300자까지 쓸 수 있어요");
        c.changeMemo(m == null || m.isEmpty() ? null : m);
    }

    /** 그 줄의 이전 값으로 되돌린다. 이전 값이 없거나(처음 만든 값) 대상이 없어졌으면 400/404 */
    @Transactional
    public void rollback(long id, long adminUserId) {
        ConfigChange c = repository.findById(id).orElseThrow(() -> ApiException.notFound("CONFIG_CHANGE_NOT_FOUND", "없는 이력이에요"));
        String old = c.getOldValue();
        if (old == null) throw ApiException.badRequest("CONFIG_NO_OLD", "되돌릴 이전 값이 없어요");
        switch (c.getScope()) {
            case ConfigChangeRecorder.SETTING -> settingService.update(adminUserId, c.getTarget(), old, c.getId());
            case ConfigChangeRecorder.MONSTER -> {
                if ("enabled".equals(c.getField())) monsterConfig.setEnabled(c.getTarget(), Boolean.parseBoolean(old), adminUserId, c.getId());
                else {
                    int slash = c.getTarget().indexOf('/');
                    if (slash < 0) throw ApiException.badRequest("CONFIG_TARGET", "되돌릴 수 없는 대상이에요");
                    monsterConfig.setValue(c.getTarget().substring(0, slash), c.getTarget().substring(slash + 1), Double.valueOf(old), adminUserId, c.getId());
                }
            }
            case ConfigChangeRecorder.SKILL -> {
                switch (c.getField()) {
                    case "cooldown" -> skillConfig.update(c.getTarget(), Double.valueOf(old), null, null, adminUserId, c.getId());
                    case "level" -> skillConfig.update(c.getTarget(), null, Integer.valueOf(old), null, adminUserId, c.getId());
                    case "enabled" -> skillConfig.update(c.getTarget(), null, null, Boolean.parseBoolean(old), adminUserId, c.getId());
                    default -> throw ApiException.badRequest("CONFIG_TARGET", "되돌릴 수 없는 항목이에요");
                }
            }
            default -> throw ApiException.badRequest("CONFIG_TARGET", "되돌릴 수 없는 대상이에요");
        }
    }
}
