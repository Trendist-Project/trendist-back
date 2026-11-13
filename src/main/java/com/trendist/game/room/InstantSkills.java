package com.trendist.game.room;

/** 바로 끝나는 스킬(4.33): 방패·은신·정화·회복 */
final class InstantSkills {
    private InstantSkills() {}

    /** 방패: 키워드를 잃은 뒤 무적과 같다. 다른 원과 충돌하지 않고 통과하며(4.3) 보호막은 같은 연출을 쓴다 */
    record Shield(Room room) implements PlayerSkill {
        public String code() { return "shield"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            double seconds = room.skillConfig().param("skill_shield_seconds");
            m.protectUntil = Math.max(m.protectUntil, room.now() + seconds);
            room.broadcast(SkillFx.shield(m.entityNo, SkillFx.ms(seconds)));
            return true;
        }
    }

    /** 은신: 남에게는 원 전체가 안 보이고 미니맵에서도 사라진다. 남에게 닿는 스킬을 쓰면 풀린다(Room.breakStealth) */
    record Stealth(Room room) implements PlayerSkill {
        public String code() { return "stealth"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            double seconds = room.skillConfig().param("stealth_seconds");
            m.stealthUntil = room.now() + seconds;
            room.broadcast(SkillFx.stealth(m.entityNo, SkillFx.ms(seconds)));
            return true;
        }
    }

    /**
     * 정화: 걸려 있는 CC(갈고리에 끌려오는 중, 흡수 선, 독·암흑·슬로우·스턴)를 한꺼번에 풀고, cleanse_immune_seconds 동안 새 CC를 막는다(기본 0 = 면역 없음).
     * 끌려오거나 스턴인 중에도 쓴다.
     */
    record Cleanse(Room room) implements PlayerSkill {
        public String code() { return "cleanse"; }
        public boolean usableWhileHeld() { return true; }
        public boolean cast(Room.Member m, double ax, double ay) {
            for (PlayerSkill s : room.playerSkills()) s.release(m);
            room.status().clearAll(m);
            m.ccImmuneUntil = room.now() + room.skillConfig().param("cleanse_immune_seconds");
            room.broadcast(SkillFx.cleanse(m.entityNo));
            return true;
        }
    }

    /** 회복: 최대 체력의 skill_heal_percent를 바로 채운다(세이프존 밖에서도 쓴다). 체력이 가득이거나 체력 방식이 꺼져 있으면 쓰지 않는다 */
    record Heal(Room room) implements PlayerSkill {
        public String code() { return "heal"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            Room.HpTuning hp = room.hpTuning();
            if (!hp.enabled() || m.hp >= hp.max()) { room.tell(m, SkillFx.fail(code(), "hpFull")); return false; }
            room.healPercent(m, room.skillConfig().param("skill_heal_percent"));
            room.broadcast(SkillFx.heal(m.entityNo)); // 다른 사람 화면에도 + 표시
            return true;
        }
    }
}
