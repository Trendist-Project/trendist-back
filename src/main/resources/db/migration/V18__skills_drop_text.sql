-- 스킬의 이름·설명(번역)은 DB가 아니라 프론트엔드 번역 파일(i18n/<언어>/skills.ts, 키 skills.<코드>_name·_desc)로 둔다. 번역 컬럼을 없앤다.
-- DB는 코드·아이콘·종류·기본 단축키·쿨타임·사용 여부·순서만 가진다.
alter table skills
  drop column name,
  drop column name_en,
  drop column description,
  drop column description_en;
