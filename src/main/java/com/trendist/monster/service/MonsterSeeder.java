package com.trendist.monster.service;

import com.trendist.monster.entity.MonsterSetting;
import com.trendist.monster.entity.MonsterType;
import com.trendist.monster.repository.MonsterSettingRepository;
import com.trendist.monster.repository.MonsterTypeRepository;
import com.trendist.monster.support.MonsterDefs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 몬스터 종류와 설정값을 서버가 켜질 때마다 채운다(DataSeeder가 부른다, 4.35). 없는 행만 만들고 이미 있는 값은 건드리지 않는다(운영에서 고친 값).
 * 이름·그림·설명·범위·단위·그룹·순서는 코드가 정하므로 맞춘다.
 */
@Component
@RequiredArgsConstructor
public class MonsterSeeder {
    private final MonsterTypeRepository types;
    private final MonsterSettingRepository settings;

    public int seed() {
        int added = 0;
        long typeOrder = 1;
        for (MonsterDefs.Type d : MonsterDefs.types()) {
            MonsterType t = types.findByCode(d.code()).orElse(null);
            if (t == null) { t = new MonsterType(d.code(), d.name(), d.nameEn(), d.kind(), d.behavior(), d.image(), typeOrder);
                t.changeEnabled(d.enabledByDefault()); // 새로 만들 때만 정한다(이미 있는 행의 켜짐·꺼짐은 운영에서 고친 값이라 건드리지 않는다)
                t = types.save(t); added++; }
            else { t.sync(d.name(), d.nameEn(), d.kind(), d.behavior(), d.image(), typeOrder); types.save(t); }
            typeOrder++;
            long order = 1;
            for (MonsterDefs.Def def : d.defs()) {
                MonsterSetting s = settings.findByMonsterTypeIdAndName(t.getId(), def.name()).orElse(null);
                if (s == null) { settings.save(new MonsterSetting(t.getId(), def.name(), def.value(), def.min(), def.max(), def.description(), def.unit(), def.group(), order)); added++; }
                else { s.sync(def.min(), def.max(), def.description(), def.unit(), def.group(), order); settings.save(s); }
                order++;
            }
        }
        return added;
    }
}
