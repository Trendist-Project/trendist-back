package com.trendist.repo;

import com.trendist.domain.Keyword;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    Optional<Keyword> findByCountryIdAndNormalized(Long countryId, String normalized);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from Keyword k where k.id = :id")
    Optional<Keyword> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update Keyword k set k.searchCount = k.searchCount + :n where k.id = :id")
    int addSearchCount(@Param("id") Long id, @Param("n") long n);
}
