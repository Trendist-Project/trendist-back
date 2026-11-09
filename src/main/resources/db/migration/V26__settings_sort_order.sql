-- 설정에 화면 순서(sort_order)를 둔다(13.3). 같은 그룹 안에서 비슷한 설정끼리 붙어 보이게, 순서를 코드(SettingKeys)가 정하고
-- 서버가 켜질 때 시더가 설정마다 채운다. 시더가 채우기 전까지는 0이다(같은 값이면 이름순으로 보인다).
alter table settings add column sort_order bigint not null default 0;
