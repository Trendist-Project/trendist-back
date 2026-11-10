-- 회복 스킬 아이콘을 + 모양으로 (시더는 이미 있는 행의 아이콘을 바꾸지 않는다)
update skills set icon = '➕' where code = 'heal';
