package com.trendist.stats.repository;

import com.trendist.stats.entity.SiteHourlyStat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteHourlyStatRepository extends JpaRepository<SiteHourlyStat, Long> {
    Optional<SiteHourlyStat> findByCountryIdAndRecordedAt(long countryId, Instant recordedAt);

    /** [from, to) 시간의 행, 오래된 순. */
    List<SiteHourlyStat> findByCountryIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            long countryId, Instant from, Instant to);
}
