-- 대시 버튼을 스킬 4개 뒤(오른쪽)로: 표시 순서(sort_order)를 마지막으로 옮긴다. 고친 적 없는(처음 값 10) 행만 바꾼다
update skills set sort_order = 60 where code = 'dash' and sort_order = 10;
