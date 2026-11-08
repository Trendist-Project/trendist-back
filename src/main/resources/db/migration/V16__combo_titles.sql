-- 콤보 칭호(4.10, 4.25)용 수치: 가장 높았던 연속 빼앗기(최고 콤보)와 상대의 불꽃을 꺼뜨린 횟수. 둘 다 줄지 않는다
alter table players add column max_combo BIGINT NOT NULL DEFAULT 0;
alter table players add column flame_out_count BIGINT NOT NULL DEFAULT 0;
