package com.trendist.game.room;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 몬스터(4.35): 방 안에서 싸우는 일반 원이 아닌 개체. 보스몹이 첫 번째이고, 다른 몬스터는 이 클래스를 이어받아 만든다
 * (행동은 서버 코드, 종류와 설정값은 DB monster_types·monster_settings). 틱 스레드에서만 다룬다.
 * 체력(유행도 값)·위치·플레이어별 피해 기록은 여기서 들고, 패턴과 충돌 규칙은 이어받은 클래스가 정한다.
 */
public abstract class Monster {
    public static final String KILLED = "KILLED", TIMEOUT = "TIMEOUT", REMOVED = "REMOVED";

    /** 등장 요청: DB 기록 번호, 종류 코드와 이름, 행동 키, 그림 키, 설정값(이름 → 값) */
    public record Spec(long spawnId, String code, String name, String nameEn, String behavior, String image, Map<String, Double> params) {}
    /** 끝난 결과: 방 밖(DB)에서 기록하고 보상을 준다 */
    public record Result(Spec spec, String status, long maxHp, long finalHp, List<Part> parts, List<Reward> rewards) {}
    public record Part(long playerId, String nickname, long damage, int hits) {}
    /** exp·keywords는 줄 양, cleared는 처치 성공 보상인지(처치 횟수 +1) */
    public record Reward(long playerId, int rank, long damage, long exp, double levelPercent, int keywords, boolean cleared) {}
    /** 등장 직후 정해진 체력과 그때의 접속 사람 수·평균·1등 유행도 */
    public record Start(long maxHp, int humans, long avgScore, long topScore) {}

    static final class Ledger { String nickname; long damage; int hits; }

    public final int id;
    public final Spec spec;
    public double x, y;
    public final double radius;
    public long hp;
    public final long maxHp;
    public final double startedAt, endsAt;
    public final Start start;
    boolean ended;
    /** 플레이어 번호 → 입힌 피해(입힌 순서대로) */
    final Map<Long, Ledger> ledger = new LinkedHashMap<>();

    protected Monster(int id, Spec spec, double radius, Start start, double now, double durationSeconds) {
        this.id = id; this.spec = spec; this.radius = radius; this.start = start;
        this.maxHp = start.maxHp(); this.hp = start.maxHp(); this.startedAt = now; this.endsAt = now + durationSeconds;
    }

    /** 행동 키에 맞는 몬스터를 만든다. 모르는 키면 null */
    static Monster create(Room room, int id, Spec spec) {
        return switch (spec.behavior()) {
            case "ELDER_TURTLE" -> ElderTurtle.spawn(room, id, spec);
            default -> null;
        };
    }

    /** 현재 설정값(방이 1초마다 새로 받는다). 없으면 등장할 때 받은 값, 그것도 없으면 def */
    double p(Room room, String name) {
        Map<String, Double> live = room.monsterParams(spec.code());
        Double v = live != null ? live.get(name) : null;
        if (v == null) v = spec.params().get(name);
        return v == null ? 0 : v;
    }

    /** 틱마다 한 번(50ms). 시간 초과·체력 소진은 여기서 room.endMonster로 끝낸다 */
    abstract void tick(Room room, double dt);
    /** 모두에게 보내는 상태(위치·체력·예고 등). 보통 10Hz, fastState()가 true인 동안(빠르게 움직일 때)은 매 틱 */
    abstract Map<String, Object> stateMessage(Room room);
    /** 위치가 빨리 바뀌는 중이라 상태를 매 틱 보내야 매끄러운가 */
    boolean fastState() { return false; }
    /** 관리자 모니터링(1초마다 갱신)에 보이는 요약 */
    abstract Map<String, Object> viewMessage(Room room);

    void record(Room.Member m, long damage) {
        Ledger l = ledger.computeIfAbsent(m.playerId, k -> new Ledger());
        l.nickname = m.nickname; l.damage += damage; l.hits++;
    }

    /** 피해 순위(내림차순) */
    List<Map.Entry<Long, Ledger>> ranking() {
        List<Map.Entry<Long, Ledger>> list = new ArrayList<>(ledger.entrySet());
        list.sort(Comparator.comparingLong((Map.Entry<Long, Ledger> e) -> e.getValue().damage).reversed());
        return list;
    }

    /** 끝난 결과와 보상 계획. 참여 인정 피해 이상인 사람만 받는다: 처치 성공이면 EXP·🔥 키워드(1~3위는 추가), 시간 초과면 EXP만, 관리자 제거는 없음 */
    Result result(Room room, String status) {
        List<Part> parts = new ArrayList<>();
        List<Reward> rewards = new ArrayList<>();
        double minShare = maxHp * p(room, "reward_min_share") / 100.0;
        int rank = 0;
        double totalDamage = 0;
        for (Ledger l : ledger.values()) totalDamage += Math.max(0, l.damage);
        for (Map.Entry<Long, Ledger> e : ranking()) {
            rank++;
            Ledger l = e.getValue();
            parts.add(new Part(e.getKey(), l.nickname, l.damage, l.hits));
            if (l.damage <= 0 || l.damage < minShare) continue;
            boolean top = rank <= 3;
            if (KILLED.equals(status)) {
                // 기여도 비례: 전체 피해에서 내가 차지한 몫(0~1)만큼 레벨 진행(%)을 받는다. 그 %는 받는 사람 자신의 "다음 레벨까지 필요 EXP" 기준이라 사람마다 EXP가 다르다
                // (실제 EXP로 바꾸는 것은 보상을 줄 때 PlayerService). 기본 EXP·키워드는 참여만 해도 받는다
                double share = totalDamage > 0 ? l.damage / totalDamage : 0;
                double levelPercent = p(room, "reward_level_percent") * share;
                long exp = Math.round(p(room, "reward_exp") + (top ? p(room, "reward_top_exp") : 0));
                int kw = (int) Math.round(Math.min(p(room, "reward_keywords_max"), p(room, "reward_keywords") + p(room, "reward_keyword_pool") * share) + (top ? p(room, "reward_top_keywords") : 0));
                rewards.add(new Reward(e.getKey(), rank, l.damage, exp, levelPercent, kw, true));
            } else if (TIMEOUT.equals(status)) {
                long exp = Math.round(p(room, "fail_exp"));
                if (exp > 0) rewards.add(new Reward(e.getKey(), rank, l.damage, exp, 0, 0, false));
            }
        }
        return new Result(spec, status, maxHp, Math.max(0, hp), parts, rewards);
    }

    Map<String, Object> topList(int n) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, Ledger> e : ranking()) {
            if (list.size() >= n) break;
            Map<String, Object> row = new HashMap<>();
            row.put("playerId", e.getKey()); row.put("nickname", e.getValue().nickname); row.put("damage", e.getValue().damage); row.put("hits", e.getValue().hits);
            list.add(row);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("top", list);
        return m;
    }
}
