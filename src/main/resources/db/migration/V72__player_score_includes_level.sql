-- 처음 접속한 플레이어의 유행도가 0으로 남아 있던 것을 바로잡는다(4.8). 유행도 = EXP 몫 + 레벨 + 키워드 점수 합이라 레벨이 1 이상이면 0일 수 없다.
-- 새 플레이어는 첫 계산(recompute) 전까지 DB 값이 0이었다. 0인 행만 레벨 값으로 맞추고, 이후 계산이 알아서 이어 간다.
update players set current_score = (select l.level from levels l where l.id = players.level_id) where current_score = 0;
