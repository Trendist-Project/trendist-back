-- 레벨 EXP를 누적하지 않고 레벨마다 쓰고 남기는 방식으로 바꾼다(4.9).
--   levels.required_exp: 누적 EXP -> 그 레벨이 되는 데 필요한 EXP(바로 앞 레벨에서 올라올 때, 1레벨은 0)
--   players.exp:         누적 EXP -> 지금 레벨에서 모은 EXP(레벨이 오르며 필요한 만큼 빼고 남은 만큼을 이어 가진다)
-- 먼저 플레이어를 바꾼다(옛 레벨표의 누적 값이 필요하다). 레벨(level_id)은 그대로이고 같은 레벨에서 남은 EXP만 남긴다.
update players p join levels l on l.id = p.level_id set p.exp = greatest(p.exp - l.required_exp, 0);

-- 그다음 레벨표를 앞 레벨과의 차이로 바꾼다
update levels l
join (select id, required_exp - lag(required_exp, 1, 0) over (order by level) as d from levels) x on x.id = l.id
set l.required_exp = x.d;
