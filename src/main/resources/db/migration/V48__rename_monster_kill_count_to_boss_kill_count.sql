-- 몬스터 처치 횟수 컬럼 이름을 보스 처치 횟수로(players.monster_kill_count → boss_kill_count). 칭호 조건의 metric 이름도 함께 바꾼다
alter table players rename column monster_kill_count to boss_kill_count;
update titles set condition_params = json_set(condition_params, '$.metric', 'boss_kill_count')
 where json_unquote(json_extract(condition_params, '$.metric')) = 'monster_kill_count';
