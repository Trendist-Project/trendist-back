-- 달토끼 끈끈 떡 장판 반지름 220 → 420(공격 대상 자리에 던지고 범위를 키움, 4.35). 운영에서 고치지 않은 값(220)만 바꾼다
update monster_settings set value_num = 420
 where name = 'mochi_radius_px' and value_num = 220
   and monster_type_id = (select id from monster_types where code = 'moon_rabbit');
