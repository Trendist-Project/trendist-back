-- 패배한 횟수(4.31, 칭호용). 사람이 키워드가 없는 채로 져서 죽은 누적 횟수이고 줄지 않는다
alter table players add column defeat_count BIGINT NOT NULL DEFAULT 0;
