-- 장로 거북이의 정기 등장을 끈다(처음에는 꺼 두고 운영자가 켠다, 4.35). 이미 만들어진 DB에만 해당한다(새 DB는 시더가 꺼진 채로 만든다)
update monster_types set enabled = 0 where code = 'elder_turtle';
