package com.trendist.game.room;

/**
 * 사람이 쓰는 스킬 하나(4.33). 보스 패턴(BossSkill)과 같은 방식으로 스킬마다 클래스 하나가 시전·틱·정화 때 풀기를 맡고,
 * Room은 PlayerSkills의 등록표에서 코드로 찾아 공통 조건(쿨타임·해금·장착·세이프존·끌려옴·스턴)을 본 뒤 cast를 부른다.
 * 새 스킬을 더하려면 클래스를 만들어 PlayerSkills.create에 넣고 Room.SKILLS에 코드를 더한다(쿨타임은 DB skills, 수치는 SkillConfig·settings).
 */
interface PlayerSkill {
    /** 스킬 코드(Room.SKILLS, DB skills.code, 클라이언트와 같은 값) */
    String code();

    /** 남에게 닿는 스킬: 세이프존 안에서는 쓸 수 없고({skillFail: safe}), 쓰면 은신이 풀린다 */
    default boolean offensive() { return false; }

    /** 갈고리에 끌려오거나 스턴인 중에도 쓸 수 있는지(정화만) */
    default boolean usableWhileHeld() { return false; }

    /** 쓴다. 쓰지 못했으면 false(쿨타임을 쓰지 않는다). 실패 이유가 있으면 스스로 본인에게 알린다 */
    boolean cast(Room.Member m, double ax, double ay);

    /** 한 틱(날아가는 것·퍼지는 것·이어진 것). 순서는 PlayerSkills의 등록 순서다 */
    default void update(double dt) {}

    /** 정화: 이 스킬이 target에게 걸어 둔 것을 푼다 */
    default void release(Room.Member target) {}
}
