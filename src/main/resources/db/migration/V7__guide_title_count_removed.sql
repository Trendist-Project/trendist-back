-- 가이드 글(칭호 설명)에서 칭호 개수 언급을 뺀다. 칭호가 늘 때마다 글을 고치지 않게 한다
update guides
   set body = regexp_replace(body, ' 등 [0-9]+가지가 있어요', ' 등 다양한 칭호가 있어요')
 where body like '%칭호를 자동으로 받아요%';

update guides
   set body_en = regexp_replace(body_en, 'There are [0-9]+ of them, covering', 'They cover')
 where body_en like '%You receive titles automatically%';
