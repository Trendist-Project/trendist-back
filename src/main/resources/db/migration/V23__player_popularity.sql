-- 플레이어 인기도(4.34): 다른 플레이어가 하루에 한 번(대상마다) 올리거나 내린다. 0 아래(음수)도 허용한다.
alter table players add column popularity BIGINT NOT NULL DEFAULT 0;
create table player_popularity_votes (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  voter_player_id BIGINT NOT NULL,
  target_player_id BIGINT NOT NULL,
  vote_date       DATE NOT NULL,
  value           TINYINT NOT NULL,
  created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_popularity_votes (voter_player_id, target_player_id, vote_date),
  KEY idx_popularity_votes_target (target_player_id),
  CONSTRAINT fk_popularity_voter FOREIGN KEY (voter_player_id) REFERENCES players (id),
  CONSTRAINT fk_popularity_target FOREIGN KEY (target_player_id) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
