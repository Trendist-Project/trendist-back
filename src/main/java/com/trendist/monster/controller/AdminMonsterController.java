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

    public record SettingItem(String name, double value, double min, double max, String description, String unit, String group) {}
    public record TypeItem(String code, String name, String nameEn, String kind, String behavior, String image, boolean enabled, List<SettingItem> settings) {}
    public record SettingUpdate(Double value) {}
    public record EnabledUpdate(Boolean enabled) {}
    public record SpawnItem(Long id, String code, String name, String country, String source, String status, Instant startedAt, Instant endedAt,
                            long maxHp, long finalHp, int humans, long avgScore, long topScore, int participants, long topDamage) {}

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

    @GetMapping("/spawns")
    @Transactional(readOnly = true)
    public List<SpawnItem> history(@RequestParam(defaultValue = "30") int limit) {
        Map<Long, MonsterType> byId = new java.util.HashMap<>();
        types.findAll().forEach(t -> byId.put(t.getId(), t));
        Map<Long, String> codes = new java.util.HashMap<>();
        countries.findAll().forEach(c -> codes.put(c.getId(), c.getCode()));
        List<SpawnItem> out = new ArrayList<>();
        for (MonsterSpawn s : spawns.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(200, limit))))) {
            MonsterType t = byId.get(s.getMonsterTypeId());
            out.add(new SpawnItem(s.getId(), t == null ? "?" : t.getCode(), t == null ? "?" : t.getName(), codes.getOrDefault(s.getCountryId(), "?"), s.getSource(), s.getStatus(),
                    s.getStartedAt(), s.getEndedAt(), s.getMaxHp(), s.getFinalHp(), s.getHumans(), s.getAvgScore(), s.getTopScore(), s.getParticipants(), s.getTopDamage()));
        }
        return out;
    }

    private static SettingItem item(MonsterSetting s) {
        return new SettingItem(s.getName(), s.getValue().doubleValue(), s.getMin().doubleValue(), s.getMax().doubleValue(), s.getDescription(), s.getUnit(), s.getGroupName());
    }
}
