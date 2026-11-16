-- 스킬 "방패"의 이름을 "무적"으로 바꿨다. 가이드 글 제목도 같이 바꾼다(본문은 시더가 예전 본문 그대로일 때만 새 본문으로 고친다).
update guides set title = '🛡 무적', title_en = '🛡 Invincible'
 where title = '🛡 방패' and guide_category_id in (select id from guide_categories where code = 'skills');
