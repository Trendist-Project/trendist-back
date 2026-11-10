-- 보스 보상 정리: 고정 EXP·피해 1~3위 추가 보상·시간 초과 EXP를 없앤다(남는 것: 🔥 키워드와 기여도 레벨 진행 %)
delete from monster_settings where name in ('reward_exp', 'reward_top_exp', 'reward_top_keywords', 'fail_exp');
