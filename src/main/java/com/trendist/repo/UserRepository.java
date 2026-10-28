package com.trendist.repo;

import com.trendist.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByVisitorId(String visitorId);

    /** 금지·탈퇴 확인용으로 상태 번호와 금지 만료 시각만 읽는다. */
    @Query("select new com.trendist.repo.UserRepository$AccountStatus(u.userState.id, u.bannedUntil) from User u where u.id = :id")
    Optional<AccountStatus> findStatus(@Param("id") Long id);

    record AccountStatus(Long userStateId, Instant bannedUntil) {}
}
