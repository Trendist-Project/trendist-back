package com.trendist.game.room;

import java.util.Map;

/**
 * 보스 패턴 한 가지(4.35). 어떤 보스든 패턴 목록(List&lt;BossSkill&gt;)만 바꿔 끼우면 된다. 보스(SkillBoss)가 쿨타임·공통 대기(GCD)·가중치 선택·예고·경직을 맡고,
 * 패턴은 예고 중과 실행 중에 무엇을 하는지만 안다. 설정값 이름은 패턴 이름(id)을 앞에 붙인다: `<id>_weight`(선택 비중, 0이면 쓰지 않음), `<id>_cooldown`(쿨타임),
 * `<id>_windup`(예고 시간). 패턴이 쓰는 나머지 값(`dash_seconds` 등)은 패턴이 정한다.
 */
abstract class BossSkill {
    /** 패턴 이름이자 설정 이름 앞머리 */
    final String id;
    /** 다음에 쓸 수 있는 시각(쿨타임이 끝나는 때) */
    double readyAt;

    protected BossSkill(String id) { this.id = id; }

    /** 화면·관리자에 보내는 단계 이름: 예고 중 / 실행 중 */
    abstract String windupPhase();
    abstract String activePhase();

    /** 쓸 수 있는 상황인가(쿨타임·가중치 말고 이 패턴만의 조건. 예: 돌진은 대상이 멀리 있을 때만). target은 없을 수 있다 */
    boolean usable(SkillBoss b, Room room, double now, Room.Member target) { return true; }

    /** 예고가 시작됐다 */
    void windupStart(SkillBoss b, Room room, double now, Room.Member target) {}
    /** 예고 중 매 틱(windupEnd는 예고가 끝나는 시각). 보스는 그동안 멈춰 있다 */
    void windupTick(SkillBoss b, Room room, double now, double windupEnd, Room.Member target) {}
    /** 예고가 끝나 실행이 시작됐다 */
    abstract void begin(SkillBoss b, Room room, double now);
    /** 실행 중 매 틱. 끝났으면 true(보스가 경직으로 넘어간다) */
    abstract boolean activeTick(SkillBoss b, Room room, double now, double dt);
    /** 예고 중 화면에 그릴 표시(종류 k와 그리는 데 필요한 값). 없으면 null */
    Map<String, Object> telegraph(SkillBoss b, Room room) { return null; }
    /** 실행 중 위치가 빨리 바뀌어 상태를 매 틱 보내야 하는가 */
    boolean fast() { return false; }
    /** 이 패턴이 실행 중일 때 사람이 보스 몸에 부딪혔다(보스의 기본 충돌 처리 뒤에 불린다) */
    void onBodyContact(SkillBoss b, Room room, Room.Member m, double now) {}
}
