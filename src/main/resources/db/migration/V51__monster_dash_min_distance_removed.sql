-- 패턴을 쓰는 거리 조건(돌진 최소 거리)을 없앴으니 설정도 지운다
delete from monster_settings where name = 'dash_min_distance';
