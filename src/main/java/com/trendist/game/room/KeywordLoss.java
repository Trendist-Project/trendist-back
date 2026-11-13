package com.trendist.game.room;

/**
 * 키워드 잃기 한 건(4.3, 4.25, 4.33, 4.36). 방이 사람의 키워드를 잃게 하는 모든 경우를 이 하나로 표현해 Outbox.requestLoss로 넘기고,
 * 서비스(RoomManagerImpl)는 원인(Cause)이 정한 방식대로 DB에서 잃게 한 뒤 같은 모양의 lost·hit 메시지로 알린다.
 * victim이 봇이면 방이 이미 메모리에서 뺐고(botKeywordId), 이긴 사람에게 그 키워드를 주는 일만 남는다.
 *
 * @param victim       잃는 원(사람, 또는 키워드를 빼앗긴 봇)
 * @param by           이긴·맞힌 원(없으면 null: 나갔거나 독을 건 사람이 사라짐)
 * @param amount       DROP이면 잃는 개수, PERCENT면 잃는 비율(%), STEAL이면 1
 * @param botKeywordId victim이 봇일 때 봇이 잃은 키워드 번호(아니면 0)
 */
public record KeywordLoss(Cause cause, Room.Member victim, Room.Member by, int amount, long botKeywordId) {

    /** 잃는 방식 */
    public enum Way {
        /** 키워드 하나를 이긴 쪽이 빼앗는다(이긴 쪽이 봇이면 봇의 메모리로 간다) */
        STEAL,
        /** amount개를 무작위로 잃고 아무도 가져가지 않는다 */
        DROP,
        /** 가진 키워드의 amount%(내림, 최소 1개)를 잃고 일부는 ! 구슬로 떨어진다 */
        PERCENT
    }

    /**
     * 잃은 원인. code는 클라이언트 lost·hit 메시지의 cause 값이다(protocol.ts의 LossCause와 짝).
     * serviceCombo면 결과(실제로 잃었는지)가 DB에서 정해지므로 서비스가 끝난 뒤 방에 싸움 결과(콤보)를 넣는다. 아니면 방이 이미 처리했다(스킬 히트·죽음).
     * countsSteal이면 이긴 사람의 빼앗은 횟수를 +1 한다(STEAL은 빼앗기 자체가 센다).
     */
    public enum Cause {
        FIGHT("fight", Way.STEAL, true, false),
        FLAME("flame", Way.PERCENT, true, true),
        DEATH("death", Way.PERCENT, false, false),
        HOOK("hook", Way.STEAL, false, false),
        ABSORB("absorb", Way.STEAL, false, false),
        POISON("poison", Way.DROP, false, false),
        QUAKE("quake", Way.DROP, false, false),
        FIREBALL("fireball", Way.DROP, false, false);

        public final String code;
        public final Way way;
        public final boolean serviceCombo, countsSteal;

        Cause(String code, Way way, boolean serviceCombo, boolean countsSteal) {
            this.code = code; this.way = way; this.serviceCombo = serviceCombo; this.countsSteal = countsSteal;
        }

        /** 스킬 코드(갈고리 hook·흡수 absorb·독 poison·충격파 quake·화염구 fireball)의 원인 */
        public static Cause ofSkill(String skillCode) {
            for (Cause c : values()) if (c.code.equals(skillCode) && c != FIGHT && c != FLAME && c != DEATH) return c;
            throw new IllegalArgumentException("not an attack skill: " + skillCode);
        }
    }

    public Way way() { return cause.way; }

    /** 몸싸움에서 져서 하나를 빼앗긴다 */
    public static KeywordLoss fight(Room.Member loser, Room.Member winner) { return new KeywordLoss(Cause.FIGHT, loser, winner, 1, 0); }
    /** 불꽃에게 부딪혀 percent%를 잃는다 */
    public static KeywordLoss flame(Room.Member loser, Room.Member winner, int percent) { return new KeywordLoss(Cause.FLAME, loser, winner, percent, 0); }
    /** 체력이 0이 되어 죽으며 percent%를 잃는다 */
    public static KeywordLoss death(Room.Member victim, Room.Member killer, int percent) { return new KeywordLoss(Cause.DEATH, victim, killer, percent, 0); }
    /** 공격 스킬의 히트(Room.skillHit): STEAL 원인이면 1개, DROP이면 count개 */
    public static KeywordLoss skill(Cause cause, Room.Member victim, Room.Member attacker, int count) { return new KeywordLoss(cause, victim, attacker, count, 0); }
    /** 봇이 (방 메모리에서) 키워드 keywordId를 잃었고 사람 winner가 그것을 가져간다 */
    public static KeywordLoss fromBot(Cause cause, Room.Member bot, Room.Member winner, long keywordId) { return new KeywordLoss(cause, bot, winner, 1, keywordId); }
}
