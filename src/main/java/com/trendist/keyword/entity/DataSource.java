package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

/** 데이터 소스와 반영값(가중치 0~2, 10.3). */
@Entity
@Table(name = "data_sources", uniqueConstraints = @UniqueConstraint(name = "uk_data_sources_name", columnNames = "name"))
public class DataSource extends BaseTimeEntity {
    public static final String INTERNAL = "internal";
    public static final String NAVER_DATALAB = "naver_datalab";
    public static final String BQ_TRENDS_RISING = "bq_trends_rising";

    @Column(nullable = false, length = 50) private String name;
    @Column(nullable = false, precision = 6, scale = 3) private BigDecimal weight;

    protected DataSource() {}
    public DataSource(String name, BigDecimal weight) { this.name = name; this.weight = weight; }

    public String getName() { return name; }
    public BigDecimal getWeight() { return weight; }
    public boolean isInternal() { return INTERNAL.equals(name); }
}
