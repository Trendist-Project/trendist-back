package com.trendist.game.service;

import com.trendist.game.event.GameEvents;
import com.trendist.game.room.Room;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.service.KeywordService;
import com.trendist.player.service.LevelService;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/** 나라별 방을 만들고 틱을 돌린다. 서비스 이벤트를 방에 전달한다(9.2). 방 관리는 지금 서버 1대 기준이다. */
@Component
public class RoomManagerImpl implements RoomManager, Room.Outbox {
    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledExecutorService> tickers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tuningRefresher = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "room-tuning"));
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor(); // DB·외부 호출은 틱 밖에서
    private final SettingService settings;
    private final KeywordService keywordService;
    private final LevelService levels;
    private final ObjectMapper json;

    public RoomManagerImpl(SettingService settings, KeywordService keywordService, LevelService levels, ObjectMapper json) {
        this.settings = settings;
        this.levels = levels;
        this.keywordService = keywordService;
        this.json = json;
        tuningRefresher.scheduleAtFixedRate(() -> {
            try {
                Room.Tuning t = tuning();
                Room.ZoneTuning z = zoneTuning();
                rooms.values().forEach(r -> {
                    if (!t.equals(r.tuning())) { r.setTuning(t); r.submit(rr -> broadcastTuning(rr)); }
                    if (!z.equals(r.zoneTuning())) r.setZoneTuning(z);
                });
            } catch (RuntimeException e) { log.warn("tuning refresh failed", e); }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public Room room(long countryId) {
        return rooms.computeIfAbsent(countryId, id -> {
            Room r = new Room(id, tuning(), this);
            r.setZoneTuning(zoneTuning());
            r.setLevelForExp(levels::levelFor); // 봇 레벨도 사람과 같은 레벨표(levels)로 정한다
            ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(
                    run -> { Thread t = new Thread(run, "room-" + id); t.setDaemon(true); return t; }); // 틱은 플랫폼 스레드
            ex.scheduleAtFixedRate(() -> {
                try { r.tick(); } catch (Throwable e) { log.error("tick failed", e); }
            }, Room.TICK_MS, Room.TICK_MS, TimeUnit.MILLISECONDS);
            tickers.put(id, ex);
            return r;
        });
    }

    @Override
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
                settings.getDouble(SettingKeys.KNOCKBACK_DISTANCE),
                settings.getDouble(SettingKeys.COLLISION_COOLDOWN),
                settings.getDouble(SettingKeys.LOSE_PROTECT_SECONDS),
                settings.getDouble(SettingKeys.JOIN_PROTECT_SECONDS),
                settings.getInt(SettingKeys.ORB_MAX),
                settings.getDouble(SettingKeys.ORB_SPAWN_SECONDS),
                settings.getDouble(SettingKeys.ORB_DENSITY),
                settings.getDouble(SettingKeys.ORB_SPAWN_PERCENT),
                settings.getDouble(SettingKeys.ORB_ACTIVE_PERCENT),
                settings.getInt(SettingKeys.BOT_PLAYERS),
                settings.getDouble(SettingKeys.BOT_CAP_RATIO),
                settings.getDouble(SettingKeys.BOT_STEP_MIN),
                settings.getDouble(SettingKeys.BOT_STEP_MAX),
                settings.getDouble(SettingKeys.EXP_SCORE_CAP));
    }

    /** 세이프존 설정(4.22). */
    private Room.ZoneTuning zoneTuning() {
        return new Room.ZoneTuning(
                settings.getDouble(SettingKeys.SAFE_CENTER_RATIO),
                settings.getDouble(SettingKeys.SAFE_CENTER_MIN),
                settings.getDouble(SettingKeys.SAFE_ZONE_RATIO),
                settings.getDouble(SettingKeys.SAFE_ZONE_MIN),
                settings.getDouble(SettingKeys.SAFE_ZONE_DENSITY),
                settings.getInt(SettingKeys.SAFE_ZONE_MAX),
                settings.getDouble(SettingKeys.SAFE_ZONE_SECONDS));
    }

    /** 클라이언트 예측에 필요한 값(9.2 설정값 전달). */
    @Override
    public Map<String, Object> tuningMessage(Room.Tuning t) {
        return Map.of("type", "tuning", "maxSpeed", t.maxSpeed(), "minRadius", t.minRadius(), "maxRadius", t.maxRadius(),
                "scoreCap", t.scoreCap(), "zoomFactor", t.zoomFactor(), "worldSize", t.worldSize());
    }

    private void broadcastTuning(Room r) {
        Map<String, Object> msg = tuningMessage(r.tuning());
        r.members().forEach(m -> sendText(m, msg));
    }

    /** 접속 중인 플레이어 점수(랭킹 실시간 보정용). */
    @Override
    public Double liveScore(long countryId, long playerId) {
        Room r = rooms.get(countryId);
        if (r == null) return null;
        Room.Live live = r.liveView().get(playerId);
        return live == null ? null : live.score();
    }

    @Override
    public java.util.Optional<Room.Live> livePlayerByEntity(long countryId, int entityNo) {
        return livePlayers(countryId).stream().filter(l -> l.entityNo() == entityNo).findFirst();
    }

    @Override
    public Collection<Room.Live> livePlayers(long countryId) {
        Room r = rooms.get(countryId);
        return r == null ? List.of() : r.liveView().values();
    }

    // ------------------------------------------------------------ 서비스 이벤트 → 방

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.PlayerScoreChanged e) {
        Room r = rooms.get(e.countryId());
        if (r == null) return;
        r.submit(room -> {
            room.applyScore(e.playerId(), e.score(), Math.toIntExact(e.level())); // 방 메모리의 레벨은 int(1~100)
            Room.Member m = room.byPlayer(e.playerId());
            if (m != null) sendText(m, Map.of("type", "score", "score", e.score(), "exp", e.exp(), "level", e.level(),
                    "holdCount", e.holdCount()));
        });
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.KeywordTrendChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyKeywordDelta(e.keywordId(), e.deltaScore(), e.excludePlayerId()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.NicknameChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyNickname(e.playerId(), e.nickname()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.TitleChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyTitle(e.playerId(), e.title()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.LookChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyLook(e.playerId(), e.background(), e.borderColor(), e.borderWidth()));
    }

    /** 새 칭호를 받았다(4.10). 접속 중이면 그 사람에게만 알린다. */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.TitleGranted e) {
        Room r = rooms.get(e.countryId());
        if (r == null) return;
        r.submit(room -> {
            Room.Member m = room.byPlayer(e.playerId());
            if (m != null) sendText(m, Map.of("type", "titleGranted", "name", e.name(), "description", e.description()));
        });
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
        String winnerName = winner.nickname, loserName = loser.nickname; // 봇(이름 Bot)에게 져도 키워드를 잃는다(4.21)
        Long winnerId = winner.bot ? null : winner.playerId;
        workers.submit(() -> {
            try {
                if (winnerId == null) {
                    // 봇에게 지면 잃기만 한다(봇은 실제 키워드를 갖지 않는다, 4.21)
                    keywordService.loseRandom(loserId).ifPresent(lost -> room.submit(r -> {
                        Room.Member m = r.byPlayer(loserId);
                        if (m != null) sendText(m, Map.of("type", "lost", "by", winnerName, "keyword", lost.text(), "score", lost.score()));
                    }));
                    return;
                }
                // 사람끼리면 이긴 사람이 그 키워드를 빼앗는다. 양쪽 모두에게 어떤 키워드인지와 점수를, 이긴 사람에게는
                // 새로 들어갔는지(ADDED)·남은 시간만 갱신됐는지(RENEWED)도 보여 준다(4.3)
                keywordService.stealRandom(loserId, winnerId).ifPresent(st -> room.submit(r -> {
                    Room.Member m = r.byPlayer(loserId);
                    if (m != null) sendText(m, Map.of("type", "lost", "by", winnerName, "keyword", st.text(), "score", st.score(), "stolen", true));
                    Room.Member w = r.byPlayer(winnerId);
                    if (w != null) sendText(w, Map.of("type", "hit", "target", loserName, "keyword", st.text(), "score", st.score(),
                            "stolen", st.result(), "gained", st.gained()));
                }));
            } catch (RuntimeException e) {
                log.warn("lose keyword failed for player {}", loserId, e);
            }
        });
    }

    @Override
    public void requestBotWin(Room room, Room.Member winner, Room.Member bot) {
        long playerId = winner.playerId;
        String botName = bot.nickname;
        workers.submit(() -> {
            try {
                KeywordDtos.OrbResult r = keywordService.gainFromBot(playerId);
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "hit");
                msg.put("target", botName);
                if ("GAINED".equals(r.status()) || "RENEWED".equals(r.status())) {
                    // BOT_RENEWED: 이미 가진 키워드라 남은 시간만 24시간으로 늘렸다
                    msg.put("keyword", r.text()); msg.put("score", r.trendValue()); msg.put("gained", r.gained());
                    msg.put("stolen", "RENEWED".equals(r.status()) ? "BOT_RENEWED" : "BOT");
                } else {
                    msg.put("score", 0); // 🔥 키워드가 없다
                }
                room.submit(rr -> {
                    Room.Member m = rr.byPlayer(playerId);
                    if (m != null) sendText(m, msg);
                });
            } catch (RuntimeException e) {
                log.warn("bot win reward failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void requestOrb(Room room, Room.Member eater) {
        long playerId = eater.playerId;
        workers.submit(() -> {
            try {
                KeywordDtos.OrbResult r = keywordService.openOrb(playerId);
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "orbResult");
                msg.put("status", r.status());
                if (r.text() != null) {
                    msg.put("text", r.text()); msg.put("state", r.state()); msg.put("trendValue", r.trendValue());
                    msg.put("holderCount", r.holderCount()); msg.put("gained", r.gained()); msg.put("expiresAt", String.valueOf(r.expiresAt()));
                }
                room.submit(rr -> {
                    Room.Member m = rr.byPlayer(playerId);
                    if (m != null) sendText(m, msg);
                });
            } catch (RuntimeException e) {
                log.warn("orb reward failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void send(Room.Member m, ByteBuffer binary) {
        if (m.bot) return; // 봇은 연결이 없다
        WebSocketSession s = (WebSocketSession) m.session;
        if (!s.isOpen()) return;
        try { s.sendMessage(new BinaryMessage(binary)); } catch (Exception e) { /* 느린 연결: 데코레이터가 버린다 */ }
    }

    @Override
    public void close(Room.Member m) {
        if (m.bot || !(m.session instanceof WebSocketSession s)) return;
        try { if (s.isOpen()) s.close(CloseStatus.GOING_AWAY.withReason("SILENT")); } catch (Exception ignored) { /* 이미 닫힘 */ }
    }

    @Override
    public void sendText(Room.Member m, Map<String, Object> message) {
        if (m.bot) return; // 봇은 연결이 없다
        WebSocketSession s = (WebSocketSession) m.session;
        if (!s.isOpen()) return;
        try { s.sendMessage(new TextMessage(json.writeValueAsString(message))); } catch (Exception e) { /* 연결 종료 */ }
    }

    @Override
    public void submitWork(Runnable r) { workers.submit(r); }

    @PreDestroy
    public void shutdown() {
        tickers.values().forEach(ExecutorService::shutdownNow);
        tuningRefresher.shutdownNow();
        workers.shutdownNow();
    }
}
