-- 스킬 "갈고리"의 화면 이름을 "쇠창살"로 바꿨다. 가이드 글 제목도 같이 바꾼다(영어 제목은 그대로, 본문은 시더가 예전 본문 그대로일 때만 새 본문으로 고친다).
update guides set title = '🪝 쇠창살'
 where title = '🪝 갈고리' and guide_category_id in (select id from guide_categories where code = 'skills');
