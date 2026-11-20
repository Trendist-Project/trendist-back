package com.trendist.monster.service;

import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.game.room.Monster;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.monster.repository.MonsterSpawnRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 몬스터 등장 스케줄(4.35): 1초마다 켜진 보스 묶음(같은 주기·오프셋)과 나라(방)마다 "지금이 등장할 때인가"를 본다. 묶음 안에서는 시간대마다 한 마리만 무작위로 나온다.
 * 주기(interval_minutes)의 정각(offset_minutes 만큼 뒤)에 나오고, 나오기 announce_seconds 전에 예고하며, 접속한 사람이 min_players보다 적으면 그 시간은 건너뛴다.
 * 같은 시간대에 두 번 나오지 않게 DB 기록(monster_spawns)을 본다. 관리자가 즉시 소환·제거할 때도 이 클래스를 거친다.
 */
@Component
public class MonsterDirector {
    private static final Logger log = LoggerFactory.getLogger(MonsterDirector.class);

    private final RoomManager rooms;
    private final MonsterCatalog catalog;
    private final MonsterRecorder recorder;
    private final MonsterSpawnRepository spawns;
    private final CountryRepository countries;
    /** 이미 처리한 시간대("종류:나라:시작초")와 예고한 시간대. 메모리에만 둔다(서버를 다시 켜면 DB 기록으로 가린다) */
    private final Set<String> handled = new HashSet<>(), alerted = new HashSet<>();

    public MonsterDirector(RoomManager rooms, MonsterCatalog catalog, MonsterRecorder recorder, MonsterSpawnRepository spawns, CountryRepository countries) {
        this.rooms = rooms; this.catalog = catalog; this.recorder = recorder; this.spawns = spawns; this.countries = countries;
    }

    @PostConstruct
    void init() {
        try { int n = recorder.closeStale(); if (n > 0) log.info("closed {} stale monster spawns", n); }
        catch (RuntimeException e) { log.warn("closing stale monster spawns failed", e); }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void tick() {
        try {
            long now = System.currentTimeMillis() / 1000;
            Map<String, List<MonsterCatalog.Entry>> groups = groups();
            for (List<MonsterCatalog.Entry> group : groups.values())
                for (Room room : rooms.rooms()) schedule(group, room, now);
            if (handled.size() > 200) handled.clear();
            if (alerted.size() > 200) alerted.clear();
        } catch (RuntimeException e) { log.warn("monster director failed", e); }
    }

    /**
     * 같은 시각(같은 주기·오프셋)에 나오는 켜진 보스끼리 한 묶음이다. 그 시각이 되면 묶음 안에서 사이클 순서대로 한 마리가 나온다(pick).
     * 비중이 0이거나 정기 등장이 꺼진 보스는 뽑히지 않는다. 묶음 안은 코드 순서로 고정해 같은 시각에는 같은 결과가 나오게 한다.
     */
    private Map<String, List<MonsterCatalog.Entry>> groups() {
        Map<String, List<MonsterCatalog.Entry>> m = new TreeMap<>();
        for (MonsterCatalog.Entry e : catalog.enabled()) {
            if (!"BOSS".equals(e.type().getKind()) || e.params().getOrDefault("spawn_weight", 1.0) <= 0) continue;
            long interval = Math.max(1, Math.round(e.params().getOrDefault("interval_minutes", 60.0))), offset = Math.round(e.params().getOrDefault("offset_minutes", 0.0));
            m.computeIfAbsent(interval + ":" + offset, k -> new ArrayList<>()).add(e);
        }
        for (List<MonsterCatalog.Entry> g : m.values()) g.sort(Comparator.comparing(e -> e.type().getCode()));
        return m;
    }

    /** 묶음의 주기·오프셋(초). 묶음은 같은 값을 가진 보스끼리라 첫 보스의 값을 쓴다 */
    private static long[] period(List<MonsterCatalog.Entry> group) {
        Map<String, Double> p = group.get(0).params();
        return new long[]{Math.max(1, Math.round(p.getOrDefault("interval_minutes", 60.0))) * 60, Math.round(p.getOrDefault("offset_minutes", 0.0)) * 60};
    }

    /**
     * 한 사이클에 나올 보스 목록(가방): 켜진 보스마다 `spawn_weight`를 반올림한 횟수(최소 1)만큼 넣는다. 모두 1이면 보스 수만큼이라 사이클 한 번에 보스마다 정확히 한 번씩 나온다.
     * 묶음은 코드 순서로 정렬돼 있어 같은 설정이면 같은 가방이다.
     */
    static List<MonsterCatalog.Entry> bag(List<MonsterCatalog.Entry> group) {
        List<MonsterCatalog.Entry> out = new ArrayList<>();
        for (MonsterCatalog.Entry e : group) {
            long copies = Math.max(1, Math.round(e.params().getOrDefault("spawn_weight", 1.0)));
            for (long i = 0; i < copies; i++) out.add(e);
        }
        return out;
    }

    /** 사이클 번호(cycle)의 순서: 가방을 시간(사이클 번호)으로 정한 난수로 섞는다(나라와 상관없이 같다). 이전 사이클의 마지막 보스가 첫째로 오면 다른 보스와 바꿔 연달아 같은 보스가 나오지 않게 한다 */
    static List<MonsterCatalog.Entry> cycleOrder(List<MonsterCatalog.Entry> bag, long cycle) {
        List<MonsterCatalog.Entry> order = shuffled(bag, cycle);
        int m = order.size();
        if (m > 2) {
            String prevLast = shuffled(bag, cycle - 1).get(m - 1).type().getCode();
            if (order.get(0).type().getCode().equals(prevLast))
                for (int j = 1; j < m - 1; j++) // 마지막 자리는 건드리지 않아 다음 사이클의 비교 기준이 변하지 않는다
                    if (!order.get(j).type().getCode().equals(prevLast)) { java.util.Collections.swap(order, 0, j); break; }
        }
        return order;
    }

    private static List<MonsterCatalog.Entry> shuffled(List<MonsterCatalog.Entry> bag, long cycle) {
        List<MonsterCatalog.Entry> order = new ArrayList<>(bag);
        java.util.Collections.shuffle(order, new Random(cycle * 1_000_003L + 17));
        return order;
    }

    /**
     * 그 시간대(slotStart, 초)에 이 방에서 나올 보스. **사이클 방식**: 정각을 순서대로 세어(주기마다 하나) 가방 크기(보스 수)만큼을 한 사이클로 보고, 사이클마다 가방을 섞어 그 순서대로 낸다.
     * 그래서 한 사이클 안에 켜진 보스가 모두 최소 한 번은 나온다(비중 `spawn_weight`는 한 사이클에서 나오는 횟수). 순서는 시간(사이클 번호)으로만 정한 난수라
     * 예고와 등장, 서버를 다시 켠 뒤에도 같고 **나라(KR·GLOBAL)와 상관없이 같은 시각에 같은 보스**가 나온다(소환·기록은 나라별로 따로). 보스를 켜고 끄면 사이클 크기가 바뀌어 그 시점부터 새 사이클로 센다.
     */
    static MonsterCatalog.Entry pick(List<MonsterCatalog.Entry> group, long slotStart) {
        if (group.size() == 1) return group.get(0);
        long[] pd = period(group);
        List<MonsterCatalog.Entry> bag = bag(group);
        long k = Math.floorDiv(slotStart - pd[1], pd[0]); // 정각 번호
        return cycleOrder(bag, Math.floorDiv(k, bag.size())).get((int) Math.floorMod(k, bag.size()));
    }

    /** 한 사이클의 칸: 시작 시각(초)과 그 시각에 나올 보스 */
    public record CyclePos(long startEpoch, MonsterCatalog.Entry boss) {}
    /** 지금 사이클: 크기, 지금 시간이 몇 번째 칸인지(0부터), 칸 목록(시간순) */
    public record Cycle(int size, int index, List<CyclePos> positions) {}

    /** 지금 사이클(가장 큰 묶음 기준, 모든 나라가 같다). 켜진 보스가 없으면 null. 관리자 출현표가 "이번 사이클 몇 번째"를 보여 줄 때 쓴다 */
    public Cycle cycle(long nowEpoch) {
        List<MonsterCatalog.Entry> group = null;
        for (List<MonsterCatalog.Entry> g : groups().values()) if (group == null || g.size() > group.size()) group = g;
        if (group == null) return null;
        long[] pd = period(group);
        List<MonsterCatalog.Entry> bag = bag(group);
        int m = bag.size();
        long kNow = Math.floorDiv(nowEpoch - pd[1], pd[0]), first = Math.floorDiv(kNow, m) * m;
        List<CyclePos> pos = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            long slot = (first + i) * pd[0] + pd[1];
            pos.add(new CyclePos(slot, pick(group, slot)));
        }
        return new Cycle(m, (int) (kNow - first), pos);
    }

    private void schedule(List<MonsterCatalog.Entry> group, Room room, long now) {
        Map<String, Double> p0 = group.get(0).params();
        long interval = Math.max(1, Math.round(p0.getOrDefault("interval_minutes", 60.0))) * 60;
        long offset = Math.round(p0.getOrDefault("offset_minutes", 0.0)) * 60;
        long slot = Math.floorDiv(now - offset, interval) * interval + offset; // 지금이 속한 시간대의 시작(초)
        // 예고: 다음 시간대가 announce_seconds 안에 다가왔고 사람이 충분하면 한 번만(나올 보스를 미리 정해 그 이름으로 알린다)
        long next = slot + interval;
        MonsterCatalog.Entry nextBoss = pick(group, next);
        Map<String, Double> pn = nextBoss.params();
        long announce = Math.round(pn.getOrDefault("announce_seconds", 60.0));
        String alertKey = nextBoss.type().getCode() + ":" + room.countryId() + ":" + next;
        if (announce > 0 && next - now <= announce && !alerted.contains(alertKey) && !active(room, nextBoss.type().getCode())
                && room.size() >= pn.getOrDefault("min_players", 1.0)) {
            alerted.add(alertKey);
            room.submit(r -> r.announce(Map.of("type", "monsterAlert", "name", nextBoss.type().getName(), "en", String.valueOf(nextBoss.type().getNameEn()), "seconds", next - System.currentTimeMillis() / 1000)));
        }
        // 등장: 시간대가 시작한 뒤 grace_seconds 안일 때만
        MonsterCatalog.Entry e = pick(group, slot);
        Map<String, Double> p = e.params();
        String code = e.type().getCode();
        String key = interval + ":" + offset + ":" + room.countryId() + ":" + slot;
        if (now - slot >= p.getOrDefault("grace_seconds", 120.0) || handled.contains(key) || active(room, code)) return;
        handled.add(key);
        for (MonsterCatalog.Entry g : group) { // 서버를 다시 켠 뒤에도 같은 시간대에 이미 나온(건너뛴 것 포함) 기록이 있으면 다시 나오지 않는다
            if (spawns.existsByMonsterTypeIdAndCountryIdAndStartedAtGreaterThanEqual(g.type().getId(), room.countryId(), Instant.ofEpochSecond(slot))) return;
        }
        if (room.size() < p.getOrDefault("min_players", 1.0)) return; // 사람이 적으면 그 시간은 건너뛴다(기록은 남기지 않는다. 메모리의 handled가 같은 실행 중 다시 시도하지 않게 한다)
        spawn(e, room, "SCHEDULE");
    }

    /** 앞으로 나올 보스 예정 한 칸: 시작 시각(초), 나라 번호·코드, 그 시각에 뽑힌 보스 */
    public record Upcoming(long startEpoch, long countryId, String countryCode, MonsterCatalog.Entry boss) {}

    /**
     * 지금(fromEpoch초) 이후 toEpoch초까지 나라마다 켜진 묶음의 정각들을 훑어 그 시각에 나올 보스를 알려 준다. 예고와 실제 등장이 쓰는 뽑기(pick)를 그대로 쓰므로
     * 보여 준 보스가 실제로 나오는 보스다(시간대·나라로 정한 난수라 서버를 다시 켜도 같다). 사람이 적어 건너뛰는 것은 알 수 없으니 예정일 뿐이다.
     */
    public List<Upcoming> upcoming(long fromEpoch, long toEpoch) {
        List<Upcoming> out = new ArrayList<>();
        List<Country> cs = countries.findByEnabledTrue();
        for (List<MonsterCatalog.Entry> group : groups().values()) {
            Map<String, Double> p0 = group.get(0).params();
            long interval = Math.max(1, Math.round(p0.getOrDefault("interval_minutes", 60.0))) * 60;
            long offset = Math.round(p0.getOrDefault("offset_minutes", 0.0)) * 60;
            long first = (Math.floorDiv(fromEpoch - offset, interval) + 1) * interval + offset; // fromEpoch 다음 첫 정각
            for (long slot = first; slot <= toEpoch; slot += interval)
                for (Country c : cs) out.add(new Upcoming(slot, c.getId(), c.getCode(), pick(group, slot)));
        }
        out.sort(Comparator.comparingLong(Upcoming::startEpoch));
        return out;
    }

    private boolean active(Room room, String code) {
        for (Map<String, Object> v : room.monsterView()) if (code.equals(v.get("code"))) return true;
        return false;
    }

    private void spawn(MonsterCatalog.Entry e, Room room, String source) {
        long spawnId = recorder.open(e.type().getId(), room.countryId(), source, "ACTIVE");
        Monster.Spec spec = new Monster.Spec(spawnId, e.type().getCode(), e.type().getName(), e.type().getNameEn(), e.type().getBehavior(), e.type().getImage(), Map.copyOf(e.params()));
        room.submit(r -> r.spawnMonster(spec));
        log.info("monster {} spawned in country {} ({})", e.type().getCode(), room.countryId(), source);
    }

    // ------------------------------------------------------------ 관리자

    /** 즉시 소환. 방이 없으면 만든다(접속자가 없어도 된다). 이미 같은 종류가 싸우는 중이면 false */
    public boolean spawnNow(String code, String countryCode) {
        MonsterCatalog.Entry e = catalog.get(code);
        Country c = countries.findByCode(countryCode).orElse(null);
        if (e == null || c == null) return false;
        Room room = rooms.room(c.getId());
        if (active(room, code)) return false;
        spawn(e, room, "ADMIN");
        return true;
    }

    /** 제거(REMOVED, 보상 없음) 또는 처치 처리(KILLED, 보상 지급). 없으면 false */
    public boolean end(int monsterId, String countryCode, boolean reward) {
        Country c = countries.findByCode(countryCode).orElse(null);
        if (c == null) return false;
        Room room = rooms.room(c.getId());
        boolean exists = room.monsterView().stream().anyMatch(v -> Integer.valueOf(monsterId).equals(v.get("id")));
        if (!exists) return false;
        room.submit(r -> r.removeMonster(monsterId, reward ? Monster.KILLED : Monster.REMOVED));
        return true;
    }
}
