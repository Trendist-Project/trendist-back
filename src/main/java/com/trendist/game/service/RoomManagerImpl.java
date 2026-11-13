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
     * 방의 DB 작업(키워드 빼앗기·잃기, 처치 기록, 구슬 보상 등)이 동시에 쓰는 DB 연결 수 상한. 방의 작업이 연결 풀을 다 쥐면
     * HTTP 요청이 연결을 얻지 못하므로 HTTP 몫을 남기려는 제한이다. 칭호 검사는 커밋 뒤 별도 스레드가 하므로(TitleServiceImpl.on)
     * 작업이 두 번째 연결을 요구하지 않아 교착은 생기지 않는다. 넘치는 작업은 가상 스레드에서 차례를 기다린다(방 틱은 막히지 않는다).
     */
    private final java.util.concurrent.Semaphore dbSlots = new java.util.concurrent.Semaphore(4);

    private void db(Runnable task) {
        workers.submit(() -> {
            dbSlots.acquireUninterruptibly();
            try { task.run(); } finally { dbSlots.release(); }
        });
    }
    private final SettingService settings;
    private final com.trendist.skill.service.SkillCatalog skillCatalog;
    private final com.trendist.skill.service.SkillLoadoutService skillLoadouts;
    private final KeywordService keywordService;
    private final com.trendist.player.service.PlayerService playerService;
    private final LevelService levels;
    private final ObjectMapper json;
    private final com.trendist.monster.service.MonsterCatalog monsterCatalog;
    private final com.trendist.monster.service.MonsterRecorder monsterRecorder;

    public RoomManagerImpl(SettingService settings, KeywordService keywordService, LevelService levels, ObjectMapper json,
                           com.trendist.monster.service.MonsterCatalog monsterCatalog,
                           com.trendist.monster.service.MonsterRecorder monsterRecorder,
                           com.trendist.skill.service.SkillCatalog skillCatalog,
                           com.trendist.skill.service.SkillLoadoutService skillLoadouts,
                           @org.springframework.context.annotation.Lazy com.trendist.player.service.PlayerService playerService) {
        this.playerService = playerService;
        this.monsterCatalog = monsterCatalog;
        this.monsterRecorder = monsterRecorder;
        this.skillCatalog = skillCatalog;
        this.skillLoadouts = skillLoadouts;
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
                Room.HpTuning hp = hpTuning();
                Room.SkillTuning skill = skillTuning();
                java.util.Map<String, Double> extra = extraSkillCooldowns();
                java.util.Set<String> disabledSkills = skillCatalog.disabledCodes();
                java.util.Map<String, Integer> requiredLevels = skillCatalog.requiredLevels();
                java.util.Map<String, java.util.Map<String, Double>> monsterParams = new java.util.HashMap<>();
                monsterCatalog.all().forEach((code, e) -> monsterParams.put(code, e.params())); // 몬스터 설정값(4.35)
                rooms.values().forEach(r -> {
                    r.setMonsterParams(monsterParams);
                    if (!skill.equals(r.skillTuning())) r.setSkillTuning(skill);
                    r.setSkillCooldowns(extra);
                    r.setDisabledSkills(disabledSkills);
                    r.setRequiredLevels(requiredLevels);
                    if (!c.equals(r.comboTuning())) r.setComboTuning(c);
                    if (!hp.equals(r.hpTuning())) r.setHpTuning(hp);
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
            r.setHpTuning(hpTuning());
            r.setDashTuning(dashTuning());
            r.setSkillTuning(skillTuning());
            r.setSkillCooldowns(extraSkillCooldowns());
            r.setSkillSlotCount(skillLoadouts.slotCount()); // 장착 칸 수는 actions의 SKILL_SLOT 동작 수(4.33)
            r.setDisabledSkills(skillCatalog.disabledCodes());
            r.setRequiredLevels(skillCatalog.requiredLevels());
            r.setMonsterParams(monsterParamsNow());
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

    private java.util.Map<String, java.util.Map<String, Double>> monsterParamsNow() {
        java.util.Map<String, java.util.Map<String, Double>> m = new java.util.HashMap<>();
        monsterCatalog.all().forEach((code, e) -> m.put(code, e.params()));
        return m;
    }

    @Override
    public java.util.Collection<Room> rooms() { return rooms.values(); }

    @Override
    public Room.Tuning tuning() {
        double world = settings.getDouble(SettingKeys.WORLD_SIZE);
        return new Room.Tuning(world,
                settings.getDouble(SettingKeys.MIN_RADIUS),
                settings.getDouble(SettingKeys.MAX_RADIUS),
                settings.getDouble(SettingKeys.SCORE_CAP),
                settings.getDouble(SettingKeys.MAX_SPEED),
                settings.getDouble(SettingKeys.ZOOM_FACTOR),
                settings.getDouble(SettingKeys.BIG_RATIO),
                settings.getDouble(SettingKeys.KNOCKBACK_CAP),
                settings.getDouble(SettingKeys.KNOCKBACK_SECONDS),
                settings.getDouble(SettingKeys.KNOCKBACK_DISTANCE),
                settings.getDouble(SettingKeys.LOSE_PROTECT_SECONDS),
                settings.getInt(SettingKeys.ORB_MAX),
                settings.getDouble(SettingKeys.ORB_SPAWN_SECONDS),
                settings.getDouble(SettingKeys.ORB_DENSITY),
                settings.getDouble(SettingKeys.ORB_SPAWN_PERCENT),
                settings.getDouble(SettingKeys.ORB_ACTIVE_PERCENT),
                settings.getInt(SettingKeys.BOT_PLAYERS),
                settings.getInt(SettingKeys.BOT_MIN_PLAYERS),
                settings.getDouble(SettingKeys.BOT_CAP_RATIO),
                settings.getDouble(SettingKeys.EXP_SCORE_CAP),
                settings.getDouble(SettingKeys.EAT_MIN_REACH));
    }

    /** 독·암흑·정화·회복·슬로우·스턴·불꽃·충격파·흡수의 쿨타임(DB skills). 테스트 중에는 0이다. */
    private java.util.Map<String, Double> extraSkillCooldowns() {
        return java.util.Map.of("poison", skillCatalog.cooldownSeconds("poison", 0), "darkness", skillCatalog.cooldownSeconds("darkness", 0), "cleanse", skillCatalog.cooldownSeconds("cleanse", 0),
                "heal", skillCatalog.cooldownSeconds("heal", 0), "heal_percent", settings.getDouble(SettingKeys.SKILL_HEAL_PERCENT),
                "slow", skillCatalog.cooldownSeconds("slow", 0), "stun", skillCatalog.cooldownSeconds("stun", 0), "flame", skillCatalog.cooldownSeconds("flame", 0),
                "quake", skillCatalog.cooldownSeconds("quake", 0), "absorb", skillCatalog.cooldownSeconds("absorb", 0)); // heal_percent는 쿨타임이 아니라 회복 스킬의 회복량(%)이다
    }

    /** 대시 설정(4.27). */
    private Room.DashTuning dashTuning() {
        return new Room.DashTuning(settings.getDouble(SettingKeys.DASH_SPEED_MULTIPLIER), settings.getDouble(SettingKeys.DASH_SECONDS),
                skillCatalog.cooldownSeconds("dash", 30));
    }

    /** 스킬 설정(4.33). */
    private Room.SkillTuning skillTuning() {
        return new Room.SkillTuning(
                settings.getDouble(SettingKeys.SHOCKWAVE_SPEED_MULTIPLIER), settings.getDouble(SettingKeys.SHOCKWAVE_REACH_PX),
                settings.getDouble(SettingKeys.SHOCKWAVE_KNOCKBACK), skillCatalog.cooldownSeconds("shockwave", 30),
                settings.getDouble(SettingKeys.HOOK_REACH_PX), settings.getDouble(SettingKeys.HOOK_SECONDS),
                settings.getDouble(SettingKeys.HOOK_PULL_SECONDS), skillCatalog.cooldownSeconds("hook", 30),
                settings.getDouble(SettingKeys.SKILL_SHIELD_SECONDS), skillCatalog.cooldownSeconds("shield", 30),
                settings.getDouble(SettingKeys.STEALTH_SECONDS), skillCatalog.cooldownSeconds("stealth", 30),
                settings.getDouble(SettingKeys.BOLT_REACH_PX));
    }

    /** 연속 빼앗기 콤보 설정(4.25). */
    private Room.ComboTuning comboTuning() {
        return new Room.ComboTuning(settings.getInt(SettingKeys.COMBO_FLAME_AT), settings.getInt(SettingKeys.COMBO_BOT_COUNTS) > 0,
                settings.getInt(SettingKeys.COMBO_MAX_SECONDS));
    }

    /** 체력(HP) 설정(4.36). */
    private Room.HpTuning hpTuning() {
        return new Room.HpTuning(settings.getInt(SettingKeys.HP_ENABLED) > 0, settings.getDouble(SettingKeys.HP_MAX),
                settings.getDouble(SettingKeys.HP_HIT_SECONDS), settings.getDouble(SettingKeys.HP_FLAME_MULTIPLIER),
                settings.getInt(SettingKeys.HP_FLAME_COUNTER) > 0,
                settings.getDouble(SettingKeys.HP_SAFE_REGEN_PERCENT), settings.getInt(SettingKeys.HP_PUBLIC) > 0,
                settings.getInt(SettingKeys.DEATH_LOSE_PERCENT), settings.getInt(SettingKeys.FLAME_HIT_LOSE_PERCENT),
                settings.getDouble(SettingKeys.HP_KILL_HEAL_PERCENT));
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
            if (m != null) sendText(m, Map.of("type", "score", "score", e.score(), "exp", e.exp(), "nextExp", levels.nextRequired(e.level()), "level", e.level(),
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

    /** 키워드를 새로 얻으면 그 점수 비율만큼 체력이 회복된다(4.36) */
    @TransactionalEventListener(fallbackExecution = true)
    public void on(GameEvents.KeywordGained e) {
        Room r = rooms.get(e.countryId());
        if (r != null) r.submit(room -> room.applyKeywordGain(e.playerId(), e.keywordScore(), e.totalScore()));
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

    // ------------------------------------------------------------ 몬스터(4.35)

    @Override
    public void monsterStarted(Room room, com.trendist.game.room.Monster.Spec spec, com.trendist.game.room.Monster.Start start) {
        db(() -> {
            try { monsterRecorder.started(spec.spawnId(), start); }
            catch (RuntimeException e) { log.warn("monster start record failed for spawn {}", spec.spawnId(), e); }
        });
    }

    /** 끝난 몬스터: 등장 기록·피해를 남기고, 보상(EXP, 🔥 키워드)을 준 뒤 접속 중인 사람에게 알린다 */
    @Override
    public void monsterEnded(Room room, com.trendist.game.room.Monster.Result result) {
        db(() -> {
            try { monsterRecorder.finish(result); }
            catch (RuntimeException e) { log.warn("monster finish record failed for spawn {}", result.spec().spawnId(), e); }
            for (var rw : result.rewards()) {
                try {
                    long gotExp = playerService.awardMonster(rw.playerId(), rw.exp(), rw.levelPercent(), rw.cleared());
                    java.util.List<String> texts = new java.util.ArrayList<>();
                    for (int i = 0; i < rw.keywords(); i++) {
                        KeywordDtos.OrbResult r = keywordService.grantRewardKeyword(rw.playerId()); // ! 구슬과 같은 규칙(구슬 횟수는 세지 않음): 무작위 🔥 키워드 하나
                        if (("GAINED".equals(r.status()) || "RENEWED".equals(r.status())) && r.text() != null) texts.add(r.text());
                    }
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("type", "monsterReward"); msg.put("name", result.spec().name()); msg.put("en", result.spec().nameEn());
                    msg.put("cleared", rw.cleared()); msg.put("rank", rw.rank()); msg.put("damage", rw.damage()); msg.put("exp", gotExp); msg.put("levelPercent", Math.round(rw.levelPercent() * 10) / 10.0); msg.put("keywords", texts);
                    room.submit(r -> { Room.Member m = r.byPlayer(rw.playerId()); if (m != null) sendText(m, msg); });
                } catch (RuntimeException e) {
                    log.warn("monster reward failed for player {}", rw.playerId(), e);
                }
            }
        });
    }

    @Override
    public void requestMonsterLose(Room room, Room.Member victim, String by, int count) {
        long victimId = victim.playerId;
        db(() -> {
            try {
                java.util.List<String> texts = new java.util.ArrayList<>();
                long total = 0;
                for (int i = 0; i < count; i++) {
                    var lost = keywordService.loseRandom(victimId);
                    if (lost.isEmpty()) break;
                    texts.add(lost.get().text());
                    total += lost.get().score();
                }
                if (texts.isEmpty()) return;
                long score = total;
                room.submit(r -> {
                    Room.Member m = r.byPlayer(victimId);
                    if (m != null) sendText(m, Map.of("type", "lost", "by", by, "keyword", texts.get(0), "score", score, "monster", true, "count", texts.size()));
                });
            } catch (RuntimeException e) {
                log.warn("monster lose failed for player {}", victimId, e);
            }
        });
    }

    @Override
    public void saveSkillSlots(Room room, Room.Member member, java.util.List<String> slots) {
        long playerId = member.playerId;
        db(() -> {
            try { skillLoadouts.save(playerId, slots); }
            catch (RuntimeException e) { log.warn("save skill slots failed for player {}", playerId, e); }
        });
    }

    @Override
    public void requestPoisonLose(Room room, Room.Member victim, Room.Member owner, int count) {
        requestSkillLose(room, victim, owner, count, "poison");
    }

    @Override
    public void requestSkillLose(Room room, Room.Member victim, Room.Member owner, int count, String cause) {
        long victimId = victim.playerId;
        String by = owner != null ? owner.nickname : "☠";
        db(() -> {
            try {
                // 스택으로 한 틱에 여러 개를 잃으면 알림을 하나로 모아 잃은 개수만 알린다(어떤 키워드인지는 알리지 않는다. 따로 보내면 마지막 하나만 보인다)
                java.util.List<String> texts = new java.util.ArrayList<>();
                long total = 0;
                for (int i = 0; i < count; i++) {
                    var lost = keywordService.loseRandom(victimId);
                    if (lost.isEmpty()) break;
                    texts.add(lost.get().text());
                    total += lost.get().score();
                }
                if (texts.isEmpty()) return;
                long score = total;
                room.submit(r -> {
                    Room.Member m = r.byPlayer(victimId);
                    if (m != null) {
                        r.poisonLost(m, owner);
                        sendText(m, Map.of("type", "lost", "by", by, "keyword", texts.get(0), "score", score, "poison", true, "cause", cause, "count", texts.size()));
                    }
                });
            } catch (RuntimeException e) {
                log.warn("poison lose failed for player {}", victimId, e);
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

    /** 키워드의 일부를 잃는다(4.36): 불꽃에게 부딪혔거나(death=false) 체력이 0이 되어 죽었다(death=true). 죽은 사람은 이미 방에서 빠졌으므로 넘겨 받은 원 기준으로 알린다 */
    @Override
    public void requestHalve(Room room, Room.Member loser, Room.Member winner, int percent, boolean death) {
        long loserId = loser.playerId, winnerId = winner.playerId;
        String winnerName = winner.nickname;
        boolean winnerBot = winner.bot;
        db(() -> {
            try {
                keywordService.loseFraction(loserId, percent).ifPresent(lost -> {
                    if (!death && !winnerBot) playerService.recordSteal(winnerId); // 불꽃으로 공격 성공 한 번 = 빼앗은 횟수 +1(4.3)
                    room.submit(r -> {
                        if (!death) r.fightResult(winner, r.byPlayer(loserId), false); // 죽는 경우는 die가 이미 콤보를 처리했다
                        r.dropPrizeOrbs(loser, lost.activeCount()); // 잃은 🔥활성 키워드 수의 일부만큼 ! 구슬(4.20)
                        Map<String, Object> msg = new HashMap<>();
                        msg.put("type", "lost"); msg.put("by", winnerName); msg.put("wiped", lost.count()); msg.put("score", lost.score());
                        msg.put("partial", true); msg.put("death", death);
                        sendText(loser, msg);
                        Room.Member w = r.byPlayer(winnerId); // 이긴 쪽이 봇이면 방 목록에 없고 알릴 연결도 없다
                        if (w != null) sendText(w, Map.of("type", "hit", "target", loser.nickname, "wiped", lost.count(), "score", lost.score(), "partial", true));
                    });
                });
            } catch (RuntimeException e) {
                log.warn("lose part of keywords failed for player {}", loserId, e);
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
    public void requestTie(Room room, Room.Member member) {
        long playerId = member.playerId;
        db(() -> {
            try {
                playerService.recordTie(playerId);
            } catch (RuntimeException e) {
                log.warn("record tie failed for player {}", playerId, e);
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
    public void requestCombo(Room room, Room.Member winner, int combo) {
        long playerId = winner.playerId;
        db(() -> {
            try {
                playerService.recordCombo(playerId, combo);
            } catch (RuntimeException e) {
                log.warn("record combo failed for player {}", playerId, e);
            }
        });
    }

    @Override
    public void requestFlameOut(Room room, Room.Member by) {
        long playerId = by.playerId;
        db(() -> {
            try {
                playerService.recordFlameOut(playerId);
            } catch (RuntimeException e) {
                log.warn("record flame out failed for player {}", playerId, e);
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
