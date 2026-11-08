package com.trendist.player.repository;

import com.trendist.player.entity.PopularityVote;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopularityVoteRepository extends JpaRepository<PopularityVote, Long> {
    Optional<PopularityVote> findByVoterPlayerIdAndTargetPlayerIdAndVoteDate(Long voter, Long target, LocalDate date);

    /** 오늘 내가 투표한 대상들(검색 결과에 표시) */
    @Query("select v from PopularityVote v where v.voterPlayerId = :voter and v.voteDate = :date and v.targetPlayerId in :targets")
    List<PopularityVote> findToday(@Param("voter") Long voter, @Param("date") LocalDate date, @Param("targets") Collection<Long> targets);
}
