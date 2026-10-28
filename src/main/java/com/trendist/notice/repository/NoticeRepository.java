package com.trendist.notice.repository;

import com.trendist.notice.entity.Notice;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    /** 게시 중인 공지. 고정 공지 먼저, 그다음 최신순. */
    List<Notice> findByDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(Pageable pageable);
}
