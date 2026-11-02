package com.trendist.profile.support;

import com.trendist.effect.support.EffectRules;
import com.trendist.player.entity.Player;
import com.trendist.profile.dto.ProfileDtos;
import com.trendist.profile.entity.Profile;
import com.trendist.profile.repository.ProfileRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 장착한 프로필의 모습(4.9): 방 입장·방 알림·관리자 화면이 같이 쓴다. 저장한 칸이 없으면 1번 칸(기본 모습)이다.
 * 게스트는 효과와 그림을 그리지 않는다(로그인 사용자만). 플레이어·꾸미기 서비스가 서로 의존하지 않게 따로 둔다.
 */
@Component
@RequiredArgsConstructor
public class ProfileLooks {
    /** 성장 캐릭터의 정해진 바탕색. 꾸미지 않았으면 이 색이다. */
    public static final String DEFAULT_BACKGROUND = "#3fb68b";
    public static final ProfileDtos.Look DEFAULT = new ProfileDtos.Look(DEFAULT_BACKGROUND, null, null, null, null, null, null, null);

    private final ProfileRepository profiles;
    private final EffectRules effects;

    /** 장착한 칸. 저장한 칸이 하나도 없거나 장착 표시가 없으면 1번 칸(없으면 비어 있음 = 기본 모습). */
    public Optional<Profile> equipped(long playerId) {
        return profiles.findFirstByPlayerIdAndEquippedTrue(playerId).or(() -> profiles.findByPlayerIdAndSlot(playerId, 1));
    }

    public ProfileDtos.Look equippedLook(Player p) {
        return equipped(p.getId()).map(pr -> look(p, pr)).orElse(DEFAULT);
    }

    /** 한 칸의 모습. 그림 주소는 원 안에 그림을 고른 칸만, 효과 코드는 로그인 사용자만. */
    public ProfileDtos.Look look(Player p, Profile pr) {
        boolean guest = p.getUser().isGuest();
        return new ProfileDtos.Look(pr.getBackgroundColor(), pr.getBorderColor(),
                pr.getBorderWidth() == null ? null : Math.toIntExact(pr.getBorderWidth()), // 두께는 1~8단계
                pr.showsArt() && !guest ? pr.artUrl() : null, effects.code(p, pr.getBorderEffectId()),
                pr.getNameColor(), pr.getNameStyle(), effects.code(p, pr.getTrailEffectId()));
    }
}
