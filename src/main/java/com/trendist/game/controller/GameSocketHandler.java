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
 * 클라이언트 → 서버(JSON): auth, input{dx,dy}, move{x,y}, ping{t}
 * 서버 → 클라이언트: 바이너리 스냅샷·미니맵, JSON(welcome, tuning, roster, score, lost, pong)
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
    private final RoomManager rooms;
    private final ObjectMapper json;
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ws-auth-timeout"));

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 느린 클라이언트: 송신 버퍼가 차면 오래된 메시지를 버린다(9.2)
        var safe = new ConcurrentWebSocketSessionDecorator(session, 2000, 256 * 1024,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP);
        session.getAttributes().put(ATTR_SAFE, safe);
        session.getAttributes().put(ATTR_INPUTS, new ConcurrentHashMap<String, long[]>()); // 메시지 종류 → [윈도 시작 ms, 횟수]
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
        if ("ping".equals(type)) {
            // 왕복 시간 측정용. 클라이언트가 보낸 시각을 그대로 돌려준다. 이동 명령 빈도 제한에는 넣지 않는다
            double t = node.path("t").asDouble(0);
            rooms.sendText(member, Map.of("type", "pong", "t", Double.isFinite(t) ? t : 0));
            return;
        }
        if (!allowInput(session, type)) return;
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

    /**
     * 이동 명령 빈도 제한(9.5). 종류별로 1초에 받는 횟수를 따로 센다. 넘으면 무시한다.
     * 클라이언트는 클릭(move)을 초당 10회, 방향키(input)를 초당 5회까지 보낸다. 서버는 네트워크에서 간격이 좁혀져
     * 도착하는 경우를 감안해 조금 더 받는다. 마지막 클릭이 버려지면 예측과 서버의 목표가 달라져 화면이 보정되기 때문이다.
     */
    private boolean allowInput(WebSocketSession session, String type) {
        String key = "move".equals(type) || "input".equals(type) ? type : "other"; // 모르는 종류는 한 칸으로 묶는다
        int limit = "move".equals(key) ? 12 : 10;
        @SuppressWarnings("unchecked")
        Map<String, long[]> windows = (Map<String, long[]>) session.getAttributes().get(ATTR_INPUTS);
        long[] w = windows.computeIfAbsent(key, k -> new long[]{0, 0});
        long now = System.currentTimeMillis();
        synchronized (w) {
            if (now - w[0] >= 1000) { w[0] = now; w[1] = 0; }
            return ++w[1] <= limit;
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
