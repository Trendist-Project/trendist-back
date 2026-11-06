-- 순위 칭호 최소 접속 인원 기본값 11 -> 10. 관리자가 따로 바꾼 값은 건드리지 않는다
update settings set value = '10' where name = 'rank_title_min_players' and value = '11';

-- 순위 칭호 설명에 적힌 인원도 같이 고친다(한국어·영어)
update titles set description = replace(description, '접속자 11명 이상일 때', '접속자 10명 이상일 때') where description like '%접속자 11명 이상일 때%';
update titles set description_en = replace(description_en, 'with 11 or more players online', 'with 10 or more players online') where description_en like '%with 11 or more players online%';
