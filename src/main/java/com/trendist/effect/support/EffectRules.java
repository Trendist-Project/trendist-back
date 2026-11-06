package com.trendist.effect.support;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.Lang;
import com.trendist.effect.entity.Effect;
import com.trendist.effect.repository.EffectRepository;
import com.trendist.player.entity.Player;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.PlayerTitleRepository;
import com.trendist.title.repository.TitleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 효과 규칙(4.9, 4.23): 플레이어가 고를 수 있는 효과 목록과 잠금 안내, 고른 효과 검사, 방에 보낼 코드.
 * 이동 효과와 테두리 효과가 같은 규칙을 쓴다: 꾸미기 해금 레벨부터, 로그인 사용자만.
 */
@Component
@RequiredArgsConstructor
public class EffectRules {
    private final EffectRepository effects;
    private final PlayerTitleRepository playerTitles;
    private final TitleRepository titles;
    private final com.trendist.setting.service.SettingService settings;

    /** 고를 수 있는 효과 하나: 잠겼으면 lockReason에 얻는 조건 */
    public record Option(long id, String code, String name, boolean unlocked, String lockReason) {}

    public List<Option> options(Player p, String typeCode) { return options(p, typeCode, Lang.KO); }

    /** 이름과 잠금 안내를 그 언어로(영어 이름이 비어 있으면 한국어) */
    public List<Option> options(Player p, String typeCode, Lang lang) {
        return effects.findEnabled(typeCode).stream().map(e -> {
            String lock = lockReason(p, e, lang);
            return new Option(e.getId(), e.getCode(), e.name(lang), lock == null, lock);
        }).toList();
    }

    /** 고른 효과 검사: 없거나 종류가 다르면 404 EFFECT_NOT_FOUND, 잠겼으면 403 EFFECT_LOCKED. null이면 끔. */
    public Long check(Player p, String typeCode, Long effectId) {
        if (effectId == null) return null;
        Effect e = effects.findWithType(effectId).filter(x -> x.isEnabled() && x.isType(typeCode))
                .orElseThrow(() -> ApiException.notFound("EFFECT_NOT_FOUND", "없는 효과예요"));
        String lock = lockReason(p, e, Lang.KO); // 오류 메시지는 코드(EFFECT_LOCKED)로 프론트가 번역한다
        if (lock != null) throw ApiException.forbiddenCode("EFFECT_LOCKED", lock);
        return e.getId();
    }

    /** 방에 보낼 효과 코드. 게스트이거나 끈 효과면 null(그리지 않는다). */
    public String code(Player p, Long effectId) {
        if (effectId == null || p.getUser().isGuest()) return null;
        return effects.findById(effectId).filter(Effect::isEnabled).map(Effect::getCode).orElse(null);
    }

    /**
     * 아직 얻지 못했으면 조건 안내, 얻었으면 null. 효과는 꾸미기의 하나라 꾸미기 해금 레벨(기본 10)부터, Google로 로그인한 사용자만 쓴다.
     * 그 위에 효과마다 조건을 더 붙일 수 있다. 결제(PURCHASE)는 아직 없어서 고를 수 없다.
     */
    private String lockReason(Player p, Effect e, Lang lang) {
        int unlock = settings.getInt(com.trendist.setting.support.SettingKeys.CUSTOMIZE_UNLOCK_LEVEL);
        if (p.getLevel() < unlock) return lang.pick(unlock + "레벨부터 쓸 수 있어요", "Unlocks at level " + unlock);
        if (p.getUser().isGuest()) return lang.pick("Google로 로그인하면 쓸 수 있어요", "Sign in with Google to use this");
        Long v = e.getUnlockValue();
        return switch (e.getUnlockType()) {
            case Effect.FREE -> null;
            case Effect.LEVEL -> v == null || p.getLevel() >= v ? null : lang.pick(v + "레벨부터 쓸 수 있어요", "Unlocks at level " + v);
            case Effect.TITLE -> v == null || playerTitles.existsByPlayerIdAndTitleId(p.getId(), v) ? null
                    : lang.pick("칭호 '" + titles.findById(v).map(Title::getName).orElse("?") + "'을(를) 받으면 쓸 수 있어요",
                            "Earn the title '" + titles.findById(v).map(t -> t.name(lang)).orElse("?") + "' to use this");
            case Effect.PURCHASE -> lang.pick("구매하면 쓸 수 있어요(준비 중)", "Available for purchase (coming soon)");
            default -> lang.pick("아직 쓸 수 없어요", "Not available yet");
        };
    }
}
