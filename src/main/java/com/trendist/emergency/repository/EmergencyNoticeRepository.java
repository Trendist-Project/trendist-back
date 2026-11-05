package com.trendist.emergency.repository;

import com.trendist.emergency.entity.EmergencyNotice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyNoticeRepository extends JpaRepository<EmergencyNotice, Long> {
    /** 지금 보여 주는 긴급 안내(취소되지 않고 끝나지 않은 것 중 가장 나중 것) */
    Optional<EmergencyNotice> findFirstByCanceledAtIsNullAndEndsAtAfterOrderByIdDesc(Instant now);

    /** 켜져 있는 것 전부(새로 올릴 때 이전 것을 취소하려고) */
    List<EmergencyNotice> findByCanceledAtIsNullAndEndsAtAfter(Instant now);

    /** 관리자 목록: 최근 20개 */
    List<EmergencyNotice> findTop20ByOrderByIdDesc();
}
