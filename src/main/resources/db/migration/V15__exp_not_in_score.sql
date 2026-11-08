-- 레벨·EXP는 원 크기와 싸움 판정에 관여하지 않는다: 현재 유행도에서 EXP 점수를 뺀다(exp_score_cap 기본 0).
-- 저장된 현재 유행도에 들어 있던 EXP 점수(옛 상한 100)를 먼저 빼고, 그다음 설정 값을 0으로 바꾼다.
update players set current_score = greatest(0, current_score - least(exp, 100))
 where (select value from settings where name = 'exp_score_cap') = '100';
update settings set value = '0' where name = 'exp_score_cap' and value = '100';
