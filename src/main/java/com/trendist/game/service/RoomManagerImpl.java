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
    /**
     * 방의 DB 작업(키워드 빼앗기·잃기, 처치 기록, 구슬 보상 등)이 동시에 쓰는 DB 연결 수 상한. 칭호 검사는 커밋 뒤 REQUIRES_NEW로 두 번째 연결을 요구하므로
     * (TitleServiceImpl.on), 연결을 하나씩 쥔 작업이 풀(기본 10개) 이상으로 몰리면 모두가 두 번째 연결을 기다리며 서버가 멈춘다. 봇이 많아 싸움이 잦을 때 그렇게 멈췄다.
     * 넘치는 작업은 가상 스레드에서 차례를 기다린다(방 틱은 막히지 않는다). 작업 하나가 최대 연결 2개를 쓰므로 4개면 풀의 8개 이하이고, HTTP 요청 몫이 남는다.
     */
    private final java.util.concurrent.Semaphore dbSlots = new java.util.concurrent.Semaphore(4);

    private void db(Runnable task) {
        workers.submit(() -> {
            dbSlots.acquireUninterruptibly();
            try { task.run(); } finally { dbSlots.release(); }
        });
    }
    private final SettingService settings;
    private final KeywordService keywordService;
    private final com.trendist.player.service.PlayerService playerService;
    private final LevelService levels;
    private final ObjectMapper json;

    public RoomManagerImpl(SettingService settings, KeywordService keywordService, LevelService levels, ObjectMapper json,
                           @org.springframework.context.annotation.Lazy com.trendist.player.service.PlayerService playerService) {
        this.playerService = playerService;
        this.settings = settings;
        this.levels = levels;
        this.keywordService = keywordService;
        this.json = json;
        tuningRefresher.scheduleAtFixedRate(() -> {
            try {
                Room.Tuning t = tuning();
                Room.ZoneTuning z = zoneTuning();
                Room.ComboTuning c = comboTuning();
                Room.DashTuning dash = dashTuning();
                rooms.values().forEach(r -> {
                    if (!c.equals(r.comboTuning())) r.setComboTuning(c);
                    if (!dash.equals(r.dashTuning())) r.setDashTuning(dash);
                    r.setAreaGrowSpeed(settings.getDouble(SettingKeys.AREA_GROW_SPEED));
                    r.setAreaShrinkSpeed(settings.getDouble(SettingKeys.AREA_SHRINK_SPEED));
                    r.setOfflineKeepSeconds(settings.getInt(SettingKeys.OFFLINE_KEEP_SECONDS));
                    if (!t.equals(r.tuning())) { r.setTuning(t); r.submit(rr -> broadcastTuning(rr)); }
                    if (!z.equals(r.zoneTuning())) r.setZoneTuning(z);
                });
            } catch (RuntimeException e) { log.warn("tuning refresh failed", e); }
        }, 1, 1, TimeUnit.SECONDS);
        // 60초마다 방마다 🔥 키워드 목록을 새로 읽는다(봇의 키워드와 점수 계산, 4.21)
        tuningRefresher.scheduleAtFixedRate(() -> {
            try { rooms.forEach((id, r) -> loadCatalog(id, r, false)); } catch (RuntimeException e) { log.warn("bot catalog refresh failed", e); }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /** 그 나라의 🔥 키워드 목록을 읽어 방에 넣는다. now면 지금 읽고(방을 만들 때), 아니면 작업 스레드에서 읽은 뒤 방 큐에 넣는다. */
    private void loadCatalog(long countryId, Room r, boolean now) {
        Runnable load = () -> {
            try {
                var list = keywordService.botCatalog(countryId).stream().map(k -> new Room.CatalogKw(k.id(), k.text(), k.score())).toList();
                if (now) r.setKeywordCatalog(list); else r.submit(rr -> rr.setKeywordCatalog(list));
            } catch (RuntimeException e) { log.warn("bot catalog load failed for country {}", countryId, e); }
        };
        if (now) load.run(); else db(load);
    }

    @Override
    public Room room(long countryId) {
        return rooms.computeIfAbsent(countryId, id -> {
            Room r = new Room(id, tuning(), this);
            r.setZoneTuning(zoneTuning());
            r.setComboTuning(comboTuning());
            r.setDashTuning(dashTuning());
            r.setAreaGrowSpeed(settings.getDouble(SettingKeys.AREA_GROW_SPEED));
            r.setAreaShrinkSpeed(settings.getDouble(SettingKeys.AREA_SHRINK_SPEED));
            r.setOfflineKeepSeconds(settings.getInt(SettingKeys.OFFLINE_KEEP_SECONDS)); // 연결이 끊겨도 원이 남아 있는 시간(4.32)
            r.setLevelForExp(levels::levelFor); // 봇 레벨도 사람과 같은 레벨표(levels)로 정한다
            loadCatalog(id, r, true); // 봇이 가질 🔥 키워드 목록. 첫 봇이 나오기 전에 읽어 둔다
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
                settings.getDouble(SettingKeys.MAX_DIAMETER) / 2,
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
                settings.getInt(SettingKeys.BOT_MIN_PLAYERS),
                settings.getDouble(SettingKeys.BOT_CAP_RATIO),
                settings.getDouble(SettingKeys.EXP_SCORE_CAP));
    }

    /** 대시 설정(4.27). */
    private Room.DashTuning dashTuning() {
        return new Room.DashTuning(settings.getDouble(SettingKeys.DASH_SPEED_MULTIPLIER), settings.getDouble(SettingKeys.DASH_SECONDS),
                settings.getDouble(SettingKeys.DASH_COOLDOWN_SECONDS));
    }

    /** 연속 빼앗기 콤보 설정(4.25). */
    private Room.ComboTuning comboTuning() {
        return new Room.ComboTuning(settings.getInt(SettingKeys.COMBO_FLAME_AT), settings.getInt(SettingKeys.COMBO_BOT_COUNTS) > 0,
                settings.getInt(SettingKeys.COMBO_WIPE_ORB_PERCENT), settings.getInt(SettingKeys.COMBO_MAX_SECONDS));
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
        r.spectators().forEach(m -> sendText(m, msg));
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
        if (r != null) r.submit(room -> room.applyLook(e.playerId(), e.skin()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.TrailChanged e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyTrail(e.playerId(), e.code()));
    }

    /** 새 칭호를 받았다(4.10). 접속 중이면 그 사람에게만 알린다. */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.TitleGranted e) {
        Room r = rooms.get(e.countryId());
        if (r == null) return;
        r.submit(room -> {
            Room.Member m = room.byPlayer(e.playerId());
            if (m != null) sendText(m, Map.of("type", "titleGranted", "titleId", e.titleId(), "name", e.name(), "description", e.description()));
        });
    }

    /** 긴급 안내가 올라오거나 끝나면(4.29) 모든 나라의 방에 접속 중인 모두에게 바로 알린다. notice가 null이면 걷는다. */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(com.trendist.emergency.event.EmergencyEvents.Changed e) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "emergency");
        msg.put("notice", e.active());
        rooms.values().forEach(r -> r.submit(room -> room.announce(msg)));
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
        db(() -> {
            try {
                if (winnerId == null) {
                    // 봇에게 지면 잃기만 한다(봇은 실제 키워드를 갖지 않는다, 4.21)
                    keywordService.loseRandom(loserId).ifPresent(lost -> room.submit(r -> {
                        r.botTakes(winner, lost.keywordId()); // 이긴 봇이 사람이 잃은 그 키워드를 메모리에 가진다(4.21)
                        r.fightResult(winner, r.byPlayer(loserId), false); // 진 사람 콤보·불꽃은 사라지고, 이긴 봇의 콤보는 +1(4.25)
                        Room.Member m = r.byPlayer(loserId);
                        if (m != null) sendText(m, Map.of("type", "lost", "by", winnerName, "keyword", lost.text(), "score", lost.score()));
                    }));
                    return;
                }
                // 사람끼리면 이긴 사람이 그 키워드를 빼앗는다. 양쪽 모두에게 어떤 키워드인지와 점수를, 이긴 사람에게는
                // 새로 들어갔는지(ADDED)·남은 시간만 갱신됐는지(RENEWED)도 보여 준다(4.3)
                keywordService.stealRandom(loserId, winnerId).ifPresent(st -> room.submit(r -> {
                    r.fightResult(winnerId, loserId, false); // 연속 빼앗기 콤보(4.25)
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
    public void requestWipe(Room room, Room.Member loser, Room.Member winner) {
        long loserId = loser.playerId, winnerId = winner.playerId;
        String winnerName = winner.nickname;
        db(() -> {
            try {
                keywordService.loseAll(loserId).ifPresent(lost -> {
                    if (!winner.bot) playerService.recordSteal(winnerId); // 모두 잃게 해도 공격 성공 한 번 = 빼앗은 횟수 +1(4.3)
                    room.submit(r -> {
                    r.fightResult(winner, r.byPlayer(loserId), false); // 이긴 쪽(사람·봇) 콤보 +1, 진 쪽 콤보·불꽃 없앰(4.25)
                    r.dropPrizeOrbs(loserId, lost.activeCount()); // 잃은 🔥활성 키워드 수의 일부만큼 ! 구슬(4.20)
                    Room.Member m = r.byPlayer(loserId);
                    if (m != null) sendText(m, Map.of("type", "lost", "by", winnerName, "wiped", lost.count(), "score", lost.score()));
                    Room.Member w = r.byPlayer(winnerId); // 이긴 쪽이 봇이면 방 목록에 없고 알릴 연결도 없다
                    if (w != null) sendText(w, Map.of("type", "hit", "target", loser.nickname, "wiped", lost.count(), "score", lost.score()));
                    });
                });
            } catch (RuntimeException e) {
                log.warn("wipe keywords failed for player {}", loserId, e);
            }
        });
    }

    @Override
    public void requestSteal(Room room, Room.Member attacker) {
        long playerId = attacker.playerId;
        db(() -> {
            try {
                playerService.recordSteal(playerId);
            } catch (RuntimeException e) {
                log.warn("record steal failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void requestDefeat(Room room, Room.Member loser) {
        long playerId = loser.playerId;
        db(() -> {
            try {
                playerService.recordDefeat(playerId);
            } catch (RuntimeException e) {
                log.warn("record defeat failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void requestKill(Room room, Room.Member killer) {
        long playerId = killer.playerId;
        db(() -> {
            try {
                playerService.recordKill(playerId);
            } catch (RuntimeException e) {
                log.warn("record kill failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void requestBotWin(Room room, Room.Member winner, Room.Member bot, long keywordId) {
        long playerId = winner.playerId;
        String botName = bot.nickname;
        db(() -> {
            try {
                KeywordDtos.OrbResult r = keywordService.gainFromBot(playerId, keywordId); // 봇이 잃은 그 키워드를 얻는다(4.21)
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
                boolean won = "GAINED".equals(r.status()) || "RENEWED".equals(r.status());
                room.submit(rr -> {
                    if (won) rr.fightResult(playerId, null, true); // 봇을 이겨 키워드를 얻은 것도 콤보(설정, 4.25)
                    Room.Member m = rr.byPlayer(playerId);
                    if (m != null) sendText(m, msg);
                });
            } catch (RuntimeException e) {
                log.warn("bot win reward failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void requestOrb(Room room, Room.Member eater, boolean prize) {
        long playerId = eater.playerId;
        db(() -> {
            try {
                KeywordDtos.OrbResult r = prize ? keywordService.openPrizeOrb(playerId) : keywordService.openOrb(playerId);
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
        if (m.bot || m.offline) return; // 봇은 연결이 없고, 연결이 끊긴 원(4.32)에는 보낼 곳이 없다
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
        if (m.bot || m.offline) return; // 봇은 연결이 없고, 연결이 끊긴 원(4.32)에는 보낼 곳이 없다
        WebSocketSession s = (WebSocketSession) m.session;
        if (!s.isOpen()) return;
        try { s.sendMessage(new TextMessage(json.writeValueAsString(message))); } catch (Exception e) { /* 연결 종료 */ }
    }

    @Override
    public void submitWork(Runnable r) { db(r); }

    @Override
    public void kick(long countryId, long playerId, String message) {
        Room r = rooms.get(countryId);
        if (r == null) return;
        r.submit(room -> {
            Room.Member m = room.byPlayer(playerId);
            if (m == null || m.bot) return;
            sendText(m, Map.of("type", "kicked", "message", message));
            room.leave(m);
            if (m.session instanceof WebSocketSession s) {
                // 클라이언트가 다시 연결하지 않게 따로 표시한 이유로 닫는다
                try { if (s.isOpen()) s.close(new CloseStatus(4001, "KICKED")); } catch (Exception ignored) { /* 이미 닫힘 */ }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        tickers.values().forEach(ExecutorService::shutdownNow);
        tuningRefresher.shutdownNow();
        workers.shutdownNow();
    }
}
