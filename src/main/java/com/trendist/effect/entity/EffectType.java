package com.trendist.effect.entity;

import com.trendist.common.entity.BaseTimeEntity;
import com.trendist.common.support.Lang;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 효과 종류(4.9, 4.23): 이동 효과(TRAIL) / 테두리 효과(BORDER). 애플리케이션이 code로 구분한다(11.1). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "effect_types", uniqueConstraints = @UniqueConstraint(name = "uk_effect_types_code", columnNames = "code"))
public class EffectType extends BaseTimeEntity {
    public static final String TRAIL = "TRAIL", BORDER = "BORDER";

    @Column(nullable = false, length = 20) private String code;
    @Column(nullable = false, length = 50) private String name;

    @Column(name = "name_en", length = 50) private String nameEn;

    public String name(Lang lang) { return lang.pick(name, nameEn); }

    /** 영어판 채우기(EnglishBackfill) */
    public void changeNameEn(String nameEn) { this.nameEn = nameEn; }

    public EffectType(String code, String name) { this.code = code; this.name = name; }
}
