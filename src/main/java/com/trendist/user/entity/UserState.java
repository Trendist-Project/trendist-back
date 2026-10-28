package com.trendist.user.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이름만 있는 코드 테이블. 애플리케이션이 이름으로 구분한다(11.1). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_states", uniqueConstraints = @UniqueConstraint(name = "uk_user_states_name", columnNames = "name"))
public class UserState extends BaseTimeEntity {
    @Column(nullable = false, length = 50) private String name;

    public UserState(String name) { this.name = name; }
}
