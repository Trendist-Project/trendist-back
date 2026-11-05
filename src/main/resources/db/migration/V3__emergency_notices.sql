-- 긴급 안내(4.29): 관리자가 올린 한 줄이 정해진 시간 동안 모든 사용자의 게임 화면 맨 위에 흘러간다.
create table emergency_notices (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    user_id bigint not null,
    message varchar(200) not null,
    message_en varchar(200),
    ends_at DATETIME(6) not null,
    canceled_at DATETIME(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_emergency_active on emergency_notices (canceled_at, ends_at);

alter table emergency_notices add constraint fk_emergency_notices_user foreign key (user_id) references users (id);
