-- 스킬 종류를 공격(ATTACK) · 방어(DEFENSE) · CC · 유틸(UTILITY)로 다시 나눈다.
-- 직접 키워드를 빼앗거나 잃게 하는 스킬은 공격, 밀어내기·보호는 방어, 은신·암흑·스턴·슬로우는 CC, 대시·회복·정화는 유틸.
-- 처음 값 그대로인 행만 바꾼다(운영에서 고친 종류는 건드리지 않는다).
update skills set category = 'UTILITY' where code = 'dash' and category = 'MOVE';
update skills set category = 'DEFENSE' where code = 'shockwave' and category = 'ATTACK';
update skills set category = 'CC' where code = 'stealth' and category = 'STEALTH';
update skills set category = 'CC' where code in ('darkness', 'slow', 'stun') and category = 'ATTACK';
update skills set category = 'UTILITY' where code in ('cleanse', 'heal') and category = 'DEFENSE';
