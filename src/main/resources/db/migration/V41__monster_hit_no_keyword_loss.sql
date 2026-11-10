-- 보스에 부딪혀도·충격고리에 맞아도 키워드를 잃지 않고 무적도 되지 않는다(4.35): 키워드 잃기·무적 설정이 필요 없어졌다.
-- 돌진·충격고리는 사람의 체력(HP)을 깎는다(dash_hp_percent·ring_max_hp_percent·ring_min_hp_percent는 시더가 채운다)
delete from monster_settings where name in ('hit_lose_keywords', 'hit_protect_seconds', 'ring_max_keywords', 'ring_min_keywords');
