-- 무적 스킬 코드를 shield에서 invincibility로 바꾼다(4.33). shield는 보호막(barrier)·쉴드와 헷갈렸다.
-- 장착(player_skill_slots)은 skills.id를 가리키므로 따로 바꿀 것이 없다. 이름·설명 번역은 프론트엔드가 새 코드로 찾는다.
-- 무적 유지 시간 설정 이름(skill_shield_seconds)은 그대로 둔다.
update skills set code = 'invincibility' where code = 'shield' and not exists (select 1 from (select code from skills) s where s.code = 'invincibility');

-- 설정 변경 이력의 대상 이름도 맞춘다(되돌리기가 새 코드를 찾도록)
update config_changes set target = 'invincibility' where scope = 'SKILL' and target = 'shield';
