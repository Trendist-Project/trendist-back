-- 대시 기본 쿨타임을 30초에서 10초로. 시더가 처음 넣은 값(30) 그대로인 행만 바꾼다(운영에서 고친 값은 건드리지 않는다)
update skills set cooldown_seconds = 10 where code = 'dash' and cooldown_seconds = 30;
