package com.trendist.game.room;

/** 끈끈 떡(달토끼, 4.35): 닿은 사람을 느리게 하는 달떡 장판. 동작은 PatchSkill 참고 */
final class MochiSkill extends PatchSkill {
    MochiSkill() { super("mochi", "mochi_slow_seconds", "mochi", (room, m, hold) -> room.status().slowZone(m, hold)); }
}
