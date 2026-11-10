-- 체력(4.36) 조정: 회복 상한 설정을 없애고, 체력바 공개를 기본 켜짐·세이프존 회복을 초당 2%로 한다(이미 만들어진 행의 값도 맞춘다)
delete from settings where name = 'hp_heal_cap_percent';
update settings set value = '1' where name = 'hp_public';
update settings set value = '2' where name = 'hp_safe_regen_percent';
-- 레벨 값을 유행도에 1:1로 더한다(4.8): 이미 저장된 현재 유행도에 레벨만큼 더한다(이후로는 다시 계산할 때마다 키워드 점수 합 + 레벨로 정해진다)
update players p join levels l on l.id = p.level_id set p.current_score = p.current_score + l.level;
