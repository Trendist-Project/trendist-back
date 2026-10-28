package com.trendist.repo;

import com.trendist.domain.DataSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSource, Long> {
    Optional<DataSource> findByName(String name);
}
