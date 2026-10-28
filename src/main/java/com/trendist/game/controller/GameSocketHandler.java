package com.trendist.game.controller;

import com.trendist.auth.security.AccountGuard;
import com.trendist.auth.service.JwtService;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.keyword.service.KeywordService;
import com.trendist.player.service.PlayerService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
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
 * 클라이언트 → 서버(JSON): auth, input{dx,dy}, move{x,y}, focus{focused}
 * 서버 → 클라이언트: 바이너리 스냅샷, JSON(welcome, tuning, roster, score, lost)
 */
@Component
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
    private final RoomManager rooms;
    private final ObjectMapper json;
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ws-auth-timeout"));

    public GameSocketHandler(JwtService jwt, AccountGuard guard, PlayerService playerService,
                             KeywordService keywordService, RoomManager rooms, ObjectMapper json) {
        this.jwt = jwt; this.guard = guard; this.playerService = playerService;
        this.keywordService = keywordService; this.rooms = rooms; this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 느린 클라이언트: 송신 버퍼가 차면 오래된 메시지를 버린다(9.2)
        var safe = new ConcurrentWebSocketSessionDecorator(session, 2000, 256 * 1024,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        session.getAttributes().put(ATTR_SAFE, safe);
        session.getAttributes().put(ATTR_INPUTS, new long[]{0, 0}); // [윈도 시작 ms, 횟수]
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
        if (!allowInput(session)) return;
        Room room = (Room) session.getAttributes().get(ATTR_ROOM);
        switch (type) {
            case "input" -> {
                double dx = clamp(node.path("dx").asDouble(0)), dy = clamp(node.path("dy").asDouble(0));
                room.submit(r -> r.setInput(member, dx, dy));
            }
            case "move" -> {
                double x = node.path("x").asDouble(0), y = node.path("y").asDouble(0);
                if (Double.isFinite(x) && Double.isFinite(y)) room.submit(r -> r.setTarget(member, x, y));
            }
            case "focus" -> {
                boolean focused = node.path("focused").asBoolean(true);
                room.submit(r -> r.setFocus(member, focused));
            }
            default -> { }
        }
    }

    private void authenticate(WebSocketSession session, String token) {
        Optional<Long> uid = jwt.verify(token).filter(guard::isUsable);
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
                    Room.Member m = r.join(player.playerId(), safe, player.nickname(), player.level(), player.score(), keywordIds);
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

    /** 이동 명령 빈도 제한: 초당 10회(방향키 누름·뗌 포함). 넘으면 무시한다(9.5). */
    private boolean allowInput(WebSocketSession session) {
        long[] w = (long[]) session.getAttributes().get(ATTR_INPUTS);
        long now = System.currentTimeMillis();
        synchronized (w) {
            if (now - w[0] >= 1000) { w[0] = now; w[1] = 0; }
            return ++w[1] <= 10;
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
