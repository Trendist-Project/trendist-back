-- 플레이어 신고는 같은 대상을 하루에 한 번(신고한 사람의 나라 시간대 기준)만 할 수 있다. 그 밖의 유형(댓글·채팅)은 예전처럼 대상마다 한 번이라
-- report_date에 고정 값(2000-01-01)을 넣어 같은 유니크 키로 막는다. 기존 행도 이 고정 값을 갖는다.
-- 옛 유니크 키는 report_type_id 외래 키가 쓰고 있어서, 새 키를 먼저 만들고 옛 키를 지운다.
alter table reports add column report_date DATE NOT NULL DEFAULT '2000-01-01';
alter table reports add unique key uk_reports_daily (report_type_id, target_id, player_id, report_date);
alter table reports drop index uk_reports_once;
