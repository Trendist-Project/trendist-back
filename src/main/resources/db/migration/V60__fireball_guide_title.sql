-- 스킬 "불꽃"의 이름을 "화염구"로 바꿨다(콤보의 불꽃과 헷갈리지 않게). 가이드 글 제목도 같이 바꾼다. 본문은 시더가 예전 본문 그대로일 때만 새 본문으로 고친다.
update guides set title = '🔥 화염구', title_en = '🔥 Fireball'
 where title = '🔥 불꽃' and guide_category_id in (select id from guide_categories where code = 'skills');
