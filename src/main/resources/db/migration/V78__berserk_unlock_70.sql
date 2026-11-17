-- 광폭화를 가장 마지막에 해금되게: 61레벨에서 70레벨로(장판 64·터지는 총알 67보다 뒤, 4.33). 시더가 처음 넣은 값(61) 그대로인 행만 바꾼다(운영에서 고친 값은 건드리지 않는다)
update skills set required_level = 70 where code = 'berserk' and required_level = 61;
