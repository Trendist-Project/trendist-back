package com.trendist.game.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 관전(13.13): 관전자는 원도 플레이어도 아니라서 방에 아무 영향을 주지 않고, 방의 스냅샷·글 메시지만 받는다. */
class RoomSpectatorTest {
    /** 받는 사람(엔티티 번호)별로 보낸 것을 모은다. */
    static class Out implements Room.Outbox {
        final Map<Integer, List<ByteBuffer>> binaries = new HashMap<>();
        final Map<Integer, List<Map<String, Object>>> texts = new HashMap<>();
        final List<Integer> closed = new ArrayList<>();
        public void requestLose(Room room, Room.Member loser, Room.Member winner) { }
        public void requestOrb(Room room, Room.Member eater, boolean prize) { }
        public void requestWipe(Room room, Room.Member loser, Room.Member winner) { }
        public void requestBotWin(Room room, Room.Member winner, Room.Member bot, long keywordId) { }
        public void send(Room.Member m, ByteBuffer b) { binaries.computeIfAbsent(m.entityNo, k -> new ArrayList<>()).add(b); }
        public void sendText(Room.Member m, Map<String, Object> msg) { texts.computeIfAbsent(m.entityNo, k -> new ArrayList<>()).add(msg); }
        public void close(Room.Member m) { closed.add(m.entityNo); }
        List<Map<String, Object>> textsOf(Room.Member m, String type) {
            return texts.getOrDefault(m.entityNo, List.of()).stream().filter(x -> type.equals(x.get("type"))).toList();
        }
    }

    /** 틱을 n번 돌린다. 조작 시계를 같이 움직여야 해서 가짜 시계 없이 그냥 부른다(스냅샷은 2틱마다). */
    private static void ticks(Room r, int n) { for (int i = 0; i < n; i++) r.tick(); }

    @Test
    void spectatorGetsStateAndSnapshotsWithoutBeingAPlayer() {
        Out out = new Out();
        Room r = new Room(1, RoomTest.T, out);
        Room.Member a = r.join(1, "s1", "가", 3, 100, List.of());
        r.join(2, "s2", "나", 2, 50, List.of());
        Room.Member sp = r.addSpectator("watcher");
        assertThat(sp).isNotNull();
        assertThat(sp.entityNo).isNegative(); // 사람·봇의 번호와 겹치지 않는다
        // 들어오는 순간 이름표(roster)·구슬·세이프존·채팅 기록을 받는다
        assertThat(out.textsOf(sp, "roster")).hasSize(1);
        assertThat(out.textsOf(sp, "orbs")).hasSize(1);
        assertThat(out.textsOf(sp, "safeZones")).hasSize(1);
        assertThat(out.textsOf(sp, "chatHistory")).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) out.textsOf(sp, "roster").get(0).get("players");
        assertThat(players).extracting(p -> p.get("nickname")).containsExactlyInAnyOrder("가", "나");

        ticks(r, 4);
        List<ByteBuffer> snaps = out.binaries.get(sp.entityNo).stream().filter(b -> b.get(0) == 1).toList();
        assertThat(snaps).isNotEmpty();
        ByteBuffer snap = ByteBuffer.wrap(snaps.get(snaps.size() - 1).array());
        int n = snap.getShort(9);
        assertThat(n).isEqualTo(2);
        for (int i = 0; i < n; i++) assertThat(snap.get(11 + i * 23 + 22)).isZero(); // 관전자에게는 '나' 표시가 없다
        ticks(r, 16); // 미니맵은 1초(20틱)마다
        assertThat(out.binaries.get(sp.entityNo)).anyMatch(b -> b.get(0) == 2);
        assertThat(a.entityNo).isPositive();
    }

    @Test
    void spectatorChangesNothingInTheRoom() {
        Out out = new Out();
        Room r = new Room(1, RoomTest.T, out);
        r.join(1, "s1", "가", 1, 0, List.of());
        ticks(r, 20); // 틱을 한 번 돌려야 사람 수·랭킹 복사본이 만들어진다
        int size = r.size();
        var live = r.liveView().keySet();
        int members = r.members().size();
        Room.Member sp = r.addSpectator("watcher");
        ticks(r, 20);
        assertThat(r.size()).isEqualTo(size);                      // 접속 인원
        assertThat(r.members()).hasSize(members);                  // 방 크기·활동 영역·충돌 대상
        assertThat(r.liveView().keySet()).isEqualTo(live);         // 랭킹·정보 팝업
        assertThat(r.byEntity(sp.entityNo)).isNull();
        assertThat(r.spectatorCount()).isEqualTo(1);
        r.removeSpectator(sp);
        assertThat(r.spectatorCount()).isZero();
        int before = out.binaries.getOrDefault(sp.entityNo, List.of()).size();
        ticks(r, 6);
        assertThat(out.binaries.getOrDefault(sp.entityNo, List.of())).hasSize(before); // 나간 뒤에는 아무것도 가지 않는다
    }

    @Test
    void chatEmoteAndRosterChangesReachSpectator() {
        Out out = new Out();
        Room r = new Room(1, RoomTest.T, out);
        Room.Member a = r.join(1, "s1", "가", 1, 0, List.of());
        Room.Member sp = r.addSpectator("watcher");
        r.chat(a, 7, "안녕", 1000);
        r.emote(a, "wave");
        r.join(2, "s2", "나", 1, 0, List.of());     // 들어오면 이름표가 다시 간다
        assertThat(out.textsOf(sp, "chat")).hasSize(1);
        assertThat(out.textsOf(sp, "emote")).hasSize(1);
        assertThat(out.textsOf(sp, "roster")).hasSize(2); // 처음 받은 것 + 새 사람
    }

    @Test
    void spectatorLimitAndSilentSpectatorsAreDropped() {
        Out out = new Out();
        long[] clock = {0};
        Room r = new Room(1, RoomTest.T, out, () -> clock[0]);
        for (int i = 0; i < Room.MAX_SPECTATORS; i++) assertThat(r.addSpectator("w" + i)).isNotNull();
        assertThat(r.addSpectator("넘침")).isNull();
        clock[0] += (Room.SILENT_SECONDS + 1) * 1_000_000_000L; // 아무 신호가 없으면 정리한다
        ticks(r, 20);
        assertThat(r.spectatorCount()).isZero();
        assertThat(out.closed).hasSize(Room.MAX_SPECTATORS);
    }
}
