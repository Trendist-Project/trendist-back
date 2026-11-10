-- 보스 보상: 기본 EXP 없음·🔥 활성 키워드 10개, 기여도 비례는 레벨 진행(%)으로(reward_level_percent는 시더가 채운다)
delete from monster_settings where name = 'reward_exp_pool';
update monster_settings set value_num = 0 where name = 'reward_exp';
update monster_settings set value_num = 10 where name = 'reward_keywords';
update monster_settings set value_num = 0 where name = 'reward_keyword_pool';
update monster_settings set value_num = 10 where name = 'reward_keywords_max';
