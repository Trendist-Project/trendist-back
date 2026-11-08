package com.trendist.player.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인기도 투표(4.34). 한 사람이 같은 대상에게 그 나라 시간대의 하루에 한 번만 올리거나(+1) 내린다(-1). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "player_popularity_votes",
        uniqueConstraints = @UniqueConstraint(name = "uk_popularity_votes", columnNames = {"voter_player_id", "target_player_id", "vote_date"}))
public class PopularityVote extends BaseTimeEntity {
    @Column(name = "voter_player_id", nullable = false) private Long voterPlayerId;
    @Column(name = "target_player_id", nullable = false) private Long targetPlayerId;
    @Column(name = "vote_date", nullable = false) private LocalDate voteDate;
    @Column(nullable = false) private int value;

    public PopularityVote(Long voterPlayerId, Long targetPlayerId, LocalDate voteDate, int value) {
        this.voterPlayerId = voterPlayerId; this.targetPlayerId = targetPlayerId; this.voteDate = voteDate; this.value = value;
    }
}
