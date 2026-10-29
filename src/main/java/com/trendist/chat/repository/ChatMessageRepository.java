package com.trendist.chat.repository;

import com.trendist.chat.entity.ChatMessage;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    /** 보관 기간이 지난 메시지. 신고된 메시지는 처리할 때까지 남긴다(4.13). */
    @Modifying
    @Query("delete from ChatMessage m where m.createdAt < :before and m.reportCount = 0")
    int deleteOlderThan(@Param("before") Instant before);

    @Modifying
    @Query("update ChatMessage m set m.reportCount = m.reportCount + 1 where m.id = :id")
    int incReportCount(@Param("id") Long id);
}
