-- 마지막 위치를 더는 기억하지 않는다(4.32): 접속하면 늘 가운데 세이프존에서 시작하고, 서버를 다시 켜도 모두 가운데에서 시작한다.
-- 위치(player_positions)와 활동 영역(room_states)을 저장하던 표를 없앤다.
drop table if exists player_positions;
drop table if exists room_states;
