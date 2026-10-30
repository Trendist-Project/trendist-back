package com.trendist.profile.service;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.game.event.GameEvents;
import com.trendist.player.entity.Player;
import com.trendist.player.service.PlayerService;
import com.trendist.profile.dto.ProfileDtos;
import com.trendist.profile.entity.Profile;
import com.trendist.profile.repository.ProfileRepository;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {
    private static final Pattern HEX = Pattern.compile("^#[0-9a-f]{6}$");
    /** 아레나 바닥색(4.2). 이 색과 대비가 너무 낮은 바탕색은 고를 수 없다 */
    private static final String FLOOR = "#1f2a37";
    private static final double MIN_CONTRAST = 1.6;
    private static final int MIN_BORDER = 1, MAX_BORDER = 8;
    /** 프리셋 24색(4.9). 모두 바닥과 대비가 충분한 색이다. 코드로 관리한다(13.4) */
    static final List<String> PRESETS = List.of(
            "#3fb68b", "#2ecc71", "#a6e04a", "#f1c40f", "#ffb020", "#ff8c42", "#ff6b6b", "#e5484d",
            "#ff7eb6", "#d16ba5", "#b388ff", "#8e7cff", "#5b8def", "#4f8cff", "#38bdf8", "#22d3ee",
            "#2dd4bf", "#94a3b8", "#cbd5e1", "#ffffff", "#f5e6c8", "#c8a27a", "#a3a380", "#ffd6a5");

    private final ProfileRepository profiles;
    private final PlayerService playerService;
    private final SettingService settings;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;

    private static final ProfileDtos.Look DEFAULT_LOOK = new ProfileDtos.Look(DEFAULT_BACKGROUND, null, null);

    @Override
    @Transactional(readOnly = true)
    public ProfileDtos.Mine mine(long userId) {
        Player p = playerService.requireCurrent(userId);
        return mine(p, lookOf(p.getId()));
    }

    private ProfileDtos.Mine mine(Player p, ProfileDtos.Look look) {
        int unlock = settings.getInt(SettingKeys.CUSTOMIZE_UNLOCK_LEVEL);
        return new ProfileDtos.Mine(look, p.getLevel() >= unlock, unlock, DEFAULT_LOOK, PRESETS);
    }

    @Override
    @Transactional
    public ProfileDtos.Mine update(long userId, ProfileDtos.UpdateRequest req) {
        Player p = playerService.requireCurrent(userId);
        int unlock = settings.getInt(SettingKeys.CUSTOMIZE_UNLOCK_LEVEL);
        if (p.getLevel() < unlock) throw ApiException.forbiddenCode("CUSTOMIZE_LOCKED", unlock + "레벨부터 꾸밀 수 있어요");
        String bg = color(req.background(), "바탕색");
        if (bg == null) throw ApiException.badRequest("COLOR_FORMAT", "바탕색을 골라 주세요");
        if (contrast(bg, FLOOR) < MIN_CONTRAST) throw ApiException.badRequest("COLOR_CONTRAST", "바닥과 비슷해서 잘 안 보이는 색이에요. 더 밝은 색을 골라 주세요");
        String border = color(req.borderColor(), "테두리 색");
        Integer width = req.borderWidth();
        if (width != null && (width < MIN_BORDER || width > MAX_BORDER)) {
            throw ApiException.badRequest("BORDER_WIDTH", "테두리 두께는 " + MIN_BORDER + "~" + MAX_BORDER + "단계예요");
        }
        if (!rateLimiter.tryAcquire("look:" + p.getId(), 1, Duration.ofSeconds(5))) {
            throw ApiException.tooMany("LOOK_TOO_FAST", "꾸미기는 5초에 한 번 바꿀 수 있어요");
        }
        Profile prof = profiles.findFirstByPlayerIdAndEquippedTrue(p.getId()).orElseGet(() -> profiles.save(new Profile(p.getId(), bg)));
        prof.changeLook(bg, border, width);
        ProfileDtos.Look look = new ProfileDtos.Look(bg, border, width);
        events.publishEvent(new GameEvents.LookChanged(p.getCountry().getId(), p.getId(), bg, border, width));
        return mine(p, look);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDtos.Look lookOf(long playerId) {
        return profiles.findFirstByPlayerIdAndEquippedTrue(playerId)
                .map(pr -> new ProfileDtos.Look(pr.getBackgroundColor(), pr.getBorderColor(), pr.getBorderWidth()))
                .orElse(DEFAULT_LOOK);
    }

    /** #RRGGBB(대소문자 무시)만 받는다. 비어 있으면 null(기본값). 투명도는 받지 않는다(4.2). */
    private static String color(String raw, String what) {
        if (raw == null || raw.isBlank()) return null;
        String c = raw.trim().toLowerCase(Locale.ROOT);
        if (!HEX.matcher(c).matches()) throw ApiException.badRequest("COLOR_FORMAT", what + "은 #RRGGBB 형식이어야 해요");
        return c;
    }

    /** WCAG 대비율: (밝은 쪽 + 0.05) / (어두운 쪽 + 0.05) */
    static double contrast(String a, String b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
