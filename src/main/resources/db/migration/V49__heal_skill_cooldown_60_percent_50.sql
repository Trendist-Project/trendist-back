-- 회복 스킬: 쿨타임 기본값 60초, 회복량 50%(설정 skill_heal_percent). 시더는 이미 있는 행·설정 값을 바꾸지 않는다
update skills set cooldown_seconds = 60 where code = 'heal';
update settings set value = '50' where name = 'skill_heal_percent';
