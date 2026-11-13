package com.trendist.game.room;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 스킬 등록표(4.33): 코드 → 스킬. 방마다 하나씩 만든다(날아가는 투사체 같은 상태를 스킬이 들고 있다).
 * 틱마다 update를 이 순서대로 부르므로 순서를 바꾸면 같은 틱에 일어난 일의 처리 순서가 바뀐다.
 */
final class PlayerSkills {
    private final Map<String, PlayerSkill> byCode = new LinkedHashMap<>();
    final HookSkill hook;
    private final Map<String, BoltSkill> bolts = new LinkedHashMap<>();

    PlayerSkills(Room room) {
        hook = new HookSkill(room);
        add(new WaveSkill(room, "repel", false));     // 반발
        add(new WaveSkill(room, "quake", true));      // 충격파
        add(hook);
        for (String kind : new String[]{"poison", "darkness", "slow", "stun"}) { BoltSkill b = new BoltSkill(room, kind); bolts.put(kind, b); add(b); }
        add(new FireballSkill(room));
        add(new AbsorbSkill(room));
        add(new InstantSkills.Shield(room));
        add(new InstantSkills.Stealth(room));
        add(new InstantSkills.Cleanse(room));
        add(new InstantSkills.Heal(room));
        if (!byCode.keySet().equals(new java.util.HashSet<>(Room.SKILLS)))
            throw new IllegalStateException("Room.SKILLS and PlayerSkills differ: " + Room.SKILLS + " vs " + byCode.keySet());
    }

    private void add(PlayerSkill s) { byCode.put(s.code(), s); }

    PlayerSkill get(String code) { return byCode.get(code); }

    Collection<PlayerSkill> all() { return byCode.values(); }

    void update(double dt) { for (PlayerSkill s : byCode.values()) s.update(dt); }

    /** 지금 날아가는 투사체 수(모든 종류) */
    int flyingBolts() { return bolts.values().stream().mapToInt(BoltSkill::flying).sum(); }
}
