package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 데이터 소스와 반영값(가중치 0~2, 지금은 모두 1, 10.3). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "data_sources", uniqueConstraints = @UniqueConstraint(name = "uk_data_sources_name", columnNames = "name"))
public class DataSource extends BaseTimeEntity {
    public static final String INTERNAL = "internal";
    public static final String GOOGLE = "google"; // 구글 트렌드 실시간 인기 RSS(10.1)
    public static final String ADMIN = "admin";   // 관리자가 직접 넣은 🔥활성 키워드(13.7)

    @Column(nullable = false, length = 50) private String name;
    @Column(nullable = false, precision = 6, scale = 3) private BigDecimal weight;

    public DataSource(String name, BigDecimal weight) { this.name = name; this.weight = weight; }

    /** 반영값 변경. 이 소스를 쓰는 키워드의 trend_value는 호출하는 쪽이 다시 계산한다(13.2). */
    public void changeWeight(BigDecimal weight) { this.weight = weight; }

    public boolean isInternal() { return INTERNAL.equals(name); }
}
