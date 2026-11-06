-- 봇이 실제 키워드를 가지게 되어(4.21) 키워드 점수 범위 설정이 필요 없어졌다
delete from settings where name in ('bot_step_min', 'bot_step_max');
