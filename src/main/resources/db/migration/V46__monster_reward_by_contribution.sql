-- 보스 처치 보상을 기여도 비례로(구슬보다 확실히 이득): 기본 EXP 30·키워드 2개 + 기여도 풀, 1~3위 추가 EXP 30·키워드 2개
-- (reward_exp_pool·reward_keyword_pool·reward_keywords_max는 시더가 채운다)
update monster_settings set value_num = 30 where name in ('reward_exp', 'reward_top_exp');
update monster_settings set value_num = 2 where name in ('reward_keywords', 'reward_top_keywords');
update monster_settings set value_num = 5 where name = 'fail_exp';
