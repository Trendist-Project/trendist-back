-- 스킬 키와 버튼 순서를 해금 레벨 순서로 맞춘다: 방패(10) 1번, 갈고리(15) 2번, 충격파(20) 3번, 은신(25) 4번, 대시는 맨 뒤(Space).
-- 처음 값 그대로인 행만 바꾼다(운영에서 고친 키·순서는 건드리지 않는다).
update skills set default_key = 'Digit1' where code = 'shield' and default_key = 'Digit3';
update skills set default_key = 'Digit3' where code = 'shockwave' and default_key = 'Digit1';
update skills set sort_order = 10 where code = 'shield' and sort_order = 40;
update skills set sort_order = 20 where code = 'hook' and sort_order = 30;
update skills set sort_order = 30 where code = 'shockwave' and sort_order = 20;
update skills set sort_order = 40 where code = 'stealth' and sort_order = 50;
