-- 인기도 투표 값 컬럼을 엔티티(int)와 같은 INT로 맞춘다(dev는 ddl validate라 TINYINT이면 서버가 뜨지 않는다).
alter table player_popularity_votes modify column value INT NOT NULL;
