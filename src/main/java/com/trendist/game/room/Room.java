package com.trendist.game.room;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 나라별 방 하나(9.2). 틱 스레드 하나만 상태를 읽고 쓰므로 락이 없다. 다른 스레드는 tasks 큐에 작업을 넣는다.
 * 이동·충돌은 서버 권위로 계산하고, 스냅샷(바이너리)을 2틱마다(10Hz) 보낸다.
 */
public class Room {
    public static final int TICK_MS = 50;
    private static final int SNAPSHOT_EVERY = 2;
    private static final int MINIMAP_EVERY = 20; // 1초마다
    private static final int MINIMAP_TOP = 20;   // 큰 아이콘 상위 20명(4.2)
    /** 목표 속도로 다가가는 빠르기. 50ms마다 차이가 절반이 된다(클라이언트 Arena 예측과 같은 값). */
    static final double ACCEL_RATE = Math.log(2) / 0.05;
    /** 나간 플레이어의 마지막 위치를 기억하는 시간(초). 서버를 다시 켜도 DB에 저장한 위치를 이 시간 안에는 돌려준다 */
    public static final double ORB_RADIUS = 14; // ? 구슬 반지름(px)
 // 나간 뒤 이 시간 안에 다시 들어오면 마지막 위치에서 이어 간다(4.2)

    /** 틱에서 쓰는 설정값. 1분 캐시된 settings에서 매초 다시 읽는다. */
    public record Tuning(double worldSize, double minRadius, double maxRadius, double scoreCap, double maxSpeed,
                         double zoomFactor, double bigRatio, double knockbackCap, double knockbackSeconds, double knockbackDistance,
                         double loseProtectSeconds,
                         int orbMax, double orbSpawnSeconds, double orbDensity, double orbSpawnPercent, double orbActivePercent,
                         int botPlayers, int botMinPlayers, double botCapRatio, double expScoreCap) {}

    /**
     * 세이프존 설정(4.22). 가운데 고정 존과 무작위 존의 한 변은 활동 영역 한 변의 비율(최소값 이상)이고,
     * 무작위 존 수는 넓이 10,000×10,000px마다 density개(상한 max), 하나는 seconds초 동안 유지된다.
     */
    public record ZoneTuning(double centerRatio, double centerMin, double zoneRatio, double zoneMin,
                             double density, int max, double seconds) {
        public static final ZoneTuning DEFAULT = new ZoneTuning(0.09, 900, 0.05, 600, 12, 80, 300);
    }

    /** 무작위 세이프존 하나. 정사각형 중심(x, y)과 한 변, 사라지는 시각(방 시계 초). */
    record SafeZone(int id, double x, double y, double size, double expiresAt) {}

    /** 방 밖(서비스)으로 나가는 요청: 충돌로 키워드 잃기, 메시지 전송. */
    public interface Outbox {
        void requestLose(Room room, Member loser, Member winner);
        /** ? 구슬을 먹었다. 보상(무작위 🔥 키워드)은 틱 밖에서 정하고 먹은 사람에게 알린다(4.20). */
        void requestOrb(Room room, Member eater, boolean prize);
        /** 불꽃인 사람(winner)에게 져서 키워드를 모두 잃는다. 잃은 수만큼 ! 구슬이 떨어진다(4.25). */
        void requestWipe(Room room, Member loser, Member winner);
        /** 사람이 봇을 이겼다. 봇이 잃은 키워드(keywordId)를 틱 밖에서 이긴 사람에게 주고(이미 가졌으면 남은 시간 갱신) 알린다(4.21). */
        void requestBotWin(Room room, Member winner, Member bot, long keywordId);
        /** 사람(killer)이 상대를 죽였다(사람·봇 모두). 죽인 횟수를 올리고 칭호를 확인한다(4.31). */
        default void requestKill(Room room, Member killer) {}
        /** 사람(attacker)이 공격에 성공했다(봇의 키워드를 불꽃으로 모두 잃게 한 경우 등). 빼앗은 횟수를 +1 하고 칭호를 확인한다(4.3). */
        default void requestSteal(Room room, Member attacker) {}
        /** 사람(loser)이 키워드가 없는 채로 져서 죽었다(패배). 패배 횟수를 올리고 칭호를 확인한다(4.31). 봇은 부르지 않는다. */
        default void requestDefeat(Room room, Member loser) {}
        void send(Member m, ByteBuffer binary);
        void sendText(Member m, Map<String, Object> message);
        /** 오래 응답이 없는 접속을 끊는다(방에서는 이미 뺐다). */
        default void close(Member m) {}
    }

    /** 성장 캐릭터의 정해진 바탕색(4.9). 꾸미지 않은 원 */
    public static final String DEFAULT_BACKGROUND = "#3fb68b";

    /** 이름표 아래 칭호, 원의 꾸미기(art는 픽셀 그림 이미지 주소, 없으면 성장 캐릭터), 이동 효과. 입장 때 함께 받는다. */
    public record Appearance(String title, Skin skin, String trail) {
        public static final Appearance DEFAULT = new Appearance(null, Skin.DEFAULT, null);
    }

    /**
     * 원의 꾸미기(4.9): 바탕색, 테두리 색·두께(1~8단계), 픽셀 그림 주소, 테두리 효과 코드, 이름표 글자색·모양.
     * null이면 기본값이다. 그림은 클라이언트가 그린다.
     */
    public record Skin(String background, String borderColor, Integer borderWidth, String art,
                       String borderEffect, String nameColor, String nameStyle) {
        public static final Skin DEFAULT = new Skin(DEFAULT_BACKGROUND, null, null, null, null, null, null);
    }

    /**
     * 연속 빼앗기 콤보(4.25). flameAt번 연속으로 이기면 원 둘레에 불꽃이 타오르고(그림은 클라이언트가 combo로 그린다),
     * 한 번이라도 싸움에서 키워드를 잃으면 콤보와 불꽃이 모두 사라진다. countBots면 봇을 이긴 것도 센다.
     * 불꽃인 사람이 불꽃이 없는 사람과 부딪히면 점수와 상관없이 이기고 상대는 키워드를 모두 잃는다(4.25).
     * wipeOrbPercent는 그때 잃은 🔥활성 키워드 수의 몇 %만큼 ! 구슬로 떨어뜨릴지(올림, 4.20).
     * maxSeconds는 콤보의 남은 유지시간(초): 마지막으로 콤보가 오른 때부터 이 시간이 지나면 연속 횟수와 불꽃이 사라진다. 콤보가 추가될 때마다 다시 시작한다(0이면 제한 없음, 4.25).
     */
    public record ComboTuning(int flameAt, boolean countBots, int wipeOrbPercent, int maxSeconds) {
        public static final ComboTuning DEFAULT = new ComboTuning(5, true, 50, 300);
    }

    /**
     * 대시(4.27): 쓰면 seconds초 동안 최대 속도가 multiplier배가 되고, 쓴 때부터 cooldownSeconds초가 지나야 다시 쓴다(대시 시간 포함).
     * 움직이려는 중(방향 입력이나 이동 목표가 있을 때)에만 쓸 수 있다.
     */
    public record DashTuning(double multiplier, double seconds, double cooldownSeconds) {
        public static final DashTuning DEFAULT = new DashTuning(2, 3, 30);
    }

    /** 이모트(4.26): 원 위에 잠깐 뜨는 이모지. 코드만 주고받는다 */
    public static final List<String> EMOTES = List.of("wave", "laugh", "cool", "cry", "angry", "thumbs", "heart", "fire");

    public static final class Member {
        public final int entityNo;
        public final long playerId;
        /** GameSocketHandler가 쓰는 세션. 연결이 끊겼다가 다시 들어오면(이어받기) 새 세션으로 바뀐다 */
        public volatile Object session;
        /** 연결이 끊겼지만 원은 아레나에 남아 있다(offline_keep_seconds 동안): 방은 똑같이 처리하되 메시지는 보내지 않는다(4.32) */
        public volatile boolean offline;
        public double offlineSince; // 끊긴 시각(방 시계 초)
        public String nickname;
        /** 활성 칭호(4.10). 없으면 null. 이름표 아래 작은 글씨로 보인다 */
        public String title;
        /** 꾸미기(4.9) */
        public Skin skin = Skin.DEFAULT;
        /** 연속 빼앗기 콤보(4.25) */
        public int combo;
        /** 콤보가 마지막으로 오른 시각(방 시계 초). 콤보 유지시간(4.25)은 이 시각부터 잰다: 콤보가 추가될 때마다 다시 시작한다 */
        public double comboStartedAt;
        /** 대시(4.27): 대시가 끝나는 시각과 다시 쓸 수 있는 시각(방 시계 초) */
        public double dashUntil, dashReadyAt;
        /** 이동 효과 코드(4.23). null이면 없음. 그림은 클라이언트가 그린다 */
        public String trail;
        public int level;
        public double score;
        public double x, y, vx, vy;
        public double radius, displayRadius;
        public double dirX, dirY;          // 방향키 입력(-1~1)
        public Double targetX, targetY;    // 클릭 목표(새로 찍으면 바로 바뀐다)
        public double kbX, kbY, kbTime;    // 튕김: 전체 밀려날 거리(벡터)와 남은 시간
        public double protectUntil;        // 키워드 잃기 보호 종료(방 시계 초)
        public boolean safe;               // 이번 틱에 세이프존 안에 있다(4.22). 부딪히지 않는다
        /** 화면에 보이는 세계 범위의 가로·세로 절반(px). 클라이언트가 알려 주며, 그 둘레(여유 포함)의 원만 스냅샷에 담는다(관심 영역 전송). 알리기 전에는 넉넉한 기본값 */
        public double viewHalfW = 5000, viewHalfH = 5000;
        public volatile boolean dead;      // 키워드가 없는 채로 져서 죽었다(4.31). 방에서는 이미 빠졌고, 다시 들어오기(리스폰) 전까지 입력을 받지 않는다
        public final Set<Long> keywords = new HashSet<>();
        /** 마지막으로 이 접속에서 메시지(ping 포함)를 받은 시각(nanoTime). 받는 스레드가 쓰고 틱이 읽는다 */
        public volatile long lastHeardNanos;
        /** NPC 봇(4.21). 연결도 키워드도 없고, 점수는 방 메모리에만 있다. playerId는 음수다. */
        public boolean bot;
        long botExp;         // 봇의 EXP(사람처럼 추가할 때마다 +1, 줄지 않는다). 레벨은 이것으로 정한다
        final List<BotKw> botKeywords = new ArrayList<>(); // 봇이 가진 실제 키워드(방 메모리에만 있고 DB에는 저장하지 않는다, 4.21)
        double botNextPlusAt; // 다음에 스스로 "추가"하는 시각
        int botStyle;        // 0 겁쟁이(큰 원을 멀리서부터 피함) / 1 사냥꾼(작은 원을 쫓음) / 2 구슬 수집가
        double botThinkAt;   // 다음에 목표를 다시 고르는 시각(방 시계 초)
        double botReact;     // 반응 속도(목표를 다시 고르는 간격, 초). 봇마다 다르다
        double botHeading;   // 돌아다닐 때의 진행 방향(라디안). 조금씩 휘어서 곡선으로 걷는다
        double botIdleUntil; // 이 시각까지 잠깐 멈춰 있는다(사람처럼 쉬기)
        double botChaseFrom, botChaseCooldown; // 쫓기 시작한 시각, 쫓다 포기한 뒤 다시 쫓지 않는 끝 시각
        Integer botOrb;      // 노리는 구슬(다른 봇과 같은 구슬로 몰리지 않게)
        /** 이번 틱 구간 안에 도착한 조작. 도착한 시점(구간 안 비율)부터 적용해 틱 경계로 반올림되지 않게 한다. */
        final List<Control> controls = new ArrayList<>();

        Member(int entityNo, long playerId, Object session) {
            this.entityNo = entityNo; this.playerId = playerId; this.session = session;
        }
    }

    private final long countryId;
    private final Outbox outbox;
    private final Queue<Consumer<Room>> tasks = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Member> members = new LinkedHashMap<>();
    /** 최근 채팅(4.13). 새로 들어온 사람에게 보여 준다. 서버를 다시 켜면 비워진다(DB에는 30일 남는다). */
    private final Deque<Map<String, Object>> chatHistory = new ArrayDeque<>();
    private static final int CHAT_HISTORY = 50;
    private final Map<Long, Member> byPlayer = new HashMap<>();
    /**
     * 관전자(13.13): 관리자가 방을 지켜본다. members에 넣지 않아서 방 크기·활동 영역·충돌·구슬·봇·랭킹에 아무 영향이 없고,
     * 방이 모두에게 보내는 스냅샷·미니맵·글 메시지만 받는다. 틱 스레드에서만 바꾼다.
     */
    private final Set<Member> spectators = new LinkedHashSet<>();
    private int nextSpectator = -1; // 관전자의 엔티티 번호(사람·봇과 겹치지 않게 음수)
    public static final int MAX_SPECTATORS = 10;
    private volatile int spectatorSize;
    private final Map<Long, Set<Member>> holders = new HashMap<>();
    private volatile double offlineKeepSeconds = 60; // 연결이 끊겨도 원이 아레나에 남아 있는 시간(초, 4.32). 0이면 바로 뺀다
    private final Map<Integer, double[]> orbs = new LinkedHashMap<>(); // ? 구슬 번호 → {x, y}
    private final Map<Integer, double[]> prizeOrbs = new LinkedHashMap<>(); // ! 구슬(불꽃에게 키워드를 모두 잃은 자리에 떨어짐) 번호 → {x, y, 사라질 시각}
    /** ! 구슬이 먹히지 않으면 사라지는 시간(초)과 방 전체 상한(4.20) */
    static final double PRIZE_ORB_SECONDS = 90;
    static final int PRIZE_ORB_MAX = 300;
    private int nextOrb = 1;
    private double nextOrbAt;
    private volatile ZoneTuning zoneTuning = ZoneTuning.DEFAULT;
    private volatile ComboTuning comboTuning = ComboTuning.DEFAULT;
    private volatile DashTuning dashTuning = DashTuning.DEFAULT;
    /**
     * 지금의 활동 영역 한 변(0이면 아직 정하지 않음). 목표(접속 인원에 따른 값)보다 클 때는 areaShrinkSpeed(px/s)로만 줄어들어서
     * 사람이 한꺼번에 나가거나 서버를 다시 켜도 원이 가장자리로 한꺼번에 밀려나지 않는다. 목표가 더 크면 바로 커진다. 틱 스레드에서만 바꾼다.
     */
    private double areaNow;
    private volatile double areaGrowSpeed = 300; // 0이면 커질 때 바로
    private volatile double areaShrinkSpeed; // 0이면 줄 때도 바로(예전 방식)
    private final List<SafeZone> zones = new ArrayList<>();
    private int nextZone = 1;
    private double centerSize, sentCenterSize = -1;

    private volatile Tuning tuning;
    private int nextEntity = 1;
    private long tick;
    private double clockSeconds;
    private volatile int size;
    private volatile Map<Long, Live> liveView = Map.of();

    /** 조작 하나: 방향키 입력(target 없음) 또는 클릭 목표. at은 틱 구간 안에서의 비율(0~1). */
    record Control(double at, double dx, double dy, Double targetX, Double targetY) {}

    private final LongSupplier nanoClock;
    private long prevTickNanos, tickNanos; // 지난 틱과 이번 틱이 시작한 시각. 그 사이에 도착한 조작의 시점을 잰다

    public Room(long countryId, Tuning tuning, Outbox outbox) {
        this(countryId, tuning, outbox, System::nanoTime);
    }

    public Room(long countryId, Tuning tuning, Outbox outbox, LongSupplier nanoClock) {
        this.countryId = countryId; this.tuning = tuning; this.outbox = outbox; this.nanoClock = nanoClock;
    }

    public long countryId() { return countryId; }
    public int size() { return size; }
    public void setTuning(Tuning t) { this.tuning = t; }
    public void setZoneTuning(ZoneTuning z) { this.zoneTuning = z; }
    public void setComboTuning(ComboTuning c) { this.comboTuning = c; }
    public void setDashTuning(DashTuning d) { this.dashTuning = d; }
    public DashTuning dashTuning() { return dashTuning; }
    public void setAreaGrowSpeed(double pxPerSecond) { this.areaGrowSpeed = Math.max(0, pxPerSecond); }
    public void setAreaShrinkSpeed(double pxPerSecond) { this.areaShrinkSpeed = Math.max(0, pxPerSecond); }
    /** 연결이 끊겨도 원이 아레나에 남아 있는 시간(초). 0이면 끊기는 순간 뺀다(4.32). */
    public void setOfflineKeepSeconds(double seconds) { this.offlineKeepSeconds = Math.max(0, seconds); }
    /** 지금 활동 영역 한 변(저장용) */
    public double currentArea() { return areaNow > 0 ? areaNow : activeArea(members.size()); }
    private double currentHalf() { return currentArea() / 2; }

    /** 영역을 목표에 맞춘다: 커질 때는 areaGrowSpeed, 줄 때는 areaShrinkSpeed만큼씩(0이면 바로). 맵 벽이 부드럽게 움직이도록 양쪽 모두 속도를 제한한다. */
    private void updateArea(double dt) {
        double target = activeArea(members.size());
        if (areaNow <= 0) { areaNow = target; return; }
        if (target > areaNow) {
            double speed = areaGrowSpeed;
            areaNow = speed <= 0 ? target : Math.min(target, areaNow + speed * dt);
        } else if (target < areaNow) {
            double speed = areaShrinkSpeed;
            areaNow = speed <= 0 ? target : Math.max(target, areaNow - speed * dt);
        }
    }
    public ComboTuning comboTuning() { return comboTuning; }
    public ZoneTuning zoneTuning() { return zoneTuning; }
    public Tuning tuning() { return tuning; }
    public void submit(Consumer<Room> task) { tasks.add(task); }

    /** 접속 중인 플레이어 한 명의 랭킹용 값. */
    public record Live(long playerId, int entityNo, String nickname, int level, double score) {}

    /** 랭킹에서 쓰는 접속 중인 플레이어(1초마다 갱신되는 읽기 전용 복사본, 플레이어 번호 → 값). */
    public Map<Long, Live> liveView() { return liveView; }

    // ------------------------------------------------------------ 틱 스레드에서만 부른다

    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds) {
        return join(playerId, session, nickname, level, score, keywordIds, Appearance.DEFAULT);
    }

    /**
     * 입장·리스폰(4.22, 4.31, 4.32): 언제나 가운데 고정 세이프존 안 무작위 위치에서 시작한다(마지막 위치는 기억하지 않는다). 세이프존이 안전한 자리라
     * 입장 보호(무적)는 따로 주지 않는다. 연결이 끊겨도 원이 남아 있는 동안(offline_keep_seconds) 같은 계정이 다시 들어오면
     * 새 원을 만들지 않고 그 원을 이어받는다(위치·보호·콤보 그대로).
     */
    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds,
                       Appearance look) {
        Member old = byPlayer.get(playerId);
        if (spectators.removeIf(s -> s.dead && s.playerId == playerId)) refreshSpectatorSize(); // 죽어서 구경하던 연결이 다시 들어온다(리스폰, 4.31)
        if (old != null) return takeOver(old, session, nickname, keywordIds, look);
        Member m = new Member(nextEntity++, playerId, session);
        m.lastHeardNanos = nanoClock.getAsLong();
        m.nickname = nickname; m.level = level; m.score = score;
        m.title = look.title(); m.skin = look.skin() == null ? Skin.DEFAULT : look.skin(); m.trail = look.trail();
        m.radius = radiusFor(score); m.displayRadius = m.radius;
        if (areaNow <= 0) areaNow = activeArea(members.size() + 1); // 방의 첫 입장: 처음 크기를 정한다. 그 뒤로는 틱이 속도에 맞춰 키운다
        double area = currentArea();
        double[] spawn = centerSpawn(area);
        m.x = spawn[0]; m.y = spawn[1];
        m.keywords.addAll(keywordIds);
        for (Long k : keywordIds) holders.computeIfAbsent(k, x -> new HashSet<>()).add(m);
        members.put(m.entityNo, m);
        byPlayer.put(playerId, m);
        size = humanCount();
        broadcastRoster();
        outbox.sendText(m, orbListMessage());
        outbox.sendText(m, zonesMessage());
        outbox.sendText(m, Map.of("type", "chatHistory", "messages", List.copyOf(chatHistory)));
        return m;
    }

    /**
     * 이미 방에 있는 같은 계정의 원을 새 연결이 이어받는다(끊긴 뒤 다시 들어왔거나, 새로고침해서 이전 연결의 끊김이 아직 안 보일 때):
     * 위치·보호·콤보·점수는 그대로이고, 연결(세션)과 이름표·꾸미기·가진 키워드만 새 값으로 맞춘다. 이전 연결이 남아 있으면 더는 이 원을 움직이지 못한다.
     */
    private Member takeOver(Member old, Object session, String nickname, Collection<Long> keywordIds, Appearance look) {
        old.session = session;
        old.offline = false;
        old.lastHeardNanos = nanoClock.getAsLong();
        old.nickname = nickname;
        old.title = look.title(); old.skin = look.skin() == null ? Skin.DEFAULT : look.skin(); old.trail = look.trail();
        for (Long k : old.keywords) {
            Set<Member> set = holders.get(k);
            if (set != null) { set.remove(old); if (set.isEmpty()) holders.remove(k); }
        }
        old.keywords.clear();
        old.keywords.addAll(keywordIds);
        for (Long k : keywordIds) holders.computeIfAbsent(k, x -> new HashSet<>()).add(old);
        old.dirX = 0; old.dirY = 0; old.targetX = null; old.targetY = null; old.controls.clear(); // 새 연결의 조작부터 받는다
        size = humanCount();
        broadcastRoster();
        outbox.sendText(old, orbListMessage());
        outbox.sendText(old, zonesMessage());
        outbox.sendText(old, Map.of("type", "chatHistory", "messages", List.copyOf(chatHistory)));
        return old;
    }

    /** 관전자를 받는다. 가득 찼으면 null. 방의 지금 상태(이름표·구슬·세이프존·최근 채팅)를 바로 보낸다. */
    public Member addSpectator(Object session) {
        if (spectatorSize >= MAX_SPECTATORS) return null;
        Member sp = new Member(nextSpectator--, 0, session);
        sp.lastHeardNanos = nanoClock.getAsLong();
        sp.nickname = "(관전)";
        spectators.add(sp);
        refreshSpectatorSize();
        outbox.sendText(sp, rosterMessage());
        outbox.sendText(sp, orbListMessage());
        outbox.sendText(sp, zonesMessage());
        outbox.sendText(sp, Map.of("type", "chatHistory", "messages", List.copyOf(chatHistory)));
        return sp;
    }

    public void removeSpectator(Member sp) {
        spectators.remove(sp);
        refreshSpectatorSize();
    }

    /** 관전자 수는 관리자 관전만 센다. 죽어서 구경 중인 플레이어(4.31)는 세지 않는다. */
    private void refreshSpectatorSize() { spectatorSize = (int) spectators.stream().filter(s -> !s.dead).count(); }

    public Collection<Member> spectators() { return spectators; }
    /** 지금 지켜보는 관전자 수(다른 스레드에서 읽어도 된다) */
    public int spectatorCount() { return spectatorSize; }

    /**
     * 채팅 한 줄을 방 전체에 보낸다(4.13). 보낸 사람은 세션 엔티티 번호와 지금 닉네임으로만 가리킨다.
     * 클라이언트는 이 번호로 말풍선을 아이콘 위에 띄운다.
     */
    public void chat(Member from, long id, String text, long atMillis) {
        if (members.get(from.entityNo) != from) return; // 저장하는 사이에 나갔다
        Map<String, Object> msg = Map.of("type", "chat", "id", id, "e", from.entityNo, "nickname", from.nickname,
                "text", text, "at", atMillis);
        chatHistory.addLast(msg);
        while (chatHistory.size() > CHAT_HISTORY) chatHistory.pollFirst();
        for (Member m : members.values()) outbox.sendText(m, msg);
        for (Member sp : spectators) outbox.sendText(sp, msg);
    }

    /** 이모트(4.26): 방 전체에 보내 그 원 위에 잠깐 띄운다. 코드는 받는 쪽에서 이미 검사했다. */
    public void emote(Member from, String code) {
        if (members.get(from.entityNo) != from) return;
        Map<String, Object> msg = Map.of("type", "emote", "e", from.entityNo, "code", code);
        for (Member m : members.values()) outbox.sendText(m, msg);
        for (Member sp : spectators) outbox.sendText(sp, msg);
    }

    /**
     * 싸움 결과(4.3, 4.25): 이긴 쪽(winnerId)의 콤보를 +1 하고, 키워드를 잃은 쪽(loserId)의 콤보를 0으로 만든다(불꽃이 있었으면 꺼진다).
     * 사람과 봇이 같은 규칙이다. 봇은 byPlayer에 없으므로 봇이 낀 싸움은 아래 멤버 버전으로 부른다(여기서 null은 "처리할 쪽 없음").
     * vsBot이면 진 쪽이 봇이다(설정 combo_bot_counts가 꺼져 있으면 봇을 이겨도 콤보에 넣지 않는다). 키워드가 실제로 오간 뒤에만 부른다.
     */
    public void fightResult(Long winnerId, Long loserId, boolean vsBot) {
        fightResult(winnerId == null ? null : byPlayer.get(winnerId), loserId == null ? null : byPlayer.get(loserId), vsBot);
    }

    /** 멤버 버전: 이긴 쪽과 진 쪽이 사람이든 봇이든 같은 규칙이다(4.25). null인 쪽은 건너뛴다. */
    public void fightResult(Member winner, Member loser, boolean vsBot) {
        comboLose(loser, winner);
        comboWin(winner, vsBot);
    }

    /**
     * 콤보 유지시간(4.25): 마지막으로 콤보가 오른 때부터 maxSeconds(설정 combo_max_seconds, 기본 300초 = 5분)가 지나면 연속 횟수와 불꽃이 사라진다.
     * 방 전체에 {combo, event: expired}를 보낸다. 1초마다 확인한다(사람·봇 모두).
     */
    private void expireCombos() {
        int max = comboTuning.maxSeconds();
        if (max <= 0) return;
        for (Member m : members.values()) {
            if (m.combo <= 0 || clockSeconds - m.comboStartedAt < max) continue;
            m.combo = 0;
            broadcast(comboMessage(m, "expired"));
        }
    }

    /** 키워드를 잃으면 콤보가 0이 되고(불꽃이 있었으면 꺼짐) 방 전체에 알린다. */
    private void comboLose(Member loser, Member by) {
        if (loser == null || loser.combo <= 0) return;
        boolean broken = loser.combo >= comboTuning.flameAt();
        loser.combo = 0;
        Map<String, Object> msg = comboMessage(loser, broken ? "broken" : "reset");
        msg.put("by", by != null ? by.nickname : "Bot");
        if (by != null) msg.put("byE", by.entityNo);
        broadcast(msg);
    }

    /** 키워드를 빼앗으면 콤보 +1. 봇을 이긴 경우는 설정(combo_bot_counts)에 따른다. */
    private void comboWin(Member winner, boolean vsBot) {
        if (winner == null || (vsBot && !comboTuning.countBots())) return;
        winner.comboStartedAt = clockSeconds; // 콤보가 추가될 때마다 유지시간이 다시 시작된다(4.25)
        winner.combo++;
        broadcast(comboMessage(winner, winner.combo == comboTuning.flameAt() ? "start" : "up")); // start: 이번에 불꽃이 붙었다
    }

    private Map<String, Object> comboMessage(Member m, String event) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "combo"); msg.put("e", m.entityNo); msg.put("nickname", m.nickname);
        msg.put("combo", m.combo); msg.put("event", event); msg.put("flameAt", comboTuning.flameAt());
        return msg;
    }

    /**
     * 연결이 끊겼다(나감·새로고침·네트워크 끊김, 4.32): 원은 마지막 위치에 그대로 offline_keep_seconds(기본 60초) 동안 남는다. 그동안 공격받거나
     * 방어하거나 무적이 되는 일은 남아 있는 사람과 똑같고, 시간이 지나면 expireOffline이 진짜로 뺀다. session은 끊긴 연결이며,
     * 이미 새 연결이 이어받았으면(다른 세션) 아무것도 하지 않는다. 시간이 0이면 바로 뺀다.
     */
    public void disconnect(Member m, Object session) {
        if (m.session != session) return;
        if (m.dead) { spectators.remove(m); refreshSpectatorSize(); return; } // 죽은 채로 나간다(4.31): 구경 목록에서만 뺀다
        if (members.get(m.entityNo) != m || m.offline) return;
        if (offlineKeepSeconds <= 0) { leave(m); return; }
        m.offline = true;
        m.offlineSince = clockSeconds;
        m.dirX = 0; m.dirY = 0; m.targetX = null; m.targetY = null; m.controls.clear(); // 마지막으로 있던 자리에 가만히 남는다
    }

    /** 끊긴 지 offline_keep_seconds가 지난 원을 아레나에서 진짜로 뺀다(1초마다, 4.32). */
    private void expireOffline() {
        double keep = offlineKeepSeconds;
        for (Member m : List.copyOf(members.values())) {
            if (m.offline && clockSeconds - m.offlineSince >= keep) leave(m);
        }
    }

    /** 방에서 바로 뺀다(끊긴 시간이 지났거나, 관리자 내보내기). 마지막 위치는 기억하지 않는다. */
    public void leave(Member m) {
        if (m.dead) { spectators.remove(m); refreshSpectatorSize(); return; } // 죽은 채로 나간다(4.31): 구경 목록에서만 뺀다
        if (members.remove(m.entityNo) == null) return;
        byPlayer.remove(m.playerId);
        for (Long k : m.keywords) {
            Set<Member> set = holders.get(k);
            if (set != null) { set.remove(m); if (set.isEmpty()) holders.remove(k); }
        }
        size = humanCount();
        broadcastRoster();
    }

    /**
     * 죽음(4.31): 키워드가 하나도 없는 사람이 져서 보호도 없을 때. 방에서 빼고(보호·위치는 남기지 않는다) 죽은 사람에게 {died, by},
     * 죽인 사람에게 {kill, target}을 보낸다. 연결은 그대로 두고, 클라이언트가 {respawn}을 보내 다시 들어올 때까지 기다린다.
     * 이긴 쪽 콤보는 +1, 진 쪽 콤보는 0(4.25)이라 싸움 결과도 함께 처리한다.
     */
    private void die(Member loser, Member killer) {
        if (loser.dead || members.get(loser.entityNo) != loser) return;
        fightResult(killer, loser, loser.bot);
        loser.dead = true;
        members.remove(loser.entityNo);
        if (!killer.bot) outbox.requestKill(this, killer); // 죽인 횟수(4.31): 죽은 쪽이 사람이든 봇이든 센다
        if (loser.bot) {
            // 봇도 죽는다(4.31): 방에서 빠지고, 다음 봇 채우기(1초마다)에서 새 봇이 가운데 세이프존에서 다시 나온다(리스폰)
            botRespawns++;
            broadcastRoster();
            if (!killer.bot) outbox.sendText(killer, Map.of("type", "kill", "target", loser.nickname));
            return;
        }
        byPlayer.remove(loser.playerId);
        outbox.requestDefeat(this, loser); // 패배 횟수(4.31): 연결이 끊긴 채 남아 있던 원이 져도 센다
        if (loser.offline) {
            // 연결이 끊긴 채 남아 있던 원이 죽었다(4.32): 구경할 연결이 없으므로 그대로 뺀다
            size = humanCount();
            broadcastRoster();
            if (!killer.bot) outbox.sendText(killer, Map.of("type", "kill", "target", loser.nickname));
            return;
        }
        spectators.add(loser); // 죽은 뒤에도 방의 실시간 모습(스냅샷·미니맵·채팅 등)을 관전자처럼 계속 받는다
        size = humanCount();
        broadcastRoster();
        Map<String, Object> died = new HashMap<>();
        died.put("type", "died"); died.put("by", killer.nickname); died.put("byE", killer.entityNo);
        outbox.sendText(loser, died);
        if (!killer.bot) outbox.sendText(killer, Map.of("type", "kill", "target", loser.nickname));
    }

    private int botRespawns; // 죽어서 다시 나와야 하는 봇 수(4.31)

    public Member byPlayer(long playerId) { return byPlayer.get(playerId); }
    public Member byEntity(int entityNo) { return members.get(entityNo); }
    public Collection<Member> members() { return members.values(); }

    public void setInput(Member m, double dx, double dy) {
        double len = Math.hypot(dx, dy);
        m.dirX = len > 1 ? dx / len : dx; m.dirY = len > 1 ? dy / len : dy;
        if (len > 0) { m.targetX = null; m.targetY = null; }
    }

    public void setTarget(Member m, double x, double y) {
        double half = currentHalf();
        m.targetX = clamp(x, -half, half); m.targetY = clamp(y, -half, half);
        m.dirX = 0; m.dirY = 0;
    }

    /** 방향키 입력. receivedNanos(도착 시각)부터 적용한다. */
    public void setInput(Member m, double dx, double dy, long receivedNanos) {
        m.controls.add(new Control(fractionOf(receivedNanos), dx, dy, null, null));
    }

    /** 클릭 목표. receivedNanos(도착 시각)부터 적용한다. */
    public void setTarget(Member m, double x, double y, long receivedNanos) {
        m.controls.add(new Control(fractionOf(receivedNanos), 0, 0, x, y));
    }

    /** 도착 시각이 지난 틱과 이번 틱 사이 어디쯤인지(0~1). */
    private double fractionOf(long nanos) {
        long span = tickNanos - prevTickNanos;
        if (span <= 0) return 0;
        return clamp((double) (nanos - prevTickNanos) / span, 0, 1);
    }

    /** 그 사람 점수를 DB 값으로 맞춘다. */
    public void applyScore(long playerId, double score, int level) {
        Member m = byPlayer.get(playerId);
        if (m == null) return;
        boolean levelUp = level > m.level;
        m.score = score; m.level = level; m.radius = radiusFor(score);
        if (levelUp) {
            broadcastRoster();
            // 레벨업 이펙트(4.30): 모두에게 같은 빛기둥(고정). 모든 원에서 그 원이 보이는 화면에 나온다
            broadcast(Map.of("type", "levelFx", "e", m.entityNo, "level", m.level));
        }
    }

    /** 키워드 트렌드 값 변화: 접속 중인 보유자 모두에게 같은 변화량을 더한다(9.2). */
    public void applyKeywordDelta(long keywordId, double deltaScore, Long excludePlayerId) {
        Set<Member> set = holders.get(keywordId);
        if (set == null) return;
        for (Member m : set) {
            if (excludePlayerId != null && m.playerId == excludePlayerId) continue;
            m.score = Math.max(0, m.score + deltaScore);
            m.radius = radiusFor(m.score);
        }
    }

    public void applyHolding(long playerId, long keywordId, boolean added) {
        Member m = byPlayer.get(playerId);
        if (m == null) return;
        if (added) {
            m.keywords.add(keywordId);
            holders.computeIfAbsent(keywordId, x -> new HashSet<>()).add(m);
        } else {
            m.keywords.remove(keywordId);
            Set<Member> set = holders.get(keywordId);
            if (set != null) { set.remove(m); if (set.isEmpty()) holders.remove(keywordId); }
        }
    }

    /** 접속 중인 플레이어의 이름표를 바꾸고 모두에게 명단을 다시 보낸다. */
    public void applyNickname(long playerId, String nickname) {
        Member m = byPlayer.get(playerId);
        if (m == null) return;
        m.nickname = nickname;
        broadcastRoster();
    }

    /** 활성 칭호가 바뀌었다(4.10). 모두에게 명단을 다시 보낸다. */
    public void applyTitle(long playerId, String title) {
        Member m = byPlayer.get(playerId);
        if (m == null) return;
        m.title = title;
        broadcastRoster();
    }

    /** 꾸미기가 바뀌었다(4.9). 모두에게 명단을 다시 보낸다. */
    public void applyLook(long playerId, Skin skin) {
        Member m = byPlayer.get(playerId);
        if (m == null) return;
        m.skin = skin;
        broadcastRoster();
    }

    /** 이동 효과가 바뀌었다(4.23). 모두에게 명단을 다시 보낸다. */
    public void applyTrail(long playerId, String trail) {
        Member m = byPlayer.get(playerId);
        if (m == null) return;
        m.trail = trail;
        broadcastRoster();
    }

    public void protect(long playerId) {
        Member m = byPlayer.get(playerId);
        if (m != null) m.protectUntil = clockSeconds + tuning.loseProtectSeconds();
    }

    public void tick() {
        long now = nanoClock.getAsLong();
        prevTickNanos = tickNanos == 0 ? now - TICK_MS * 1_000_000L : tickNanos;
        tickNanos = now;
        Consumer<Room> t;
        while ((t = tasks.poll()) != null) {
            try { t.accept(this); } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(Room.class).warn("room task failed", e); }
        }
        double dt = TICK_MS / 1000.0;
        clockSeconds += dt;
        tick++;
        Tuning tu = tuning;
        updateArea(dt);
        double half = currentHalf();
        if (tick % 20 == 0) { dropSilent(); expireOffline(); maintainBots(tu); expireCombos(); }
        thinkBots(tu, half);
        for (Member m : members.values()) move(m, dt, tu, half);
        updateZones(half);
        for (Member m : members.values()) m.safe = inSafeZone(m.x, m.y);
        collide(tu);
        eatOrbs();
        spawnOrbs(tu, half);
        for (Member m : members.values()) {
            // 크기 변화는 0.5초에 걸쳐 부드럽게(4.2)
            m.displayRadius += (m.radius - m.displayRadius) * Math.min(1, dt / 0.5 * 3);
        }
        if (tick % SNAPSHOT_EVERY == 0) sendSnapshots(half);
        if (tick % MINIMAP_EVERY == 0) sendMinimap();
        if (tick % 20 == 0) {
            Map<Long, Live> view = new HashMap<>();
            for (Member m : members.values()) if (!m.bot) view.put(m.playerId, new Live(m.playerId, m.entityNo, m.nickname, m.level, m.score)); // 랭킹·정보 팝업은 사람만
            liveView = view;
        }
    }

    // ------------------------------------------------------------ 내부

    /**
     * 이번 틱 구간을 조작이 도착한 시점마다 나눠 움직인다. 방향키를 짧게 눌렀다 떼도 누른 시간만큼만 움직여서
     * 클라이언트 예측과 같은 곳에 멈춘다(틱 경계로 반올림하면 최대 50ms만큼 더 가거나 덜 가서, 멈춘 뒤 화면이 보정된다).
     */
    private void move(Member m, double dt, Tuning tu, double half) {
        double done = 0;
        for (Control c : m.controls) {
            double at = c.at() * dt;
            if (at > done) { advance(m, at - done, tu); done = at; }
            if (c.targetX() != null) setTarget(m, c.targetX(), c.targetY());
            else setInput(m, c.dx(), c.dy());
        }
        m.controls.clear();
        advance(m, dt - done, tu);
        if (m.kbTime > 0) {
            // 튕김은 처음에 빠르고 점점 느려진다(속도 ∝ (남은 시간/T)²). 남은 거리 비율은 (남은 시간/T)³이라
            // 이번 틱에 갈 거리를 정확히 구해서, 틱 간격과 관계없이 같은 거리만큼 밀려난다
            double T = tu.knockbackSeconds(), before = m.kbTime / T, after = Math.max(0, m.kbTime - dt) / T;
            double share = before * before * before - after * after * after;
            m.x += m.kbX * share; m.y += m.kbY * share;
            m.kbTime = Math.max(0, m.kbTime - dt);
        }
        double r = m.displayRadius;
        m.x = clamp(m.x, -half + r, half - r); m.y = clamp(m.y, -half + r, half - r);
    }

    /**
     * h초 동안 목표 속도로 다가가며 움직인다. 속도 차이가 지수로 줄어드는 식을 정확히 풀어서, 나눠 계산해도
     * 한 번에 계산한 것과 같고 클라이언트 예측(Arena)과도 같은 결과가 된다.
     */
    private void advance(Member m, double h, Tuning tu) {
        if (h <= 0) return;
        double speed = tu.maxSpeed() * (clockSeconds < m.dashUntil ? dashTuning.multiplier() : 1); // 크기와 관계없이 모두 같다. 대시 중이면 배수
        double wantX = 0, wantY = 0;
        if (m.dirX != 0 || m.dirY != 0) {
            wantX = m.dirX * speed; wantY = m.dirY * speed;
        } else if (m.targetX != null) {
            double dx = m.targetX - m.x, dy = m.targetY - m.y, d = Math.hypot(dx, dy);
            if (d < 4) { m.targetX = null; m.targetY = null; }
            else {
                double s = speed * Math.min(1, d / 120); // 근처에서 감속
                wantX = dx / d * s; wantY = dy / d * s;
            }
        }
        // 입력도 목표도 없으면 감속해 그 자리에 멈춘다(자동으로 떠다니지 않는다)
        double e = Math.exp(-ACCEL_RATE * h), moved = (1 - e) / ACCEL_RATE;
        m.x += wantX * h + (m.vx - wantX) * moved; m.y += wantY * h + (m.vy - wantY) * moved;
        m.vx = wantX + (m.vx - wantX) * e; m.vy = wantY + (m.vy - wantY) * e;
    }

    // ------------------------------------------------------------ 세이프존(4.22)

    /** 원의 중심이 가운데 고정 존이나 무작위 존 안에 있는지. */
    boolean inSafeZone(double x, double y) { return inSafeZone(x, y, 0); }

    /** 존을 margin만큼 넓혀서 본다(구슬이 존 가장자리에 걸치지 않게). */
    private boolean inSafeZone(double x, double y, double margin) {
        double c = centerSize / 2 + margin;
        if (Math.abs(x) <= c && Math.abs(y) <= c) return true;
        for (SafeZone z : zones) {
            double h = z.size() / 2 + margin;
            if (Math.abs(x - z.x()) <= h && Math.abs(y - z.y()) <= h) return true;
        }
        return false;
    }

    /**
     * 가운데 고정 존 크기를 활동 영역에 맞추고, 시간이 다 됐거나 영역 밖으로 나간 무작위 존을 지운다.
     * 무작위 존은 목표 개수(넓이 비례)보다 적으면 1초에 하나씩 무작위 위치에 생긴다. 바뀌면 모두에게 목록을 다시 보낸다.
     */
    private void updateZones(double half) {
        ZoneTuning zt = zoneTuning;
        double area = half * 2;
        centerSize = Math.min(area / 2, Math.max(zt.centerMin(), area * zt.centerRatio()));
        boolean changed = Math.abs(centerSize - sentCenterSize) > 1;
        changed |= zones.removeIf(z -> z.expiresAt() <= clockSeconds
                || Math.abs(z.x()) + z.size() / 2 > half || Math.abs(z.y()) + z.size() / 2 > half);
        int target = (int) Math.min(zt.max(), Math.round(area * area / 1e8 * zt.density()));
        if (!members.isEmpty() && zones.size() < target && tick % 20 == 0) changed |= spawnZone(half, zt);
        if (changed) {
            sentCenterSize = centerSize;
            broadcast(zonesMessage());
            removeOrbsInZones();
        }
    }

    /** 세이프존이 생기거나 커지면 그 안에 있던 ? 구슬은 사라진다(모두의 화면에서 지운다). */
    private void removeOrbsInZones() {
        Iterator<Map.Entry<Integer, double[]>> it = orbs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> o = it.next();
            if (!inSafeZone(o.getValue()[0], o.getValue()[1], ORB_RADIUS)) continue;
            it.remove();
            broadcast(Map.of("type", "orbEaten", "id", o.getKey(), "e", 0));
        }
    }

    /** 다른 존과 겹치지 않는 무작위 위치에 존 하나. 10번 안에 자리를 못 찾으면 다음 기회에. */
    private boolean spawnZone(double half, ZoneTuning zt) {
        double size = Math.max(zt.zoneMin(), half * 2 * zt.zoneRatio()), gap = 200;
        double range = half - size / 2 - 100;
        if (range <= 0) return false;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int tries = 0; tries < 10; tries++) {
            double x = (rnd.nextDouble() * 2 - 1) * range, y = (rnd.nextDouble() * 2 - 1) * range;
            double reach = (centerSize + size) / 2 + gap;
            if (Math.abs(x) < reach && Math.abs(y) < reach) continue;
            boolean blocked = false;
            for (SafeZone z : zones) {
                double r = (z.size() + size) / 2 + gap;
                if (Math.abs(x - z.x()) < r && Math.abs(y - z.y()) < r) { blocked = true; break; }
            }
            if (blocked) continue;
            zones.add(new SafeZone(nextZone++, x, y, size, clockSeconds + zt.seconds()));
            return true;
        }
        return false;
    }

    /** {type: safeZones, center: 한 변, zones: [[번호, x, y, 한 변, 남은 ms]...]} */
    private Map<String, Object> zonesMessage() {
        List<List<Object>> list = new ArrayList<>();
        for (SafeZone z : zones) list.add(List.of(z.id(), z.x(), z.y(), z.size(), Math.round((z.expiresAt() - clockSeconds) * 1000)));
        return Map.of("type", "safeZones", "center", centerSize, "zones", list);
    }

    /** 처음 온 플레이어의 자리: 가운데 고정 존 안 무작위 위치. */
    private double[] centerSpawn(double area) {
        double c = Math.min(area / 2, Math.max(zoneTuning.centerMin(), area * zoneTuning.centerRatio()));
        double range = Math.max(0, c / 2 - 60);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return new double[]{(rnd.nextDouble() * 2 - 1) * range, (rnd.nextDouble() * 2 - 1) * range};
    }

    /** 테스트용: 지금 무작위 세이프존 수와 가운데 존 한 변. */
    int zoneCount() { return zones.size(); }
    double centerZoneSize() { return centerSize; }

    // ------------------------------------------------------------ ? 구슬(4.20)

    /** 원이 구슬 중심을 덮으면 먹는다. 구슬은 바로 사라지고, 모두에게 누가 먹었는지 알려 먹는 연출을 보여 준다. */
    private void eatOrbs() {
        eatPrizeOrbs();
        if (orbs.isEmpty()) return;
        Iterator<Map.Entry<Integer, double[]>> it = orbs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> o = it.next();
            double[] p = o.getValue();
            Member eater = null;
            for (Member m : members.values()) {
                if (Math.hypot(m.x - p[0], m.y - p[1]) < m.displayRadius + ORB_RADIUS * 0.5) { eater = m; break; }
            }
            if (eater == null) continue;
            it.remove();
            broadcast(Map.of("type", "orbEaten", "id", o.getKey(), "e", eater.entityNo));
            if (eater.bot) botOrb(eater);
            else outbox.requestOrb(this, eater, false);
        }
    }

    /** ! 구슬: ? 구슬과 같이 덮으면 먹고, 먹으면 항상 키워드를 얻는다. 시간이 지나도 안 먹히면 사라진다(4.20). */
    private void eatPrizeOrbs() {
        if (prizeOrbs.isEmpty()) return;
        Iterator<Map.Entry<Integer, double[]>> it = prizeOrbs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> o = it.next();
            double[] p = o.getValue();
            if (clockSeconds >= p[2]) {
                it.remove();
                broadcast(Map.of("type", "orbEaten", "id", o.getKey(), "e", 0));
                continue;
            }
            Member eater = null;
            for (Member m : members.values()) {
                if (Math.hypot(m.x - p[0], m.y - p[1]) < m.displayRadius + ORB_RADIUS * 0.5) { eater = m; break; }
            }
            if (eater == null) continue;
            it.remove();
            broadcast(Map.of("type", "orbEaten", "id", o.getKey(), "e", eater.entityNo));
            if (eater.bot) botPlus(eater, tuning);
            else outbox.requestOrb(this, eater, true);
        }
    }

    /**
     * 불꽃에게 키워드를 모두 잃은 사람 둘레에 ! 구슬을 떨어뜨린다(4.20, 4.25). 개수 = 잃은 🔥활성 키워드 수 × wipeOrbPercent%(올림, 활성이 하나라도 있었으면 최소 1개).
     * 원 가장자리 바깥으로 흩어지고, 활동 영역 안·세이프존 밖에만 놓는다. 방 전체 상한을 넘으면 더 만들지 않는다.
     */
    public void dropPrizeOrbs(long playerId, int lostActiveCount) { dropPrizeOrbs(byPlayer.get(playerId), lostActiveCount); }

    /** 멤버 버전(봇은 byPlayer에 없다) */
    public void dropPrizeOrbs(Member m, int lostActiveCount) {
        if (m == null || lostActiveCount <= 0) return;
        int count = (int) Math.ceil(lostActiveCount * comboTuning.wipeOrbPercent() / 100.0);
        if (comboTuning.wipeOrbPercent() > 0) count = Math.max(1, count);
        double half = currentHalf(), expires = clockSeconds + PRIZE_ORB_SECONDS;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count && prizeOrbs.size() < PRIZE_ORB_MAX; i++) {
            for (int tries = 0; tries < 8; tries++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double dist = m.displayRadius + 50 + rnd.nextDouble() * (120 + Math.sqrt(count) * 45);
                double x = Math.max(-half + ORB_RADIUS, Math.min(half - ORB_RADIUS, m.x + Math.cos(ang) * dist));
                double y = Math.max(-half + ORB_RADIUS, Math.min(half - ORB_RADIUS, m.y + Math.sin(ang) * dist));
                if (inSafeZone(x, y, ORB_RADIUS)) continue;
                int id = nextOrb++;
                prizeOrbs.put(id, new double[]{x, y, expires});
                broadcast(Map.of("type", "orbSpawn", "id", id, "x", x, "y", y, "prize", true));
                break;
            }
        }
    }

    /** 테스트·관리용: 지금 떠 있는 ! 구슬 수. */
    public int prizeOrbCount() { return prizeOrbs.size(); }

    /** 테스트용: 그 자리에 ! 구슬을 놓는다. */
    public int placePrizeOrb(double x, double y) {
        int id = nextOrb++;
        prizeOrbs.put(id, new double[]{x, y, clockSeconds + PRIZE_ORB_SECONDS});
        return id;
    }

    /**
     * 목표 개수 = 활동 영역 넓이 × 밀도(상한 orbMax). 맵이 넓어지거나 사람이 늘어 영역이 커지면 같은 비율로 늘어난다(4.20).
     * 간격마다 목표의 일정 비율(기본 5%, 최소 1개)씩 빈 곳에 만들어서, 넓은 맵도 좁은 맵과 비슷한 시간에 다 찬다.
     * 영역이 좁아져 밖에 남은 구슬은 지운다.
     */
    int orbTarget(Tuning tu, double half) {
        double millionPx = (half * 2) * (half * 2) / 1_000_000;
        return (int) Math.min(tu.orbMax(), Math.round(millionPx * tu.orbDensity()));
    }


    private void spawnOrbs(Tuning tu, double half) {
        Iterator<Map.Entry<Integer, double[]>> it = orbs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> o = it.next();
            if (Math.abs(o.getValue()[0]) > half - ORB_RADIUS || Math.abs(o.getValue()[1]) > half - ORB_RADIUS) {
                it.remove();
                broadcast(Map.of("type", "orbEaten", "id", o.getKey(), "e", 0));
            }
        }
        int target = orbTarget(tu, half);
        if (members.isEmpty() || orbs.size() >= target || clockSeconds < nextOrbAt) return;
        nextOrbAt = clockSeconds + tu.orbSpawnSeconds();
        int batch = Math.min(target - orbs.size(), Math.max(1, (int) Math.ceil(target * tu.orbSpawnPercent() / 100)));
        for (int i = 0; i < batch; i++) spawnOneOrb(half);
    }

    /** 활동 영역 안, 원 바로 위가 아닌 곳에 구슬 하나. 8번 안에 자리를 못 찾으면 이번에는 만들지 않는다. */
    private void spawnOneOrb(double half) {
        double margin = 80;
        for (int tries = 0; tries < 8; tries++) {
            double x = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * (half - margin);
            double y = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * (half - margin);
            boolean blocked = inSafeZone(x, y, ORB_RADIUS); // 세이프존 위에는 만들지 않는다(4.22)
            for (Member m : members.values()) {
                if (blocked) break;
                if (Math.hypot(m.x - x, m.y - y) < m.displayRadius + 60) { blocked = true; break; } // 원 바로 위에는 만들지 않는다
            }
            if (blocked) continue;
            int id = nextOrb++;
            orbs.put(id, new double[]{x, y});
            broadcast(Map.of("type", "orbSpawn", "id", id, "x", x, "y", y));
            return;
        }
    }

    private Map<String, Object> orbListMessage() {
        List<List<Object>> list = new ArrayList<>();
        orbs.forEach((id, p) -> list.add(List.of(id, p[0], p[1], 0)));
        prizeOrbs.forEach((id, p) -> list.add(List.of(id, p[0], p[1], 1))); // 마지막 값: 1이면 ! 구슬
        return Map.of("type", "orbs", "orbs", list);
    }

    /** 테스트·관리용: 지금 떠 있는 구슬 수. */
    public int orbCount() { return orbs.size(); }

    /** 테스트용: 그 자리에 구슬을 놓는다. */
    public int placeOrb(double x, double y) {
        int id = nextOrb++;
        orbs.put(id, new double[]{x, y});
        return id;
    }

    /** 방의 모두(관전 포함)에게 글 메시지를 보낸다. 방 스레드에서 부른다(긴급 안내처럼 서버 전체 알림, 4.29). */
    public void announce(Map<String, Object> msg) { broadcast(msg); }

    private void broadcast(Map<String, Object> msg) {
        for (Member m : members.values()) outbox.sendText(m, msg);
        for (Member sp : spectators) outbox.sendText(sp, msg);
    }

    private void collide(Tuning tu) {
        // 공간 격자로 가까운 아이콘끼리만 검사한다. 큰 아이콘은 걸치는 칸 모두에 넣는다.
        double cell = 200;
        Map<Long, List<Member>> grid = new HashMap<>();
        for (Member m : members.values()) {
            int x0 = (int) Math.floor((m.x - m.displayRadius) / cell), x1 = (int) Math.floor((m.x + m.displayRadius) / cell);
            int y0 = (int) Math.floor((m.y - m.displayRadius) / cell), y1 = (int) Math.floor((m.y + m.displayRadius) / cell);
            for (int gx = x0; gx <= x1; gx++) for (int gy = y0; gy <= y1; gy++)
                grid.computeIfAbsent(((long) gx << 32) ^ (gy & 0xffffffffL), k -> new ArrayList<>()).add(m);
        }
        Set<Long> checked = new HashSet<>();
        for (List<Member> list : grid.values()) {
            for (int i = 0; i < list.size(); i++) for (int j = i + 1; j < list.size(); j++) {
                Member a = list.get(i), b = list.get(j);
                long key = a.entityNo < b.entityNo ? ((long) a.entityNo << 32) | b.entityNo : ((long) b.entityNo << 32) | a.entityNo;
                if (!checked.add(key)) continue;
                if (a.dead || b.dead) continue; // 이번 틱에 앞선 싸움에서 죽었다(4.31)
                if (a.safe || b.safe) continue; // 세이프존에서는 겹쳐 있어도 튕기지 않고 키워드도 잃지 않는다(4.22)
                double dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy), min = a.displayRadius + b.displayRadius;
                if (d >= min) continue;
                if (d < 0.001) { dx = 1; dy = 0; d = 1; }
                double nx = dx / d, ny = dy / d, overlap = min - d;
                Member big = a.displayRadius >= b.displayRadius ? a : b, small = big == a ? b : a;
                double ratio = big.displayRadius / small.displayRadius;
                double sign = small == b ? 1 : -1; // small을 big에서 멀어지는 방향으로
                // 무적(잃은 뒤·입장·리스폰 직후 보호)인 원은 유령처럼 다른 원과 충돌하지 않고 통과한다(4.3): 밀거나 튕기지 않고 싸움도 없다
                if (a.protectUntil > clockSeconds || b.protectUntil > clockSeconds) continue;
                boolean aHot = isFlame(a), bHot = isFlame(b);
                if (aHot != bHot) {
                    // 불꽃이 불꽃 아닌 쪽과 부딪히면(이기는 쪽은 크기와 상관없이 불꽃, 4.25) 물리도 같게 한다: 불꽃은 제자리에 있고 불꽃 아닌 쪽만
                    // 밀려난다(작은 불꽃이 이기면서 튕겨 나가는 어색함을 없앤다). 튕김 세기는 일반 충돌과 같다
                    Member hot = aHot ? a : b, cold = hot == a ? b : a;
                    double away = cold == b ? 1 : -1; // cold를 hot에서 멀어지는 방향으로
                    cold.x += away * nx * overlap; cold.y += away * ny * overlap;
                    double base = hot.displayRadius >= cold.displayRadius ? Math.min(hot.displayRadius / cold.displayRadius, tu.knockbackCap()) : 1;
                    knock(cold, away * nx, away * ny, base, tu);
                    cold.targetX = null;
                } else if (ratio < tu.bigRatio()) {
                    a.x -= nx * overlap / 2; a.y -= ny * overlap / 2;
                    b.x += nx * overlap / 2; b.y += ny * overlap / 2;
                    knock(a, -nx, -ny, 1, tu); knock(b, nx, ny, 1, tu);
                    a.targetX = null; b.targetX = null; // 같이 튕기면 둘 다 목표 해제
                } else {
                    small.x += sign * nx * overlap; small.y += sign * ny * overlap;
                    knock(small, sign * nx, sign * ny, Math.min(ratio, tu.knockbackCap()), tu); small.targetX = null;
                }
                boolean aFlame = isFlame(a), bFlame = isFlame(b);
                if (aFlame != bFlame) {
                    // 불꽃이 붙은 쪽(사람·봇)이 불꽃 없는 쪽과 부딪히면 점수와 상관없이 이기고, 상대는 키워드를 모두 잃는다(4.25)
                    Member hot = aFlame ? a : b, cold = hot == a ? b : a;
                    if (cold.protectUntil <= clockSeconds && cold.bot && !cold.botKeywords.isEmpty()) {
                        botWipe(cold, hot, tu); // 봇의 (메모리) 키워드 점수를 모두 잃는다
                    } else if (cold.protectUntil <= clockSeconds && !cold.bot && !cold.keywords.isEmpty()) {
                        cold.protectUntil = clockSeconds + tu.loseProtectSeconds();
                        shield(cold, tu);
                        outbox.requestWipe(this, cold, hot); // 이긴 쪽이 봇이어도 모두 잃는다
                    } else if (cold.protectUntil <= clockSeconds) {
                        die(cold, hot); // 이미 키워드가 없으면 죽는다(사람·봇, 4.31)
                    }
                // 현재 유행도가 낮은 쪽이 키워드 하나를 잃는다(4.3). 같으면 없음. 둘 다 불꽃이어도 이 규칙이다
                } else if (a.score != b.score) {
                    Member loser = a.score < b.score ? a : b, winner = loser == a ? b : a;
                    if (loser.protectUntil <= clockSeconds && loser.bot && !loser.botKeywords.isEmpty()) {
                        loser.protectUntil = clockSeconds + tu.loseProtectSeconds();
                        BotKw lost = botLose(loser, tu); // 봇도 사람처럼 (메모리의) 실제 키워드 하나를 잃는다(4.21)
                        shield(loser, tu);
                        comboLose(loser, winner); // 잃으면 봇의 콤보와 불꽃도 사라진다(4.25)
                        // 이긴 사람은 봇이 잃은 바로 그 키워드를 빼앗고, 이긴 쪽이 봇이면 그 키워드가 그 봇의 메모리 목록에 들어간다
                        if (!winner.bot) outbox.requestBotWin(this, winner, loser, lost.id());
                        else { botGain(winner, lost.id()); comboWin(winner, true); }
                    } else if (loser.protectUntil <= clockSeconds && !loser.keywords.isEmpty()) {
                        loser.protectUntil = clockSeconds + tu.loseProtectSeconds();
                        shield(loser, tu);
                        outbox.requestLose(this, loser, winner); // 이긴 쪽이 봇이어도 잃는다
                    } else if (loser.protectUntil <= clockSeconds) {
                        die(loser, winner); // 잃을 키워드가 없는 채로 지면 죽는다(사람·봇, 4.31)
                    }
                }
            }
        }
    }

    /**
     * 대시(4.27): 쿨타임이 지났고 움직이려는 중이면 시작한다. 시작하면 본인에게 {dash: 지속 ms, 쿨타임 ms, 배수}를 알려
     * 클라이언트 예측이 같은 속도로 가게 하고, 모두에게 {dashFx: 원 번호, 지속 ms}를 보내 그 원에 강조 효과를 그리게 한다. 아니면 아무 일도 없다(쿨타임도 쓰지 않는다).
     */
    public void dash(Member m) {
        if (m.bot || members.get(m.entityNo) != m) return;
        DashTuning d = dashTuning;
        if (clockSeconds < m.dashReadyAt) return;
        boolean moving = m.dirX != 0 || m.dirY != 0 || m.targetX != null;
        if (!moving || d.seconds() <= 0 || d.multiplier() <= 1) return;
        m.dashUntil = clockSeconds + d.seconds();
        m.dashReadyAt = clockSeconds + Math.max(d.seconds(), d.cooldownSeconds());
        outbox.sendText(m, Map.of("type", "dash", "ms", Math.round(d.seconds() * 1000), "cooldownMs", Math.round(Math.max(d.seconds(), d.cooldownSeconds()) * 1000), "mult", d.multiplier()));
        broadcast(Map.of("type", "dashFx", "e", m.entityNo, "ms", Math.round(d.seconds() * 1000))); // 모두의 화면에 대시 강조 효과(4.27)
    }

    /** 콤보 불꽃이 붙었는지(사람과 봇 모두, 4.25) */
    private boolean isFlame(Member m) { return m.combo >= comboTuning.flameAt(); }

    /** 키워드를 잃은 원은 잠깐 무적이다(4.3). 모두의 화면에 그 원의 보호막과 카운트다운이 보이게 알린다. */
    private void shield(Member m, Tuning tu) {
        broadcast(Map.of("type", "shield", "e", m.entityNo, "ms", (long) (tu.loseProtectSeconds() * 1000)));
    }

    /**
     * 튕김(4.3): 밀려나는 거리 = 최대 속도 × knockbackDistance(초) × 세기. 같은 크기끼리는 세기 1, 작은 쪽은 크기 비율만큼(상한 3배).
     * 사람에게는 같은 튕김을 알려서 클라이언트 예측도 같이 밀려나게 한다(크게 밀려도 화면이 순간 이동하지 않게).
     */
    private void knock(Member m, double nx, double ny, double strength, Tuning tu) {
        double dist = tu.maxSpeed() * tu.knockbackDistance() * strength;
        m.kbX = nx * dist; m.kbY = ny * dist; m.kbTime = tu.knockbackSeconds();
        if (!m.bot) outbox.sendText(m, Map.of("type", "knock", "dx", m.kbX, "dy", m.kbY, "ms", (long) (tu.knockbackSeconds() * 1000)));
    }

    /** 클라이언트가 알린 보이는 범위(가로·세로 절반, px)를 받는다. 값은 한도 안으로 맞춘다(너무 작아 화면이 비거나 너무 커서 전부 보내게 되는 것을 막는다). */
    public void setView(Member m, double halfW, double halfH) {
        if (members.get(m.entityNo) != m) return;
        m.viewHalfW = clamp(halfW, VIEW_MIN, VIEW_MAX);
        m.viewHalfH = clamp(halfH, VIEW_MIN, VIEW_MAX);
    }

    static final double VIEW_MIN = 300, VIEW_MAX = 30000;
    /** 보이는 범위에 더하는 여유: 범위의 비율과 고정 거리. 가장자리에서 원이 갑자기 나타나지 않게 한다(스냅샷은 초당 10번이라 그 사이 움직이는 거리도 포함) */
    static final double VIEW_MARGIN_RATIO = 0.3, VIEW_MARGIN_PX = 400;
    private static final double SNAP_CELL = 1000;

    /**
     * 스냅샷(관심 영역 전송): [u8 type=1][u32 tick][f32 half][u16 n] + n × [u32 entity][f32 x][f32 y][f32 r][f32 score][u16 level][u8 0=예약].
     * 사람마다 그 사람 화면 둘레(보이는 범위 + 여유)에 걸친 원만 담는다. 내 원은 늘 들어간다(내 위치 보정에 쓴다).
     * 방 전원이 범위 안이면(작은 방) 모두에게 같은 배열을 그대로 보낸다. 관전자와 죽어서 구경 중인 사람은 전체를 받는다.
     * 내 원은 클라이언트가 welcome의 entityNo로 찾는다.
     */
    private void sendSnapshots(double half) {
        int n = members.size();
        final int REC = 23;
        Member[] order = members.values().toArray(new Member[0]);
        byte[] recs = new byte[n * REC];
        ByteBuffer rb = ByteBuffer.wrap(recs);
        double maxR = 0;
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Member m = order[i];
            rb.putInt(m.entityNo).putFloat((float) m.x).putFloat((float) m.y)
                    .putFloat((float) m.displayRadius).putFloat((float) m.score).putShort((short) m.level).put((byte) 0);
            maxR = Math.max(maxR, m.displayRadius);
            cells.computeIfAbsent(cellKey(Math.floor(m.x / SNAP_CELL), Math.floor(m.y / SNAP_CELL)), k -> new ArrayList<>()).add(i);
        }
        byte[] all = withHeader(recs, n, half);
        for (Member m : order) {
            if (m.bot || m.offline) continue;
            double ex = m.viewHalfW * (1 + VIEW_MARGIN_RATIO) + VIEW_MARGIN_PX, ey = m.viewHalfH * (1 + VIEW_MARGIN_RATIO) + VIEW_MARGIN_PX;
            List<Integer> pick = new ArrayList<>();
            int cx0 = (int) Math.floor((m.x - ex - maxR) / SNAP_CELL), cx1 = (int) Math.floor((m.x + ex + maxR) / SNAP_CELL);
            int cy0 = (int) Math.floor((m.y - ey - maxR) / SNAP_CELL), cy1 = (int) Math.floor((m.y + ey + maxR) / SNAP_CELL);
            for (int cx = cx0; cx <= cx1; cx++) for (int cy = cy0; cy <= cy1; cy++) {
                List<Integer> l = cells.get(cellKey(cx, cy));
                if (l == null) continue;
                for (int idx : l) {
                    Member o = order[idx];
                    if (o == m || (Math.abs(o.x - m.x) <= ex + o.displayRadius && Math.abs(o.y - m.y) <= ey + o.displayRadius)) pick.add(idx);
                }
            }
            if (pick.size() >= n) { outbox.send(m, ByteBuffer.wrap(all)); continue; } // 전원이 범위 안: 같은 배열을 나눠 쓴다
            byte[] mine = new byte[pick.size() * REC];
            int p = 0;
            for (int idx : pick) { System.arraycopy(recs, idx * REC, mine, p, REC); p += REC; }
            outbox.send(m, ByteBuffer.wrap(withHeader(mine, pick.size(), half)));
        }
        for (Member sp : spectators) outbox.send(sp, ByteBuffer.wrap(all)); // 관전자·죽어서 구경 중인 사람은 전체
    }

    private static long cellKey(double cx, double cy) { return ((long) cx << 32) ^ ((long) cy & 0xffffffffL); }

    private byte[] withHeader(byte[] recs, int count, double half) {
        ByteBuffer b = ByteBuffer.allocate(11 + recs.length);
        b.put((byte) 1).putInt((int) tick).putFloat((float) half).putShort((short) count).put(recs);
        return b.array();
    }

    /**
     * 미니맵: 큰 아이콘 상위 20명(4.2). 관심 영역과 관계없이 모두에게 같은 내용을 보낸다(내 위치는 클라이언트가 그린다).
     * [u8 type=2][u8 n] + n × [u32 entity][f32 x][f32 y][f32 r]
     */
    private void sendMinimap() {
        if (members.isEmpty()) return;
        List<Member> top = members.values().stream()
                .sorted(Comparator.comparingDouble((Member m) -> m.displayRadius).reversed())
                .limit(MINIMAP_TOP).toList();
        ByteBuffer buf = ByteBuffer.allocate(2 + top.size() * 16);
        buf.put((byte) 2).put((byte) top.size());
        for (Member m : top) buf.putInt(m.entityNo).putFloat((float) m.x).putFloat((float) m.y).putFloat((float) m.displayRadius);
        byte[] bytes = buf.array();
        for (Member m : members.values()) outbox.send(m, ByteBuffer.wrap(bytes));
        for (Member sp : spectators) outbox.send(sp, ByteBuffer.wrap(bytes));
    }

    private Map<String, Object> rosterMessage() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Member m : members.values()) {
            // 칭호·테두리·효과는 없을 수 있어서(null) Map.of 대신 HashMap을 쓴다
            Map<String, Object> e = new HashMap<>();
            e.put("e", m.entityNo); e.put("nickname", m.nickname); e.put("level", m.level); e.put("bot", m.bot);
            Skin k = m.skin;
            e.put("title", m.title); e.put("bg", k.background()); e.put("borderColor", k.borderColor()); e.put("borderWidth", k.borderWidth());
            e.put("trail", m.trail); e.put("art", k.art()); e.put("borderEffect", k.borderEffect());
            e.put("nameColor", k.nameColor()); e.put("nameStyle", k.nameStyle());
            e.put("combo", m.combo);
            list.add(e);
        }
        return Map.of("type", "roster", "players", list, "flameAt", comboTuning.flameAt()); // 불꽃을 그릴 콤보 기준(4.25)
    }

    private void broadcastRoster() {
        Map<String, Object> msg = rosterMessage();
        for (Member m : members.values()) outbox.sendText(m, msg);
        for (Member sp : spectators) outbox.sendText(sp, msg);
    }

    /**
     * 응답 없는 접속 정리: 클라이언트는 2초마다 ping을 보낸다. SILENT_SECONDS 동안 아무 메시지도 없으면(네트워크가 끊겼는데 닫힘 신호가
     * 오지 않은 경우, 중간 프록시가 서버 쪽 연결을 남겨 둔 경우) 방에서 빼고 연결을 닫는다. 그러지 않으면 랭킹·방에 유령 접속자가 남는다.
     * 백그라운드 탭은 브라우저가 타이머를 1분 간격까지 늦추므로 그보다 넉넉하게 잡는다.
     */
    static final long SILENT_SECONDS = 90;

    private void dropSilent() {
        long now = nanoClock.getAsLong();
        for (Member m : List.copyOf(members.values())) {
            if (m.bot || m.offline || now - m.lastHeardNanos < SILENT_SECONDS * 1_000_000_000L) continue;
            disconnect(m, m.session); // 응답 없는 접속도 끊긴 것으로 보고 원은 남은 시간만큼 둔다(4.32)
            outbox.close(m);
        }
        for (Member sp : List.copyOf(spectators)) {
            if (now - sp.lastHeardNanos < SILENT_SECONDS * 1_000_000_000L) continue;
            removeSpectator(sp);
            outbox.close(sp);
        }
    }

    // ------------------------------------------------------------ NPC 봇(4.21)

    private long botSeq;
    /** EXP → 레벨. 서버가 레벨표(levels)로 바꿔 끼운다. 기본값은 시드와 같은 곡선(4.9). */
    private java.util.function.LongToIntFunction levelForExp = Room::defaultLevelForExp;

    public void setLevelForExp(java.util.function.LongToIntFunction f) { this.levelForExp = f; }

    static int defaultLevelForExp(long exp) {
        long[] low = {0, 0, 1, 3, 8, 13, 22, 33, 50, 72, 100};
        int level = 1;
        for (int lv = 2; lv <= 100; lv++) {
            long need = lv <= 10 ? low[lv] : Math.round(100 * Math.pow(lv / 10.0, 2));
            if (exp >= need) level = lv; else break;
        }
        return level;
    }

    /** 사람 수. 방 정원(1000명) 검사와 접속 인원에 쓴다. 봇은 세지 않는다. */
    private int humanCount() {
        int n = 0;
        for (Member m : members.values()) if (!m.bot) n++;
        return n;
    }

    /** 봇 유행도 상한: 그 방에 접속 중인 사람들의 현재 유행도 평균 × botCapRatio(기본 1, 즉 평균). 봇은 평균에 넣지 않는다. */
    double botCap(Tuning tu) {
        double sum = 0;
        int n = 0;
        for (Member m : members.values()) if (!m.bot) { sum += m.score; n++; }
        return n == 0 ? 0 : Math.floor(sum / n * tu.botCapRatio());
    }

    /**
     * 1초마다: 사람이 있으면 사람 + 봇이 botPlayers가 되도록 봇을 하나씩 넣거나 뺀다. 사람이 많아도 봇은 botMinPlayers 이상 둔다
     * (사람이 없으면 봇도 없다).
     * 사람들 평균이 내려가 상한을 넘은 봇은 키워드를 하나씩 내려놓고, EXP만으로도 넘으면 빼고 새 봇으로 바꾼다.
     */
    void maintainBots(Tuning tu) {
        double cap = botCap(tu);
        for (Member b : List.copyOf(members.values())) {
            if (!b.bot || b.score <= cap) continue;
            if (!b.botKeywords.isEmpty()) { b.botKeywords.removeFirst(); botRecompute(b, tu); }
            else { members.remove(b.entityNo); broadcastRoster(); }
        }
        int humans = humanCount(), bots = members.size() - humans;
        int want = humans == 0 ? 0 : Math.max(tu.botMinPlayers(), tu.botPlayers() - humans);
        if (bots < want) addBot(tu);
        else if (bots > want) members.values().stream().filter(m -> m.bot).findFirst().ifPresent(b -> {
            members.remove(b.entityNo);
            broadcastRoster();
        });
    }

    /**
     * 사람과 같은 기준으로 봇을 만든다: EXP를 정하고(레벨은 레벨표로), 그 EXP 안에서 키워드 몇 개를 가진 것으로 한다.
     * 유행도 = min(EXP, 100) + 키워드 점수 합이고 상한(사람 평균)을 넘지 않는다.
     */
    private void addBot(Tuning tu) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double cap = botCap(tu);
        Member m = new Member(nextEntity++, -(++botSeq), null);
        m.bot = true;
        m.nickname = "Bot";
        // EXP를 정하고, 많이 추가한 사람은 대개 가진 키워드도 많으므로 EXP에 비례해 가진 키워드 수를 정한다
        // (최근 30번 추가 중 30~100%를 아직 가진 것으로 본다. 추가한 횟수(EXP)보다 많을 수는 없다).
        // 합이 상한(사람 평균)을 넘으면 키워드만 빼지 않고 EXP 범위를 줄여 다시 뽑는다. 작은 원인데 레벨만 높은 봇이 되지 않게
        double expMax = Math.min(300, cap);
        for (int attempt = 0; attempt < 12; attempt++) {
            m.botExp = expMax < 1 ? 0 : rnd.nextLong((long) expMax + 1);
            if (Math.min(m.botExp, tu.expScoreCap()) <= cap) break; // EXP만으로 상한을 넘으면 범위를 줄여 다시 뽑는다
            expMax *= 0.7;
            if (attempt == 11) m.botExp = 0; // 끝내 못 맞추면 가장 작은 봇
        }
        // 그 나라의 🔥 키워드 중에서 무작위로 가져오되, 합이 상한(사람 평균)을 넘지 않는 것만 담는다(없으면 키워드 없는 봇)
        long want = Math.round(Math.min(m.botExp, 30) * (0.3 + rnd.nextDouble() * 0.7));
        double room = cap - Math.min(m.botExp, tu.expScoreCap());
        List<CatalogKw> pool = new ArrayList<>(catalog.values());
        Collections.shuffle(pool, rnd);
        for (CatalogKw k : pool) {
            if (m.botKeywords.size() >= want) break;
            if (k.value() > room) continue;
            m.botKeywords.add(new BotKw(k.id()));
            room -= k.value();
        }
        m.botStyle = rnd.nextInt(3);
        m.botReact = 0.25 + rnd.nextDouble() * 0.45;
        m.botHeading = rnd.nextDouble() * Math.PI * 2;
        m.botNextPlusAt = clockSeconds + 15 + rnd.nextDouble() * 30;
        botRecompute(m, tu);
        m.displayRadius = m.radius;
        double area = currentArea();
        // 죽은 봇을 다시 채울 때는 사람처럼 가운데 세이프존에서 나온다(4.31)
        boolean respawn = botRespawns > 0;
        double[] spawn = respawn ? centerSpawn(area) : findSpawn(area);
        if (respawn) botRespawns--;
        m.x = spawn[0]; m.y = spawn[1];
        members.put(m.entityNo, m);
        broadcastRoster();
    }

    /** 봇의 유행도와 레벨을 사람과 같은 식으로 다시 정한다: min(EXP, 100) + Σ 키워드 점수, 레벨은 레벨표. */
    private void botRecompute(Member b, Tuning tu) {
        b.score = Math.min(b.botExp, tu.expScoreCap()) + botValue(b);
        b.radius = radiusFor(b.score);
        int lv = levelForExp.applyAsInt(b.botExp);
        if (lv != b.level) { b.level = lv; broadcastRoster(); }
    }

    /** 방 메모리에서 봇이 가진 키워드의 현재 유행도 합. 목록에 없어진(비활성·금지·삭제된) 키워드는 세지 않는다. */
    private long botValue(Member b) {
        long sum = 0;
        for (BotKw k : b.botKeywords) { CatalogKw c = catalog.get(k.id()); if (c != null) sum += c.value(); }
        return sum;
    }

    private static boolean holds(Member b, long keywordId) {
        for (BotKw k : b.botKeywords) if (k.id() == keywordId) return true;
        return false;
    }

    /**
     * 봇이 키워드 하나를 얻는다(4.21). 이긴 봇이 진 쪽(사람·봇)이 잃은 키워드를 가져가는 경우이고, 이미 가졌거나 지금 🔥 목록에 없으면 건너뛴다.
     * 사람이 빼앗는 것과 달리 DB에는 아무것도 쓰지 않는다.
     */
    private void botGain(Member b, long keywordId) {
        if (b.dead || members.get(b.entityNo) != b || !catalog.containsKey(keywordId) || holds(b, keywordId)) return;
        b.botKeywords.add(new BotKw(keywordId));
        botRecompute(b, tuning);
    }

    /** 봇의 "추가": 사람처럼 EXP +1과 🔥 키워드 하나(메모리). 상한(사람 평균)을 넘으면 하지 않는다. */
    private void botPlus(Member b, Tuning tu) {
        double headroom = botCap(tu) - (Math.min(b.botExp + 1, tu.expScoreCap()) + botValue(b));
        List<CatalogKw> fit = new ArrayList<>();
        for (CatalogKw k : catalog.values()) if (k.value() <= headroom && !holds(b, k.id())) fit.add(k);
        if (fit.isEmpty()) return;
        b.botExp++;
        b.botKeywords.add(new BotKw(fit.get(ThreadLocalRandom.current().nextInt(fit.size())).id()));
        botRecompute(b, tu);
    }

    /** 봇이 충돌로 지면 사람처럼 가진 키워드 하나를 잃는다. 호출하는 쪽이 비어 있지 않은지 보았다. 잃은 키워드를 돌려준다. */
    private BotKw botLose(Member b, Tuning tu) {
        BotKw lost = b.botKeywords.remove(ThreadLocalRandom.current().nextInt(b.botKeywords.size()));
        botRecompute(b, tu);
        return lost;
    }

    /** 봇이 사람에게서 키워드를 빼앗았다(4.21): 이긴 봇이 그 키워드를 메모리에 가진다. 이미 가졌거나 🔥 목록에 없으면 건너뛴다. */
    public void botTakes(Member bot, long keywordId) { botGain(bot, keywordId); }

    /** 방이 쓰는 키워드 목록 한 줄(4.21): 번호, 이름, 지금 유행도 */
    public record CatalogKw(long id, String text, long value) {}
    /** 봇이 가진 키워드 하나(번호만 들고, 이름·점수는 목록에서 찾는다) */
    public record BotKw(long id) {}

    private Map<Long, CatalogKw> catalog = new HashMap<>();

    /**
     * 이 나라의 🔥 키워드 목록을 새로 받는다(서버가 주기적으로 읽어 온다, 4.21). 봇이 키워드를 고르고 점수를 계산하는 데 쓰며,
     * 목록에서 사라진(비활성·금지·삭제) 키워드는 봇 목록에서도 뺀다.
     */
    public void setKeywordCatalog(Collection<CatalogKw> list) {
        Map<Long, CatalogKw> next = new HashMap<>();
        for (CatalogKw k : list) next.put(k.id(), k);
        catalog = next;
        for (Member b : members.values()) {
            if (!b.bot) continue;
            b.botKeywords.removeIf(k -> !next.containsKey(k.id()));
            botRecompute(b, tuning);
        }
    }

    /**
     * 불꽃에 맞은 봇은 가진 키워드 점수를 모두 잃는다(4.25). 사람과 같이 3초 무적, 이긴 쪽 콤보 +1, 진 쪽 콤보·불꽃 없앰,
     * 잃은 개수의 일부만큼 ! 구슬이 떨어진다. 이긴 쪽이 사람이면 알림을 보낸다(봇의 키워드는 이름이 없어 개수와 점수만 알린다).
     */
    private void botWipe(Member cold, Member hot, Tuning tu) {
        int n = cold.botKeywords.size();
        long score = botValue(cold);
        cold.botKeywords.clear();
        botRecompute(cold, tu);
        cold.protectUntil = clockSeconds + tu.loseProtectSeconds();
        shield(cold, tu);
        fightResult(hot, cold, true);
        dropPrizeOrbs(cold, n); // 봇의 키워드는 모두 🔥로 본다
        if (!hot.bot) {
            outbox.sendText(hot, Map.of("type", "hit", "target", cold.nickname, "wiped", n, "score", score));
            outbox.requestSteal(this, hot); // 모두 잃게 해도 공격 성공 한 번 = 빼앗은 횟수 +1(4.3)
        }
    }

    /** 봇이 ? 구슬을 먹으면 사람과 같은 확률로 "추가"하고, 나머지는 가진 키워드 점수 하나를 잃는다(없으면 꽝). */
    private void botOrb(Member b) {
        Tuning tu = tuning;
        if (ThreadLocalRandom.current().nextDouble(100) < tu.orbActivePercent()) botPlus(b, tu);
        else if (!b.botKeywords.isEmpty()) botLose(b, tu);
    }

    /** 봇도 가끔 스스로 키워드를 추가하고, 오래된 키워드는 사라진다(사람의 24시간 삭제처럼 줄었다 늘었다 한다). */
    private void botLife(Member b, Tuning tu) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        b.botNextPlusAt = clockSeconds + 15 + rnd.nextDouble() * 30;
        if (!b.botKeywords.isEmpty() && rnd.nextDouble() < 0.35) { b.botKeywords.removeFirst(); botRecompute(b, tu); }
        else botPlus(b, tu);
    }

    /**
     * 봇이 사람처럼 움직이게 한다. 봇마다 반응 속도(0.25~0.7초)가 달라 그 간격으로 다시 판단한다.
     * 1) 나를 이길 원이 가까우면 반대쪽으로 도망치되, 벽에 몰리지 않게 안쪽으로 꺾고 지그재그로 흔든다.
     * 2) 내가 이길 원이 보이면 그 원이 갈 곳을 앞질러 쫓는다. 4~7초 안에 못 잡으면 포기하고 한동안 쫓지 않는다.
     * 3) 아니면 다른 봇이 노리지 않는 가까운 구슬로 간다.
     * 4) 할 일이 없으면 진행 방향을 조금씩 틀며 곡선으로 돌아다니고, 가끔 잠깐 멈춘다.
     * 15~45초마다 스스로 키워드를 추가하거나 오래된 키워드가 사라진다(botLife).
     */
    private void thinkBots(Tuning tu, double half) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Set<Integer> claimed = new HashSet<>();
        for (Member m : members.values()) if (m.bot && m.botOrb != null && orbs.containsKey(m.botOrb)) claimed.add(m.botOrb);
        BotGrid grid = null; // 이번 틱에 생각할 봇이 있을 때만, 한 번 만든다
        for (Member b : members.values()) {
            if (!b.bot) continue;
            if (clockSeconds >= b.botNextPlusAt) botLife(b, tu);
            if (clockSeconds < b.botThinkAt) continue;
            b.botThinkAt = clockSeconds + b.botReact * (0.7 + rnd.nextDouble() * 0.6);
            double fleeRange = b.botStyle == 0 ? 900 : 450, chaseRange = b.botStyle == 1 ? 1000 : 450, orbRange = b.botStyle == 2 ? 1600 : 700;
            Member threat = null, prey = null;
            double threatD = Double.MAX_VALUE, preyD = Double.MAX_VALUE;
            if (grid == null) grid = new BotGrid(members.values(), orbs);
            // 방 전체가 아니라 이 봇 주변 칸에 있는 원만 본다(공간 격자). 볼 수 있는 가장 먼 거리 = 도망·쫓기 범위 + 내 반지름 + 가장 큰 원의 반지름
            double look = Math.max(fleeRange, chaseRange) + b.displayRadius + grid.maxRadius;
            for (Member o : grid.membersNear(b.x, b.y, look)) {
                if (o == b) continue;
                double d = Math.hypot(o.x - b.x, o.y - b.y) - o.displayRadius - b.displayRadius;
                // 세이프존 안의 원은 잡을 수 없고, 내가 세이프존 안이면 도망칠 필요가 없다(4.22)
                if (!b.safe && !o.safe && o.score > b.score && d < fleeRange && d < threatD) { threat = o; threatD = d; }
                if (!o.safe && o.score < b.score && d < chaseRange && d < preyD) { prey = o; preyD = d; }
            }
            double limit = half - 60 - b.displayRadius;

            if (threat != null) {
                // 도망: 위협 반대 방향 + 벽에서 안쪽으로 미는 힘, 그리고 좌우로 흔들기
                double dx = b.x - threat.x, dy = b.y - threat.y, len = Math.max(1, Math.hypot(dx, dy));
                double ax = dx / len, ay = dy / len;
                double wall = 500;
                if (b.x > limit - wall) ax -= (b.x - (limit - wall)) / wall;
                if (b.x < -limit + wall) ax += (-limit + wall - b.x) / wall;
                if (b.y > limit - wall) ay -= (b.y - (limit - wall)) / wall;
                if (b.y < -limit + wall) ay += (-limit + wall - b.y) / wall;
                double ang = Math.atan2(ay, ax) + (rnd.nextDouble() - 0.5) * 0.9;
                b.botHeading = ang; b.botIdleUntil = 0; b.botChaseFrom = 0;
                setTarget(b, clamp(b.x + Math.cos(ang) * 550, -limit, limit), clamp(b.y + Math.sin(ang) * 550, -limit, limit));
                continue;
            }
            if (clockSeconds < b.botIdleUntil) continue; // 잠깐 쉬는 중

            if (prey != null && clockSeconds >= b.botChaseCooldown) {
                if (b.botChaseFrom == 0) b.botChaseFrom = clockSeconds;
                if (clockSeconds - b.botChaseFrom > 4 + (b.entityNo % 4)) {
                    // 오래 쫓아도 못 잡으면 포기한다(끝없이 따라오지 않게)
                    b.botChaseFrom = 0;
                    b.botChaseCooldown = clockSeconds + 5 + rnd.nextDouble() * 5;
                } else {
                    double lead = 0.4 + rnd.nextDouble() * 0.4; // 상대가 갈 곳을 조금 앞질러 노린다
                    setTarget(b, clamp(prey.x + prey.vx * lead, -limit, limit), clamp(prey.y + prey.vy * lead, -limit, limit));
                    continue;
                }
            } else if (prey == null) {
                b.botChaseFrom = 0;
            }

            double[] orb = nearestFreeOrb(b, orbRange, claimed, grid);
            if (orb != null) {
                b.botOrb = (int) orb[2];
                claimed.add(b.botOrb);
                setTarget(b, orb[0], orb[1]);
                continue;
            }
            b.botOrb = null;

            // 돌아다니기: 진행 방향을 조금씩 틀어 곡선으로, 벽 가까이면 안쪽으로 꺾는다. 가끔 잠깐 멈춘다
            if (rnd.nextDouble() < 0.06) {
                b.botIdleUntil = clockSeconds + 0.6 + rnd.nextDouble() * 1.6;
                b.targetX = null; b.targetY = null;
                setInput(b, 0, 0);
                continue;
            }
            b.botHeading += (rnd.nextDouble() - 0.5) * 0.9;
            double nx = b.x + Math.cos(b.botHeading) * 400, ny = b.y + Math.sin(b.botHeading) * 400;
            if (Math.abs(nx) > limit || Math.abs(ny) > limit) {
                b.botHeading = Math.atan2(-b.y, -b.x) + (rnd.nextDouble() - 0.5) * 1.2; // 가운데 쪽으로 돌아선다
                nx = b.x + Math.cos(b.botHeading) * 400; ny = b.y + Math.sin(b.botHeading) * 400;
            }
            setTarget(b, clamp(nx, -limit, limit), clamp(ny, -limit, limit));
        }
    }

    /** 다른 봇이 노리지 않는 가장 가까운 구슬 {x, y, 번호}. */
    private double[] nearestFreeOrb(Member b, double range, Set<Integer> claimed, BotGrid grid) {
        double[] best = null;
        double bestD = range;
        for (Map.Entry<Integer, double[]> o : grid.orbsNear(b.x, b.y, range)) {
            if (claimed.contains(o.getKey()) && !o.getKey().equals(b.botOrb)) continue;
            double[] p = o.getValue();
            double d = Math.hypot(p[0] - b.x, p[1] - b.y);
            if (d < bestD) { bestD = d; best = new double[]{p[0], p[1], o.getKey()}; }
        }
        return best;
    }

    /**
     * 봇이 주변만 보게 하는 공간 격자(4.21). 맵을 한 변 CELL(500px)인 정사각형 칸으로 나누고, 원과 구슬을 중심이 속한 칸에 넣어 둔다.
     * 봇은 방 전체를 훑는 대신 자기 주변 칸의 것만 꺼내 본다(봇 수 × 인원 → 봇 수 × 주변 수). 이번 틱에 한 번 만든다.
     */
    private static final class BotGrid {
        static final double CELL = 500;
        final Map<Long, List<Member>> members = new HashMap<>();
        final Map<Long, List<Map.Entry<Integer, double[]>>> orbs = new HashMap<>();
        double maxRadius;

        BotGrid(Collection<Member> ms, Map<Integer, double[]> orbMap) {
            for (Member m : ms) {
                members.computeIfAbsent(key(m.x, m.y), k -> new ArrayList<>()).add(m);
                maxRadius = Math.max(maxRadius, m.displayRadius);
            }
            for (Map.Entry<Integer, double[]> o : orbMap.entrySet()) {
                orbs.computeIfAbsent(key(o.getValue()[0], o.getValue()[1]), k -> new ArrayList<>()).add(o);
            }
        }

        private static int cell(double v) { return (int) Math.floor(v / CELL); }
        private static long key(int cx, int cy) { return ((long) cx << 32) ^ (cy & 0xffffffffL); }
        private static long key(double x, double y) { return key(cell(x), cell(y)); }

        /** 중심이 (x, y)에서 reach 안쪽 사각형에 걸친 칸의 원들(거리 판단은 부르는 쪽이 다시 한다) */
        List<Member> membersNear(double x, double y, double reach) {
            List<Member> out = new ArrayList<>();
            for (int cx = cell(x - reach); cx <= cell(x + reach); cx++)
                for (int cy = cell(y - reach); cy <= cell(y + reach); cy++) {
                    List<Member> l = members.get(key(cx, cy));
                    if (l != null) out.addAll(l);
                }
            return out;
        }

        List<Map.Entry<Integer, double[]>> orbsNear(double x, double y, double reach) {
            List<Map.Entry<Integer, double[]>> out = new ArrayList<>();
            for (int cx = cell(x - reach); cx <= cell(x + reach); cx++)
                for (int cy = cell(y - reach); cy <= cell(y + reach); cy++) {
                    List<Map.Entry<Integer, double[]>> l = orbs.get(key(cx, cy));
                    if (l != null) out.addAll(l);
                }
            return out;
        }
    }

    /** 인원이 적으면 활동 영역을 좁힌다(4.2). 1000명에서 기준 맵 한 변, 아무리 적어도 그 1/4보다 좁히지 않는다. */
    double activeArea(int n) {
        double full = tuning.worldSize();
        return clamp(full * Math.sqrt(Math.max(n, 1) / 1000.0), full / 4, full);
    }

    public double radiusFor(double score) {
        Tuning t = tuning;
        return t.minRadius() + (t.maxRadius() - t.minRadius()) * Math.min(score, t.scoreCap()) / t.scoreCap();
    }

    /** 밀도가 낮은 곳: 무작위 후보 12개 중 가장 가까운 아이콘과의 거리가 가장 먼 곳. */
    private double[] findSpawn(double area) {
        double half = area / 2 - 100;
        double[] best = {0, 0};
        double bestD = -1;
        for (int i = 0; i < 12; i++) {
            double x = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * half;
            double y = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * half;
            double nearest = Double.MAX_VALUE;
            for (Member m : members.values()) nearest = Math.min(nearest, Math.hypot(m.x - x, m.y - y) - m.displayRadius);
            if (nearest > bestD) { bestD = nearest; best = new double[]{x, y}; }
        }
        return best;
    }

    static double clamp(double v, double lo, double hi) { return lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v)); }

}
