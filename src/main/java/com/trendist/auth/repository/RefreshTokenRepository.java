package com.trendist.auth.repository;

import com.trendist.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 그 사용자의 살아 있는 갱신 토큰을 모두 폐기한다(재사용 감지·로그아웃·탈퇴). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAll(@Param("userId") Long userId, @Param("now") Instant now);
}
