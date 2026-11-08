-- ? 구슬에서 🔥활성 키워드를 얻을 확률(orb_active_percent) 기본값을 70 → 60으로 바꿨다(4.20). 처음 값(70) 그대로인 행만 바꾼다(운영에서 고친 값은 건드리지 않는다).
update settings set value = '60' where name = 'orb_active_percent' and value = '70';
