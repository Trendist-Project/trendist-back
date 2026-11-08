-- 스킬 데이터(4.33): 이름·설명·아이콘·종류·기본 단축키·쿨타임을 DB에서 관리한다. 스킬을 늘릴 때 행을 더하고(동작은 코드), 쿨타임·설명·기본 키는 DB 값만 고치면 된다.
-- 행은 시더(DataSeeder)가 없는 스킬 코드만 채운다. 쿨타임 설정은 settings에서 이 테이블로 옮겼으므로 옛 설정 행을 지운다.
create table skills (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  code             VARCHAR(30) NOT NULL,
  name             VARCHAR(50) NOT NULL,
  name_en          VARCHAR(50) NULL,
  description      VARCHAR(500) NOT NULL,
  description_en   VARCHAR(500) NULL,
  icon             VARCHAR(20) NULL,
  category         VARCHAR(20) NOT NULL,
  default_key      VARCHAR(30) NOT NULL,
  cooldown_seconds DECIMAL(10,2) NOT NULL,
  sort_order       BIGINT NOT NULL DEFAULT 0,
  enabled          TINYINT(1) NOT NULL DEFAULT 1,
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_skills_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

delete from settings where name in ('dash_cooldown_seconds', 'shockwave_cooldown_seconds', 'hook_cooldown_seconds',
                                    'skill_shield_cooldown_seconds', 'stealth_cooldown_seconds');
