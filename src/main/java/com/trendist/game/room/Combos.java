package com.trendist.game.room;

import java.util.HashMap;
import java.util.Map;

/**
 * 콤보 규칙(4.25)의 한 곳. 콤보는 **스킬 히트와 몸싸움 튕김 두 가지로만** 바뀌고, 키워드를 잃고 빼앗는 일은 콤보를 건드리지 않는다.
 * - 스킬 히트(hit): 맞힌 쪽 +1, 맞은 쪽 0
 * - 몸싸움(knock): 튕겨 낸 쪽 +1, 튕겨 나간 쪽 0 / 서로 같이 튕기면(bounce) 둘 다 0
 * - 죽음·재접속·유지시간(expire)은 콤보를 끝낸다
 * 상태(Member.combo·comboStartedAt)와 설정(Room.comboTuning)은 Room이 들고 있고, 이 클래스는 올리고 끊는 규칙과 방 전체 알림만 맡는다.
 */
final class Combos {
    /** 같은 두 원의 튕김 콤보는 이 시간(초)에 한 번만 센다(겹친 채 곧바로 다시 닿아도 몇 번씩 올리지 않게) */
    private static final double KNOCK_COOLDOWN = 1.0;

    private final Room room;
    private final Map<Long, Double> knockAt = new HashMap<>();

    Combos(Room room) { this.room = room; }

    /** 불꽃이 붙을 콤보 수에 닿았는가 */
    boolean flame(Room.Member m) { return m.combo >= room.comboTuning().flameAt(); }

    /** 스킬 히트 하나: 맞힌 쪽 +1(맞힌 사람이 방에 없으면 올리지 않는다), 맞은 쪽 0 */
    void hit(Room.Member attacker, Room.Member victim) {
        lose(victim, attacker);
        if (attacker != null && room.present(attacker)) win(attacker, victim.bot);
    }

    /** 충돌에서 상대를 튕겨 낸 쪽(pusher)은 +1, 튕겨 나간 쪽(pushed)은 0. 같은 두 원은 1초에 한 번만 센다 */
    void knock(Room.Member pusher, Room.Member pushed, long pairKey) {
        double now = room.clock();
        Double last = knockAt.get(pairKey);
        if (last != null && now - last < KNOCK_COOLDOWN) return;
        knockAt.put(pairKey, now);
        if (knockAt.size() > 512) knockAt.values().removeIf(t -> now - t >= KNOCK_COOLDOWN);
        lose(pushed, pusher);
        win(pusher, pushed.bot);
    }

    /** 서로 같이 튕겨 나갔다: 둘 다 콤보를 잃고 오르는 사람은 없다 */
    void bounce(Room.Member a, Room.Member b) { lose(a, b); lose(b, a); }

    /** 콤보 +1(봇을 이긴 경우는 설정 combo_bot_counts에 따른다). 유지시간은 올 때마다 다시 시작하고 최고 기록은 칭호(4.10)에 알린다 */
    void win(Room.Member winner, boolean vsBot) {
        Room.ComboTuning tuning = room.comboTuning();
        if (winner == null || (vsBot && !tuning.countBots())) return;
        winner.comboStartedAt = room.clock();
        winner.combo++;
        if (!winner.bot && winner.combo > winner.comboRecordSent) {
            winner.comboRecordSent = winner.combo;
            room.outbox().requestCombo(room, winner, winner.combo);
        }
        room.broadcast(message(winner, winner.combo == tuning.flameAt() ? "start" : "up")); // start: 이번에 불꽃이 붙었다
    }

    /** 콤보를 0으로(불꽃이 있었으면 꺼짐) 하고 방 전체에 알린다. by는 끊은 쪽(없어도 된다) */
    void lose(Room.Member loser, Room.Member by) {
        if (loser == null || loser.combo <= 0) return;
        boolean broken = flame(loser);
        loser.combo = 0;
        if (broken && by != null && !by.bot) room.outbox().requestFlameOut(room, by); // 불꽃을 꺼뜨렸다(칭호, 4.10)
        Map<String, Object> msg = message(loser, broken ? "broken" : "reset");
        msg.put("by", by != null ? by.nickname : "Bot");
        if (by != null) msg.put("byE", by.entityNo);
        room.broadcast(msg);
    }

    /** 끊은 쪽 없이 조용히 끝낸다(재접속·몬스터에게 죽음): 모두의 화면에서 지운다 */
    void reset(Room.Member m) {
        if (m.combo <= 0) return;
        m.combo = 0;
        room.broadcast(message(m, "reset"));
    }

    /** 유지시간(combo_max_seconds)이 지난 콤보를 끝낸다(1초마다, 사람·봇 모두). 0이면 제한 없음 */
    void expire() {
        int max = room.comboTuning().maxSeconds();
        if (max <= 0) return;
        for (Room.Member m : room.all()) {
            if (m.combo <= 0 || room.clock() - m.comboStartedAt < max) continue;
            m.combo = 0;
            room.broadcast(message(m, "expired"));
        }
    }

    /** 방 전체에 보내는 콤보 메시지(클라이언트 protocol.ts의 combo와 짝) */
    Map<String, Object> message(Room.Member m, String event) {
        Room.ComboTuning tuning = room.comboTuning();
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "combo"); msg.put("e", m.entityNo); msg.put("nickname", m.nickname);
        msg.put("combo", m.combo); msg.put("event", event); msg.put("flameAt", tuning.flameAt());
        msg.put("maxMs", tuning.maxSeconds() * 1000L); // 콤보가 오른 때부터 사라질 때까지(ms, 0이면 제한 없음). 클라이언트가 남은 시간을 센다(4.25)
        return msg;
    }
}
