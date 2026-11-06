-- ? 구슬에서 🔥활성 키워드를 얻을 확률(orb_active_percent) 기본값을 50 → 70으로 바꿨다(4.20, 13.3).
-- 설정 시더는 없는 값만 채우므로 이미 만든 DB는 옛 기본값(50)이 남아 있다. 관리자가 따로 바꾼 값은 건드리지 않게 50일 때만 70으로 고친다.
update settings set value = '70' where name = 'orb_active_percent' and value = '50';
