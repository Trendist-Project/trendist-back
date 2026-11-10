package com.trendist.monster.service;

import com.trendist.audit.service.ConfigChangeRecorder;
import com.trendist.common.exception.ApiException;
import com.trendist.monster.entity.MonsterSetting;
import com.trendist.monster.entity.MonsterType;
import com.trendist.monster.repository.MonsterSettingRepository;
import com.trendist.monster.repository.MonsterTypeRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 몬스터 설정값·정기 등장 여부를 바꾸는 한 곳(13.16). 관리자 화면과 변경 이력 되돌리기(13.17)가 함께 쓰고, 바꿀 때마다 이력을 남긴다. */
@Service
@RequiredArgsConstructor
public class MonsterConfigService {
    private final MonsterTypeRepository types;
    private final MonsterSettingRepository settings;
    private final MonsterCatalog catalog;
    private final ConfigChangeRecorder changes;

    private MonsterType type(String code) { return types.findByCode(code).orElseThrow(() -> ApiException.notFound("MONSTER_NOT_FOUND", "없는 몬스터예요")); }

    @Transactional
    public MonsterSetting setValue(String code, String name, Double value, long adminUserId, Long revertedFromId) {
        MonsterType t = type(code);
        MonsterSetting s = settings.findByMonsterTypeIdAndName(t.getId(), name).orElseThrow(() -> ApiException.notFound("MONSTER_SETTING_NOT_FOUND", "없는 설정이에요"));
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) throw ApiException.badRequest("MONSTER_SETTING_VALUE", "숫자를 입력해 주세요");
        BigDecimal v = BigDecimal.valueOf(value);
        if (v.compareTo(s.getMin()) < 0 || v.compareTo(s.getMax()) > 0)
            throw ApiException.badRequest("MONSTER_SETTING_RANGE", s.getMin().stripTrailingZeros().toPlainString() + " ~ " + s.getMax().stripTrailingZeros().toPlainString() + " 사이로 정해 주세요");
        String old = ConfigChangeRecorder.num(s.getValue());
        s.changeValue(v);
        settings.saveAndFlush(s);
        catalog.invalidate();
        changes.record(ConfigChangeRecorder.MONSTER, code + "/" + name, "value", old, ConfigChangeRecorder.num(s.getValue()), adminUserId, revertedFromId);
        return s;
    }

    @Transactional
    public MonsterType setEnabled(String code, boolean enabled, long adminUserId, Long revertedFromId) {
        MonsterType t = type(code);
        boolean old = t.isEnabled();
        t.changeEnabled(enabled);
        types.saveAndFlush(t);
        catalog.invalidate();
        changes.record(ConfigChangeRecorder.MONSTER, code, "enabled", String.valueOf(old), String.valueOf(enabled), adminUserId, revertedFromId);
        return t;
    }
}
