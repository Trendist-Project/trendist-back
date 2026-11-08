package com.trendist.player.repository;

import com.trendist.player.entity.Player;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByUserIdAndCountryId(Long userId, Long countryId);

    java.util.List<Player> findByUserId(Long userId);

    /** 패배 횟수를 한 문장으로 올린다(행을 잠그고 읽지 않는다: 점수·레벨을 다시 정할 일이 없다, 4.31). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Player p set p.defeatCount = p.defeatCount + 1 where p.id = :id")
    int incrementDefeatCount(@org.springframework.data.repository.query.Param("id") Long id);

    /** 최고 콤보를 기록보다 높을 때만 올린다(4.25). 올랐으면 1 */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Player p set p.maxCombo = :combo where p.id = :id and p.maxCombo < :combo")
    int raiseMaxCombo(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("combo") long combo);

    /** 상대의 불꽃을 꺼뜨린 횟수를 올린다(4.25) */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Player p set p.flameOutCount = p.flameOutCount + 1 where p.id = :id")
    int incrementFlameOutCount(@org.springframework.data.repository.query.Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id = :id")
    Optional<Player> findByIdForUpdate(@Param("id") Long id);

    /** 1시간 점검 대상: 보유 키워드 수가 있거나 + 행이 남은 플레이어. */
    @Query("select p.id from Player p where p.holdCount > 0 or exists (select 1 from PlayerKeyword pk where pk.player = p)")
    List<Long> findCheckTargets();

    /** 댓글 점수를 더한다. 0 아래로 내려가지 않는다(4.12). */
    @Modifying(flushAutomatically = true)
    @Query("update Player p set p.commentScore = case when p.commentScore + :delta < 0 then 0 else p.commentScore + :delta end where p.id = :id")
    int addCommentScore(@Param("id") Long id, @Param("delta") int delta);

    /** 점수 기록 대상(4.15): 그 시간 뒤에 행이 바뀐 플레이어(점수가 바뀌면 updated_at도 바뀐다). */
    @Query("select p from Player p join fetch p.country where p.updatedAt >= :since")
    List<Player> findChangedSince(@Param("since") java.time.Instant since);

    /** 접속 중인 플레이어의 마지막 접속 시각(13.9). 일괄 UPDATE라 updated_at은 바뀌지 않는다. */
    @Modifying
    @Query("update Player p set p.lastSeenAt = :now where p.id in :ids")
    int touchSeen(@Param("ids") java.util.Collection<Long> ids, @Param("now") java.time.Instant now);

    long countByCountryId(Long countryId);

    /** 인기도를 한 문장으로 바꾼다(점수 기록 대상에 끼지 않게 updated_at은 그대로). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Player p set p.popularity = p.popularity + :delta where p.id = :id")
    int addPopularity(@Param("id") Long id, @Param("delta") long delta);

    /** 플레이어 검색: 같은 나라, 탈퇴한 사용자 제외, 닉네임 일부(소문자, %포함). 인기도 높은 순 */
    @Query("select p from Player p join fetch p.user u join fetch p.level where p.country.id = :countryId and u.deletedAt is null "
            + "and lower(p.nickname) like :q order by p.popularity desc, p.currentScore desc, p.id")
    List<Player> searchByNickname(@Param("countryId") Long countryId, @Param("q") String q, org.springframework.data.domain.Pageable pageable);

    /**
     * 관리자 사용자 목록(13.10). q: 닉네임 일부(소문자, %포함) 또는 사용자·플레이어 번호(qid), filter: all / guest / google / admin /
     * banned / withdrawn / online(online에 든 플레이어만).
     */
    @Query(value = """
            select p from Player p join fetch p.user u
            where p.country.id = :countryId
              and (:q is null or lower(p.nickname) like :q or u.id = :qid or p.id = :qid)
              and (:filter = 'all'
                   or (:filter = 'guest' and u.oauthId is null)
                   or (:filter = 'google' and u.oauthId is not null)
                   or (:filter = 'admin' and u.role = 'ADMIN')
                   or (:filter = 'banned' and u.bannedUntil > :now)
                   or (:filter = 'withdrawn' and u.deletedAt is not null)
                   or (:filter = 'online' and p.id in :online))""",
            countQuery = """
            select count(p) from Player p join p.user u
            where p.country.id = :countryId
              and (:q is null or lower(p.nickname) like :q or u.id = :qid or p.id = :qid)
              and (:filter = 'all'
                   or (:filter = 'guest' and u.oauthId is null)
                   or (:filter = 'google' and u.oauthId is not null)
                   or (:filter = 'admin' and u.role = 'ADMIN')
                   or (:filter = 'banned' and u.bannedUntil > :now)
                   or (:filter = 'withdrawn' and u.deletedAt is not null)
                   or (:filter = 'online' and p.id in :online))""")
    org.springframework.data.domain.Page<Player> searchForAdmin(@Param("countryId") Long countryId, @Param("q") String q,
                                                                @Param("qid") Long qid, @Param("filter") String filter,
                                                                @Param("now") java.time.Instant now,
                                                                @Param("online") java.util.Collection<Long> online,
                                                                org.springframework.data.domain.Pageable pageable);

    long countByCountryIdAndLastSeenAtGreaterThanEqual(Long countryId, java.time.Instant since);

    /** 레벨 분포(13.9): [레벨, 인원]. 탈퇴한 사용자는 뺀다. */
    @Query("select p.level.level, count(p) from Player p where p.country.id = :countryId and p.user.deletedAt is null group by p.level.level")
    List<Object[]> levelCounts(@Param("countryId") Long countryId);

    /** 검색 횟수 +1(4.4). 일괄 UPDATE라 updated_at은 바뀌지 않는다(점수 기록 대상에 끼지 않게). */
    @Modifying
    @Query("update Player p set p.searchCount = p.searchCount + 1 where p.id = :id")
    int incSearchCount(@Param("id") Long id);

    /** 관리자 대시보드(13.11): 최근에 만든 플레이어(사용자와 함께). */
    @Query("select p from Player p join fetch p.user where p.country.id = :countryId order by p.id desc")
    List<Player> findRecent(@Param("countryId") Long countryId, org.springframework.data.domain.Pageable pageable);
}
