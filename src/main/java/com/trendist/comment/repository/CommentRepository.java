package com.trendist.comment.repository;

import com.trendist.comment.entity.Comment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /** 추천 상위(4.12): 추천 1개 이상, 삭제·탈퇴 제외, 동점이면 먼저 쓴 것. */
    @Query("""
            select c from Comment c join fetch c.player p join fetch p.user u
            where c.keyword.id = :keywordId and c.deletedAt is null and u.deletedAt is null and c.likeCount >= 1
            order by c.likeCount desc, c.createdAt asc, c.id asc""")
    List<Comment> findTop(@Param("keywordId") Long keywordId, Pageable pageable);

    /** 최신순(추천 상위로 고정한 댓글은 뺀다). */
    @Query(value = """
            select c from Comment c join fetch c.player p join fetch p.user
            where c.keyword.id = :keywordId and c.id not in :excluded
            order by c.createdAt desc, c.id desc""",
            countQuery = "select count(c) from Comment c where c.keyword.id = :keywordId and c.id not in :excluded")
    Page<Comment> findLatest(@Param("keywordId") Long keywordId, @Param("excluded") Collection<Long> excluded, Pageable pageable);

    long countByKeywordId(Long keywordId);

    /** 추천·비추천 수를 바꾼다. 0 아래로 내려가지 않는다(9.6). updated_at은 건드리지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Comment c set
              c.likeCount = case when c.likeCount + :like < 0 then 0 else c.likeCount + :like end,
              c.dislikeCount = case when c.dislikeCount + :dislike < 0 then 0 else c.dislikeCount + :dislike end
            where c.id = :id""")
    int addVotes(@Param("id") Long id, @Param("like") int like, @Param("dislike") int dislike);

    @Modifying
    @Query("update Comment c set c.reportCount = c.reportCount + 1 where c.id = :id")
    int incReportCount(@Param("id") Long id);
}
