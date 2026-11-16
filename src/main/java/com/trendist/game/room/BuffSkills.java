package com.trendist.game.room;

/**
 * 나에게 거는 스킬(4.33): 공격 증폭·피해 감소(버프), 보호막(체력 위에 쌓이는 쉴드), 광폭화(켜고 끄는 스킬).
 * 상태는 Room.Member에 있고(amplifyUntil·fortifyUntil·barrier…·berserk), 끝나는 때와 광폭화의 초당 체력 소모는 Room.updateBuffs가 센다.
 * 피해 계산은 Room.modifyDamage(증폭·감소)와 Room.absorb(쉴드 먼저)가 맡는다.
 */
final class BuffSkills {
    private BuffSkills() {}

    /** 공격 증폭: amplify_seconds 동안 내가 주는 체력 피해가 amplify_multiplier배. 다른 스킬과 함께 쓴다. 다시 쓰면 남은 시간을 새로 센다 */
    record Amplify(Room room) implements PlayerSkill {
        public String code() { return "amplify"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            double seconds = room.skillConfig().param("amplify_seconds");
            m.amplifyUntil = room.now() + seconds;
            room.broadcast(SkillFx.buff("amplify", m.entityNo, SkillFx.ms(seconds)));
            return true;
        }
    }

    /** 피해 감소: fortify_seconds 동안 내가 받는 체력 피해가 fortify_percent%(기본 절반). 다른 스킬과 함께 쓴다. 다시 쓰면 남은 시간을 새로 센다 */
    record Fortify(Room room) implements PlayerSkill {
        public String code() { return "fortify"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            double seconds = room.skillConfig().param("fortify_seconds");
            m.fortifyUntil = room.now() + seconds;
            room.broadcast(SkillFx.buff("fortify", m.entityNo, SkillFx.ms(seconds)));
            return true;
        }
    }

    /**
     * 보호막: barrier_seconds 동안 최대 체력의 barrier_hp_percent%(절대값)만큼의 쉴드가 체력 위에 쌓인다(체력이 가득이어도 쌓인다). 피해는 쉴드가 먼저 받는다.
     * 다시 쓰면 쉴드를 새로 채운다(겹쳐 쌓지 않는다). 체력 방식이 꺼져 있으면 쓸 곳이 없어 쓰지 않는다.
     */
    record Barrier(Room room) implements PlayerSkill {
        public String code() { return "barrier"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            if (!room.hpTuning().enabled()) return false;
            SkillConfig c = room.skillConfig();
            double seconds = c.param("barrier_seconds"), pct = c.param("barrier_hp_percent");
            m.barrier = Math.round(room.hpTuning().max() * pct) / 100.0;
            m.barrierMax = pct;
            m.barrierUntil = room.now() + seconds;
            room.broadcast(SkillFx.barrier(m.entityNo, SkillFx.ms(seconds), pct, pct));
            return true;
        }
    }

    /**
     * 광폭화(켜고 끄는 스킬): 켜 두면 1초마다 최대 체력의 berserk_drain_percent%가 닳고, 잃은 체력 비율만큼 유행도(충돌·피해 판정에만, 표시값은 그대로)와
     * 이동 속도가 오른다(체력 70% → 둘 다 +30%). 체력이 berserk_min_hp_percent% 아래로 내려가기 전에 스스로 꺼진다. 다시 누르면 꺼진다.
     */
    record Berserk(Room room) implements PlayerSkill {
        public String code() { return "berserk"; }
        public boolean cast(Room.Member m, double ax, double ay) {
            if (m.berserk) { room.berserkOff(m); return true; }
            Room.HpTuning hp = room.hpTuning();
            double min = hp.max() * room.skillConfig().param("berserk_min_hp_percent") / 100.0;
            if (!hp.enabled() || m.hp <= min) { room.tell(m, SkillFx.fail(code(), "hpLow")); return false; }
            m.berserk = true;
            m.berserkNextAt = room.now() + 1.0;
            room.broadcast(SkillFx.berserk(m.entityNo, room.lostPercent(m)));
            return true;
        }
    }
}
