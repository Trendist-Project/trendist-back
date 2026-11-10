package com.trendist.monster.entity;

import com.trendist.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 몬스터 종류(4.35). 보스몹이 첫 번째이고 앞으로 다른 몬스터를 같은 표에 더한다.
 * kind는 분류(BOSS 등), behavior는 서버 코드(Monster 구현)가 행동을 찾는 키, image는 프론트가 그리는 그림 키다.
 * 이름은 화면 문구라 한국어·영어를 모두 둔다. 쓰는 값(주기·체력·속도·패턴·보상)은 monster_settings에 둔다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "monster_types", uniqueConstraints = @UniqueConstraint(name = "uk_monster_types_code", columnNames = "code"))
public class MonsterType extends BaseTimeEntity {
    @Column(nullable = false, length = 30) private String code;
    @Column(nullable = false, length = 50) private String name;
    @Column(name = "name_en", length = 50) private String nameEn;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false, length = 30) private String behavior;
    @Column(length = 60) private String image;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "sort_order", nullable = false) private long sortOrder;

    public MonsterType(String code, String name, String nameEn, String kind, String behavior, String image, long sortOrder) {
        this.code = code; this.name = name; this.nameEn = nameEn; this.kind = kind; this.behavior = behavior; this.image = image; this.sortOrder = sortOrder;
    }

    public void changeEnabled(boolean enabled) { this.enabled = enabled; }
    /** 이름·그림·순서는 코드(시더)가 정한다 */
    public void sync(String name, String nameEn, String kind, String behavior, String image, long sortOrder) {
        this.name = name; this.nameEn = nameEn; this.kind = kind; this.behavior = behavior; this.image = image; this.sortOrder = sortOrder;
    }
}
