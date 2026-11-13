package com.trendist.game.room;

import java.util.List;
import java.util.Map;

/**
 * 방이 보내는 짧은 메시지(4.2~4.36)를 만드는 한 곳. 클라이언트 `game/protocol.ts`의 ServerMessage와 필드 이름·타입이 짝이다:
 * 여기서 이름을 바꾸면 그 파일도 같이 바꾼다. 스킬 메시지는 SkillFx, 키워드 잃기 알림은 LossMessages가 만든다.
 * 필드가 많은 메시지(콤보·체력·보스·쿨타임·장착 등)는 그 값을 아는 메서드가 직접 만든다(같은 패키지 안에서 한 군데씩이다).
 */
final class RoomMessages {
    private RoomMessages() {}

    /** 들어올 때 보여 줄 최근 채팅 */
    static Map<String, Object> chatHistory(List<Map<String, Object>> messages) { return Map.of("type", "chatHistory", "messages", List.copyOf(messages)); }
    static Map<String, Object> chat(long id, int e, String nickname, String text, long at) {
        return Map.of("type", "chat", "id", id, "e", e, "nickname", nickname, "text", text, "at", at);
    }
    static Map<String, Object> emote(int e, String code) { return Map.of("type", "emote", "e", e, "code", code); }

    /** 내가 상대(target)를 쓰러뜨렸다(4.31) */
    static Map<String, Object> kill(String target) { return Map.of("type", "kill", "target", target); }
    /** 레벨업 빛기둥(4.30) */
    static Map<String, Object> levelFx(int e, int level) { return Map.of("type", "levelFx", "e", e, "level", level); }

    /** 무적 보호막과 카운트다운(4.3): 원 e에 ms 동안 */
    static Map<String, Object> shield(int e, long ms) { return Map.of("type", "shield", "e", e, "ms", ms); }
    /** 튕김(4.3): 본인 예측이 같이 밀려나게 */
    static Map<String, Object> knock(double dx, double dy, long ms) { return Map.of("type", "knock", "dx", dx, "dy", dy, "ms", ms); }

    /** 체력 피해(4.36): 원 e가 by(보스면 -1)에게 dmg%를 잃어 pct%가 남았다 */
    static Map<String, Object> hpHit(int e, int by, double dmg, int pct) { return Map.of("type", "hpHit", "e", e, "by", by, "dmg", dmg, "pct", pct); }
    /** 모두의 체력([원 번호, %] 목록, hp_public일 때) */
    static Map<String, Object> hps(List<?> list) { return Map.of("type", "hps", "list", list); }

    /** ? 구슬(prize면 ! 구슬)이 생겼다(4.20) */
    static Map<String, Object> orbSpawn(int id, double x, double y, boolean prize) {
        return prize ? Map.of("type", "orbSpawn", "id", id, "x", x, "y", y, "prize", true) : Map.of("type", "orbSpawn", "id", id, "x", x, "y", y);
    }
    /** 구슬을 원 e가 먹었다(e가 0이면 영역 밖으로 사라졌다) */
    static Map<String, Object> orbEaten(int id, int e) { return Map.of("type", "orbEaten", "id", id, "e", e); }
    /** 입장 때 구슬 전체 */
    static Map<String, Object> orbs(List<?> list) { return Map.of("type", "orbs", "orbs", list); }
    /** 세이프존(4.22): 가운데 존 한 변과 무작위 존 목록 */
    static Map<String, Object> safeZones(double center, List<?> zones) { return Map.of("type", "safeZones", "center", center, "zones", zones); }

    /** 대시 시작(4.27): 본인에게 지속·쿨타임·배수, 모두에게 강조 효과 */
    static Map<String, Object> dash(long ms, long cooldownMs, double mult) { return Map.of("type", "dash", "ms", ms, "cooldownMs", cooldownMs, "mult", mult); }
    static Map<String, Object> dashFx(int e, long ms) { return Map.of("type", "dashFx", "e", e, "ms", ms); }

    /** 방의 명단(이름표·꾸미기)과 불꽃 기준 콤보 */
    static Map<String, Object> roster(List<?> players, int flameAt) { return Map.of("type", "roster", "players", players, "flameAt", flameAt); }
}
