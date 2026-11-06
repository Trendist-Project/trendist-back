-- 죽인 횟수(4.31, 칭호용). 키워드가 없는 상대(사람·봇)를 쓰러뜨린 누적 횟수이고 줄지 않는다
alter table players add column kill_count BIGINT NOT NULL DEFAULT 0;
