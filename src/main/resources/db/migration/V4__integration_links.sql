-- 관리자 [연동] 탭(13.15): 연동된 외부 사이트의 바로가기 링크. 주소는 관리자가 고칠 수 있다.
create table integration_links (
    id bigint not null auto_increment,
    created_at DATETIME(6) not null,
    updated_at DATETIME(6) not null,
    code varchar(30) not null,
    label varchar(50) not null,
    url varchar(500) not null,
    sort_order int not null,
    primary key (id),
    unique key uk_integration_links_code (code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
-- 기본 항목 3개(네이버 서치어드바이저, 구글 서치 콘솔, 구글 애널리틱스)는 서버가 켜질 때 없으면 채운다(IntegrationSeeder)
