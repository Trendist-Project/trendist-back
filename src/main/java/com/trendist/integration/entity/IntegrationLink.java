package com.trendist.integration.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 연동된 외부 사이트 바로가기(13.15). code로 아이콘을 고르고, 주소(url)만 관리자가 바꾼다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "integration_links", uniqueConstraints = @UniqueConstraint(name = "uk_integration_links_code", columnNames = "code"))
public class IntegrationLink extends BaseTimeEntity {
    @Column(nullable = false, length = 30) private String code;
    @Column(nullable = false, length = 50) private String label;
    @Column(nullable = false, length = 500) private String url;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    public IntegrationLink(String code, String label, String url, int sortOrder) {
        this.code = code; this.label = label; this.url = url; this.sortOrder = sortOrder;
    }

    public void changeUrl(String url) { this.url = url; }
}
