package com.trendist.user.repository;

import com.trendist.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByVisitorId(String visitorId);

    Optional<User> findByOauthTypeAndOauthId(String oauthType, String oauthId);

    Optional<User> findFirstByRoleOrderByIdAsc(String role);

    /** 금지·탈퇴·로그아웃 확인과 관리자 권한 확인용으로 상태 번호, 금지 만료 시각, 로그아웃 시각, 권한만 읽는다. */
    @Query("select new com.trendist.user.repository.UserRepository$AccountStatus(u.userState.id, u.bannedUntil, u.tokensRevokedAt, u.role) from User u where u.id = :id")
    Optional<AccountStatus> findStatus(@Param("id") Long id);

    /** 관리자 통계(13.9): 실제 사용자(운영자 계정 제외), 게스트, 구글, 관리자, 탈퇴, 지금 금지 중. */
    @Query("""
            select count(u),
                   coalesce(sum(case when u.oauthId is null then 1 else 0 end), 0),
                   coalesce(sum(case when u.oauthId is not null then 1 else 0 end), 0),
                   coalesce(sum(case when u.oauthId is not null and u.role = 'ADMIN' then 1 else 0 end), 0),
                   coalesce(sum(case when u.deletedAt is not null then 1 else 0 end), 0),
                   coalesce(sum(case when u.bannedUntil > :now then 1 else 0 end), 0)
            from User u where u.visitorId is not null or u.oauthId is not null""")
    List<Object[]> countSummary(@Param("now") Instant now);

    record AccountStatus(Long userStateId, Instant bannedUntil, Instant tokensRevokedAt, String role) {}
}
