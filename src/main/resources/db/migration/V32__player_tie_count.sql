-- 유행도가 같은 상대와 부딪힌 횟수(4.3, 칭호용 "도토리 키재기"). 사람이 현재 유행도가 같은 원과 부딪힐 때마다 +1이고 줄지 않는다
alter table players add column tie_count BIGINT NOT NULL DEFAULT 0;
