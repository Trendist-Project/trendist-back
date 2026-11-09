-- 레벨 필요 EXP의 값 자체를 "그 레벨이 되는 데 필요한 EXP"로 둔다(4.9).
-- V30은 레벨표를 앞 레벨과의 차이로 바꿨지만, 의도는 옛 누적 곡선의 값 그대로를 레벨마다 필요한 양으로 쓰는 것이다
-- (예: 100레벨은 99레벨에서 EXP 0부터 10,000을 모아야 한다). 그래서 V30의 차이를 다시 합쳐 옛 곡선 값으로 되돌린다.
-- 플레이어의 EXP(지금 레벨에서 모은 양)는 그대로 둔다.
update levels l
join (select id, sum(required_exp) over (order by level) as c from levels) x on x.id = l.id
set l.required_exp = x.c;
