-- 공개 키워드 페이지(/k/{나라}/{키워드}, 4.28)의 방문 수. 같은 방문자는 하루 1번만 세고 크롤러는 센 값에 넣지 않는다.
ALTER TABLE keywords ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
