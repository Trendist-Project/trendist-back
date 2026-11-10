-- 패배 횟수 컬럼을 사망 횟수로 이름을 바꾼다(players.defeat_count → death_count). 칭호 조건의 metric 이름도 함께 바꾼다
alter table players rename column defeat_count to death_count;
update titles set condition_params = json_set(condition_params, '$.metric', 'death_count')
 where json_unquote(json_extract(condition_params, '$.metric')) = 'defeat_count';
