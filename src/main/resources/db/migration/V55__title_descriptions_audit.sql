-- 칭호 설명 전수검사 수정: 빼앗은 횟수는 봇 포함·구슬은 ! 구슬 포함·패배는 사망(HP 포함)·활동일은 모은 날 수(한국어·영어)
update titles set description = '키워드를 처음 빼앗았어요', description_en = 'Stole a keyword for the first time' where name = '첫 승리';
update titles set description = '키워드를 50번 빼앗았어요', description_en = 'Stole keywords 50 times' where name = '사냥꾼의 본능';
update titles set description = '키워드를 300번 빼앗았어요', description_en = 'Stole keywords 300 times' where name = '아레나의 포식자';
update titles set description = '구슬을 10개 먹었어요', description_en = 'Ate 10 orbs' where name = '행운 탐색자';
update titles set description = '구슬을 100개 먹었어요', description_en = 'Ate 100 orbs' where name = '구슬 수집가';
update titles set description = '구슬을 1,000개 먹었어요', description_en = 'Ate 1,000 orbs' where name = '구슬 마니아';
update titles set description = '처음으로 사망했어요', description_en = 'Died for the first time' where name = '첫 패배';
update titles set description = '10번 사망하고도 다시 일어났어요', description_en = 'Got back up after dying 10 times' where name = '칠전팔기';
update titles set description = '50번 사망했지만 계속 도전해요', description_en = 'Died 50 times but keep challenging' where name = '백전노장';
update titles set description = '1,000번 사망해도 멈추지 않았어요', description_en = 'Kept going after dying 1,000 times' where name = '끝없는 도전자';
update titles set description = '키워드를 모은 날이 3일이 됐어요', description_en = 'Collected keywords on 3 different days' where name = '새내기';
update titles set description = '키워드를 모은 날이 7일이 됐어요', description_en = 'Collected keywords on 7 different days' where name = '단골';
update titles set description = '키워드를 모은 날이 30일이 됐어요', description_en = 'Collected keywords on 30 different days' where name = '터줏대감';
update titles set description = '키워드를 모은 날이 100일이 됐어요', description_en = 'Collected keywords on 100 different days' where name = '산증인';
