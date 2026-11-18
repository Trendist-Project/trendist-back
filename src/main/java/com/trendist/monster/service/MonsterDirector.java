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
     * 같은 시각(같은 주기·오프셋)에 나오는 켜진 보스끼리 한 묶음이다. 그 시각이 되면 묶음 안에서 한 마리만 무작위로(`spawn_weight` 비중) 뽑아 낸다.
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

    /** 그 시간대(slotStart, 초)에 이 방에서 나올 보스: 시간대와 나라로 정한 난수로 비중에 따라 하나를 고른다(예고와 등장, 서버를 다시 켠 뒤에도 같은 결과) */
    static MonsterCatalog.Entry pick(List<MonsterCatalog.Entry> group, long countryId, long slotStart) {
        if (group.size() == 1) return group.get(0);
        double total = 0;
        for (MonsterCatalog.Entry e : group) total += Math.max(0, e.params().getOrDefault("spawn_weight", 1.0));
        double roll = new Random(slotStart * 1_000_003L + countryId * 7919L).nextDouble() * total;
        for (MonsterCatalog.Entry e : group) {
            roll -= Math.max(0, e.params().getOrDefault("spawn_weight", 1.0));
            if (roll < 0) return e;
        }
        return group.get(group.size() - 1);
    }

    private void schedule(List<MonsterCatalog.Entry> group, Room room, long now) {
        Map<String, Double> p0 = group.get(0).params();
        long interval = Math.max(1, Math.round(p0.getOrDefault("interval_minutes", 60.0))) * 60;
        long offset = Math.round(p0.getOrDefault("offset_minutes", 0.0)) * 60;
        long slot = Math.floorDiv(now - offset, interval) * interval + offset; // 지금이 속한 시간대의 시작(초)
        // 예고: 다음 시간대가 announce_seconds 안에 다가왔고 사람이 충분하면 한 번만(나올 보스를 미리 정해 그 이름으로 알린다)
        long next = slot + interval;
        MonsterCatalog.Entry nextBoss = pick(group, room.countryId(), next);
        Map<String, Double> pn = nextBoss.params();
        long announce = Math.round(pn.getOrDefault("announce_seconds", 60.0));
        String alertKey = nextBoss.type().getCode() + ":" + room.countryId() + ":" + next;
        if (announce > 0 && next - now <= announce && !alerted.contains(alertKey) && !active(room, nextBoss.type().getCode())
                && room.size() >= pn.getOrDefault("min_players", 1.0)) {
            alerted.add(alertKey);
            room.submit(r -> r.announce(Map.of("type", "monsterAlert", "name", nextBoss.type().getName(), "en", String.valueOf(nextBoss.type().getNameEn()), "seconds", next - System.currentTimeMillis() / 1000)));
        }
        // 등장: 시간대가 시작한 뒤 grace_seconds 안일 때만
        MonsterCatalog.Entry e = pick(group, room.countryId(), slot);
        Map<String, Double> p = e.params();
        String code = e.type().getCode();
        String key = interval + ":" + offset + ":" + room.countryId() + ":" + slot;
        if (now - slot >= p.getOrDefault("grace_seconds", 120.0) || handled.contains(key) || active(room, code)) return;
        handled.add(key);
        for (MonsterCatalog.Entry g : group) { // 서버를 다시 켠 뒤에도 같은 시간대에 이미 나온(건너뛴 것 포함) 기록이 있으면 다시 나오지 않는다
            if (spawns.existsByMonsterTypeIdAndCountryIdAndStartedAtGreaterThanEqual(g.type().getId(), room.countryId(), Instant.ofEpochSecond(slot))) return;
        }
        if (room.size() < p.getOrDefault("min_players", 1.0)) {
            recorder.open(e.type().getId(), room.countryId(), "SCHEDULE", "SKIPPED"); // 사람이 적어 건너뛴 것도 기록한다
            return;
        }
        spawn(e, room, "SCHEDULE");
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
