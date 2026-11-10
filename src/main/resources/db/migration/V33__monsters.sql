-- 몬스터(4.35): 보스몹의 시초. 몬스터 종류와 그 설정값, 등장 기록, 등장마다 플레이어별 피해를 DB로 운영한다.
-- 새 몬스터는 monster_types에 행을 더하고(행동은 서버 코드가 behavior 키로 구현), 설정값은 monster_settings에 둔다.
create table monster_types (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  code       VARCHAR(30) NOT NULL,
  name       VARCHAR(50) NOT NULL,
  name_en    VARCHAR(50) NULL,
  kind       VARCHAR(20) NOT NULL,
  behavior   VARCHAR(30) NOT NULL,
  image      VARCHAR(60) NULL,
  enabled    TINYINT(1) NOT NULL DEFAULT 1,
  sort_order BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_monster_types_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table monster_settings (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  monster_type_id BIGINT NOT NULL,
  name            VARCHAR(60) NOT NULL,
  value_num       DECIMAL(16,4) NOT NULL,
  min_v           DECIMAL(16,4) NOT NULL,
  max_v           DECIMAL(16,4) NOT NULL,
  description     VARCHAR(400) NOT NULL,
  unit            VARCHAR(20) NULL,
  group_name      VARCHAR(30) NOT NULL,
  sort_order      BIGINT NOT NULL DEFAULT 0,
  created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_monster_settings (monster_type_id, name),
  CONSTRAINT fk_monster_settings_type FOREIGN KEY (monster_type_id) REFERENCES monster_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table monster_spawns (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  monster_type_id BIGINT NOT NULL,
  country_id      BIGINT NOT NULL,
  source          VARCHAR(20) NOT NULL,
  status          VARCHAR(20) NOT NULL,
  started_at      DATETIME(6) NOT NULL,
  ended_at        DATETIME(6) NULL,
  max_hp          BIGINT NOT NULL DEFAULT 0,
  final_hp        BIGINT NOT NULL DEFAULT 0,
  humans          INT NOT NULL DEFAULT 0,
  avg_score       BIGINT NOT NULL DEFAULT 0,
  top_score       BIGINT NOT NULL DEFAULT 0,
  participants    INT NOT NULL DEFAULT 0,
  top_player_id   BIGINT NULL,
  top_damage      BIGINT NOT NULL DEFAULT 0,
  created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_monster_spawns_type_country (monster_type_id, country_id, started_at),
  CONSTRAINT fk_monster_spawns_type FOREIGN KEY (monster_type_id) REFERENCES monster_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table monster_damages (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  spawn_id   BIGINT NOT NULL,
  player_id  BIGINT NOT NULL,
  damage     BIGINT NOT NULL DEFAULT 0,
  hits       INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_monster_damages (spawn_id, player_id),
  CONSTRAINT fk_monster_damages_spawn FOREIGN KEY (spawn_id) REFERENCES monster_spawns (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 몬스터를 쓰러뜨리는 데 참여한 횟수(칭호용)
alter table players add column monster_kill_count BIGINT NOT NULL DEFAULT 0;
