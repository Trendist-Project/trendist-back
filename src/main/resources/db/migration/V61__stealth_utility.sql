-- 은신(stealth)을 CC가 아니라 유틸(UTILITY)로 분류한다. 처음 값(CC) 그대로인 행만 바꾼다(운영에서 고친 종류는 건드리지 않는다).
update skills set category = 'UTILITY' where code = 'stealth' and category = 'CC';
