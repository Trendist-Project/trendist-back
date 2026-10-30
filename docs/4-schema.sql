-- Trendist 4부. SQL — 17. 스키마 SQL
-- 문서 목록과 변경 이력은 docs/README.md. 절 번호는 기획·기술·운영 문서와 이어진다.
--
-- ERD Cloud "SQL 가져오기"(MySQL)용 DDL이다. 테이블 32개, 외래 키 34개. 설계 기준이다. 개발·테스트에서는 JPA 엔티티가 스키마를 만들고(11.3), 운영은 이 SQL을 기준으로 Flyway 마이그레이션을 만든다. 처음 만드는 DB용이라 기존 테이블을 지우는 문장은 넣지 않았다.
-- guides의 WITH PARSER ngram은 MySQL 전용이다. MariaDB나 ERD Cloud에서 오류가 나면 그 부분만 빼고 실행한다.
-- keyword_log_dailies, player_score_dailies의 월 파티션은 넣지 않았다(필요해지면 Flyway에서 적용).
-- 확인 범위: 88판 스키마는 MariaDB 10.11.14에서 두 번 연속 실행해 확인했다. 이후 판은 DB 접속 권한이 없어 실행하지 못했고, 쉼표·괄호·외래 키 참조 대상·생성 순서만 스크립트로 검사했다. MySQL 8에서는 실행하지 않았다.

-- Trendist 스키마 (MySQL 8, InnoDB, utf8mb4) - ERD Cloud 가져오기용
-- 시각은 모두 UTC로 저장한다. 처음 만드는 DB용 설계 기준이다.

CREATE TABLE countries (
  id             BIGINT NOT NULL AUTO_INCREMENT COMMENT '지역 ID',
  code           VARCHAR(10) NOT NULL COMMENT '지역 코드(KR/GLOBAL/JP/CN/IN)',
  name           VARCHAR(100) NOT NULL COMMENT '지역 이름(한글). 다른 언어는 프론트엔드에서 code로 번역',
  default_locale VARCHAR(10) NOT NULL COMMENT '기본 언어(ko/en/ja/zh-CN/hi)',
  timezone       VARCHAR(50) NOT NULL COMMENT '시간대(Asia/Seoul, UTC 등)',
  enabled        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '오픈 여부',
  max_room_size  BIGINT NOT NULL DEFAULT 1000 COMMENT '방 인원 상한',
  sort_order     BIGINT NOT NULL DEFAULT 0 COMMENT '표시 순서',
  created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_countries_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지역(나라)';

CREATE TABLE titles (
  id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '칭호 ID',
  name             VARCHAR(100) NOT NULL COMMENT '칭호 이름(한글, 유일). 다른 언어는 프론트엔드에서 칭호 ID로 번역',
  description      VARCHAR(500) NOT NULL COMMENT '칭호 설명(한글)',
  icon             VARCHAR(100) NULL COMMENT '아이콘',
  condition_type   VARCHAR(20) NOT NULL COMMENT '조건 유형(METRIC=players 수치가 기준값 이상, RANK=그 나라 순위가 N위 안)',
  condition_params JSON NOT NULL COMMENT '조건 상세(JSON). METRIC: {"metric", "gte"}, RANK: {"metric", "within"}',
  sort_order       BIGINT NOT NULL DEFAULT 0 COMMENT '표시 순서',
  enabled          TINYINT(1) NOT NULL DEFAULT 1 COMMENT '사용 여부',
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_titles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='칭호';

CREATE TABLE trail_effects (
  id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '이동 효과 ID',
  code          VARCHAR(30) NOT NULL COMMENT '그림 코드(프론트엔드가 이 값으로 그린다)',
  name          VARCHAR(100) NOT NULL COMMENT '이름(한글)',
  description   VARCHAR(500) NOT NULL COMMENT '설명(한글)',
  unlock_type   VARCHAR(20) NOT NULL DEFAULT 'FREE' COMMENT '얻는 조건: FREE / LEVEL / TITLE / PURCHASE(결제, 아직 없음)',
  unlock_value  BIGINT NULL COMMENT 'LEVEL이면 레벨, TITLE이면 칭호 ID',
  sort_order    BIGINT NOT NULL DEFAULT 0 COMMENT '표시 순서',
  enabled       BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부',
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trail_effects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='이동 효과(4.23)';

CREATE TABLE levels (
  id           BIGINT NOT NULL AUTO_INCREMENT COMMENT '레벨 ID',
  level        BIGINT NOT NULL COMMENT '레벨(1~100). 1레벨은 필요 EXP 0',
  required_exp BIGINT NOT NULL COMMENT '그 레벨에 필요한 누적 EXP(레벨이 오를수록 커져야 함, 애플리케이션이 검사)',
  created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_levels_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='레벨별 필요 누적 EXP';

CREATE TABLE user_states (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '사용자 상태 ID',
  name       VARCHAR(50) NOT NULL COMMENT '상태 이름(한글: 정상/탈퇴). 애플리케이션이 이 이름으로 상태를 구분한다. 다른 언어 표시는 ID로 번역',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_states_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 상태';

CREATE TABLE users (
  id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
  country_id          BIGINT NULL COMMENT '마지막으로 플레이한 지역',
  user_state_id       BIGINT NOT NULL COMMENT '사용자 상태 ID(정상/탈퇴)',
  visitor_id          VARCHAR(51) NULL COMMENT '방문자 ID(yyyyMMddHHmmss-UUID, UTC). 탈퇴 후에도 유지',
  oauth_type          VARCHAR(20) NULL COMMENT 'OAuth 제공자(GOOGLE 등). 게스트는 NULL',
  oauth_id            VARCHAR(64) NULL COMMENT '제공자의 사용자 ID(구글은 sub의 SHA-256 해시). 탈퇴 후에도 유지',
  last_login_at       DATETIME(6) NULL COMMENT '마지막 OAuth 로그인 시각(게스트는 NULL)',
  role                VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '권한(USER/ADMIN)',
  ui_locale           VARCHAR(10) NOT NULL DEFAULT 'ko' COMMENT 'UI 언어',
  banned_until        DATETIME(6) NULL COMMENT '계정 금지 만료 시각(NULL이면 정상, 지금보다 뒤면 금지 중, 영구는 9999-12-31 23:59:59)',
  upload_banned_until DATETIME(6) NULL COMMENT '이미지 업로드 정지 만료 시각(NULL이면 정상, 지금보다 뒤면 정지 중, 영구는 9999-12-31 23:59:59)',
  deleted_at          DATETIME(6) NULL COMMENT '탈퇴 시각(탈퇴 상태일 때만 값이 있고 복구하면 NULL). 화면 표시만 덮어씀',
  tokens_revoked_at   DATETIME(6) NULL COMMENT '로그아웃 시각. 이전에 발급한 액세스 토큰(JWT iat)은 거절',
  created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_visitor_id (visitor_id),
  UNIQUE KEY uk_users_oauth (oauth_type, oauth_id),
  CONSTRAINT fk_users_country FOREIGN KEY (country_id) REFERENCES countries (id),
  CONSTRAINT fk_users_user_state FOREIGN KEY (user_state_id) REFERENCES user_states (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자(게스트 포함)';

CREATE TABLE players (
  id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '플레이어 ID',
  user_id             BIGINT NOT NULL COMMENT '사용자 ID',
  country_id          BIGINT NOT NULL COMMENT '지역 ID',
  title_id            BIGINT NULL COMMENT '활성 칭호(NULL이면 없음)',
  trail_effect_id     BIGINT NULL COMMENT '쓰는 이동 효과(NULL이면 없음, 4.23)',
  nickname            VARCHAR(40) NOT NULL COMMENT '닉네임(나라별, 중복 허용). 탈퇴 후에도 유지하고 화면에서만 탈퇴한 사용자로 표시',
  nickname_changed_at DATETIME(6) NULL COMMENT '마지막 닉네임 변경 시각',
  exp                 BIGINT NOT NULL DEFAULT 0 COMMENT '누적 경험치 EXP(그 나라, + 횟수로만 쌓이고 줄지 않음)',
  level               BIGINT NOT NULL DEFAULT 1 COMMENT '레벨(1~100, levels의 누적 EXP로 정한 캐시). 아이콘은 10레벨에서 완성, 11레벨부터 꾸미기 가능(설정)',
  current_score       BIGINT NOT NULL DEFAULT 0 COMMENT '현재 유행도(실시간 점수, 아이콘 크기, 정수). min(EXP, 100) + 보관함 점수 합. 방 메모리에서 실시간 계산해 클라이언트로 보내고 DB에는 5초마다 모아서 저장',
  hold_count          BIGINT NOT NULL DEFAULT 0 COMMENT '지금 가진(만료되지 않은) 트렌드 키워드 개수(player_keywords 행 수 캐시, 0 아래로 내려가지 않음, 1시간마다 실제 행 수로 보정)',
  total_plus_count    BIGINT NOT NULL DEFAULT 0 COMMENT '누적 + 횟수(만료 행 재사용 포함, 줄지 않음)',
  comment_score       BIGINT NOT NULL DEFAULT 0 COMMENT '댓글 점수: 댓글을 쓰면 +1, 삭제하면 -1, 내 댓글이 추천을 받으면 +1, 비추천을 받으면 -1(0 아래로 내려가지 않음)',
  search_count        BIGINT NOT NULL DEFAULT 0 COMMENT '누적 검색 횟수(검색할 때마다 +1, 줄지 않음)',
  steal_count         BIGINT NOT NULL DEFAULT 0 COMMENT '다른 플레이어에게서 키워드를 빼앗은 횟수(칭호용, 줄지 않음)',
  bot_win_count       BIGINT NOT NULL DEFAULT 0 COMMENT '봇을 이겨 보상을 받은 횟수(칭호용)',
  orb_count           BIGINT NOT NULL DEFAULT 0 COMMENT '? 구슬을 먹은 횟수(꽝 포함, 칭호용)',
  lose_count          BIGINT NOT NULL DEFAULT 0 COMMENT '충돌로 키워드를 잃은 횟수(칭호용)',
  active_days         BIGINT NOT NULL DEFAULT 0 COMMENT '플레이한 날의 수(만든 날·키워드를 얻은 날, 나라 시간대 기준, 칭호용)',
  last_played_at      DATETIME(6) NULL COMMENT '마지막 플레이 시각(만들기·키워드 얻기). active_days를 하루에 한 번만 세는 기준',
  created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '처음 들어간 시각',
  updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_players (user_id, country_id),
  KEY idx_players_ranking (country_id, current_score),
  CONSTRAINT fk_players_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_players_country FOREIGN KEY (country_id) REFERENCES countries (id),
  CONSTRAINT fk_players_title FOREIGN KEY (title_id) REFERENCES titles (id),
  CONSTRAINT fk_players_trail_effect FOREIGN KEY (trail_effect_id) REFERENCES trail_effects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='플레이어(사용자 × 나라): 닉네임, EXP·레벨, 활성 칭호, 트렌드 수치';

CREATE TABLE profiles (
  id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '프로필 ID',
  player_id        BIGINT NOT NULL COMMENT '플레이어 ID(players.id). 꾸미기는 나라별, 한 플레이어에 여러 개',
  equipped         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '장착 여부(플레이어당 하나, 애플리케이션이 보장)',
  background_color VARCHAR(7) NOT NULL COMMENT '바탕색(#RRGGBB)',
  border_color     VARCHAR(7) NULL COMMENT '테두리 색(#RRGGBB). NULL이면 기본 테두리',
  border_width     TINYINT UNSIGNED NULL COMMENT '테두리 두께(px). NULL이면 기본 두께',
  profile_type     VARCHAR(20) NOT NULL DEFAULT 'GROWTH' COMMENT '프로필 종류(GROWTH=성장형 캐릭터, PIXEL=직접 그린 픽셀 그림, IMAGE=업로드 이미지). PIXEL·IMAGE 파일은 profiles/{id}로 찾고 복사·공유 대상이 아님',
  image_changed_at DATETIME(6) NULL COMMENT '픽셀 그림·업로드 이미지를 바꾼 시각(1시간 1회 제한과 파일 주소 버전 표시용). 플레이어의 모든 프로필 중 가장 최근 값으로 판단',
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_profiles_player (player_id, equipped),
  CONSTRAINT fk_profiles_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='플레이어 꾸미기 프로필(1 player : N profiles)';

CREATE TABLE data_sources (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '데이터 소스 ID',
  name       VARCHAR(50) NOT NULL COMMENT '어느 데이터인지(google, admin, internal). 화면 표시는 코드로 번역',
  weight     DECIMAL(6,3) NOT NULL COMMENT '반영값(가중치, 0~2). 지금은 모든 소스 1. 키워드 트렌드 값에 곱함. 0이면 사용 안 함',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_data_sources_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='데이터 소스와 반영값(가중치)';

CREATE TABLE keyword_states (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '키워드 상태 ID',
  name       VARCHAR(50) NOT NULL COMMENT '상태 이름(한글: 활성/비활성/금지). 애플리케이션이 이 이름으로 상태를 구분한다. 다른 언어 표시는 ID로 번역',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_keyword_states_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='키워드 상태';

CREATE TABLE keywords (
  id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '키워드 ID',
  country_id       BIGINT NOT NULL COMMENT '지역 ID',
  data_source_id   BIGINT NOT NULL COMMENT '현재 반영 소스(점수 가중치를 정하는 소스). 외부에서 확인되면 그 외부 소스, 아니면 internal',
  keyword_state_id BIGINT NOT NULL COMMENT '키워드 상태 ID(활성/비활성/금지)',
  text             VARCHAR(20) NOT NULL COMMENT '표시용 키워드(최대 20자, 입력한 표기 유지)',
  normalized       VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '정규화 키워드(같은 단어 판정용 유일 키, 바이트 비교. NFKC·소문자·공백 제거, 규칙은 4.18)',
  trend_value      BIGINT NOT NULL DEFAULT 0 COMMENT '트렌드 값(0~100 정수, 반올림). 활성 상태면 반영 소스 가중치 × (3 + 47 × g(가진 비율)), 그 외 0. 가진 비율 = holder_count / 그 나라 활동 인원, g는 4.8. 키워드 점수 = trend_value',
  search_count     BIGINT NOT NULL DEFAULT 0 COMMENT '누적 검색 수(매시 메모리 카운터를 더함)',
  total_plus_count BIGINT NOT NULL DEFAULT 0 COMMENT '누적 + 수(삭제·잃기로 줄지 않음)',
  holder_count     BIGINT NOT NULL DEFAULT 0 COMMENT '지금 가진 사람 수(player_keywords 행 수 캐시)',
  activated_at     DATETIME(6) NULL COMMENT '처음 🔥활성이 된 시각(NULL이면 한 번도 활성 전)',
  inactive_at      DATETIME(6) NULL COMMENT '비활성 전환 시각',
  banned_at        DATETIME(6) NULL COMMENT '금지 시각',
  external_until   DATETIME(6) NULL COMMENT '목록형 외부 소스(google)에 의한 활성 유지 끝 시각(목록에 보일 때마다 지금 + 7일). 지나면 목록에 아직 있는지 다시 본다(10.2)',
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_keywords_country_normalized (country_id, normalized),
  KEY idx_keywords_state (country_id, keyword_state_id, trend_value),
  CONSTRAINT fk_keywords_country FOREIGN KEY (country_id) REFERENCES countries (id),
  CONSTRAINT fk_keywords_data_source FOREIGN KEY (data_source_id) REFERENCES data_sources (id),
  CONSTRAINT fk_keywords_keyword_state FOREIGN KEY (keyword_state_id) REFERENCES keyword_states (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='키워드(지역별)';

CREATE TABLE keyword_log_24h (
  id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '키워드 시간별 기록 ID',
  keyword_id       BIGINT NOT NULL COMMENT '키워드 ID',
  hour_slot        TINYINT UNSIGNED NOT NULL COMMENT '나라 현지 시(0~23). 키워드당 24칸을 덮어씀(행을 지우지 않음)',
  recorded_at      DATETIME NOT NULL COMMENT '이 칸에 마지막으로 기록한 시간의 시작(UTC 정시). 24시간이 지난 칸은 조회에서 무시',
  search_count     BIGINT NOT NULL DEFAULT 0 COMMENT '그 시간이 끝날 때의 누적 검색 수(keywords.search_count 스냅샷)',
  total_plus_count BIGINT NOT NULL DEFAULT 0 COMMENT '그 시간이 끝날 때의 누적 + 수(keywords.total_plus_count 스냅샷)',
  holder_count     BIGINT NOT NULL DEFAULT 0 COMMENT '그 시간이 끝날 때의 가진 사람 수(keywords.holder_count 스냅샷)',
  search_inc       BIGINT NOT NULL DEFAULT 0 COMMENT '그 시간의 검색 증가량',
  total_plus_inc   BIGINT NOT NULL DEFAULT 0 COMMENT '그 시간의 + 증가량',
  holder_inc       BIGINT NOT NULL DEFAULT 0 COMMENT '그 시간의 가진 사람 증감(음수 가능)',
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각(칸을 덮어쓸 때 갱신)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_keyword_log_24h (keyword_id, hour_slot),
  CONSTRAINT fk_keyword_log_24h_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='키워드 시간별 로그(키워드당 24칸 덮어쓰기)';

CREATE TABLE keyword_log_dailies (
  id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '키워드 일별 기록 ID',
  keyword_id       BIGINT NOT NULL COMMENT '키워드 ID',
  record_date      DATE NOT NULL COMMENT '나라 현지 날짜',
  search_count     BIGINT NOT NULL DEFAULT 0 COMMENT '그날 마지막 누적 검색 수',
  total_plus_count BIGINT NOT NULL DEFAULT 0 COMMENT '그날 마지막 누적 + 수',
  holder_count     BIGINT NOT NULL DEFAULT 0 COMMENT '그날 마지막 가진 사람 수',
  search_inc       BIGINT NOT NULL DEFAULT 0 COMMENT '그날의 검색 증가량 합',
  total_plus_inc   BIGINT NOT NULL DEFAULT 0 COMMENT '그날의 + 증가량 합',
  holder_inc       BIGINT NOT NULL DEFAULT 0 COMMENT '그날의 가진 사람 증감 합(음수 가능)',
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각(매시간 다시 만듦)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_keyword_log_dailies (keyword_id, record_date),
  KEY idx_keyword_log_dailies_date (record_date),
  CONSTRAINT fk_keyword_log_dailies_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='키워드 일별 기록(180일 보관)';

CREATE TABLE player_keywords (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '보관함 ID(+ 한 번)',
  player_id  BIGINT NOT NULL COMMENT '플레이어 ID(players.id). 키워드와 같은 지역인지는 애플리케이션에서 검사',
  keyword_id BIGINT NOT NULL COMMENT '키워드 ID',
  expires_at DATETIME(6) NOT NULL COMMENT '삭제 시각(created_at + 24시간)',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '+ 시각(만료 행을 재사용하면 새 + 시각으로 갱신)',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각(만료 행 재사용)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_keywords (player_id, keyword_id),
  KEY idx_player_keywords_keyword (keyword_id),
  KEY idx_player_keywords_expires (expires_at),
  CONSTRAINT fk_player_keywords_player FOREIGN KEY (player_id) REFERENCES players (id),
  CONSTRAINT fk_player_keywords_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='보관함(+ 기록, 24시간 뒤 삭제)';

CREATE TABLE comments (
  id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '댓글 ID',
  keyword_id    BIGINT NOT NULL COMMENT '키워드 ID',
  player_id     BIGINT NOT NULL COMMENT '작성자 플레이어 ID(players.id). 닉네임·댓글 점수는 나라별',
  body          VARCHAR(400) NOT NULL COMMENT '본문(삭제·탈퇴 후에도 보관)',
  like_count    BIGINT NOT NULL DEFAULT 0 COMMENT '추천 수',
  dislike_count BIGINT NOT NULL DEFAULT 0 COMMENT '비추천 수',
  deleted_at    DATETIME(6) NULL COMMENT '작성자 삭제 시각(NULL이면 삭제 안 됨)',
  report_count  BIGINT NOT NULL DEFAULT 0 COMMENT '신고 수',
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작성 시각',
  updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_comments_latest (keyword_id, created_at, id),
  KEY idx_comments_top (keyword_id, deleted_at, like_count, created_at),
  KEY idx_comments_user (player_id),
  CONSTRAINT fk_comments_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id),
  CONSTRAINT fk_comments_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='키워드 댓글';

CREATE TABLE comment_votes (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '투표 ID',
  comment_id BIGINT NOT NULL COMMENT '댓글 ID',
  player_id  BIGINT NOT NULL COMMENT '투표자 플레이어 ID(players.id)',
  vote       VARCHAR(10) NOT NULL COMMENT '투표(LIKE/DISLIKE). 값 검증은 애플리케이션에서 한다',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '투표 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각(추천↔비추천 변경 시 갱신)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_votes (comment_id, player_id),
  CONSTRAINT fk_comment_votes_comment FOREIGN KEY (comment_id) REFERENCES comments (id),
  CONSTRAINT fk_comment_votes_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='키워드 댓글 투표';

CREATE TABLE notices (
  id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '공지 ID',
  user_id       BIGINT NOT NULL COMMENT '작성한 관리자',
  title         VARCHAR(100) NOT NULL COMMENT '제목(1~50자)',
  body          TEXT NOT NULL COMMENT '본문(1~5000자)',
  pinned        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '상단 고정',
  popup_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '팝업 노출 여부',
  popup_until   DATETIME(6) NULL COMMENT '팝업 노출 종료 시각(created_at + 관리자가 고른 일수)',
  deleted_at    DATETIME(6) NULL COMMENT '삭제 시각(NULL이면 게시 중)',
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '최초 등록 시각',
  updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_notices_list (deleted_at, pinned, created_at),
  KEY idx_notices_popup (deleted_at, popup_enabled, popup_until),
  CONSTRAINT fk_notices_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공지사항';

CREATE TABLE user_notices (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '열람 기록 ID',
  user_id    BIGINT NOT NULL COMMENT '사용자 ID',
  notice_id  BIGINT NOT NULL COMMENT '공지 ID',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '팝업으로 처음 본 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notices (user_id, notice_id),
  CONSTRAINT fk_user_notices_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_notices_notice FOREIGN KEY (notice_id) REFERENCES notices (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공지 팝업 열람 기록';

CREATE TABLE guide_categories (
  id           BIGINT NOT NULL AUTO_INCREMENT COMMENT '가이드 카테고리 ID',
  code         VARCHAR(50) NOT NULL COMMENT '주소용 영문 코드(예: getting-started)',
  name         VARCHAR(100) NOT NULL COMMENT '카테고리 이름',
  display_type VARCHAR(20) NOT NULL COMMENT '보여 주는 형식(FAQ/ARTICLE)',
  sort_order   BIGINT NOT NULL DEFAULT 0 COMMENT '표시 순서',
  enabled      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '사용 여부',
  created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_categories_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게임 가이드 카테고리';

CREATE TABLE guides (
  id                BIGINT NOT NULL AUTO_INCREMENT COMMENT '가이드 글 ID',
  guide_category_id BIGINT NOT NULL COMMENT '카테고리 ID',
  user_id           BIGINT NOT NULL COMMENT '작성한 관리자',
  title             VARCHAR(100) NOT NULL COMMENT '제목(FAQ형은 질문)',
  body              MEDIUMTEXT NOT NULL COMMENT '본문(마크다운, FAQ형은 답)',
  sort_order        BIGINT NOT NULL DEFAULT 0 COMMENT '카테고리 안 표시 순서',
  deleted_at        DATETIME(6) NULL COMMENT '삭제 시각(NULL이면 게시 중)',
  created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_guides_list (guide_category_id, deleted_at, sort_order),
  FULLTEXT KEY ft_guides_search (title, body) WITH PARSER ngram,
  CONSTRAINT fk_guides_category FOREIGN KEY (guide_category_id) REFERENCES guide_categories (id),
  CONSTRAINT fk_guides_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='게임 가이드 글(FAQ·지식백과)';

CREATE TABLE player_score_24h (
  id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '기록 ID',
  player_id     BIGINT NOT NULL COMMENT '플레이어 ID',
  hour_slot     TINYINT UNSIGNED NOT NULL COMMENT '나라 현지 시(0~23). 플레이어당 24칸을 덮어씀(행을 지우지 않음)',
  recorded_at   DATETIME NOT NULL COMMENT '이 칸에 마지막으로 기록한 시간의 시작(UTC 정시). 24시간이 지난 칸은 조회에서 무시',
  current_score BIGINT NOT NULL COMMENT '그 시간 마지막 현재 유행도',
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각(칸을 덮어쓸 때 갱신)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_score_24h (player_id, hour_slot),
  CONSTRAINT fk_player_score_24h_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='플레이어 점수 시간별 로그(플레이어당 24칸 덮어쓰기, 그래프용)';

CREATE TABLE player_score_dailies (
  id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '기록 ID',
  player_id     BIGINT NOT NULL COMMENT '플레이어 ID',
  record_date   DATE NOT NULL COMMENT '지역 현지 날짜',
  current_score BIGINT NOT NULL COMMENT '그날 마지막 현재 유행도',
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각(매시간 다시 만듦)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_score_dailies (player_id, record_date),
  CONSTRAINT fk_player_score_dailies_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='플레이어 점수 일별 기록(영구 보관)';

CREATE TABLE player_titles (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '보유 칭호 ID',
  player_id  BIGINT NOT NULL COMMENT '플레이어 ID(players.id). 칭호는 나라별',
  title_id   BIGINT NOT NULL COMMENT '칭호 ID',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '획득 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_titles (player_id, title_id),
  CONSTRAINT fk_player_titles_player FOREIGN KEY (player_id) REFERENCES players (id),
  CONSTRAINT fk_player_titles_title FOREIGN KEY (title_id) REFERENCES titles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='플레이어 보유 칭호';

CREATE TABLE chat_messages (
  id           BIGINT NOT NULL AUTO_INCREMENT COMMENT '채팅 메시지 ID',
  country_id   BIGINT NOT NULL COMMENT '방의 지역 ID',
  player_id    BIGINT NOT NULL COMMENT '보낸 플레이어 ID(players.id). 닉네임은 나라별',
  body         VARCHAR(100) NOT NULL COMMENT '메시지(최대 100자)',
  report_count BIGINT NOT NULL DEFAULT 0 COMMENT '신고 수',
  created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '보낸 시각',
  PRIMARY KEY (id),
  KEY idx_chat_messages_room (country_id, created_at),
  KEY idx_chat_messages_created (created_at),
  CONSTRAINT fk_chat_messages_country FOREIGN KEY (country_id) REFERENCES countries (id),
  CONSTRAINT fk_chat_messages_player FOREIGN KEY (player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='채팅 메시지(보관 기간 뒤 삭제)';

CREATE TABLE report_types (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 대상 유형 ID',
  name       VARCHAR(50) NOT NULL COMMENT '신고 대상 유형 이름(한글: 키워드/댓글/아이콘 이미지/플레이어/채팅). 애플리케이션이 이 이름으로 유형을 구분한다',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_types_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='신고 대상 유형';

CREATE TABLE reports (
  id             BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 ID',
  player_id      BIGINT NOT NULL COMMENT '신고한 플레이어 ID(어느 나라에서 신고했는지 알 수 있다)',
  report_type_id BIGINT NOT NULL COMMENT '신고 대상 유형 ID',
  target_id      BIGINT NOT NULL COMMENT '대상 ID(유형에 따라 keywords/comments/profiles/players/chat_messages의 ID, 외래 키 없음)',
  reason         VARCHAR(500) NULL COMMENT '신고 사유',
  status         VARCHAR(20) NOT NULL DEFAULT 'RECEIVED' COMMENT '상태(RECEIVED/RESOLVED/REJECTED)',
  created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '신고 시각',
  updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reports_once (report_type_id, target_id, player_id),
  KEY idx_reports_status (status, created_at),
  CONSTRAINT fk_reports_player FOREIGN KEY (player_id) REFERENCES players (id),
  CONSTRAINT fk_reports_report_type FOREIGN KEY (report_type_id) REFERENCES report_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='신고';

CREATE TABLE sanction_types (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '제재 타입 ID',
  name       VARCHAR(100) NOT NULL COMMENT '제재 이름(한글: 계정 금지/이미지 업로드 정지/댓글 작성 정지). 애플리케이션이 이 이름으로 타입을 구분한다',
  day        BIGINT NULL COMMENT '제재 일수(NULL이면 영구)',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sanction_types_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='제재 타입(타입별 제재 일수)';

CREATE TABLE sanctions (
  id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '제재 ID',
  user_id          BIGINT NOT NULL COMMENT '제재받은 사용자 ID',
  sanction_type_id BIGINT NOT NULL COMMENT '제재 타입 ID',
  reason           VARCHAR(500) NOT NULL COMMENT '제재 사유',
  expires_at       DATETIME(6) NOT NULL COMMENT '제재 만료 예정 시각(걸 때 시각 + 타입의 day, 영구는 9999-12-31 23:59:59)',
  released_at      DATETIME(6) NULL COMMENT '관리자가 만료 전에 실제로 푼 시각(NULL이면 풀지 않음)',
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '제재 시작 시각',
  updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  KEY idx_sanctions_user (user_id, sanction_type_id, created_at),
  CONSTRAINT fk_sanctions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_sanctions_type FOREIGN KEY (sanction_type_id) REFERENCES sanction_types (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 제재 기록(사유·기간·해제)';

CREATE TABLE refresh_tokens (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '갱신 토큰 ID',
  user_id    BIGINT NOT NULL COMMENT '사용자 ID',
  token_hash VARCHAR(64) NOT NULL COMMENT '갱신 토큰의 SHA-256 해시(원본은 저장하지 않음)',
  expires_at DATETIME(6) NOT NULL COMMENT '만료 시각',
  revoked_at DATETIME(6) NULL COMMENT '폐기 시각(회전·로그아웃·금지·탈퇴·재사용 감지). NULL이면 유효',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '발급 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_hash (token_hash),
  KEY idx_refresh_tokens_user (user_id, revoked_at),
  KEY idx_refresh_tokens_expires (expires_at),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='갱신 토큰(계정 로그인 세션)';

CREATE TABLE blocked_terms (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  term       VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '차단어(정규화 값)',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blocked_terms (term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='차단어(채팅 필터, 키워드 등록 차단 공통)';

CREATE TABLE blocked_nicknames (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  term       VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '금지 단어(정규화 값). 닉네임에 포함되면 막음',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 시각',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_blocked_nicknames (term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='금지 닉네임';

CREATE TABLE settings (
  id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '설정 ID',
  name        VARCHAR(100) NOT NULL COMMENT '설정 이름(유일)',
  value       VARCHAR(2000) NOT NULL COMMENT '설정 값(문자열로 저장)',
  value_type  VARCHAR(20) NOT NULL COMMENT '값 유형(INT/DECIMAL/JSON)',
  min_value   DECIMAL(18,6) NULL COMMENT '허용 최솟값',
  max_value   DECIMAL(18,6) NULL COMMENT '허용 최댓값',
  description VARCHAR(500) NULL COMMENT '설정 설명(한글, 관리자 화면용)',
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
  updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uk_settings_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='밸런스 설정값';

CREATE TABLE setting_histories (
  id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '이력 ID',
  setting_id BIGINT NOT NULL COMMENT '설정 ID',
  old_value  VARCHAR(2000) NULL COMMENT '이전 값',
  new_value  VARCHAR(2000) NOT NULL COMMENT '새 값',
  reason     VARCHAR(500) NOT NULL COMMENT '변경 사유',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '변경 시각',
  PRIMARY KEY (id),
  KEY idx_sh_setting (setting_id, created_at),
  CONSTRAINT fk_sh_setting FOREIGN KEY (setting_id) REFERENCES settings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='밸런스 설정 변경 이력';
