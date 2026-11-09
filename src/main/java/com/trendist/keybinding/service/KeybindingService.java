package com.trendist.keybinding.service;

import com.trendist.keybinding.dto.KeybindingDtos;
import java.util.List;

/**
 * 플레이어의 단축키(4.33). 동작마다 지금 쓰는 키 = 플레이어가 바꾼 연결(key_bindings)이 있으면 그것(null이면 비움), 없으면 actions.default_key.
 * 저장은 서버가 검증한다: 알려진 동작과 쓸 수 있는 키만, 막은 키 제외, 같은 키(숫자패드 짝 포함)를 두 동작에 쓰면 거절.
 */
public interface KeybindingService {
    /** 모든 동작의 지금 키(동작 목록 순서). 바꾸지 않은 것은 기본 키로 채워진다 */
    List<KeybindingDtos.Binding> effective(long playerId);

    /** 쓸 수 있는(사용 여부가 켜진) 키 목록 */
    List<KeybindingDtos.KeyInfo> keys();

    /** 동작의 키를 바꾼다(key null = 비움). 한 번에 여러 개를 바꾸고, 서로 키를 맞바꾸는 것도 된다. 기본 키와 같아지면 행을 두지 않는다 */
    List<KeybindingDtos.Binding> update(long playerId, List<KeybindingDtos.Change> changes);

    /** 기본 키로 되돌린다. actionCode가 null이면 전부 */
    List<KeybindingDtos.Binding> reset(long playerId, String actionCode);
}
