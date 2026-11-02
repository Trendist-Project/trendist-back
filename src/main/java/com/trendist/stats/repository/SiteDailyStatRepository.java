package com.trendist.stats.repository;

import com.trendist.stats.entity.SiteDailyStat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteDailyStatRepository extends JpaRepository<SiteDailyStat, Long> {
    Optional<SiteDailyStat> findByCountryIdAndRecordDate(long countryId, LocalDate recordDate);

    List<SiteDailyStat> findByCountryIdAndRecordDateGreaterThanEqualOrderByRecordDateAsc(long countryId, LocalDate from);
}
