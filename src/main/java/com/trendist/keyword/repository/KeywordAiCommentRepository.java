package com.trendist.keyword.repository;

import com.trendist.keyword.entity.KeywordAiComment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordAiCommentRepository extends JpaRepository<KeywordAiComment, Long> {
    /** 키워드가 가리키는 AI 설명 행(없으면 빈 값) */
    @Query("select a from Keyword k join k.aiComment a where k.id = :keywordId")
    Optional<KeywordAiComment> findByKeywordId(@Param("keywordId") Long keywordId);
}
