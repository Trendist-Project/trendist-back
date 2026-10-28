package com.trendist.keyword.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;

/** 이름만 있는 코드 테이블. 애플리케이션이 이름으로 구분한다(11.1). */
@Entity
@Table(name = "keyword_states", uniqueConstraints = @UniqueConstraint(name = "uk_keyword_states_name", columnNames = "name"))
public class KeywordState extends BaseTimeEntity {
    @Column(nullable = false, length = 50) private String name;

    protected KeywordState() {}
    public KeywordState(String name) { this.name = name; }
    public String getName() { return name; }
}
