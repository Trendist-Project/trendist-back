-- total_plus_count → add_count, total_plus_inc → add_inc ("누적 추가"를 "추가수"로 줄여 부르는 이름 정리)
alter table players rename column total_plus_count to add_count;
alter table keywords rename column total_plus_count to add_count;
alter table keyword_log_24h rename column total_plus_count to add_count;
alter table keyword_log_24h rename column total_plus_inc to add_inc;
alter table keyword_log_dailies rename column total_plus_count to add_count;
alter table keyword_log_dailies rename column total_plus_inc to add_inc;

-- 칭호 조건의 metric 이름
update titles set condition_params = json_set(condition_params, '$.metric', 'add_count')
 where json_unquote(json_extract(condition_params, '$.metric')) = 'total_plus_count';
