-- 돌림판(하루 한 번): 플레이어가 그날(나라 시간대) 돌린 기록. 같은 날 두 번 돌리지 못하게 (player_id, spin_date)를 유일하게 둔다
create table wheel_spins (
    id bigint not null auto_increment,
    player_id bigint not null,
    spin_date date not null,
    reward_count int not null,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    primary key (id),
    unique key uk_wheel_spins_player_date (player_id, spin_date)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
