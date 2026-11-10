-- 설정 변경 이력(13.17): 관리자가 밸런스 설정·몬스터 설정·스킬 설정을 바꿀 때마다 한 줄씩 남기고, 이전 값으로 되돌릴 수 있게 한다.
create table config_changes (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  scope            VARCHAR(20) NOT NULL,
  target           VARCHAR(120) NOT NULL,
  field            VARCHAR(30) NOT NULL,
  old_value        VARCHAR(100) NULL,
  new_value        VARCHAR(100) NULL,
  admin_user_id    BIGINT NOT NULL,
  admin_name       VARCHAR(60) NULL,
  memo             VARCHAR(300) NULL,
  reverted_from_id BIGINT NULL,
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_config_changes_scope (scope, id),
  KEY idx_config_changes_target (target, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
