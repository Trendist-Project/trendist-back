package com.trendist.monster.service;

import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.game.room.Monster;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.monster.repository.MonsterSpawnRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 몬스터 등장 스케줄(4.35): 1초마다 켜진 몬스터 종류와 나라(방)마다 "지금이 등장할 때인가"를 본다.
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
            for (MonsterCatalog.Entry e : catalog.enabled()) {
                if (!"BOSS".equals(e.type().getKind())) continue;
                for (Room room : rooms.rooms()) schedule(e, room, now);
            }
            if (handled.size() > 200) handled.clear();
            if (alerted.size() > 200) alerted.clear();
        } catch (RuntimeException e) { log.warn("monster director failed", e); }
    }

    private void schedule(MonsterCatalog.Entry e, Room room, long now) {
        Map<String, Double> p = e.params();
        long interval = Math.max(1, Math.round(p.getOrDefault("interval_minutes", 60.0))) * 60;
        long offset = Math.round(p.getOrDefault("offset_minutes", 0.0)) * 60;
        long slot = Math.floorDiv(now - offset, interval) * interval + offset; // 지금이 속한 시간대의 시작(초)
        String code = e.type().getCode();
        // 예고: 다음 시간대가 announce_seconds 안에 다가왔고 사람이 충분하면 한 번만
        long announce = Math.round(p.getOrDefault("announce_seconds", 60.0)), next = slot + interval;
        String alertKey = code + ":" + room.countryId() + ":" + next;
        if (announce > 0 && next - now <= announce && !alerted.contains(alertKey) && !active(room, code)
                && room.size() >= p.getOrDefault("min_players", 3.0)) {
            alerted.add(alertKey);
            room.submit(r -> r.announce(Map.of("type", "monsterAlert", "name", e.type().getName(), "en", String.valueOf(e.type().getNameEn()), "seconds", next - System.currentTimeMillis() / 1000)));
        }
        // 등장: 시간대가 시작한 뒤 grace_seconds 안일 때만
        String key = code + ":" + room.countryId() + ":" + slot;
        if (now - slot >= p.getOrDefault("grace_seconds", 120.0) || handled.contains(key) || active(room, code)) return;
        handled.add(key);
        if (spawns.existsByMonsterTypeIdAndCountryIdAndStartedAtGreaterThanEqual(e.type().getId(), room.countryId(), Instant.ofEpochSecond(slot))) return;
        if (room.size() < p.getOrDefault("min_players", 3.0)) {
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
