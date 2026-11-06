-- 설정 그룹 테이블과 단위 컬럼(13.3). 관리자 설정 화면이 코드에만 있던 그룹을 DB로 옮기고, 설정이 외래 키로 그룹을 가리킨다.
create table setting_groups (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    name varchar(50) not null,
    sort_order bigint not null,
    primary key (id),
    unique key uk_setting_groups_name (name)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- 그룹 이름과 순서는 코드(SettingKeys.GROUP_ORDER)와 같다. 서버가 켜질 때 시더가 다시 맞춘다
insert into setting_groups (created_at, updated_at, name, sort_order) values
    (utc_timestamp(6), utc_timestamp(6), '트렌드·키워드', 1),
    (utc_timestamp(6), utc_timestamp(6), '성장·경험치·칭호', 2),
    (utc_timestamp(6), utc_timestamp(6), '맵·크기', 3),
    (utc_timestamp(6), utc_timestamp(6), '이동·충돌', 4),
    (utc_timestamp(6), utc_timestamp(6), '보호·접속', 5),
    (utc_timestamp(6), utc_timestamp(6), '세이프존', 6),
    (utc_timestamp(6), utc_timestamp(6), '? 구슬', 7),
    (utc_timestamp(6), utc_timestamp(6), 'NPC 봇', 8),
    (utc_timestamp(6), utc_timestamp(6), '대시', 9),
    (utc_timestamp(6), utc_timestamp(6), '콤보·불꽃', 10),
    (utc_timestamp(6), utc_timestamp(6), '꾸미기·계정', 11),
    (utc_timestamp(6), utc_timestamp(6), 'AI 설명', 12),
    (utc_timestamp(6), utc_timestamp(6), '기타', 13);

-- 설정에 단위와 그룹을 붙인다. 이미 있는 설정은 일단 '기타'로 두고, 서버가 켜질 때 시더가 코드의 그룹·단위로 맞춘다
alter table settings add column unit varchar(20) null;
alter table settings add column group_id bigint null;
update settings set group_id = (select id from setting_groups where name = '기타');
alter table settings modify column group_id bigint not null;
alter table settings add constraint fk_settings_group foreign key (group_id) references setting_groups (id);

-- 입장 직후 보호는 없앤다. 접속은 늘 가운데 세이프존에서 시작하고, 연결이 끊겨도 원이 남아 있어 도망 방지가 된다(4.3, 4.32)
delete from settings where name = 'join_protect_seconds';

-- 기본값 변경: 꾸미기 해금 레벨 11 -> 10, 닉네임 변경 간격 60분 -> 5분. 관리자가 따로 바꾼 값은 건드리지 않는다
update settings set value = '10' where name = 'customize_unlock_level' and value = '11';
update settings set value = '5' where name = 'nickname_change_minutes' and value = '60';

-- 꾸미기가 10레벨부터 열리므로 칭호 '스타일리스트'(꾸미기가 열렸어요)의 조건과 설명, 꾸미기 가이드 글의 레벨 표기도 맞춘다
update titles set condition_params = json_set(condition_params, '$.gte', 10), description = replace(description, '(11레벨)', '(10레벨)')
 where name = '스타일리스트' and description like '%(11레벨)%';
update titles set description_en = replace(description_en, '(level 11)', '(level 10)')
 where name = '스타일리스트' and description_en like '%(level 11)%';

update guides set title = replace(title, '원 꾸미기 (11레벨부터)', '원 꾸미기 (10레벨부터)') where title = '원 꾸미기 (11레벨부터)';
update guides set title_en = replace(title_en, 'Customizing your orb (from level 11)', 'Customizing your orb (from level 10)') where title_en = 'Customizing your orb (from level 11)';
update guides set body = replace(body, '꾸미기가 열리는 11레벨부터', '꾸미기가 열리는 10레벨부터') where body like '%꾸미기가 열리는 11레벨부터%';
update guides set body_en = replace(body_en, 'Available from level 11, when customization unlocks', 'Available from level 10, when customization unlocks') where body_en like '%Available from level 11, when customization unlocks%';

-- 닉네임 변경 간격이 5분이 되어 가이드 글(닉네임)의 안내도 맞춘다
update guides set body = replace(body, '한 번 바꾸면 1시간 뒤에 다시 바꿀 수 있어요', '한 번 바꾸면 5분 뒤에 다시 바꿀 수 있어요') where body like '%한 번 바꾸면 1시간 뒤에 다시 바꿀 수 있어요%';
update guides set body_en = replace(body_en, 'you can change it again after 1 hour.', 'you can change it again after 5 minutes.') where body_en like '%you can change it again after 1 hour.%';
