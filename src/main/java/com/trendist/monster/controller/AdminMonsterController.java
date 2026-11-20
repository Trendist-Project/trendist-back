package com.trendist.monster.controller;

import com.trendist.auth.security.AuthUser;
import com.trendist.common.exception.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.monster.entity.MonsterSetting;
import com.trendist.monster.entity.MonsterSpawn;
import com.trendist.monster.entity.MonsterType;
import com.trendist.monster.repository.MonsterSettingRepository;
import com.trendist.monster.repository.MonsterSpawnRepository;
import com.trendist.monster.repository.MonsterTypeRepository;
import com.trendist.monster.service.MonsterCatalog;
import com.trendist.monster.service.MonsterDirector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 몬스터(13.16, 4.35): 종류와 설정값 보기·고치기, 즉시 소환·제거, 지금 싸우는 몬스터 모니터링, 등장 기록.
 * 설정값은 저장하면 바로 DB에 들어가고 게임 방에는 1초 안에 반영된다(체력·몸 크기는 다음 등장부터).
 */
@RestController
@RequestMapping("/api/admin/monsters")
@RequiredArgsConstructor
public class AdminMonsterController {
    private final MonsterTypeRepository types;
    private final MonsterSettingRepository settings;
    private final MonsterSpawnRepository spawns;
    private final CountryRepository countries;
    private final MonsterCatalog catalog;
    private final MonsterDirector director;
    private final RoomManager rooms;
    private final com.trendist.monster.service.MonsterConfigService config;
    /** 예전에 사람이 적어 건너뛴 시간을 남긴 행의 상태(지금은 남기지 않고, 옛 행은 화면에서 숨긴다) */
    private static final String SKIPPED = "SKIPPED";

    public record SettingItem(String name, double value, double min, double max, String description, String unit, String group) {}
    public record TypeItem(String code, String name, String nameEn, String kind, String behavior, String image, boolean enabled, List<SettingItem> settings) {}
    public record SettingUpdate(Double value) {}
    public record EnabledUpdate(Boolean enabled) {}
    public record SpawnPage(List<SpawnItem> items, int page, int size, long total, int totalPages) {}
    public record SpawnItem(Long id, String code, String name, String country, String source, String status, Instant startedAt, Instant endedAt,
                            long maxHp, long finalHp, int humans, long avgScore, long topScore, int participants, long topDamage) {}

    public record TimelineEntry(String code, String name, String nameEn, String image, String status, String source, Instant startedAt, int participants, long maxHp, long finalHp) {}
    public record TimelineRow(Instant hour, Map<String, List<TimelineEntry>> cells) {}
    /** 이번 사이클(나라별): 크기, 지금이 몇 번째 칸인지(0부터), 칸마다 시작 시각과 보스 */
    public record CycleItem(Instant start, String code, String name, String image) {}
    public record CycleView(int size, int index, List<CycleItem> items) {}
    public record Timeline(Instant now, List<String> countries, List<TimelineRow> rows, Map<String, CycleView> cycles) {}

    @GetMapping("/types")
    @Transactional(readOnly = true)
    public List<TypeItem> list() {
        List<TypeItem> out = new ArrayList<>();
        for (MonsterType t : types.findAllByOrderBySortOrderAscIdAsc()) {
            List<SettingItem> items = settings.findByMonsterTypeIdOrderBySortOrderAscIdAsc(t.getId()).stream().map(AdminMonsterController::item).toList();
            out.add(new TypeItem(t.getCode(), t.getName(), t.getNameEn(), t.getKind(), t.getBehavior(), t.getImage(), t.isEnabled(), items));
        }
        return out;
    }

    @PatchMapping("/types/{code}")
    @Transactional
    public TypeItem setEnabled(@AuthenticationPrincipal AuthUser user, @PathVariable String code, @RequestBody EnabledUpdate req) {
        if (req.enabled() != null) config.setEnabled(code, req.enabled(), user.userId(), null);
        return list().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
    }

    @PatchMapping("/types/{code}/settings/{name}")
    public SettingItem setValue(@AuthenticationPrincipal AuthUser user, @PathVariable String code, @PathVariable String name, @RequestBody SettingUpdate req) {
        return item(config.setValue(code, name, req.value(), user.userId(), null));
    }

    /** 지금 싸우는 몬스터(나라별). 방에서 1초마다 갱신한 복사본을 읽는다 */
    @GetMapping("/live")
    public List<Map<String, Object>> live() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<Long, String> codes = new java.util.HashMap<>();
        countries.findAll().forEach(c -> codes.put(c.getId(), c.getCode()));
        for (Room r : rooms.rooms()) {
            for (Map<String, Object> v : r.monsterView()) {
                Map<String, Object> row = new java.util.HashMap<>(v);
                row.put("country", codes.getOrDefault(r.countryId(), String.valueOf(r.countryId())));
                row.put("roomHumans", r.size());
                out.add(row);
            }
        }
        return out;
    }

    /** 나라별 방의 접속 사람 수(소환 화면에서 보여 준다) */
    @GetMapping("/rooms")
    public List<Map<String, Object>> roomList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Country c : countries.findByEnabledTrue()) {
            Room r = rooms.rooms().stream().filter(x -> x.countryId() == c.getId()).findFirst().orElse(null);
            out.add(Map.of("country", c.getCode(), "humans", r == null ? 0 : r.size()));
        }
        return out;
    }

    @PostMapping("/types/{code}/spawn")
    public Map<String, Object> spawn(@PathVariable String code, @RequestParam(defaultValue = "KR") String country) {
        if (catalog.get(code) == null) throw ApiException.notFound("MONSTER_NOT_FOUND", "없는 몬스터예요");
        if (countries.findByCode(country).isEmpty()) throw ApiException.badRequest("COUNTRY_NOT_FOUND", "없는 나라예요");
        if (!director.spawnNow(code, country)) throw ApiException.conflict("MONSTER_ACTIVE", "이미 싸우는 중이에요");
        return Map.of("ok", true);
    }

    /** 제거(reward=false, 보상 없음) 또는 처치 처리(reward=true, 지금까지의 피해로 보상 지급) */
    @PostMapping("/{monsterId}/end")
    public Map<String, Object> end(@PathVariable int monsterId, @RequestParam(defaultValue = "KR") String country, @RequestParam(defaultValue = "false") boolean reward) {
        if (!director.end(monsterId, country, reward)) throw ApiException.notFound("MONSTER_NOT_ACTIVE", "싸우는 중인 몬스터가 아니에요");
        return Map.of("ok", true);
    }

    /**
     * 시간별 출현표: 정각마다 나라별로 어떤 보스가 나왔는지(지난 `past`시간)와 나올 예정인지(앞으로 `ahead`시간)를 최신순(미래가 위)으로 준다.
     * 지난 칸은 등장 기록(처치·시간 초과·제거·싸우는 중, 관리자 소환 포함)이고 사람이 적어 건너뛴 시간은 기록이 없으니 빈 칸이다.
     * 앞날 칸은 예고·등장과 같은 뽑기로 미리 계산한 예정(SCHEDULED)이라 정기 등장이 켜진 보스만 나온다. `cycles`는 나라별 이번 사이클(한 사이클에 보스마다 최소 한 번)의 칸 목록이다.
     */
    @GetMapping("/timeline")
    @Transactional(readOnly = true)
    public Timeline timeline(@RequestParam(defaultValue = "24") int past, @RequestParam(defaultValue = "12") int ahead) {
        int back = Math.max(1, Math.min(72, past)), fwd = Math.max(0, Math.min(48, ahead));
        Instant now = Instant.now(), thisHour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        Instant from = thisHour.minus(back, java.time.temporal.ChronoUnit.HOURS), to = thisHour.plus(fwd + 1, java.time.temporal.ChronoUnit.HOURS);
        List<String> codes = countries.findByEnabledTrue().stream().map(Country::getCode).toList();
        Map<Long, String> countryCode = new java.util.HashMap<>();
        countries.findAll().forEach(c -> countryCode.put(c.getId(), c.getCode()));
        Map<Long, MonsterType> byId = new java.util.HashMap<>();
        types.findAll().forEach(t -> byId.put(t.getId(), t));
        Map<Instant, Map<String, List<TimelineEntry>>> grid = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (Instant h = from; h.isBefore(to); h = h.plus(1, java.time.temporal.ChronoUnit.HOURS)) {
            Map<String, List<TimelineEntry>> cells = new java.util.LinkedHashMap<>();
            for (String c : codes) cells.put(c, new ArrayList<>());
            grid.put(h, cells);
        }
        for (MonsterSpawn s : spawns.findByStartedAtGreaterThanEqualAndStartedAtLessThanAndStatusNotOrderByStartedAtAsc(from, to, SKIPPED)) {
            MonsterType t = byId.get(s.getMonsterTypeId());
            Map<String, List<TimelineEntry>> cells = grid.get(s.getStartedAt().truncatedTo(java.time.temporal.ChronoUnit.HOURS));
            List<TimelineEntry> cell = cells == null ? null : cells.get(countryCode.get(s.getCountryId()));
            if (t == null || cell == null) continue;
            cell.add(new TimelineEntry(t.getCode(), t.getName(), t.getNameEn(), t.getImage(), s.getStatus(), s.getSource(), s.getStartedAt(), s.getParticipants(), s.getMaxHp(), s.getFinalHp()));
        }
        for (MonsterDirector.Upcoming u : director.upcoming(now.getEpochSecond(), to.getEpochSecond() - 1)) {
            Instant start = Instant.ofEpochSecond(u.startEpoch());
            Map<String, List<TimelineEntry>> cells = grid.get(start.truncatedTo(java.time.temporal.ChronoUnit.HOURS));
            List<TimelineEntry> cell = cells == null ? null : cells.get(u.countryCode());
            if (cell == null) continue;
            MonsterType t = u.boss().type();
            cell.add(new TimelineEntry(t.getCode(), t.getName(), t.getNameEn(), t.getImage(), "SCHEDULED", "SCHEDULE", start, 0, 0, 0));
        }
        List<TimelineRow> rows = new ArrayList<>();
        grid.forEach((h, cells) -> rows.add(new TimelineRow(h, cells)));
        Map<String, CycleView> cycles = new java.util.LinkedHashMap<>();
        MonsterDirector.Cycle cy = director.cycle(now.getEpochSecond()); // 모든 나라가 같은 사이클이다
        if (cy != null) {
            List<CycleItem> items = new ArrayList<>();
            for (MonsterDirector.CyclePos p : cy.positions()) items.add(new CycleItem(Instant.ofEpochSecond(p.startEpoch()), p.boss().type().getCode(), p.boss().type().getName(), p.boss().type().getImage()));
            CycleView view = new CycleView(cy.size(), cy.index(), items);
            for (String c : codes) cycles.put(c, view);
        }
        return new Timeline(now, codes, rows, cycles);
    }

    /** 등장 기록(페이지 단위, 최신순). code가 있으면 그 종류만, 없으면 전부. size 기본 10 */
    @GetMapping("/spawns")
    @Transactional(readOnly = true)
    public SpawnPage history(@RequestParam(required = false) String code, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Map<Long, MonsterType> byId = new java.util.HashMap<>();
        types.findAll().forEach(t -> byId.put(t.getId(), t));
        Map<Long, String> codes = new java.util.HashMap<>();
        countries.findAll().forEach(c -> codes.put(c.getId(), c.getCode()));
        PageRequest req = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        org.springframework.data.domain.Page<MonsterSpawn> found;
        if (code == null || code.isBlank()) found = spawns.findByStatusNot(SKIPPED, req);
        else {
            MonsterType t = types.findByCode(code).orElse(null);
            found = t == null ? org.springframework.data.domain.Page.empty(req) : spawns.findByMonsterTypeIdAndStatusNot(t.getId(), SKIPPED, req);
        }
        List<SpawnItem> out = new ArrayList<>();
        for (MonsterSpawn s : found.getContent()) {
            MonsterType t = byId.get(s.getMonsterTypeId());
            out.add(new SpawnItem(s.getId(), t == null ? "?" : t.getCode(), t == null ? "?" : t.getName(), codes.getOrDefault(s.getCountryId(), "?"), s.getSource(), s.getStatus(),
                    s.getStartedAt(), s.getEndedAt(), s.getMaxHp(), s.getFinalHp(), s.getHumans(), s.getAvgScore(), s.getTopScore(), s.getParticipants(), s.getTopDamage()));
        }
        return new SpawnPage(out, found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    private static SettingItem item(MonsterSetting s) {
        return new SettingItem(s.getName(), s.getValue().doubleValue(), s.getMin().doubleValue(), s.getMax().doubleValue(), s.getDescription(), s.getUnit(), s.getGroupName());
    }
}
