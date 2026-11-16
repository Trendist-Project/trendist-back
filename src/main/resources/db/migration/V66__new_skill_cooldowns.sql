-- 새 스킬 7개의 기본 쿨타임: 광폭화 1초(켜고 끄는 스킬), 나머지 30초. 시더가 처음 넣은 값 그대로인 행만 바꾼다(운영에서 고친 값은 건드리지 않는다).
update skills set cooldown_seconds = 30 where code in ('fortify', 'barrier', 'amplify') and cooldown_seconds = 45;
update skills set cooldown_seconds = 1 where code = 'berserk' and cooldown_seconds = 10;
