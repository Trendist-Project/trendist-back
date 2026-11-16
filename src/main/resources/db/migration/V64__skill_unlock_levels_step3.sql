-- 스킬 해금 레벨을 4레벨부터 3레벨 간격으로 다시 정한다(4.33). 처음 값 그대로인 행만 바꾼다(운영에서 관리자 화면으로 고친 값은 건드리지 않는다).
-- 예전(5레벨 간격) → 지금: 무적(방패) 5→4, 갈고리 10→7, 슬로우 15→10, 은신 20→13, 반발 25→16, 정화 30→22, 암흑 35→25, 회복 40→31, 독 45→34, 스턴 50→40, 충격파 55→46, 화염구 60→52, 흡수 65→58.
-- 서버에 아직 없는 스킬(속박·공격 증폭·피해 경감·침묵·광폭화·혼란·보호막)은 서버에 넣을 때 정해 둔 레벨(19~61, 광폭화가 61로 가장 늦다)로 넣는다.
update skills set required_level = 4 where code = 'shield' and required_level = 5;
update skills set required_level = 7 where code = 'hook' and required_level = 10;
update skills set required_level = 10 where code = 'slow' and required_level = 15;
update skills set required_level = 13 where code = 'stealth' and required_level = 20;
update skills set required_level = 16 where code = 'repel' and required_level = 25;
update skills set required_level = 22 where code = 'cleanse' and required_level = 30;
update skills set required_level = 25 where code = 'darkness' and required_level = 35;
update skills set required_level = 31 where code = 'heal' and required_level = 40;
update skills set required_level = 34 where code = 'poison' and required_level = 45;
update skills set required_level = 40 where code = 'stun' and required_level = 50;
update skills set required_level = 46 where code = 'quake' and required_level = 55;
update skills set required_level = 52 where code = 'fireball' and required_level = 60;
update skills set required_level = 58 where code = 'absorb' and required_level = 65;
