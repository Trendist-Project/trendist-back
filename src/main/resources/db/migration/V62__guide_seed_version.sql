-- 가이드 시드 글의 판 번호(4.17). 시드 글을 고치면 코드(GuideSeed.VERSIONS)의 번호를 올리고, 시더가 DB 판이 낮고 본문이 시더가 마지막으로 넣은 그대로(seed_hash)일 때만 새 본문으로 바꾼다.
-- 관리자가 만든 글은 둘 다 NULL이다. 값은 서버가 켜질 때 시더가 채운다.
alter table guides add column seed_version INT NULL;
alter table guides add column seed_hash VARCHAR(64) NULL;
