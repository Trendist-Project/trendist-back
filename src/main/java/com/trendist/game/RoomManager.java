package com.trendist.game;

import com.trendist.keyword.KeywordService;
import com.trendist.setting.SettingKeys;
import com.trendist.setting.SettingService;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/** 나라별 방을 만들고 틱을 돌린다. 서비스 이벤트를 방에 전달한다(9.2). 방 관리는 지금 서버 1대 기준이다. */
@Component
public class RoomManager implements Room.Outbox {
    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledExecutorService> tickers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tuningRefresher = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "room-tuning"));
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor(); // DB·외부 호출은 틱 밖에서
    private final SettingService settings;
    private final KeywordService keywordService;
    private final ObjectMapper json;

    public RoomManager(SettingService settings, KeywordService keywordService, ObjectMapper json) {
        this.settings = settings;
        this.keywordService = keywordService;
        this.json = json;
        tuningRefresher.scheduleAtFixedRate(() -> {
            try {
                Room.Tuning t = tuning();
                rooms.values().forEach(r -> {
                    if (!t.equals(r.tuning())) { r.setTuning(t); r.submit(rr -> broadcastTuning(rr)); }
                });
            } catch (RuntimeException e) { log.warn("tuning refresh failed", e); }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public Room room(long countryId) {
        return rooms.computeIfAbsent(countryId, id -> {
            Room r = new Room(id, tuning(), this);
            ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(
                    run -> { Thread t = new Thread(run, "room-" + id); t.setDaemon(true); return t; }); // 틱은 플랫폼 스레드
            ex.scheduleAtFixedRate(() -> {
                try { r.tick(); } catch (Throwable e) { log.error("tick failed", e); }
            }, Room.TICK_MS, Room.TICK_MS, TimeUnit.MILLISECONDS);
            tickers.put(id, ex);
            return r;
        });
    }

    public Room.Tuning tuning() {
        double world = settings.getDouble(SettingKeys.WORLD_SIZE);
        return new Room.Tuning(world,
                settings.getDouble(SettingKeys.MIN_RADIUS),
                world * settings.getDouble(SettingKeys.MAX_DIAMETER_RATIO) / 2,
                settings.getDouble(SettingKeys.SCORE_CAP),
                settings.getDouble(SettingKeys.MAX_SPEED),
                settings.getDouble(SettingKeys.ZOOM_FACTOR),
                settings.getDouble(SettingKeys.BIG_RATIO),
                settings.getDouble(SettingKeys.KNOCKBACK_CAP),
                settings.getDouble(SettingKeys.KNOCKBACK_SECONDS),
                settings.getDouble(SettingKeys.COLLISION_COOLDOWN),
                settings.getDouble(SettingKeys.LOSE_PROTECT_SECONDS),
                settings.getDouble(SettingKeys.JOIN_PROTECT_SECONDS));
    }

    /** 클라이언트 예측에 필요한 값(9.2 설정값 전달). */
    public Map<String, Object> tuningMessage(Room.Tuning t) {
        return Map.of("type", "tuning", "maxSpeed", t.maxSpeed(), "minRadius", t.minRadius(), "maxRadius", t.maxRadius(),
                "scoreCap", t.scoreCap(), "zoomFactor", t.zoomFactor(), "worldSize", t.worldSize());
    }

    private void broadcastTuning(Room r) {
        Map<String, Object> msg = tuningMessage(r.tuning());
        r.members().forEach(m -> sendText(m, msg));
    }

    /** 접속 중인 플레이어 점수(랭킹 실시간 보정용). */
    public Double liveScore(long countryId, long playerId) {
        Room r = rooms.get(countryId);
        return r == null ? null : r.scoreView().get(playerId);
    }

    public Map<Long, Double> liveScores(long countryId) {
        Room r = rooms.get(countryId);
        return r == null ? Map.of() : r.scoreView();
    }

    // ------------------------------------------------------------ 서비스 이벤트 → 방

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.PlayerScoreChanged e) {
        Room r = rooms.get(e.countryId());
        if (r == null) return;
        r.submit(room -> {
            room.applyScore(e.playerId(), e.score().doubleValue(), e.level());
            Room.Member m = room.byPlayer(e.playerId());
            if (m != null) sendText(m, Map.of("type", "score", "score", e.score(), "exp", e.exp(), "level", e.level(),
                    "holdCount", e.holdCount()));
        });
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.KeywordTrendChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyKeywordDelta(e.keywordId(), e.deltaScore().doubleValue(), e.excludePlayerId()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.HoldingChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyHolding(e.playerId(), e.keywordId(), e.added()));
    }

    // ------------------------------------------------------------ Room.Outbox

    @Override
    public void requestLose(Room room, Room.Member loser, Room.Member winner) {
        long loserId = loser.playerId;
        String winnerName = winner.nickname;
        workers.submit(() -> {
            try {
                keywordService.loseRandom(loserId).ifPresent(text -> room.submit(r -> {
                    Room.Member m = r.byPlayer(loserId);
                    if (m != null) sendText(m, Map.of("type", "lost", "keyword", text, "by", winnerName));
                }));
            } catch (RuntimeException e) {
                log.warn("lose keyword failed for player {}", loserId, e);
            }
        });
    }

    @Override
    public void send(Room.Member m, ByteBuffer binary) {
        WebSocketSession s = (WebSocketSession) m.session;
        if (!s.isOpen()) return;
        try { s.sendMessage(new BinaryMessage(binary)); } catch (Exception e) { /* 느린 연결: 데코레이터가 버린다 */ }
    }

    @Override
    public void sendText(Room.Member m, Map<String, Object> message) {
        WebSocketSession s = (WebSocketSession) m.session;
        if (!s.isOpen()) return;
        try { s.sendMessage(new TextMessage(json.writeValueAsString(message))); } catch (Exception e) { /* 연결 종료 */ }
    }

    public void submitWork(Runnable r) { workers.submit(r); }

    @PreDestroy
    public void shutdown() {
        tickers.values().forEach(ExecutorService::shutdownNow);
        tuningRefresher.shutdownNow();
        workers.shutdownNow();
    }
}
