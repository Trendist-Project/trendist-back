-- 스킬 해금 레벨(4.33): 대시는 기본 스킬(1), 나머지는 레벨이 되어야 쓴다. 처음 값으로 방패 10, 갈고리 15, 충격파 20, 은신 25.
alter table skills add column required_level INT NOT NULL DEFAULT 1;
update skills set required_level = 10 where code = 'shield' and required_level = 1;
update skills set required_level = 15 where code = 'hook' and required_level = 1;
update skills set required_level = 20 where code = 'shockwave' and required_level = 1;
update skills set required_level = 25 where code = 'stealth' and required_level = 1;
