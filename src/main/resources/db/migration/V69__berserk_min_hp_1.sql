-- 광폭화가 스스로 꺼지는 최소 체력을 5%에서 1%로 바꿨다. 시더가 처음 넣은 값(5) 그대로인 행만 바꾼다(운영에서 고친 값은 건드리지 않는다).
update settings set value = '1' where name = 'berserk_min_hp_percent' and value = '5';
