package com.trendist.effect.service;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.effect.dto.EffectDtos;
import com.trendist.effect.entity.TrailEffect;
import com.trendist.effect.repository.TrailEffectRepository;
import com.trendist.game.event.GameEvents;
import com.trendist.player.entity.Player;
import com.trendist.player.service.PlayerService;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.PlayerTitleRepository;
import com.trendist.title.repository.TitleRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EffectServiceImpl implements EffectService {
    private final TrailEffectRepository effects;
    private final PlayerTitleRepository playerTitles;
    private final TitleRepository titles;
    private final PlayerService playerService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public EffectDtos.Box box(long userId) {
        Player p = playerService.requireCurrent(userId);
        boolean guest = p.getUser().isGuest();
        List<EffectDtos.Item> items = new ArrayList<>();
        for (TrailEffect e : effects.findByEnabledTrueOrderBySortOrderAsc()) {
            String lock = lockReason(p, e, guest);
            items.add(new EffectDtos.Item(e.getId(), e.getCode(), e.getName(), e.getDescription(), e.getUnlockType(), e.getUnlockValue(),
                    lock == null, lock, Objects.equals(p.getTrailEffectId(), e.getId())));
        }
        // 게스트는 골라 둔 효과가 있어도 쓰지 않는 것으로 보여 준다(로그인하면 다시 보인다)
        return new EffectDtos.Box(guest ? null : p.getTrailEffectId(), guest ? items.stream().map(i -> new EffectDtos.Item(i.id(), i.code(),
                i.name(), i.description(), i.unlockType(), i.unlockValue(), i.unlocked(), i.lockReason(), false)).toList() : items);
    }

    @Override
    @Transactional
    public EffectDtos.Box choose(long userId, Long effectId) {
        Player p = playerService.requireCurrent(userId);
        String code = null;
        if (effectId != null) {
            TrailEffect e = effects.findById(effectId).filter(TrailEffect::isEnabled)
                    .orElseThrow(() -> ApiException.notFound("EFFECT_NOT_FOUND", "없는 효과예요"));
            String lock = lockReason(p, e, p.getUser().isGuest());
            if (lock != null) throw ApiException.forbiddenCode("EFFECT_LOCKED", lock);
            code = e.getCode();
        }
        // 거절된 요청은 세지 않는다(검사를 통과한 변경만 5초에 1번)
        if (!rateLimiter.tryAcquire("effect:" + p.getId(), 1, Duration.ofSeconds(5))) {
            throw ApiException.tooMany("EFFECT_TOO_FAST", "효과는 5초에 한 번 바꿀 수 있어요");
        }
        p.changeTrailEffect(effectId);
        events.publishEvent(new GameEvents.TrailChanged(p.getCountry().getId(), p.getId(), code));
        return box(userId);
    }

    /** 아직 얻지 못했으면 조건 안내, 얻었으면 null. 게스트는 모두 잠기고, 결제(PURCHASE)는 아직 없어서 고를 수 없다. */
    private String lockReason(Player p, TrailEffect e, boolean guest) {
        if (guest) return "Google로 로그인하면 쓸 수 있어요";
        Long v = e.getUnlockValue();
        return switch (e.getUnlockType()) {
            case TrailEffect.FREE -> null;
            case TrailEffect.LEVEL -> v == null || p.getLevel() >= v ? null : v + "레벨부터 쓸 수 있어요";
            case TrailEffect.TITLE -> v == null || playerTitles.existsByPlayerIdAndTitleId(p.getId(), v) ? null
                    : "칭호 '" + titles.findById(v).map(Title::getName).orElse("?") + "'을(를) 받으면 쓸 수 있어요";
            case TrailEffect.PURCHASE -> "구매하면 쓸 수 있어요(준비 중)";
            default -> "아직 쓸 수 없어요";
        };
    }
}
