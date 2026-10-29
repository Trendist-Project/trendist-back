package com.trendist.comment.repository;

import com.trendist.comment.entity.CommentVote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentVoteRepository extends JpaRepository<CommentVote, Long> {
    Optional<CommentVote> findByCommentIdAndPlayerId(Long commentId, Long playerId);

    List<CommentVote> findByPlayerIdAndCommentIdIn(Long playerId, Collection<Long> commentIds);
}
