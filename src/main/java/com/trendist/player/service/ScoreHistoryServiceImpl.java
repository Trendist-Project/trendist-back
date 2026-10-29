package com.trendist.player.service;

import com.trendist.common.exception.ApiException;
import com.trendist.game.service.RoomManager;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import com.trendist.player.entity.PlayerScore24h;
import com.trendist.player.entity.PlayerScoreDaily;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.repository.PlayerScore24hRepository;
import com.trendist.player.repository.PlayerScoreDailyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScoreHistoryServiceImpl implements ScoreHistoryService {
    private final PlayerRepository players;
    private final PlayerScore24hRepository hourly;
    private final PlayerScoreDailyRepository daily;
    private final PlayerService playerService;
    private final RoomManager rooms;
    private final Clock clock;

    // ---------------------------------------------------------------- 기록(매시 정각)

    @Override
    @Transactional
    public int recordHour(Instant hourStart) {
        int written = 0;
        for (Player p : players.findChangedSince(hourStart)) {
            long score = p.getCurrentScore();
            // 점수가 바뀐 시간만 기록한다(행이 바뀌었어도 점수는 같을 수 있다: 닉네임 변경 등)
            Optional<PlayerScore24h> prev = hourly.findTopByPlayerIdAndRecordedAtBeforeOrderByRecordedAtDesc(p.getId(), hourStart);
            if (prev.isPresent() && prev.get().getCurrentScore() == score) continue;
            ZoneId zone = ZoneId.of(p.getCountry().getTimezone());
            int slot = hourStart.atZone(zone).getHour();
            // 새 칸은 값을 채운 뒤에 저장한다(recorded_at 등은 NOT NULL)
            PlayerScore24h row = hourly.findByPlayerIdAndHourSlot(p.getId(), slot).orElseGet(() -> new PlayerScore24h(p, slot));
            row.overwrite(hourStart, score);
            hourly.save(row);
            LocalDate date = hourStart.atZone(zone).toLocalDate();
            PlayerScoreDaily day = daily.findByPlayerIdAndRecordDate(p.getId(), date).orElseGet(() -> new PlayerScoreDaily(p, date));
            day.overwrite(score);
            daily.save(day);
            written++;
        }
        return written;
    }

    // ---------------------------------------------------------------- 조회

    @Override
    @Transactional(readOnly = true)
    public PlayerDtos.ScoreHistory history(long userId, String range) {
        return historyOf(playerService.requireCurrent(userId), range);
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerDtos.ScoreHistory weekOf(long playerId) {
        return historyOf(players.findById(playerId).orElseThrow(), "7d");
    }

    private PlayerDtos.ScoreHistory historyOf(Player p, String range) {
        ZoneId zone = ZoneId.of(p.getCountry().getTimezone());
        Instant now = Instant.now(clock);
        // 지금 값: 접속 중이면 방 메모리의 실시간 점수, 아니면 DB 값
        Double live = rooms.liveScore(p.getCountry().getId(), p.getId());
        long current = live != null ? Math.round(live) : p.getCurrentScore();
        List<PlayerDtos.ScorePoint> points = switch (range) {
            case "24h" -> hourlyPoints(p, now, current);
            case "7d" -> dailyPoints(p, zone, now, 7, current);
            case "30d" -> dailyPoints(p, zone, now, 30, current);
            case "all" -> dailyPoints(p, zone, now, allDays(p, zone, now), current);
            default -> throw ApiException.badRequest("SCORE_RANGE", "기간은 24h, 7d, 30d, all 중 하나예요");
        };
        return new PlayerDtos.ScoreHistory(range, zone.getId(), points);
    }

    /** 최근 24시간: 지난 23시간의 칸(없으면 직전 값) + 이번 시간(지금 값). */
    private List<PlayerDtos.ScorePoint> hourlyPoints(Player p, Instant now, long current) {
        Instant thisHour = now.truncatedTo(ChronoUnit.HOURS), from = thisHour.minus(23, ChronoUnit.HOURS);
        Map<Instant, Long> rows = new HashMap<>();
        for (PlayerScore24h r : hourly.findByPlayerIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAt(p.getId(), from, thisHour)) {
            rows.put(r.getRecordedAt(), r.getCurrentScore());
        }
        long carry = hourly.findTopByPlayerIdAndRecordedAtBeforeOrderByRecordedAtDesc(p.getId(), from)
                .map(PlayerScore24h::getCurrentScore).orElse(firstKnown(rows, current));
        List<PlayerDtos.ScorePoint> out = new ArrayList<>();
        for (Instant t = from; t.isBefore(thisHour); t = t.plus(1, ChronoUnit.HOURS)) {
            carry = rows.getOrDefault(t, carry);
            out.add(new PlayerDtos.ScorePoint(t, carry, false));
        }
        out.add(new PlayerDtos.ScorePoint(thisHour, current, true));
        return out;
    }

    /** 최근 N일(현지 날짜): 지난 날은 하루 행(없으면 직전 값), 오늘은 지금 값. */
    private List<PlayerDtos.ScorePoint> dailyPoints(Player p, ZoneId zone, Instant now, int days, long current) {
        LocalDate today = now.atZone(zone).toLocalDate(), from = today.minusDays(days - 1L);
        Map<LocalDate, Long> rows = new HashMap<>();
        for (PlayerScoreDaily d : daily.findByPlayerIdAndRecordDateGreaterThanEqualOrderByRecordDate(p.getId(), from)) {
            rows.put(d.getRecordDate(), d.getCurrentScore());
        }
        long carry = daily.findTopByPlayerIdAndRecordDateBeforeOrderByRecordDateDesc(p.getId(), from)
                .map(PlayerScoreDaily::getCurrentScore).orElse(firstKnown(rows, current));
        List<PlayerDtos.ScorePoint> out = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(today); d = d.plusDays(1)) {
            carry = rows.getOrDefault(d, carry);
            out.add(new PlayerDtos.ScorePoint(d.atStartOfDay(zone).toInstant(), carry, false));
        }
        out.add(new PlayerDtos.ScorePoint(today.atStartOfDay(zone).toInstant(), current, true));
        return out;
    }

    /** 전체: 첫 기록 날부터 오늘까지(기록이 없으면 오늘 하루). 플레이어를 만든 날보다 앞서지 않는다. */
    private int allDays(Player p, ZoneId zone, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate first = daily.findTopByPlayerIdOrderByRecordDateAsc(p.getId()).map(PlayerScoreDaily::getRecordDate).orElse(today);
        return (int) Math.max(1, ChronoUnit.DAYS.between(first, today) + 1);
    }

    /**
     * 구간 앞에 기록이 없을 때 처음 칸에 쓸 값. 구간 안에 기록이 있으면 그중 가장 이른 값, 없으면 지금 값
     * (점수가 바뀐 시간만 기록하므로, 기록이 없다는 것은 그동안 지금과 같았다는 뜻이다).
     */
    private static <K extends Comparable<? super K>> long firstKnown(Map<K, Long> rows, long current) {
        return rows.isEmpty() ? current : rows.get(Collections.min(rows.keySet()));
    }
}
