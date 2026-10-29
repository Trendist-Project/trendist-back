package com.trendist.user.repository;

import com.trendist.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByVisitorId(String visitorId);

    Optional<User> findByOauthTypeAndOauthId(String oauthType, String oauthId);

    Optional<User> findFirstByRoleOrderByIdAsc(String role);

    /** 금지·탈퇴·로그아웃 확인용으로 상태 번호, 금지 만료 시각, 로그아웃 시각만 읽는다. */
    @Query("select new com.trendist.user.repository.UserRepository$AccountStatus(u.userState.id, u.bannedUntil, u.tokensRevokedAt) from User u where u.id = :id")
    Optional<AccountStatus> findStatus(@Param("id") Long id);

    record AccountStatus(Long userStateId, Instant bannedUntil, Instant tokensRevokedAt) {}
}
