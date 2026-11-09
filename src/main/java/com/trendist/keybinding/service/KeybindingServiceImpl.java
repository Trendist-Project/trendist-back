package com.trendist.keybinding.service;

import com.trendist.common.exception.ApiException;
import com.trendist.keybinding.dto.KeybindingDtos;
import com.trendist.keybinding.entity.GameAction;
import com.trendist.keybinding.entity.KeyBinding;
import com.trendist.keybinding.entity.KeyboardKey;
import com.trendist.keybinding.repository.GameActionRepository;
import com.trendist.keybinding.repository.KeyBindingRepository;
import com.trendist.keybinding.repository.KeyboardKeyRepository;
import com.trendist.player.repository.PlayerRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KeybindingServiceImpl implements KeybindingService {
    private final GameActionRepository actions;
    private final KeyboardKeyRepository keyboardKeys;
    private final KeyBindingRepository bindings;
    private final PlayerRepository players;

    /** 동작 → 지금 키(null이면 비움)와 기본 키를 쓰는지 */
    private record Resolved(KeyboardKey key, boolean usingDefault) {}

    private Map<GameAction, Resolved> resolve(long playerId, List<GameAction> acts) {
        Map<Long, KeyBinding> byAction = bindings.findAllByPlayerId(playerId).stream()
                .collect(Collectors.toMap(b -> b.getAction().getId(), b -> b, (a, b) -> a));
        Map<GameAction, Resolved> out = new LinkedHashMap<>();
        for (GameAction a : acts) {
            KeyBinding b = byAction.get(a.getId());
            out.put(a, b == null ? new Resolved(a.getDefaultKey(), true) : new Resolved(b.getKey(), false));
        }
        return out;
    }

    private KeybindingDtos.Binding toDto(GameAction a, Resolved r) {
        return new KeybindingDtos.Binding(a.getCode(), a.getType().getCode(), a.getParam(), r.key() == null ? null : r.key().getCode(), r.usingDefault());
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeybindingDtos.Binding> effective(long playerId) {
        return resolve(playerId, actions.findAllWithTypeAndDefaultKey()).entrySet().stream().map(e -> toDto(e.getKey(), e.getValue())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeybindingDtos.KeyInfo> keys() {
        return keyboardKeys.findAllEnabledWithCategory().stream()
                .map(k -> new KeybindingDtos.KeyInfo(k.getCode(), k.getLabel(), k.getCategory().getCode(), k.isReserved(), k.getAliasOf() == null ? null : k.getAliasOf().getCode()))
                .toList();
    }

    @Override
    @Transactional
    public List<KeybindingDtos.Binding> update(long playerId, List<KeybindingDtos.Change> changes) {
        var player = players.findById(playerId).orElseThrow(() -> ApiException.notFound("NO_PLAYER", "플레이어를 찾을 수 없어요"));
        List<GameAction> acts = actions.findAllWithTypeAndDefaultKey();
        Map<String, GameAction> actionByCode = acts.stream().collect(Collectors.toMap(GameAction::getCode, a -> a));
        Map<String, KeyboardKey> keyByCode = keyboardKeys.findAllEnabledWithCategory().stream().collect(Collectors.toMap(KeyboardKey::getCode, k -> k));

        // 요청 검증: 알려진 동작과 쓸 수 있는 키만, 같은 동작을 두 번 바꾸지 않는다
        Map<GameAction, KeyboardKey> wanted = new LinkedHashMap<>();
        for (KeybindingDtos.Change c : changes) {
            GameAction a = actionByCode.get(c.action());
            if (a == null) throw ApiException.badRequest("UNKNOWN_ACTION", "알 수 없는 동작이에요");
            if (wanted.containsKey(a)) throw ApiException.badRequest("DUPLICATE_ACTION", "같은 동작이 두 번 들어 있어요");
            KeyboardKey k = null;
            if (c.key() != null && !c.key().isBlank()) {
                k = keyByCode.get(c.key());
                if (k == null) throw ApiException.badRequest("INVALID_KEY", "쓸 수 없는 키예요");
                if (k.isReserved()) throw ApiException.badRequest("RESERVED_KEY", "이미 다른 기능이 쓰는 키예요");
            }
            wanted.put(a, k);
        }

        // 바꾼 뒤의 전체 결과에서 같은 키(숫자패드 짝 포함)를 두 동작이 쓰면 거절한다. 아직 바꾸지 않은 동작의 기본 키와 겹치는 경우도 여기서 걸린다
        Map<GameAction, Resolved> now = resolve(playerId, acts);
        Map<String, String> usedBy = new HashMap<>();
        for (GameAction a : acts) {
            KeyboardKey k = wanted.containsKey(a) ? wanted.get(a) : now.get(a).key();
            if (k == null) continue;
            String other = usedBy.put(k.canonicalCode(), a.getCode());
            if (other != null) throw ApiException.conflict("KEY_CONFLICT", "이미 다른 동작이 쓰는 키예요");
        }

        // 저장: 바꾼 동작의 옛 행을 먼저 지우고(서로 키를 맞바꿔도 유일 제약에 걸리지 않게) 기본 키와 다른 것만 새로 넣는다
        bindings.deleteByPlayerIdAndActionIds(playerId, wanted.keySet().stream().map(GameAction::getId).toList());
        for (var e : wanted.entrySet()) {
            GameAction a = e.getKey();
            KeyboardKey k = e.getValue(), def = a.getDefaultKey();
            boolean sameAsDefault = k == null ? def == null : def != null && def.getId().equals(k.getId());
            if (!sameAsDefault) bindings.save(new KeyBinding(player, a, k));
        }
        bindings.flush();
        return effective(playerId);
    }

    @Override
    @Transactional
    public List<KeybindingDtos.Binding> reset(long playerId, String actionCode) {
        if (actionCode == null) {
            bindings.deleteAllByPlayerId(playerId);
        } else {
            GameAction a = actions.findByCode(actionCode).orElseThrow(() -> ApiException.badRequest("UNKNOWN_ACTION", "알 수 없는 동작이에요"));
            bindings.deleteByPlayerIdAndActionIds(playerId, List.of(a.getId()));
        }
        return effective(playerId);
    }
}
