package com.trendist.game.room;

import java.util.HashMap;
import java.util.Map;

/**
 * 키워드를 잃은 사람(lost)과 잃게 한 사람(hit)에게 보내는 알림(4.3, 4.25, 4.33, 4.36)을 만드는 한 곳. 클라이언트 `game/protocol.ts`의
 * `lost`·`hit` 메시지와 필드 이름이 짝이다. cause는 KeywordLoss.Cause.code이고 화면은 이것만 보고 원인(빼앗김·잃음·불꽃·죽음 …)을 고른다.
 */
public final class LossMessages {
    private LossMessages() {}

    /**
     * 내가 잃었다. count개(점수 합계 score)를 잃었고, keyword는 하나만 잃었거나 첫 번째 키워드의 이름(불꽃·죽음은 이름을 보내지 않는다).
     * stolen이면 이긴 쪽이 가져갔다(몸싸움·갈고리·흡수), 아니면 아무도 가져가지 않았다.
     */
    public static Map<String, Object> lost(KeywordLoss.Cause cause, String by, int count, long score, String keyword) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "lost"); m.put("cause", cause.code); m.put("by", by); m.put("count", count); m.put("score", score);
        if (keyword != null) m.put("keyword", keyword);
        if (cause.way == KeywordLoss.Way.STEAL) m.put("stolen", true);
        return m;
    }

    /** 내가 상대(target)의 키워드 count개(점수 합계 score)를 잃게 했다(불꽃·죽음처럼 가져오지 않는 경우) */
    public static Map<String, Object> hit(KeywordLoss.Cause cause, String target, int count, long score) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "hit"); m.put("cause", cause.code); m.put("target", target); m.put("count", count); m.put("score", score);
        return m;
    }

    /**
     * 내가 상대(target)의 키워드 하나를 가져왔다. stolen: ADDED(새로 들어옴)·RENEWED(이미 있어 남은 시간만 갱신)·BOT·BOT_RENEWED(봇에게서),
     * 가져올 키워드가 없었으면(봇의 🔥 키워드가 없음) keyword·stolen 없이 score 0.
     */
    public static Map<String, Object> took(KeywordLoss.Cause cause, String target, String keyword, long score, String stolen, long gained) {
        Map<String, Object> m = hit(cause, target, keyword == null ? 0 : 1, score);
        if (keyword != null) { m.put("keyword", keyword); m.put("stolen", stolen); m.put("gained", gained); }
        return m;
    }
}
