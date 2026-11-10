-- 보스(장로 거북이) 이동 속도를 일반 사용자와 똑같이 맞춘다: 속도 비율 1배, 분노 속도 배수 1배
update monster_settings set value_num = 1 where name in ('speed_ratio', 'rage_speed_mult');
