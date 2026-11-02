package com.trendist.profile.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.effect.entity.EffectType;
import com.trendist.effect.support.EffectRules;
import com.trendist.game.event.GameEvents;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.profile.dto.ProfileDtos;
import com.trendist.profile.entity.Profile;
import com.trendist.profile.repository.ProfileRepository;
import com.trendist.profile.support.PixelArt;
import com.trendist.profile.support.ProfileLooks;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {
    private static final Pattern HEX = Pattern.compile("^#[0-9a-f]{6}$");
    /** 아레나 바닥색(4.2). 이 색과 대비가 너무 낮은 바탕색·글자색은 고를 수 없다 */
    private static final String FLOOR = "#1f2a37";
    private static final double MIN_CONTRAST = 1.6;
    private static final int MIN_BORDER = 1, MAX_BORDER = 8;
    /** 프리셋 24색(4.9). 모두 바닥과 대비가 충분한 색이다. 코드로 관리한다(13.4) */
    static final List<String> PRESETS = List.of(
            "#3fb68b", "#2ecc71", "#a6e04a", "#f1c40f", "#ffb020", "#ff8c42", "#ff6b6b", "#e5484d",
            "#ff7eb6", "#d16ba5", "#b388ff", "#8e7cff", "#5b8def", "#4f8cff", "#38bdf8", "#22d3ee",
            "#2dd4bf", "#94a3b8", "#cbd5e1", "#ffffff", "#f5e6c8", "#c8a27a", "#a3a380", "#ffd6a5");
    /** 이름표 모양: RIBBON(테두리 색 리본 바탕), GLOW(글자 둘레 빛). null이면 기본(검은 외곽선) */
    static final List<String> NAME_STYLES = List.of("RIBBON", "GLOW");

    private final ProfileRepository profiles;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final ProfileLooks looks;
    private final EffectRules effects;
    private final SettingService settings;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** 그린 PNG. 주소에 바꾼 시각이 붙어 같은 키의 그림은 바뀌지 않는다 */
    private final Cache<String, byte[]> pngs = Caffeine.newBuilder().maximumSize(5_000).build();

    @Override
    @Transactional(readOnly = true)
    public ProfileDtos.Box box(long userId) {
        return box(playerService.requireCurrent(userId));
    }

    private ProfileDtos.Box box(Player p) {
        int unlock = settings.getInt(SettingKeys.CUSTOMIZE_UNLOCK_LEVEL);
        Instant now = Instant.now(clock);
        List<Profile> mine = profiles.findByPlayerIdOrderBySlotAsc(p.getId());
        int equippedSlot = looks.equipped(p.getId()).map(x -> Math.toIntExact(x.getSlot())).orElse(1);
        List<ProfileDtos.Slot> slots = new ArrayList<>();
        for (int s = 1; s <= Profile.SLOTS; s++) {
            int n = s;
            Profile pr = mine.stream().filter(x -> x.getSlot() == n).findFirst().orElse(null);
            slots.add(pr == null
                    ? new ProfileDtos.Slot(s, false, s == equippedSlot, Profile.GROWTH, ProfileLooks.DEFAULT, null, null, null, null)
                    : new ProfileDtos.Slot(s, true, s == equippedSlot, pr.getProfileType(), looks.look(p, pr),
                            pr.hasArt() ? art(pr) : null, pr.getTrailEffectId(), pr.getBorderEffectId(), pr.getUpdatedAt()));
        }
        String lock = p.getUser().isGuest() ? "Google로 로그인하면 그림을 그릴 수 있어요"
                : p.getUser().isUploadBanned(now) ? "그림 저장이 정지된 계정이에요" : null;
        Instant changeableAt = nextArtChange(mine);
        return new ProfileDtos.Box(slots, equippedSlot, p.getLevel() >= unlock, unlock, ProfileLooks.DEFAULT, PRESETS, NAME_STYLES,
                lock == null, lock, changeableAt != null && now.isBefore(changeableAt) ? changeableAt : null, PixelArt.SIZE, PixelArt.MAX_COLORS,
                effects.options(p, EffectType.TRAIL), effects.options(p, EffectType.BORDER));
    }

    @Override
    @Transactional
    public ProfileDtos.Box save(long userId, int slot, ProfileDtos.SaveRequest req) {
        checkSlot(slot);
        Player p = requireUnlocked(userId);
        Instant now = Instant.now(clock);
        List<Profile> mine = profiles.findByPlayerIdOrderBySlotAsc(p.getId());
        Profile pr = mine.stream().filter(x -> x.getSlot() == slot).findFirst().orElse(null);

        String bg = color(req.background(), "바탕색");
        if (bg == null) throw ApiException.badRequest("COLOR_FORMAT", "바탕색을 골라 주세요");
        String border = color(req.borderColor(), "테두리 색");
        Integer width = req.borderWidth();
        String name = color(req.nameColor(), "이름표 글자색");
        String style = req.nameStyle() == null || req.nameStyle().isBlank() ? null : req.nameStyle().toUpperCase(Locale.ROOT);
        String inner = req.inner() == null || req.inner().isBlank() ? Profile.GROWTH : req.inner().toUpperCase(Locale.ROOT);
        PixelArt.Valid art = req.art() == null ? null : PixelArt.validate(req.art().palette(), req.art().pixels());
        boolean artChanged = art != null && (pr == null || !art.pixels().equals(pr.getPixelData()) || !art.palette().equals(pr.getPixelPalette()));
        if (contrast(bg, FLOOR) < MIN_CONTRAST) throw ApiException.badRequest("COLOR_CONTRAST", "바닥과 비슷해서 잘 안 보이는 색이에요. 더 밝은 색을 골라 주세요");
        if (width != null && (width < MIN_BORDER || width > MAX_BORDER)) {
            throw ApiException.badRequest("BORDER_WIDTH", "테두리 두께는 " + MIN_BORDER + "~" + MAX_BORDER + "단계예요");
        }
        if (name != null && contrast(name, FLOOR) < MIN_CONTRAST) throw ApiException.badRequest("NAME_CONTRAST", "바닥과 비슷해서 잘 안 보이는 글자색이에요");
        if (style != null && !NAME_STYLES.contains(style)) throw ApiException.badRequest("NAME_STYLE", "없는 이름표 모양이에요");
        if (!inner.equals(Profile.GROWTH) && !inner.equals(Profile.PIXEL)) throw ApiException.badRequest("PROFILE_TYPE", "GROWTH 또는 PIXEL이어야 해요");
        if ((artChanged || inner.equals(Profile.PIXEL)) && p.getUser().isGuest()) {
            throw ApiException.forbiddenCode("ART_LOGIN", "그림은 Google로 로그인한 사용자만 쓸 수 있어요");
        }
        if (artChanged) {
            if (p.getUser().isUploadBanned(now)) throw ApiException.forbiddenCode("ART_BANNED", "그림 저장이 정지된 계정이에요");
            Instant next = nextArtChange(mine); // 서버를 다시 켜도 유지되게 DB의 바꾼 시각으로 확인한다(4.9)
            if (next != null && now.isBefore(next)) {
                long minutes = Math.max(1, (Duration.between(now, next).getSeconds() + 59) / 60);
                throw ApiException.conflict("ART_TOO_SOON", "그림은 " + minutes + "분 뒤에 다시 바꿀 수 있어요");
            }
        }
        if (inner.equals(Profile.PIXEL) && art == null && (pr == null || !pr.hasArt())) {
            throw ApiException.badRequest("ART_NONE", "먼저 그림을 그려 주세요");
        }
        // 효과는 로그인 사용자만(EffectRules). 이미 고른 효과는 그대로 둘 수 있다
        Long trail = Objects.equals(req.trailEffectId(), pr == null ? null : pr.getTrailEffectId())
                ? req.trailEffectId() : effects.check(p, EffectType.TRAIL, req.trailEffectId());
        Long borderEffect = Objects.equals(req.borderEffectId(), pr == null ? null : pr.getBorderEffectId())
                ? req.borderEffectId() : effects.check(p, EffectType.BORDER, req.borderEffectId());
        lookRate(p);

        boolean first = mine.isEmpty();
        if (pr == null) pr = profiles.save(new Profile(p.getId(), slot, bg));
        pr.changeLook(bg, border, width == null ? null : width.longValue(), name, style, inner);
        if (artChanged) pr.changeArt(art.palette(), art.pixels(), now);
        pr.changeEffects(trail, borderEffect);
        if (Boolean.TRUE.equals(req.equip())) {
            for (Profile o : mine) o.setEquipped(o.getSlot() == slot);
            pr.setEquipped(true);
        } else if (first && slot == 1) {
            pr.setEquipped(true); // 아무것도 저장하지 않았으면 1번 칸이 장착된 것으로 보던 그대로
        }
        profiles.flush();
        if (looks.equipped(p.getId()).map(x -> x.getSlot() == slot).orElse(slot == 1)) publish(p);
        return box(p);
    }

    @Override
    @Transactional
    public ProfileDtos.Box equip(long userId, int slot) {
        checkSlot(slot);
        Player p = requireUnlocked(userId);
        lookRate(p);
        List<Profile> mine = profiles.findByPlayerIdOrderBySlotAsc(p.getId());
        Profile target = mine.stream().filter(x -> x.getSlot() == slot).findFirst()
                .orElseGet(() -> profiles.save(new Profile(p.getId(), slot, ProfileLooks.DEFAULT_BACKGROUND))); // 빈 칸은 기본 모습
        for (Profile pr : mine) pr.setEquipped(pr.getSlot() == slot);
        target.setEquipped(true);
        profiles.flush();
        publish(p);
        return box(p);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDtos.Look lookOf(long playerId) {
        return players.findById(playerId).map(looks::equippedLook).orElse(ProfileLooks.DEFAULT);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> artPng(long profileId) {
        return profiles.findById(profileId).filter(Profile::hasArt).map(pr ->
                pngs.get(pr.getId() + ":" + pr.getImageChangedAt().toEpochMilli(), k -> PixelArt.png(pr.getPixelPalette(), pr.getPixelData())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileDtos.Art> equippedArt(long playerId) {
        return looks.equipped(playerId).filter(Profile::showsArt).map(ProfileServiceImpl::art);
    }

    @Override
    @Transactional
    public void adminReset(long playerId) {
        Player p = players.findById(playerId).orElseThrow(() -> ApiException.notFound("NO_PLAYER", "플레이어가 없어요"));
        profiles.deleteAll(profiles.findByPlayerIdOrderBySlotAsc(playerId));
        profiles.flush();
        publish(p);
    }

    /** 다음에 그림을 바꿀 수 있는 시각: 플레이어의 모든 칸 중 가장 최근에 그림을 바꾼 때 + 설정 간격. 그림이 없으면 null. */
    private Instant nextArtChange(List<Profile> mine) {
        return mine.stream().map(Profile::getImageChangedAt).filter(Objects::nonNull).max(Instant::compareTo)
                .map(t -> t.plus(Duration.ofMinutes(settings.getInt(SettingKeys.ART_CHANGE_MINUTES)))).orElse(null);
    }

    /** 꾸미기(프로필 저장·장착, 효과 포함)는 해금 레벨(기본 11)부터 */
    private Player requireUnlocked(long userId) {
        Player p = playerService.requireCurrent(userId);
        int unlock = settings.getInt(SettingKeys.CUSTOMIZE_UNLOCK_LEVEL);
        if (p.getLevel() < unlock) throw ApiException.forbiddenCode("CUSTOMIZE_LOCKED", unlock + "레벨부터 꾸밀 수 있어요");
        return p;
    }

    private static void checkSlot(int slot) {
        if (slot < 1 || slot > Profile.SLOTS) throw ApiException.badRequest("PROFILE_SLOT", "프로필은 1~" + Profile.SLOTS + "번이에요");
    }

    private void lookRate(Player p) {
        if (!rateLimiter.tryAcquire("look:" + p.getId(), 1, Duration.ofSeconds(5))) {
            throw ApiException.tooMany("LOOK_TOO_FAST", "꾸미기는 5초에 한 번 바꿀 수 있어요");
        }
    }

    /** 접속 중인 방의 원과 이동 효과를 바로 바꾼다. */
    private void publish(Player p) {
        ProfileDtos.Look look = looks.equippedLook(p);
        events.publishEvent(new GameEvents.LookChanged(p.getCountry().getId(), p.getId(), look.toSkin()));
        events.publishEvent(new GameEvents.TrailChanged(p.getCountry().getId(), p.getId(), look.trailEffect()));
    }

    private static ProfileDtos.Art art(Profile pr) {
        return new ProfileDtos.Art(PixelArt.paletteList(pr.getPixelPalette()), pr.getPixelData());
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
