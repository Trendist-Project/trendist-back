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
    public static final double REJOIN_KEEP_SECONDS = 60;
    public static final double ORB_RADIUS = 14; // ? 구슬 반지름(px)
 // 나간 뒤 이 시간 안에 다시 들어오면 마지막 위치에서 이어 간다(4.2)

    /** 틱에서 쓰는 설정값. 1분 캐시된 settings에서 매초 다시 읽는다. */
    public record Tuning(double worldSize, double minRadius, double maxRadius, double scoreCap, double maxSpeed,
                         double zoomFactor, double bigRatio, double knockbackCap, double knockbackSeconds, double knockbackDistance,
                         double cooldownSeconds, double loseProtectSeconds, double joinProtectSeconds,
                         int orbMax, double orbSpawnSeconds, double orbDensity, double orbSpawnPercent, double orbActivePercent,
                         int botPlayers, double botCapRatio, double botStepMin, double botStepMax, double expScoreCap) {}

    /** 방 밖(서비스)으로 나가는 요청: 충돌로 키워드 잃기, 메시지 전송. */
    public interface Outbox {
        void requestLose(Room room, Member loser, Member winner);
        /** ? 구슬을 먹었다. 보상(무작위 🔥 키워드)은 틱 밖에서 정하고 먹은 사람에게 알린다(4.20). */
        void requestOrb(Room room, Member eater);
        void send(Member m, ByteBuffer binary);
        void sendText(Member m, Map<String, Object> message);
        /** 오래 응답이 없는 접속을 끊는다(방에서는 이미 뺐다). */
        default void close(Member m) {}
    }

    public static final class Member {
        public final int entityNo;
        public final long playerId;
        public final Object session; // GameSocketHandler가 쓰는 세션
        public String nickname;
        public int level;
        public double score;
        public double x, y, vx, vy;
        public double radius, displayRadius;
        public double dirX, dirY;          // 방향키 입력(-1~1)
        public Double targetX, targetY;    // 클릭 목표(새로 찍으면 바로 바뀐다)
        public double kbX, kbY, kbTime;    // 튕김: 전체 밀려날 거리(벡터)와 남은 시간
        public double protectUntil;        // 키워드 잃기 보호 종료(방 시계 초)
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
    private final Map<Long, Set<Member>> holders = new HashMap<>();
    private final Map<Long, Double> pairCooldown = new HashMap<>();
    private final Map<Long, LastSeen> lastSeen = new HashMap<>(); // 나간 플레이어의 마지막 위치
    private final Map<Integer, double[]> orbs = new LinkedHashMap<>(); // ? 구슬 번호 → {x, y}
    private int nextOrb = 1;
    private double nextOrbAt;

    /** 나간 플레이어의 마지막 위치와 보호 종료 시각. keepUntil이 지나면 버린다. */
    private record LastSeen(double x, double y, double protectUntil, double keepUntil) {}
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
    public Tuning tuning() { return tuning; }
    public void submit(Consumer<Room> task) { tasks.add(task); }

    /** 접속 중인 플레이어 한 명의 랭킹용 값. */
    public record Live(long playerId, int entityNo, String nickname, int level, double score) {}

    /** 랭킹에서 쓰는 접속 중인 플레이어(1초마다 갱신되는 읽기 전용 복사본, 플레이어 번호 → 값). */
    public Map<Long, Live> liveView() { return liveView; }

    // ------------------------------------------------------------ 틱 스레드에서만 부른다

    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds) {
        Member old = byPlayer.get(playerId);
        if (old != null) leave(old); // 한 계정은 한 방에 한 연결
        Member m = new Member(nextEntity++, playerId, session);
        m.lastHeardNanos = nanoClock.getAsLong();
        m.nickname = nickname; m.level = level; m.score = score;
        m.radius = radiusFor(score); m.displayRadius = m.radius;
        double area = activeArea(members.size() + 1);
        LastSeen seen = lastSeen.remove(playerId);
        if (seen != null && seen.keepUntil() >= clockSeconds) {
            // 새로고침으로 위치를 바꾸거나 보호를 다시 받지 못하게, 마지막 위치와 남은 보호 시간을 그대로 이어 간다
            double half = area / 2;
            m.x = clamp(seen.x(), -half, half); m.y = clamp(seen.y(), -half, half);
            m.protectUntil = seen.protectUntil();
        } else {
            double[] spawn = findSpawn(area);
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
        outbox.sendText(m, Map.of("type", "chatHistory", "messages", List.copyOf(chatHistory)));
        return m;
    }

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
    }

    public void leave(Member m) {
        if (members.remove(m.entityNo) == null) return;
        byPlayer.remove(m.playerId);
        lastSeen.put(m.playerId, new LastSeen(m.x, m.y, m.protectUntil, clockSeconds + REJOIN_KEEP_SECONDS));
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
        double half = activeArea(members.size()) / 2;
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
        double half = activeArea(members.size()) / 2;
        if (tick % 20 == 0) { dropSilent(); maintainBots(tu); }
        thinkBots(tu, half);
        for (Member m : members.values()) move(m, dt, tu, half);
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

    // ------------------------------------------------------------ ? 구슬(4.20)

    /** 원이 구슬 중심을 덮으면 먹는다. 구슬은 바로 사라지고, 모두에게 누가 먹었는지 알려 먹는 연출을 보여 준다. */
    private void eatOrbs() {
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
            else outbox.requestOrb(this, eater);
        }
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
            boolean blocked = false;
            for (Member m : members.values()) {
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
        orbs.forEach((id, p) -> list.add(List.of(id, p[0], p[1])));
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
                    // 현재 유행도가 낮은 쪽이 키워드 하나를 잃는다(4.3). 같으면 없음.
                    if (a.score != b.score) {
                        Member loser = a.score < b.score ? a : b, winner = loser == a ? b : a;
                        if (loser.protectUntil <= clockSeconds && loser.bot && !loser.botKeywords.isEmpty()) {
                            loser.protectUntil = clockSeconds + tu.loseProtectSeconds();
                            int lost = botLose(loser, tu); // 봇도 사람처럼 (메모리의) 키워드 하나를 잃는다(4.21)
                            shield(loser, tu);
                            // 봇의 키워드는 이름이 없어 이긴 사람에게 점수만 보여 준다
                            if (!winner.bot) outbox.sendText(winner, Map.of("type", "hit", "target", loser.nickname, "score", lost));
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
    }

    private void broadcastRoster() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Member m : members.values()) list.add(Map.of("e", m.entityNo, "nickname", m.nickname, "level", m.level, "bot", m.bot));
        Map<String, Object> msg = Map.of("type", "roster", "players", list);
        for (Member m : members.values()) outbox.sendText(m, msg);
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

    /** 봇 유행도 상한: 그 방 1등(사람) 현재 유행도 × botCapRatio(기본 1.5). */
    double botCap(Tuning tu) {
        double top = 0;
        for (Member m : members.values()) if (!m.bot) top = Math.max(top, m.score);
        return Math.floor(top * tu.botCapRatio());
    }

    /**
     * 1초마다: 사람이 있으면 사람 + 봇이 botPlayers가 되도록 봇을 하나씩 넣거나 뺀다(사람이 없으면 봇도 없다).
     * 1등 점수가 내려가 상한을 넘은 봇은 키워드를 하나씩 내려놓고, EXP만으로도 넘으면 빼고 새 봇으로 바꾼다.
     */
    void maintainBots(Tuning tu) {
        double cap = botCap(tu);
        for (Member b : List.copyOf(members.values())) {
            if (!b.bot || b.score <= cap) continue;
            if (!b.botKeywords.isEmpty()) { b.botKeywords.removeFirst(); botRecompute(b, tu); }
            else { members.remove(b.entityNo); broadcastRoster(); }
        }
        int humans = humanCount(), bots = members.size() - humans;
        int want = humans == 0 ? 0 : Math.max(0, tu.botPlayers() - humans);
        if (bots < want) addBot(tu);
        else if (bots > want) members.values().stream().filter(m -> m.bot).findFirst().ifPresent(b -> {
            members.remove(b.entityNo);
            broadcastRoster();
        });
    }

    /**
     * 사람과 같은 기준으로 봇을 만든다: EXP를 정하고(레벨은 레벨표로), 그 EXP 안에서 키워드 몇 개를 가진 것으로 한다.
     * 유행도 = min(EXP, 100) + 키워드 점수 합이고 상한(1등 × 1.5)을 넘지 않는다.
     */
    private void addBot(Tuning tu) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double cap = botCap(tu);
        Member m = new Member(nextEntity++, -(++botSeq), null);
        m.bot = true;
        m.nickname = "Bot";
        // EXP를 정하고, 많이 추가한 사람은 대개 가진 키워드도 많으므로 EXP에 비례해 가진 키워드 수를 정한다
        // (최근 30번 추가 중 30~100%를 아직 가진 것으로 본다. 추가한 횟수(EXP)보다 많을 수는 없다).
        // 합이 상한(1등 × 1.5)을 넘으면 키워드만 빼지 않고 EXP 범위를 줄여 다시 뽑는다. 작은 원인데 레벨만 높은 봇이 되지 않게
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
        double[] spawn = findSpawn(activeArea(members.size() + 1));
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

    /** 봇의 "추가": 사람처럼 EXP +1과 키워드 하나. 상한(1등 × 1.5)을 넘으면 하지 않는다. */
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

    /** 봇이 ? 구슬을 먹으면 사람과 같은 확률로 당첨되어 "추가"하고, 나머지는 꽝이다. */
    private void botOrb(Member b) {
        Tuning tu = tuning;
        if (ThreadLocalRandom.current().nextDouble(100) < tu.orbActivePercent()) botPlus(b, tu);
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
                if (o.score > b.score && d < fleeRange && d < threatD) { threat = o; threatD = d; }
                if (o.score < b.score && d < chaseRange && d < preyD) { prey = o; preyD = d; }
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
