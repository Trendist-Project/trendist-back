package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 데이터 소스와 반영값(가중치 0~2, 10.3). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "data_sources", uniqueConstraints = @UniqueConstraint(name = "uk_data_sources_name", columnNames = "name"))
public class DataSource extends BaseTimeEntity {
    public static final String INTERNAL = "internal";
    public static final String NAVER_DATALAB = "naver_datalab";
    public static final String BQ_TRENDS_RISING = "bq_trends_rising";
    public static final String GOOGLE = "google"; // 구글 트렌드 실시간 인기 RSS(10.1)

    @Column(nullable = false, length = 50) private String name;
    @Column(nullable = false, precision = 6, scale = 3) private BigDecimal weight;

    public DataSource(String name, BigDecimal weight) { this.name = name; this.weight = weight; }

    public boolean isInternal() { return INTERNAL.equals(name); }
}
