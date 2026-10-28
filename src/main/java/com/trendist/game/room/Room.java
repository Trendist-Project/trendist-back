package com.trendist.game.room;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 나라별 방 하나(9.2). 틱 스레드 하나만 상태를 읽고 쓰므로 락이 없다. 다른 스레드는 tasks 큐에 작업을 넣는다.
 * 이동·충돌은 서버 권위로 계산하고, 스냅샷(바이너리)을 2틱마다(10Hz) 보낸다.
 */
public class Room {
    public static final int TICK_MS = 50;
    private static final int SNAPSHOT_EVERY = 2;
    private static final int MINIMAP_EVERY = 20; // 1초마다
    private static final int MINIMAP_TOP = 20;   // 큰 아이콘 상위 20명(4.2)

    /** 틱에서 쓰는 설정값. 1분 캐시된 settings에서 매초 다시 읽는다. */
    public record Tuning(double worldSize, double minRadius, double maxRadius, double scoreCap, double maxSpeed,
                         double zoomFactor, double bigRatio, double knockbackCap, double knockbackSeconds,
                         double cooldownSeconds, double loseProtectSeconds, double joinProtectSeconds) {}

    /** 방 밖(서비스)으로 나가는 요청: 충돌로 키워드 잃기, 메시지 전송. */
    public interface Outbox {
        void requestLose(Room room, Member loser, Member winner);
        void send(Member m, ByteBuffer binary);
        void sendText(Member m, Map<String, Object> message);
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
        public double kbX, kbY, kbTime;    // 튕김
        public double protectUntil;        // 키워드 잃기 보호 종료(방 시계 초)
        public final Set<Long> keywords = new HashSet<>();

        Member(int entityNo, long playerId, Object session) {
            this.entityNo = entityNo; this.playerId = playerId; this.session = session;
        }
    }

    private final long countryId;
    private final Outbox outbox;
    private final Queue<Consumer<Room>> tasks = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Member> members = new LinkedHashMap<>();
    private final Map<Long, Member> byPlayer = new HashMap<>();
    private final Map<Long, Set<Member>> holders = new HashMap<>();
    private final Map<Long, Double> pairCooldown = new HashMap<>();
    private volatile Tuning tuning;
    private int nextEntity = 1;
    private long tick;
    private double clockSeconds;
    private volatile int size;
    private volatile Map<Long, Double> scoreView = Map.of();

    public Room(long countryId, Tuning tuning, Outbox outbox) {
        this.countryId = countryId; this.tuning = tuning; this.outbox = outbox;
    }

    public long countryId() { return countryId; }
    public int size() { return size; }
    public void setTuning(Tuning t) { this.tuning = t; }
    public Tuning tuning() { return tuning; }
    public void submit(Consumer<Room> task) { tasks.add(task); }

    /** 랭킹에서 쓰는 접속 중인 플레이어 점수(틱마다 갱신되는 읽기 전용 복사본). */
    public Map<Long, Double> scoreView() { return scoreView; }

    // ------------------------------------------------------------ 틱 스레드에서만 부른다

    public Member join(long playerId, Object session, String nickname, int level, double score, Collection<Long> keywordIds) {
        Member old = byPlayer.get(playerId);
        if (old != null) leave(old); // 한 계정은 한 방에 한 연결
        Member m = new Member(nextEntity++, playerId, session);
        m.nickname = nickname; m.level = level; m.score = score;
        m.radius = radiusFor(score); m.displayRadius = m.radius;
        double area = activeArea(members.size() + 1);
        double[] spawn = findSpawn(area);
        m.x = spawn[0]; m.y = spawn[1];
        m.protectUntil = clockSeconds + tuning.joinProtectSeconds();
        m.keywords.addAll(keywordIds);
        for (Long k : keywordIds) holders.computeIfAbsent(k, x -> new HashSet<>()).add(m);
        members.put(m.entityNo, m);
        byPlayer.put(playerId, m);
        size = members.size();
        broadcastRoster();
        return m;
    }

    public void leave(Member m) {
        if (members.remove(m.entityNo) == null) return;
        byPlayer.remove(m.playerId);
        for (Long k : m.keywords) {
            Set<Member> set = holders.get(k);
            if (set != null) { set.remove(m); if (set.isEmpty()) holders.remove(k); }
        }
        size = members.size();
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

    public void protect(long playerId) {
        Member m = byPlayer.get(playerId);
        if (m != null) m.protectUntil = clockSeconds + tuning.loseProtectSeconds();
    }

    public void tick() {
        Consumer<Room> t;
        while ((t = tasks.poll()) != null) {
            try { t.accept(this); } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(Room.class).warn("room task failed", e); }
        }
        double dt = TICK_MS / 1000.0;
        clockSeconds += dt;
        tick++;
        Tuning tu = tuning;
        double half = activeArea(members.size()) / 2;
        for (Member m : members.values()) move(m, dt, tu, half);
        collide(tu);
        for (Member m : members.values()) {
            // 크기 변화는 0.5초에 걸쳐 부드럽게(4.2)
            m.displayRadius += (m.radius - m.displayRadius) * Math.min(1, dt / 0.5 * 3);
        }
        if (tick % SNAPSHOT_EVERY == 0) sendSnapshots(half);
        if (tick % MINIMAP_EVERY == 0) sendMinimap();
        if (tick % 20 == 0) {
            Map<Long, Double> view = new HashMap<>();
            for (Member m : members.values()) view.put(m.playerId, m.score);
            scoreView = view;
            pairCooldown.values().removeIf(until -> until < clockSeconds);
        }
    }

    // ------------------------------------------------------------ 내부

    private void move(Member m, double dt, Tuning tu, double half) {
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
        double a = Math.min(1, dt * 10);
        m.vx += (wantX - m.vx) * a; m.vy += (wantY - m.vy) * a;
        double kx = 0, ky = 0;
        if (m.kbTime > 0) {
            double f = m.kbTime / tu.knockbackSeconds(); // 0.4초에 걸쳐 감속
            kx = m.kbX * f; ky = m.kbY * f;
            m.kbTime = Math.max(0, m.kbTime - dt);
        }
        m.x += (m.vx + kx) * dt; m.y += (m.vy + ky) * dt;
        double r = m.displayRadius;
        m.x = clamp(m.x, -half + r, half - r); m.y = clamp(m.y, -half + r, half - r);
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
                        if (loser.protectUntil <= clockSeconds && !loser.keywords.isEmpty()) {
                            loser.protectUntil = clockSeconds + tu.loseProtectSeconds();
                            outbox.requestLose(this, loser, winner);
                        }
                    }
                }
            }
        }
    }

    private void knock(Member m, double nx, double ny, double strength, Tuning tu) {
        double base = 420;
        m.kbX = nx * base * strength; m.kbY = ny * base * strength; m.kbTime = tu.knockbackSeconds();
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
        for (Member m : members.values()) list.add(Map.of("e", m.entityNo, "nickname", m.nickname, "level", m.level));
        Map<String, Object> msg = Map.of("type", "roster", "players", list);
        for (Member m : members.values()) outbox.sendText(m, msg);
    }

    /** 인원이 적으면 활동 영역을 좁힌다(4.2). 1000명에서 기준 맵 한 변. */
    double activeArea(int n) {
        double full = tuning.worldSize();
        return clamp(full * Math.sqrt(Math.max(n, 1) / 1000.0), Math.min(3000, full), full);
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

    public static double toDouble(BigDecimal v) { return v == null ? 0 : v.doubleValue(); }
}
