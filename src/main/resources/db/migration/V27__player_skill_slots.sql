-- 스킬 장착(4.33): 플레이어가 고른 장착 스킬 4칸을 쉼표로 이은 코드로 저장한다(빈 칸은 빈 문자열). null이면 아직 고른 적 없다(해금된 스킬이 해금 순서대로 채워진다).
alter table players add column skill_slots varchar(120) null;
-- 새 스킬의 해금 레벨(이미 만들어진 행만 고친다. 새로 만들 때는 시더가 같은 값으로 넣는다)
update skills set required_level = 30 where code = 'darkness';
update skills set required_level = 35 where code = 'cleanse';
update skills set required_level = 40 where code = 'poison';
