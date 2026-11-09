-- 키 바인딩(4.33): 단축키로 쓸 수 있는 키 목록(keyboard_keys, KEYS가 MySQL 예약어라 이 이름), 키를 묶을 수 있는 동작 목록(actions),
-- 플레이어가 바꾼 연결(key_bindings)만 저장한다. 행이 없으면 actions.default_key_id를 쓰고, key_id가 null이면 일부러 비운 상태다.
-- 분류·종류 문자열은 목록 테이블(key_categories, action_types)로 빼서 외래 키로 가리킨다. 목록 행은 서버가 켜질 때 시더가 채운다.
create table key_categories (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    code varchar(20) not null,
    name varchar(30) not null,
    primary key (id),
    unique key uk_key_categories_code (code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table keyboard_keys (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    code varchar(32) not null,
    label varchar(16) not null,
    category_id bigint not null,
    alias_of_id bigint null,
    reserved boolean not null default false,
    enabled boolean not null default true,
    primary key (id),
    unique key uk_keyboard_keys_code (code),
    constraint fk_keyboard_keys_category foreign key (category_id) references key_categories (id),
    constraint fk_keyboard_keys_alias foreign key (alias_of_id) references keyboard_keys (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table action_types (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    code varchar(20) not null,
    name varchar(30) not null,
    primary key (id),
    unique key uk_action_types_code (code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table actions (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    code varchar(40) not null,
    type_id bigint not null,
    param int null,
    default_key_id bigint null,
    sort_order bigint not null,
    primary key (id),
    unique key uk_actions_code (code),
    constraint fk_actions_type foreign key (type_id) references action_types (id),
    constraint fk_actions_default_key foreign key (default_key_id) references keyboard_keys (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table key_bindings (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    player_id bigint not null,
    action_id bigint not null,
    key_id bigint null,
    primary key (id),
    unique key uk_key_bindings_player_action (player_id, action_id),
    unique key uk_key_bindings_player_key (player_id, key_id),
    constraint fk_key_bindings_player foreign key (player_id) references players (id) on delete cascade,
    constraint fk_key_bindings_action foreign key (action_id) references actions (id),
    constraint fk_key_bindings_key foreign key (key_id) references keyboard_keys (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
