-- 헷갈리던 스킬 코드 이름을 바꾼다(4.33). 반발은 shockwave, 화염구는 flame이라 충격파(quake)·콤보 불꽃과 헷갈렸다.
--   shockwave → repel, flame → fireball (충격파 quake는 그대로)
-- 장착(player_skill_slots)은 skills.id를 가리키므로 따로 바꿀 것이 없다. 이름·설명 번역은 프론트엔드가 새 코드로 찾는다.
update skills set code = 'repel' where code = 'shockwave';
update skills set code = 'fireball' where code = 'flame';

-- 반발 설정 이름(값은 그대로 옮긴다). 새 이름 행이 이미 있으면(시더가 먼저 만든 경우) 옛 행은 지운다
update settings set name = 'repel_speed_multiplier' where name = 'shockwave_speed_multiplier' and not exists (select 1 from (select name from settings) s where s.name = 'repel_speed_multiplier');
update settings set name = 'repel_reach_px' where name = 'shockwave_reach_px' and not exists (select 1 from (select name from settings) s where s.name = 'repel_reach_px');
update settings set name = 'repel_knockback' where name = 'shockwave_knockback' and not exists (select 1 from (select name from settings) s where s.name = 'repel_knockback');
update settings set name = 'fireball_count' where name = 'flame_count' and not exists (select 1 from (select name from settings) s where s.name = 'fireball_count');
update settings set name = 'fireball_interval_seconds' where name = 'flame_interval_seconds' and not exists (select 1 from (select name from settings) s where s.name = 'fireball_interval_seconds');
delete from settings where name in ('shockwave_speed_multiplier', 'shockwave_reach_px', 'shockwave_knockback', 'flame_count', 'flame_interval_seconds');

-- 설정 변경 이력의 대상 이름도 맞춘다(되돌리기가 새 이름을 찾도록)
update config_changes set target = 'repel' where scope = 'SKILL' and target = 'shockwave';
update config_changes set target = 'fireball' where scope = 'SKILL' and target = 'flame';
update config_changes set target = 'repel_speed_multiplier' where scope = 'SETTING' and target = 'shockwave_speed_multiplier';
update config_changes set target = 'repel_reach_px' where scope = 'SETTING' and target = 'shockwave_reach_px';
update config_changes set target = 'repel_knockback' where scope = 'SETTING' and target = 'shockwave_knockback';

-- 설명도 새 이름에 맞춘다(관리자 화면). 시더는 이미 있는 설정의 설명을 바꾸지 않는다
update settings set description = '반발·충격파가 퍼지는 속도: 최대 속도의 몇 배인지(기본 4 = 대시 속도(기본 최대 속도의 2배)의 2배)' where name = 'repel_speed_multiplier';
update settings set description = '반발·충격파 범위: 내 원 가장자리에서 화면에 보이는 길이(px)로 얼마나 퍼지는지(시작은 내 원 크기, 중심은 쓴 자리). 원 크기에 따른 줌을 반영해 서버가 월드 길이로 바꾸므로 원이 작아도 커도 화면에서 같은 길이로 보인다. 갈고리 사거리와 같은 방식이다' where name = 'repel_reach_px';
update settings set description = '반발에 맞은 원이 밀려나는 세기(튕김 세기와 같은 단위. 1이면 같은 크기끼리 부딪힐 때만큼)' where name = 'repel_knockback';
