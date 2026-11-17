-- 스킬 히트 피해를 스킬마다 따로 두던 설정 12개를 없애고 하나(skill_hit_damage_percent, 몸싸움 피해의 %)로 합쳤다(4.33).
-- 시더가 이미 넣어 둔 옛 설정 행과 그 변경 이력 대상을 지운다(새 설정은 시더가 처음 켤 때 넣는다)
delete from settings where name in ('hook_damage_percent', 'slow_damage_percent', 'darkness_damage_percent', 'silence_damage_percent', 'stun_damage_percent', 'confuse_damage_percent',
    'quake_damage_percent', 'poison_damage_percent', 'fireball_damage_percent', 'absorb_damage_percent', 'zone_damage_percent', 'burst_damage_percent', 'burst_explode_damage_percent');

-- 흡수를 뺀 모든 스킬은 히트 하나에 키워드 1개를 잃게 한다(4.33): 충격파가 닿은 원이 잃는 키워드를 3개에서 1개로. 시더가 처음 넣은 값(3) 그대로인 행만 바꾼다
update settings set value = '1' where name = 'quake_drop_keywords' and value = '3';
