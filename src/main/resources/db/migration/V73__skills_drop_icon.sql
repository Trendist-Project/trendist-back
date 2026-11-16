-- 스킬 아이콘은 프론트엔드가 스킬마다 직접 그린 SVG(`SkillIcon`)로 보여 주고, DB의 이모지 글자는 쓰이지 않아 컬럼을 없앤다(4.33).
alter table skills drop column icon;
