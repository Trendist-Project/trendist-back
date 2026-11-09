package com.trendist.keybinding.service;

import com.trendist.keybinding.entity.ActionType;
import com.trendist.keybinding.entity.GameAction;
import com.trendist.keybinding.entity.KeyCategory;
import com.trendist.keybinding.entity.KeyboardKey;
import com.trendist.keybinding.repository.ActionTypeRepository;
import com.trendist.keybinding.repository.GameActionRepository;
import com.trendist.keybinding.repository.KeyCategoryRepository;
import com.trendist.keybinding.repository.KeyboardKeyRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 키 바인딩 목록(4.33)을 서버가 켜질 때마다 채운다(DataSeeder가 부른다). 코드가 이름으로 찾는 값이라 없는 행을 만들고, 이미 있는 행은 코드와 맞춘다.
 * 키의 사용 여부(enabled)와 동작의 기본 키는 운영에서 고칠 수 있어 건드리지 않고, 처음 만들 때만 넣는다.
 */
@Component
@RequiredArgsConstructor
public class KeybindingSeeder {
    private final KeyCategoryRepository categories;
    private final KeyboardKeyRepository keyboardKeys;
    private final ActionTypeRepository actionTypes;
    private final GameActionRepository actions;

    /** 이미 다른 기능이 쓰는 키(이동·이모트 E·미니맵 M·스킬창 K·채팅·창 닫기, 브라우저가 쓰는 F5·F11·F12)는 묶을 수 없다 */
    static final Set<String> RESERVED = Set.of("KeyW", "KeyA", "KeyS", "KeyD", "KeyE", "KeyM", "KeyK",
            "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Enter", "Escape", "Tab", "F5", "F11", "F12");

    private static final String[][] CATEGORIES = {{"DIGIT", "숫자"}, {"LETTER", "글자"}, {"FUNCTION", "기능키"}, {"NUMPAD", "숫자패드"}, {"SPECIAL", "기타"}};
    private static final String[][] SPECIAL_KEYS = {
            {"Space", "Space"}, {"Backquote", "`"}, {"Minus", "-"}, {"Equal", "="}, {"BracketLeft", "["}, {"BracketRight", "]"},
            {"Semicolon", ";"}, {"Quote", "'"}, {"Comma", ","}, {"Period", "."}, {"Slash", "/"}, {"Backslash", "\\"},
            {"Enter", "Enter"}, {"Escape", "Esc"}, {"Tab", "Tab"}, {"ArrowUp", "↑"}, {"ArrowDown", "↓"}, {"ArrowLeft", "←"}, {"ArrowRight", "→"}};

    /** 동작(코드, 종류, 칸 번호, 기본 키): 스킬 칸 4개와 대시. 새 기능은 여기에 한 줄을 더한다 */
    private static final Object[][] ACTIONS = {
            {"skill_slot_1", "SKILL_SLOT", 1, "Digit1"}, {"skill_slot_2", "SKILL_SLOT", 2, "Digit2"},
            {"skill_slot_3", "SKILL_SLOT", 3, "Digit3"}, {"skill_slot_4", "SKILL_SLOT", 4, "Digit4"},
            {"dash", "SYSTEM", null, "Space"}};
    private static final String[][] ACTION_TYPES = {{"SKILL_SLOT", "스킬 칸"}, {"SYSTEM", "시스템"}};

    /** 새로 만든 행 수 */
    public int seed() {
        int added = 0;
        Map<String, KeyCategory> cat = new HashMap<>();
        for (String[] c : CATEGORIES) {
            KeyCategory k = categories.findByCode(c[0]).orElse(null);
            if (k == null) { k = categories.save(new KeyCategory(c[0], c[1])); added++; }
            else if (!c[1].equals(k.getName())) { k.changeName(c[1]); categories.save(k); }
            cat.put(c[0], k);
        }
        List<String[]> keys = new ArrayList<>(); // 코드, 표시, 분류, 짝(없으면 null)
        for (int i = 0; i <= 9; i++) keys.add(new String[]{"Digit" + i, String.valueOf(i), "DIGIT", null});
        for (int i = 0; i <= 9; i++) keys.add(new String[]{"Numpad" + i, "Num" + i, "NUMPAD", "Digit" + i});
        for (char c = 'A'; c <= 'Z'; c++) keys.add(new String[]{"Key" + c, String.valueOf(c), "LETTER", null});
        for (int i = 1; i <= 12; i++) keys.add(new String[]{"F" + i, "F" + i, "FUNCTION", null});
        for (String[] s : SPECIAL_KEYS) keys.add(new String[]{s[0], s[1], "SPECIAL", null});
        Map<String, KeyboardKey> byCode = new HashMap<>();
        for (String[] k : keys) { // 짝이 되는 키(Digit)가 먼저 만들어지도록 위 순서를 지킨다
            KeyboardKey alias = k[3] == null ? null : byCode.get(k[3]);
            boolean reserved = RESERVED.contains(k[0]);
            KeyboardKey e = keyboardKeys.findByCode(k[0]).orElse(null);
            if (e == null) { e = keyboardKeys.save(new KeyboardKey(k[0], k[1], cat.get(k[2]), alias, reserved)); added++; }
            else { e.sync(k[1], cat.get(k[2]), alias, reserved); keyboardKeys.save(e); }
            byCode.put(k[0], e);
        }
        Map<String, ActionType> types = new HashMap<>();
        for (String[] t : ACTION_TYPES) {
            ActionType a = actionTypes.findByCode(t[0]).orElse(null);
            if (a == null) { a = actionTypes.save(new ActionType(t[0], t[1])); added++; }
            else if (!t[1].equals(a.getName())) { a.changeName(t[1]); actionTypes.save(a); }
            types.put(t[0], a);
        }
        for (int i = 0; i < ACTIONS.length; i++) {
            Object[] a = ACTIONS[i];
            GameAction e = actions.findByCode((String) a[0]).orElse(null);
            long order = (i + 1) * 10L;
            if (e == null) { actions.save(new GameAction((String) a[0], types.get((String) a[1]), (Integer) a[2], byCode.get((String) a[3]), order)); added++; }
            else if (e.getSortOrder() != order) { e.changeSortOrder(order); actions.save(e); }
        }
        return added;
    }
}
