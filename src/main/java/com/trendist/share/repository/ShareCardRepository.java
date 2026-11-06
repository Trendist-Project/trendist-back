package com.trendist.share.repository;

import com.trendist.share.entity.ShareCard;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareCardRepository extends JpaRepository<ShareCard, Long> {
    Optional<ShareCard> findByCode(String code);

    /**
     * 잠금 읽기라 트랜잭션의 읽기 시점(REPEATABLE READ 스냅샷)과 관계없이 방금 커밋된 행까지 본다.
     * players 행을 먼저 잠근 뒤 부르므로, 동시에 만든 두 번째 요청이 첫 번째가 넣은 행을 찾는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShareCard c where c.player.id = :playerId and c.contentHash = :hash")
    Optional<ShareCard> findByPlayerIdAndContentHashForUpdate(@Param("playerId") long playerId, @Param("hash") String hash);

    /** 조회·클릭 증가분을 더한다(1분마다, 4.24) */
    @Modifying
    @Query("update ShareCard c set c.viewCount = c.viewCount + :views, c.playCount = c.playCount + :plays, c.updatedAt = :now where c.id = :id")
    int addCounts(@Param("id") long id, @Param("views") long views, @Param("plays") long plays, @Param("now") Instant now);

    /** 한 사용자가 만든 모든 카드(모든 나라)의 조회·클릭 합계: [조회, 클릭] */
    @Query("select coalesce(sum(c.viewCount), 0), coalesce(sum(c.playCount), 0) from ShareCard c where c.player.user.id = :userId")
    java.util.List<Object[]> totalsOfUser(@Param("userId") long userId);

    @Query("select c.id from ShareCard c where c.player.user.id = :userId")
    java.util.List<Long> idsOfUser(@Param("userId") long userId);

    /** 관리자 통계: [카드 수, 조회 합, 클릭 합] */
    @Query("select count(c), coalesce(sum(c.viewCount), 0), coalesce(sum(c.playCount), 0) from ShareCard c")
    java.util.List<Object[]> totals();

    long countByCreatedAtGreaterThanEqual(Instant from);

    @Query(value = "select c from ShareCard c join fetch c.player", countQuery = "select count(c) from ShareCard c")
    Page<ShareCard> findPage(Pageable pageable);

    /** 사이트맵용(4.28): 최근 카드부터 [코드, 만든 시각] */
    @Query("select c.code, c.createdAt from ShareCard c order by c.id desc")
    java.util.List<Object[]> findSitemapRows(Pageable pageable);
}
