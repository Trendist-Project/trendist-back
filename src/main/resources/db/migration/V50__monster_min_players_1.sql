-- 보스는 사람 1명만 접속해 있어도 정기 등장한다(min_players 3 → 1). 0명이면 건너뛴다
update monster_settings set value_num = 1 where name = 'min_players';
