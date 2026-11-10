package com.trendist.audit.service;

import com.trendist.audit.entity.ConfigChange;
import com.trendist.audit.repository.ConfigChangeRepository;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 설정을 바꾸는 쪽(설정·몬스터·스킬 서비스)이 부른다(13.17). 호출한 쪽의 트랜잭션 안에서 한 줄을 남기므로 값 변경과 이력이 함께 저장되거나 함께 취소된다.
 * 값이 그대로면 남기지 않는다. 관리자 이름은 그 계정의 플레이어 닉네임(없으면 번호)을 그때 적어 둔다.
 */
@Component
@RequiredArgsConstructor
public class ConfigChangeRecorder {
    public static final String SETTING = "SETTING", MONSTER = "MONSTER", SKILL = "SKILL";

    private final ConfigChangeRepository repository;
    private final PlayerRepository players;

    public void record(String scope, String target, String field, String oldValue, String newValue, long adminUserId, Long revertedFromId) {
        if (Objects.equals(oldValue, newValue)) return;
        String name = players.findByUserId(adminUserId).stream().map(Player::getNickname).findFirst().orElse("#" + adminUserId);
        repository.save(new ConfigChange(scope, target, field, oldValue, newValue, adminUserId, name, revertedFromId));
    }

    /** 숫자는 불필요한 0 없이(2.0 → 2) */
    public static String num(BigDecimal v) { return v == null ? null : v.stripTrailingZeros().toPlainString(); }
    public static String num(double v) { return num(BigDecimal.valueOf(v)); }
}
