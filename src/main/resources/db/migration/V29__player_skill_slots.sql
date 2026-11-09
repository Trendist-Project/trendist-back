-- 스킬 장착(4.33): 플레이어마다 칸(actions 중 종류가 SKILL_SLOT인 동작)에 끼운 스킬을 행으로 저장한다. 칸 번호는 actions.param이고, 칸 수는 그 행의 수라서
-- 칸을 늘리려면 actions에 행만 더하면 된다. skill_id가 NULL이면 그 칸을 일부러 비운 것이고, 플레이어의 행이 하나도 없으면 아직 고른 적 없다(해금된 스킬이 순서대로 채워진다).
-- V27의 players.skill_slots(쉼표로 이은 문자열)는 이 표로 대체한다. 아직 배포된 적 없는 컬럼이라 값을 옮기지 않고 지운다.
create table player_skill_slots (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    player_id bigint not null,
    action_id bigint not null,
    skill_id bigint null,
    primary key (id),
    unique key uk_player_skill_slots_action (player_id, action_id),
    unique key uk_player_skill_slots_skill (player_id, skill_id),
    constraint fk_player_skill_slots_player foreign key (player_id) references players (id) on delete cascade,
    constraint fk_player_skill_slots_action foreign key (action_id) references actions (id),
    constraint fk_player_skill_slots_skill foreign key (skill_id) references skills (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

alter table players drop column skill_slots;
