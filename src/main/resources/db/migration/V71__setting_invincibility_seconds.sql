-- 무적 유지 시간 설정 이름을 skill_shield_seconds에서 invincibility_seconds로 바꾼다(4.33). 스킬 코드를 바꾼 V70과 짝이다(V70의 주석에 "그대로 둔다"고 적었지만 이 마이그레이션이 바꾼다).
-- 값은 그대로 옮기고, 새 이름 행이 이미 있으면(시더가 먼저 만든 경우) 옛 행은 지운다
update settings set name = 'invincibility_seconds' where name = 'skill_shield_seconds' and not exists (select 1 from (select name from settings) s where s.name = 'invincibility_seconds');
delete from settings where name = 'skill_shield_seconds';

-- 설정 변경 이력의 대상 이름도 맞춘다(되돌리기가 새 이름을 찾도록)
update config_changes set target = 'invincibility_seconds' where scope = 'SETTING' and target = 'skill_shield_seconds';

-- 설명도 새 이름에 맞춘다(관리자 화면). 시더는 이미 있는 설정의 설명을 바꾸지 않는다
update settings set description = '무적 스킬이 유지되는 시간(키워드를 잃은 뒤 무적과 같이 다른 원과 충돌하지 않는다)' where name = 'invincibility_seconds';
