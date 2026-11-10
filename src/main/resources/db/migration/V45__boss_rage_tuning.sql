-- 보스 조정: 패턴 뒤 경직 1초, 충격고리 최대 33%, 분노 불꽃은 끝까지 유지(0). rage_damage_mult(분노 피해 2배)는 시더가 채운다
update monster_settings set value_num = 1 where name = 'recover_seconds';
update monster_settings set value_num = 33 where name = 'ring_max_hp_percent';
update monster_settings set value_num = 0 where name = 'rage_flame_seconds';
