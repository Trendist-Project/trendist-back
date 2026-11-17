package com.trendist.game.room;

/**
 * 투사체 스킬의 종류(4.33). 모두 BoltSkill 하나가 날려 보내고, 맞았을 때 무엇을 하는지만 종류마다 다르다.
 * 종류를 늘리려면 여기에 한 줄을 더하고(코드·맞으면 히트인지) 맞았을 때의 효과를 StatusEffects.applyBolt(상태 효과) 또는 BoltSkill(터지는 총알처럼 따로 할 일)에 더한다.
 * Room.SKILLS·DB skills·프론트 SKILL_DEFS에도 같은 코드가 있어야 한다.
 */
enum BoltKind {
    /** 독: 맞은 순간은 히트가 아니고, 걸린 뒤 틱마다 히트(StatusEffects.tick) */
    POISON("poison", false),
    DARKNESS("darkness", true),
    SLOW("slow", true),
    STUN("stun", true),
    ROOT("root", true),
    SILENCE("silence", true),
    CONFUSE("confuse", true),
    /** 터지는 총알: 맞으면 히트 하나, 도화선이 다 타면 터지면서 히트 하나 더(BoltSkill) */
    BURST("burst", true);

    final String code;
    /** 맞은 순간 스킬 히트 하나(콤보 +1·체력 피해·키워드 1개 잃기, Room.skillHit)를 주는가 */
    final boolean hitsOnImpact;

    BoltKind(String code, boolean hitsOnImpact) { this.code = code; this.hitsOnImpact = hitsOnImpact; }
}
