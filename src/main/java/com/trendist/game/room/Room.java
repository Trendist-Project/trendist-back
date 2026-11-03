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
    public static final double REJOIN_KEEP_SECONDS = 300;
    public static final double ORB_RADIUS = 14; // ? 구슬 반지름(px)
 // 나간 뒤 이 시간 안에 다시 들어오면 마지막 위치에서 이어 간다(4.2)

    /** 틱에서 쓰는 설정값. 1분 캐시된 settings에서 매초 다시 읽는다. */
    public record Tuning(double worldSize, double minRadius, double maxRadius, double scoreCap, double maxSpeed,
                         double zoomFactor, double bigRatio, double knockbackCap, double knockbackSeconds, double knockbackDistance,
                         double cooldownSeconds, double loseProtectSeconds, double joinProtectSeconds,
                         int orbMax, double orbSpawnSeconds, double orbDensity, double orbSpawnPercent, double orbActivePercent,
                         int botPlayers, int botMinPlayers, double botCapRatio, double botStepMin, double botStepMax, double expScoreCap) {}

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
        /** 사람이 봇을 이겼다. 보상(내 보관함에 없는 무작위 🔥 키워드)은 틱 밖에서 정하고 이긴 사람에게 알린다(4.21). */
        void requestBotWin(Room room, Member winner, Member bot);
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
     */
    public record ComboTuning(int flameAt, boolean countBots, int wipeOrbPercent) {
        public static final ComboTuning DEFAULT = new ComboTuning(5, true, 50);
    }

    /** 이모트(4.26): 원 위에 잠깐 뜨는 이모지. 코드만 주고받는다 */
    public static final List<String> EMOTES = List.of("wave", "laugh", "cool", "cry", "angry", "thumbs", "heart", "fire");

    public static final class Member {
        public final int entityNo;
        public final long playerId;
        public final Object session; // GameSocketHandler가 쓰는 세션
        public String nickname;
        /** 활성 칭호(4.10). 없으면 null. 이름표 아래 작은 글씨로 보인다 */
        public String title;
        /** 꾸미기(4.9) */
        public Skin skin = Skin.DEFAULT;
        /** 연속 빼앗기 콤보(4.25) */
        public int combo;
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
        public final Set<Long> keywords = new HashSet<>();
        /** 마지막으로 이 접속에서 메시지(ping 포함)를 받은 시각(nanoTime). 받는 스레드가 쓰고 틱이 읽는다 */
        public volatile long lastHeardNanos;
        /** NPC 봇(4.21). 연결도 키워드도 없고, 점수는 방 메모리에만 있다. playerId는 음수다. */
        public boolean bot;
        long botExp;         // 봇의 EXP(사람처럼 추가할 때마다 +1, 줄지 않는다). 레벨은 이것으로 정한다
        final List<Integer> botKeywords = new ArrayList<>(); // 봇이 가진 키워드 점수들(메모리에만, 실제 키워드는 아니다)
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
    private final Map<Long, Double> pairCooldown = new HashMap<>();
    private final Map<Long, LastSeen> lastSeen = new HashMap<>(); // 나간 플레이어의 마지막 위치
    private final Map<Integer, double[]> orbs = new LinkedHashMap<>(); // ? 구슬 번호 → {x, y}
    private final Map<Integer, double[]> prizeOrbs = new LinkedHashMap<>(); // ! 구슬(불꽃에게 키워드를 모두 잃은 자리에 떨어짐) 번호 → {x, y, 사라질 시각}
    /** ! 구슬이 먹히지 않으면 사라지는 시간(초)과 방 전체 상한(4.20) */
    static final double PRIZE_ORB_SECONDS = 90;
    static final int PRIZE_ORB_MAX = 300;
    private int nextOrb = 1;
    private double nextOrbAt;
    private volatile ZoneTuning zoneTuning = ZoneTuning.DEFAULT;
    private volatile ComboTuning comboTuning = ComboTuning.DEFAULT;
    /**
     * 지금의 활동 영역 한 변(0이면 아직 정하지 않음). 목표(접속 인원에 따른 값)보다 클 때는 areaShrinkSpeed(px/s)로만 줄어들어서
     * 사람이 한꺼번에 나가거나 서버를 다시 켜도 원이 가장자리로 한꺼번에 밀려나지 않는다. 목표가 더 크면 바로 커진다. 틱 스레드에서만 바꾼다.
     */
    private double areaNow;
    private volatile double areaShrinkSpeed; // 0이면 줄 때도 바로(예전 방식)
    private final List<SafeZone> zones = new ArrayList<>();
    private int nextZone = 1;
    private double centerSize, sentCenterSize = -1;

    /** 나간 플레이어의 마지막 위치와 보호 종료 시각. keepUntil이 지나면 버린다. */
    private record LastSeen(double x, double y, double protectUntil, double keepUntil, int combo) {}

    /** DB에 저장해 둔 마지막 위치와 저장한 지 지난 시간(초). 서버를 다시 켠 뒤 들어올 때 쓴다. */
    public record Resume(double x, double y, double ageSeconds) {}

    /** 저장할 위치 하나: 접속 중이면 ageSeconds 0, 나간 지 얼마 안 됐으면 나간 뒤 지난 시간. */
    public record SavedPos(long playerId, double x, double y, double ageSeconds) {}

    /** 접속 중인 사람과 최근(REJOIN_KEEP_SECONDS 안에) 나간 사람의 위치. 틱 스레드에서 부른다. */
    public List<SavedPos> snapshotPositions() {
        List<SavedPos> out = new ArrayList<>();
        for (Member m : members.values()) if (!m.bot) out.add(new SavedPos(m.playerId, m.x, m.y, 0));
        for (Map.Entry<Long, LastSeen> e : lastSeen.entrySet()) {
            double age = REJOIN_KEEP_SECONDS - (e.getValue().keepUntil() - clockSeconds);
            if (age < REJOIN_KEEP_SECONDS) out.add(new SavedPos(e.getKey(), e.getValue().x(), e.getValue().y(), Math.max(0, age)));
        }
        return out;
    }
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
    public void setAreaShrinkSpeed(double pxPerSecond) { this.areaShrinkSpeed = Math.max(0, pxPerSecond); }
    /** 서버를 다시 켠 뒤 저장해 둔 활동 영역에서 시작한다(틱을 시작하기 전에 부른다). 기준 맵 범위로 맞춘다. */
    public void restoreArea(double area) {
        if (area > 0) areaNow = clamp(area, tuning.worldSize() / 4, tuning.worldSize());
    }
    /** 지금 활동 영역 한 변(저장용) */
    public double currentArea() { return areaNow > 0 ? areaNow : activeArea(members.size()); }
    private double currentHalf() { return currentArea() / 2; }

    /** 영역을 목표에 맞춘다: 커질 때는 바로, 줄 때는 areaShrinkSpeed만큼씩. */
    private void updateArea(double dt) {
        double target = activeArea(members.size());
        double speed = areaShrinkSpeed;
        if (areaNow <= 0 || target >= areaNow || speed <= 0) areaNow = target;
        else areaNow = Math.max(target, areaNow - speed * dt);
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
        return join(playerId, session, nickname, level, score, keywordIds, false);
    }

    /** firstTime이면(처음 온 플레이어) 가운데 고정 세이프존 안에서 시작한다(4.22). */
    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds,
                       boolean firstTime) {
        return join(playerId, session, nickname, level, score, keywordIds, firstTime, Appearance.DEFAULT);
    }

    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds,
                       boolean firstTime, Appearance look) {
        return join(playerId, session, nickname, level, score, keywordIds, firstTime, look, null);
    }

    /** resume가 있고 방이 기억하는 위치가 없으면(서버를 다시 켠 뒤) 저장해 둔 위치에서 시작한다. REJOIN_KEEP_SECONDS가 지났으면 쓰지 않는다. */
    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds,
                       boolean firstTime, Appearance look, Resume resume) {
        Member old = byPlayer.get(playerId);
        if (old != null) leave(old); // 한 계정은 한 방에 한 연결
        Member m = new Member(nextEntity++, playerId, session);
        m.lastHeardNanos = nanoClock.getAsLong();
        m.nickname = nickname; m.level = level; m.score = score;
        m.title = look.title(); m.skin = look.skin() == null ? Skin.DEFAULT : look.skin(); m.trail = look.trail();
        m.radius = radiusFor(score); m.displayRadius = m.radius;
        double area = Math.max(currentArea(), activeArea(members.size() + 1)); // 들어오는 사람 때문에 영역이 커져야 하면 바로 커진다
        areaNow = area;
        LastSeen seen = lastSeen.remove(playerId);
        if (seen != null && seen.keepUntil() >= clockSeconds) {
            // 새로고침으로 위치를 바꾸거나 보호를 다시 받지 못하게, 마지막 위치와 남은 보호 시간을 그대로 이어 간다
            double half = area / 2;
            m.x = clamp(seen.x(), -half, half); m.y = clamp(seen.y(), -half, half);
            m.protectUntil = seen.protectUntil();
            m.combo = seen.combo(); // 콤보도 이어 간다(4.25)
        } else if (resume != null && resume.ageSeconds() < REJOIN_KEEP_SECONDS) {
            // 서버를 다시 켠 뒤: 저장해 둔 마지막 위치에서 시작한다. 모두가 한꺼번에 돌아오므로 입장 보호를 준다
            double half = area / 2;
            m.x = clamp(resume.x(), -half, half); m.y = clamp(resume.y(), -half, half);
            m.protectUntil = clockSeconds + tuning.joinProtectSeconds();
        } else {
            double[] spawn = firstTime ? centerSpawn(area) : findSpawn(area);
            m.x = spawn[0]; m.y = spawn[1];
            m.protectUntil = clockSeconds + tuning.joinProtectSeconds();
        }
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

    /** 관전자를 받는다. 가득 찼으면 null. 방의 지금 상태(이름표·구슬·세이프존·최근 채팅)를 바로 보낸다. */
    public Member addSpectator(Object session) {
        if (spectators.size() >= MAX_SPECTATORS) return null;
        Member sp = new Member(nextSpectator--, 0, session);
        sp.lastHeardNanos = nanoClock.getAsLong();
        sp.nickname = "(관전)";
        spectators.add(sp);
        spectatorSize = spectators.size();
        outbox.sendText(sp, rosterMessage());
        outbox.sendText(sp, orbListMessage());
        outbox.sendText(sp, zonesMessage());
        outbox.sendText(sp, Map.of("type", "chatHistory", "messages", List.copyOf(chatHistory)));
        return sp;
    }

    public void removeSpectator(Member sp) {
        spectators.remove(sp);
        spectatorSize = spectators.size();
    }

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
     * 싸움 결과(4.3, 4.25): 이긴 사람(winnerId, 봇이면 null)의 콤보를 +1 하고, 키워드를 잃은 사람(loserId, 봇이면 null)의 콤보를
     * 0으로 만든다(불꽃이 있었으면 꺼진다). vsBot이면 상대가 봇이다(설정에 따라 콤보에 넣지 않는다). 키워드가 실제로 오간 뒤에만 부른다.
     */
    public void fightResult(Long winnerId, Long loserId, boolean vsBot) {
        ComboTuning ct = comboTuning;
        Member w = winnerId == null ? null : byPlayer.get(winnerId);
        Member l = loserId == null ? null : byPlayer.get(loserId);
        if (l != null && !l.bot && l.combo > 0) {
            boolean broken = l.combo >= ct.flameAt();
            l.combo = 0;
            Map<String, Object> msg = comboMessage(l, broken ? "broken" : "reset");
            msg.put("by", w != null ? w.nickname : "Bot");
            if (w != null) msg.put("byE", w.entityNo);
            broadcast(msg);
        }
        if (w != null && !w.bot && (!vsBot || ct.countBots())) {
            w.combo++;
            broadcast(comboMessage(w, w.combo == ct.flameAt() ? "start" : "up")); // start: 이번에 불꽃이 붙었다
        }
    }

    private Map<String, Object> comboMessage(Member m, String event) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "combo"); msg.put("e", m.entityNo); msg.put("nickname", m.nickname);
        msg.put("combo", m.combo); msg.put("event", event); msg.put("flameAt", comboTuning.flameAt());
        return msg;
    }

    public void leave(Member m) {
        if (members.remove(m.entityNo) == null) return;
        byPlayer.remove(m.playerId);
        lastSeen.put(m.playerId, new LastSeen(m.x, m.y, m.protectUntil, clockSeconds + REJOIN_KEEP_SECONDS, m.combo));
        for (Long k : m.keywords) {
            Set<Member> set = holders.get(k);
            if (set != null) { set.remove(m); if (set.isEmpty()) holders.remove(k); }
        }
        size = humanCount();
        broadcastRoster();
    }

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
        if (levelUp) broadcastRoster();
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
        if (tick % 20 == 0) { dropSilent(); maintainBots(tu); }
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
            pairCooldown.values().removeIf(until -> until < clockSeconds);
            lastSeen.values().removeIf(seen -> seen.keepUntil() < clockSeconds);
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
        double speed = tu.maxSpeed(); // 크기와 관계없이 모두 같다
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
    public void dropPrizeOrbs(long playerId, int lostActiveCount) {
        Member m = byPlayer.get(playerId);
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
                if (a.safe || b.safe) continue; // 세이프존에서는 겹쳐 있어도 튕기지 않고 키워드도 잃지 않는다(4.22)
                double dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy), min = a.displayRadius + b.displayRadius;
                if (d >= min) continue;
                if (d < 0.001) { dx = 1; dy = 0; d = 1; }
                double nx = dx / d, ny = dy / d, overlap = min - d;
                Member big = a.displayRadius >= b.displayRadius ? a : b, small = big == a ? b : a;
                double ratio = big.displayRadius / small.displayRadius;
                double sign = small == b ? 1 : -1; // small을 big에서 멀어지는 방향으로
                Double until = pairCooldown.get(key);
                boolean fresh = until == null || until < clockSeconds;
                if (ratio < tu.bigRatio()) {
                    a.x -= nx * overlap / 2; a.y -= ny * overlap / 2;
                    b.x += nx * overlap / 2; b.y += ny * overlap / 2;
                    if (fresh) {
                        knock(a, -nx, -ny, 1, tu); knock(b, nx, ny, 1, tu);
                        a.targetX = null; b.targetX = null; // 같이 튕기면 둘 다 목표 해제
                    }
                } else {
                    small.x += sign * nx * overlap; small.y += sign * ny * overlap;
                    if (fresh) { knock(small, sign * nx, sign * ny, Math.min(ratio, tu.knockbackCap()), tu); small.targetX = null; }
                }
                if (fresh) {
                    pairCooldown.put(key, clockSeconds + tu.cooldownSeconds());
                    boolean aFlame = isFlame(a), bFlame = isFlame(b);
                    if (aFlame != bFlame && !(aFlame ? b : a).bot) {
                        // 불꽃인 사람이 불꽃 없는 사람과 부딪히면 점수와 상관없이 이기고, 상대는 키워드를 모두 잃는다(4.25). 봇은 예전 규칙대로
                        Member hot = aFlame ? a : b, cold = hot == a ? b : a;
                        if (cold.protectUntil <= clockSeconds && !cold.keywords.isEmpty()) {
                            cold.protectUntil = clockSeconds + tu.loseProtectSeconds();
                            shield(cold, tu);
                            outbox.requestWipe(this, cold, hot);
                        }
                    // 현재 유행도가 낮은 쪽이 키워드 하나를 잃는다(4.3). 같으면 없음. 둘 다 불꽃이어도 이 규칙이다
                    } else if (a.score != b.score) {
                        Member loser = a.score < b.score ? a : b, winner = loser == a ? b : a;
                        if (loser.protectUntil <= clockSeconds && loser.bot && !loser.botKeywords.isEmpty()) {
                            loser.protectUntil = clockSeconds + tu.loseProtectSeconds();
                            botLose(loser, tu); // 봇도 사람처럼 (메모리의) 키워드 하나를 잃는다(4.21)
                            shield(loser, tu);
                            // 봇의 키워드는 이름이 없으므로, 이긴 사람은 대신 내 보관함에 없는 무작위 🔥 키워드를 얻는다
                            if (!winner.bot) outbox.requestBotWin(this, winner, loser);
                        } else if (loser.protectUntil <= clockSeconds && !loser.keywords.isEmpty()) {
                            loser.protectUntil = clockSeconds + tu.loseProtectSeconds();
                            shield(loser, tu);
                            outbox.requestLose(this, loser, winner); // 이긴 쪽이 봇이어도 잃는다
                        }
                    }
                }
            }
        }
    }

    /** 콤보 불꽃이 붙은 사람인지(봇은 불꽃이 없다, 4.25) */
    private boolean isFlame(Member m) { return !m.bot && m.combo >= comboTuning.flameAt(); }

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

    /** 스냅샷: [u8 type=1][u32 tick][f32 half][u16 n] + n × [u32 entity][f32 x][f32 y][f32 r][f32 score][u16 level][u8 flags] */
    private void sendSnapshots(double half) {
        int n = members.size();
        ByteBuffer base = ByteBuffer.allocate(11 + n * 23);
        base.put((byte) 1).putInt((int) tick).putFloat((float) half).putShort((short) n);
        int[] flagsPos = new int[n];
        int i = 0;
        List<Member> order = new ArrayList<>(members.values());
        for (Member m : order) {
            base.putInt(m.entityNo).putFloat((float) m.x).putFloat((float) m.y)
                    .putFloat((float) m.displayRadius).putFloat((float) m.score).putShort((short) m.level);
            flagsPos[i++] = base.position();
            base.put((byte) 0);
        }
        byte[] bytes = base.array();
        // 관심 영역(줌에 맞춘 주변 엔티티만 보내기)과 상한은 아직 넣지 않았다. 지금은 방 전원을 보낸다.
        for (int k = 0; k < order.size(); k++) {
            if (order.get(k).bot) continue;
            byte[] copy = bytes.clone();
            copy[flagsPos[k]] = 1; // 나
            outbox.send(order.get(k), ByteBuffer.wrap(copy));
        }
        for (Member sp : spectators) outbox.send(sp, ByteBuffer.wrap(bytes)); // 관전자는 '나'가 없다(표시 0)
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
            if (m.bot || now - m.lastHeardNanos < SILENT_SECONDS * 1_000_000_000L) continue;
            leave(m);
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
            m.botKeywords.clear();
            long want = Math.round(Math.min(m.botExp, 30) * (0.3 + rnd.nextDouble() * 0.7));
            int sum = 0;
            while (m.botKeywords.size() < want) {
                int v = (int) Math.round(botStep(tu));
                m.botKeywords.add(v);
                sum += v;
            }
            if (Math.min(m.botExp, tu.expScoreCap()) + sum <= cap) break;
            expMax *= 0.7;
            if (attempt == 11) { m.botExp = 0; m.botKeywords.clear(); } // 끝내 못 맞추면 가장 작은 봇
        }
        m.botStyle = rnd.nextInt(3);
        m.botReact = 0.25 + rnd.nextDouble() * 0.45;
        m.botHeading = rnd.nextDouble() * Math.PI * 2;
        m.botNextPlusAt = clockSeconds + 15 + rnd.nextDouble() * 30;
        botRecompute(m, tu);
        m.displayRadius = m.radius;
        double[] spawn = findSpawn(Math.max(currentArea(), activeArea(members.size() + 1)));
        m.x = spawn[0]; m.y = spawn[1];
        m.protectUntil = clockSeconds + tu.joinProtectSeconds();
        members.put(m.entityNo, m);
        broadcastRoster();
    }

    /** 봇의 유행도와 레벨을 사람과 같은 식으로 다시 정한다: min(EXP, 100) + Σ 키워드 점수, 레벨은 레벨표. */
    private void botRecompute(Member b, Tuning tu) {
        int sum = 0;
        for (int v : b.botKeywords) sum += v;
        b.score = Math.min(b.botExp, tu.expScoreCap()) + sum;
        b.radius = radiusFor(b.score);
        int lv = levelForExp.applyAsInt(b.botExp);
        if (lv != b.level) { b.level = lv; broadcastRoster(); }
    }

    private double botStep(Tuning tu) {
        double lo = Math.min(tu.botStepMin(), tu.botStepMax()), hi = Math.max(tu.botStepMin(), tu.botStepMax());
        return lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
    }

    /** 봇의 "추가": 사람처럼 EXP +1과 키워드 하나. 상한(사람 평균)을 넘으면 하지 않는다. */
    private void botPlus(Member b, Tuning tu) {
        int v = (int) Math.round(botStep(tu));
        double next = Math.min(b.botExp + 1, tu.expScoreCap()) + b.botKeywords.stream().mapToInt(Integer::intValue).sum() + v;
        if (next > botCap(tu)) return;
        b.botExp++;
        b.botKeywords.add(v);
        botRecompute(b, tu);
    }

    /** 봇이 충돌로 지면 사람처럼 가진 키워드 하나를 잃는다. 없으면 잃을 것도 없다. 잃은 키워드 점수를 돌려준다. */
    private int botLose(Member b, Tuning tu) {
        int lost = b.botKeywords.remove(ThreadLocalRandom.current().nextInt(b.botKeywords.size()));
        botRecompute(b, tu);
        return lost;
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
        for (Member b : members.values()) {
            if (!b.bot) continue;
            if (clockSeconds >= b.botNextPlusAt) botLife(b, tu);
            if (clockSeconds < b.botThinkAt) continue;
            b.botThinkAt = clockSeconds + b.botReact * (0.7 + rnd.nextDouble() * 0.6);
            double fleeRange = b.botStyle == 0 ? 900 : 450, chaseRange = b.botStyle == 1 ? 1000 : 450, orbRange = b.botStyle == 2 ? 1600 : 700;
            Member threat = null, prey = null;
            double threatD = Double.MAX_VALUE, preyD = Double.MAX_VALUE;
            for (Member o : members.values()) {
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

            double[] orb = nearestFreeOrb(b, orbRange, claimed);
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
    private double[] nearestFreeOrb(Member b, double range, Set<Integer> claimed) {
        double[] best = null;
        double bestD = range;
        for (Map.Entry<Integer, double[]> o : orbs.entrySet()) {
            if (claimed.contains(o.getKey()) && !o.getKey().equals(b.botOrb)) continue;
            double[] p = o.getValue();
            double d = Math.hypot(p[0] - b.x, p[1] - b.y);
            if (d < bestD) { bestD = d; best = new double[]{p[0], p[1], o.getKey()}; }
        }
        return best;
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
