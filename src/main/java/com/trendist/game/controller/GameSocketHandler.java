package com.trendist.game.controller;

import com.trendist.auth.security.AccountGuard;
import com.trendist.auth.service.JwtService;
import com.trendist.chat.service.ChatService;
import com.trendist.common.exception.ApiException;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.keyword.service.KeywordService;
import com.trendist.player.service.PlayerService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 게임 WebSocket(9.2, 9.3). 토큰은 주소에 담지 않고 연결 직후 첫 메시지로 받는다. 5초 안에 인증하지 못하면 끊는다.
 * 클라이언트 → 서버(JSON): auth, input{dx,dy}, move{x,y}, ping{t}, chat{text}, bye(페이지를 떠남)
 * 서버 → 클라이언트: 바이너리 스냅샷·미니맵, JSON(welcome, tuning, roster, score, lost, pong, chat, chatHistory, chatError, safeZones)
 */
@Component
@RequiredArgsConstructor
public class GameSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(GameSocketHandler.class);
    private static final String ATTR_MEMBER = "member";
    private static final String ATTR_ROOM = "room";
    private static final String ATTR_SAFE = "safe";
    private static final String ATTR_INPUTS = "inputs";

    private final JwtService jwt;
    private final AccountGuard guard;
    private final PlayerService playerService;
    private final KeywordService keywordService;
    private final ChatService chatService;
    private final RoomManager rooms;
    private final ObjectMapper json;
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ws-auth-timeout"));

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 느린 클라이언트: 송신 버퍼가 차면 오래된 메시지를 버린다(9.2)
        var safe = new ConcurrentWebSocketSessionDecorator(session, 2000, 256 * 1024,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        session.getAttributes().put(ATTR_SAFE, safe);
        session.getAttributes().put(ATTR_INPUTS, new ConcurrentHashMap<String, Bucket>()); // 메시지 종류 → 토큰 버킷
        timeouts.schedule(() -> {
            if (session.isOpen() && !session.getAttributes().containsKey(ATTR_MEMBER)) close(session, CloseStatus.POLICY_VIOLATION);
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode node;
        try { node = json.readTree(message.getPayload()); } catch (Exception e) { return; }
        String type = node.path("type").asString("");
        Room.Member member = (Room.Member) session.getAttributes().get(ATTR_MEMBER);
        if (member == null) {
            if ("auth".equals(type)) authenticate(session, node.path("token").asString(""));
            return;
        }
        member.lastHeardNanos = System.nanoTime(); // 살아 있다는 표시(응답 없는 접속 정리에 쓴다)
        if ("bye".equals(type)) {
            // 페이지를 떠난다(구글 로그인으로 이동, 새로고침, 탭 닫기): 닫힘 신호를 기다리지 않고 바로 방에서 뺀다
            Room room = (Room) session.getAttributes().get(ATTR_ROOM);
            if (room != null) room.submit(r -> r.leave(member));
            close(session, CloseStatus.NORMAL);
            return;
        }
        if ("ping".equals(type)) {
            // 왕복 시간 측정용. 클라이언트가 보낸 시각을 그대로 돌려준다. 이동 명령 빈도 제한에는 넣지 않는다
            double t = node.path("t").asDouble(0);
            rooms.sendText(member, Map.of("type", "pong", "t", Double.isFinite(t) ? t : 0));
            return;
        }
        if ("chat".equals(type)) { chat(session, member, node.path("text").asString("")); return; }
        if (!allowInput(session, type)) return;
        long receivedNanos = System.nanoTime(); // 방은 이 시점부터 조작을 적용한다(틱 경계로 반올림하지 않는다)
        Room room = (Room) session.getAttributes().get(ATTR_ROOM);
        switch (type) {
            case "input" -> {
                double dx = clamp(node.path("dx").asDouble(0)), dy = clamp(node.path("dy").asDouble(0));
                room.submit(r -> r.setInput(member, dx, dy, receivedNanos));
            }
            case "move" -> {
                double x = node.path("x").asDouble(0), y = node.path("y").asDouble(0);
                if (Double.isFinite(x) && Double.isFinite(y)) room.submit(r -> r.setTarget(member, x, y, receivedNanos));
            }
            default -> { }
        }
    }

    private void authenticate(WebSocketSession session, String token) {
        Optional<Long> uid = jwt.verify(token).filter(guard::accepts).map(JwtService.Verified::userId);
        if (uid.isEmpty()) { close(session, CloseStatus.POLICY_VIOLATION.withReason("AUTH_FAILED")); return; }
        rooms.submitWork(() -> {
            try {
                Optional<PlayerService.JoinInfo> info = playerService.joinInfo(uid.get());
                if (info.isEmpty()) { close(session, CloseStatus.POLICY_VIOLATION.withReason("NO_PLAYER")); return; }
                PlayerService.JoinInfo player = info.get();
                List<Long> keywordIds = keywordService.activeKeywordIds(player.playerId());
                Room room = rooms.room(player.countryId());
                WebSocketSession safe = (WebSocketSession) session.getAttributes().get(ATTR_SAFE);
                room.submit(r -> {
                    if (!session.isOpen()) return;
                    Room.Member m = r.join(player.playerId(), safe, player.nickname(), player.level(), player.score(), keywordIds, player.firstTime());
                    session.getAttributes().put(ATTR_ROOM, r);
                    session.getAttributes().put(ATTR_MEMBER, m);
                    rooms.sendText(m, Map.of("type", "welcome", "entityNo", m.entityNo, "playerId", player.playerId(),
                            "x", m.x, "y", m.y, "score", m.score, "level", m.level));
                    rooms.sendText(m, rooms.tuningMessage(r.tuning()));
                });
            } catch (RuntimeException e) {
                log.warn("ws join failed", e);
                close(session, CloseStatus.SERVER_ERROR);
            }
        });
    }

    /** 채팅(4.13): 1초에 1회, 검사를 통과하면 저장한 뒤 방 전체에 보낸다. 거절하면 보낸 사람에게만 이유를 알린다. */
    private void chat(WebSocketSession session, Room.Member member, String raw) {
        if (!allowInput(session, "chat")) {
            rooms.sendText(member, Map.of("type", "chatError", "code", "CHAT_TOO_FAST", "message", "채팅은 1초에 한 번 보낼 수 있어요"));
            return;
        }
        String text;
        try {
            text = chatService.validate(raw);
        } catch (ApiException e) {
            rooms.sendText(member, Map.of("type", "chatError", "code", e.code(), "message", e.getMessage()));
            return;
        }
        Room room = (Room) session.getAttributes().get(ATTR_ROOM);
        rooms.submitWork(() -> {
            try {
                ChatService.Saved saved = chatService.save(room.countryId(), member.playerId, text);
                room.submit(r -> r.chat(member, saved.id(), text, saved.atMillis()));
            } catch (RuntimeException e) {
                log.warn("chat save failed", e);
                rooms.sendText(member, Map.of("type", "chatError", "code", "CHAT_FAILED", "message", "메시지를 보내지 못했어요"));
            }
        });
    }

    /**
     * 이동 명령 빈도 제한(9.5). 종류별 토큰 버킷: 초당 rate개씩 채워지고 최대 burst개까지 모인다. 버킷이 비면 무시한다.
     * 클라이언트는 클릭(move)을 초당 10회, 방향키(input)를 누르고 뗄 때마다 바로 보낸다. 고정 1초 창으로 세면 네트워크·프록시가
     * 메시지를 몰아서 전달할 때 한도를 넘겨 그 창이 끝날 때까지 모든 명령이 버려졌다(화면은 갔다가 되돌아옴).
     * 버킷은 잠깐 몰려 도착해도 받아 주고, 평균 빈도만 제한한다.
     */
    private boolean allowInput(WebSocketSession session, String type) {
        String key = "move".equals(type) || "input".equals(type) || "chat".equals(type) ? type : "other"; // 모르는 종류는 한 칸으로 묶는다
        double rate = switch (key) { case "move" -> 12; case "input" -> 60; case "chat" -> 1; default -> 10; };
        double burst = switch (key) { case "move" -> 24; case "input" -> 60; case "chat" -> 1; default -> 10; };
        @SuppressWarnings("unchecked")
        Map<String, Bucket> buckets = (Map<String, Bucket>) session.getAttributes().get(ATTR_INPUTS);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(burst, System.nanoTime()));
        synchronized (bucket) {
            return bucket.take(System.nanoTime(), rate, burst);
        }
    }

    /** 토큰 버킷 하나: 남은 토큰과 마지막으로 채운 시각. */
    static final class Bucket {
        private double tokens;
        private long filledAt;

        Bucket(double tokens, long nowNanos) { this.tokens = tokens; this.filledAt = nowNanos; }

        /** 지난 시간만큼 채우고, 하나를 쓸 수 있으면 쓰고 true. */
        boolean take(long nowNanos, double rate, double burst) {
            tokens = Math.min(burst, tokens + (nowNanos - filledAt) / 1e9 * rate);
            filledAt = nowNanos;
            if (tokens < 1) return false;
            tokens -= 1;
            return true;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Room room = (Room) session.getAttributes().get(ATTR_ROOM);
        Room.Member m = (Room.Member) session.getAttributes().get(ATTR_MEMBER);
        if (room != null && m != null) room.submit(r -> r.leave(m));
    }

    private static double clamp(double v) { return Double.isFinite(v) ? Math.max(-1, Math.min(1, v)) : 0; }

    private static void close(WebSocketSession s, CloseStatus status) {
        try { if (s.isOpen()) s.close(status); } catch (Exception ignored) { }
    }
}
