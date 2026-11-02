package com.trendist.stats.service;

import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.country.service.CountryService;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.report.entity.Report;
import com.trendist.report.repository.ReportRepository;
import com.trendist.stats.dto.StatsDtos;
import com.trendist.stats.entity.SiteDailyStat;
import com.trendist.stats.entity.SiteHourlyStat;
import com.trendist.stats.entity.StatCounts;
import com.trendist.stats.repository.SiteDailyStatRepository;
import com.trendist.stats.repository.SiteHourlyStatRepository;
import com.trendist.stats.support.ActivityCounter;
import com.trendist.stats.support.OnlineTracker;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {
    private final CountryRepository countries;
    private final CountryService countryService;
    private final RoomManager rooms;
    private final PlayerRepository players;
    private final UserRepository users;
    private final KeywordRepository keywords;
    private final ReportRepository reports;
    private final SiteHourlyStatRepository hourlyRepo;
    private final SiteDailyStatRepository dailyRepo;
    private final ActivityCounter counter;
    private final OnlineTracker tracker;
    private final Clock clock;
    private long samples;

    @Override
    @Transactional
    public void sample() {
        Instant now = Instant.now(clock);
        boolean touch = samples++ % 6 == 0; // 마지막 접속 시각은 1분마다
        for (Country c : countries.findByEnabledTrue()) {
            List<Long> ids = rooms.livePlayers(c.getId()).stream().map(Room.Live::playerId).toList(); // 사람만(봇 제외)
            tracker.sample(c.getId(), ids, LocalDate.ofInstant(now, zone(c)));
            if (touch && !ids.isEmpty()) players.touchSeen(ids, now);
        }
    }

    @Override
    @Transactional
    public void closeHour() {
        writeHour(Instant.now(clock).truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(1)));
    }

    @Override
    @Transactional
    public void flushCurrentHour() {
        writeHour(Instant.now(clock).truncatedTo(ChronoUnit.HOURS));
    }

    private void writeHour(Instant hourStart) {
        for (Country c : countries.findByEnabledTrue()) {
            OnlineTracker.Hour h = tracker.drainHour(c.getId());
            long[] counts = counter.drain(c.getId());
            SiteHourlyStat row = hourlyRepo.findByCountryIdAndRecordedAt(c.getId(), hourStart)
                    .orElseGet(() -> new SiteHourlyStat(c.getId(), hourStart));
            row.merge(h.peak(), h.avg(), h.active(), counts);
            hourlyRepo.save(row);
            rewriteDay(c, LocalDate.ofInstant(hourStart, zone(c)));
        }
    }

    /** 그날 행을 그날의 시간별 행에서 다시 만든다(매번 덮어써서 재시작해도 중복 합산이 없다). */
    private void rewriteDay(Country c, LocalDate date) {
        ZoneId zone = zone(c);
        Instant from = date.atStartOfDay(zone).toInstant(), to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<SiteHourlyStat> hours = hourlyRepo.findByCountryIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(c.getId(), from, to);
        long[] sum = new long[ActivityCounter.Kind.values().length];
        long peak = 0;
        for (SiteHourlyStat s : hours) {
            long[] x = s.counts();
            for (int i = 0; i < sum.length; i++) sum[i] += x[i];
            peak = Math.max(peak, s.getPeakOnline());
        }
        SiteDailyStat day = dailyRepo.findByCountryIdAndRecordDate(c.getId(), date).orElseGet(() -> new SiteDailyStat(c.getId(), date));
        day.rewrite(peak, tracker.dayActive(c.getId(), date), sum);
        dailyRepo.save(day);
    }

    @Override
    @Transactional(readOnly = true)
    public StatsDtos.Summary summary(String countryCode) {
        Country c = countryService.requireOpen(countryCode);
        Instant now = Instant.now(clock);
        List<StatsDtos.Row> days = daily(c, 2, now);
        StatsDtos.Row y = days.get(0), t = days.get(1);

        Object[] u = users.countSummary(now).get(0);
        StatsDtos.Totals totals = new StatsDtos.Totals(n(u[0]), n(u[1]), n(u[2]), n(u[3]), n(u[4]), n(u[5]),
                players.countByCountryId(c.getId()),
                players.countByCountryIdAndLastSeenAtGreaterThanEqual(c.getId(), now.minus(Duration.ofDays(7))),
                players.countByCountryIdAndLastSeenAtGreaterThanEqual(c.getId(), now.minus(Duration.ofDays(30))));

        Map<String, Long> states = new LinkedHashMap<>();
        for (Object[] r : keywords.countByStateForAdmin(c.getId(), null, null)) states.put((String) r[0], n(r[1]));

        return new StatsDtos.Summary(rooms.livePlayers(c.getId()).size(), period(t), period(y), totals, states,
                reports.countByStatus(Report.RECEIVED), levelBuckets(c.getId()));
    }

    /** 레벨 분포: 1~10레벨은 하나씩, 그 뒤는 구간으로 묶는다. */
    private List<StatsDtos.Bucket> levelBuckets(long countryId) {
        Map<Long, Long> byLevel = players.levelCounts(countryId).stream()
                .collect(Collectors.toMap(r -> n(r[0]), r -> n(r[1])));
        long[][] ranges = {{1, 1}, {2, 2}, {3, 3}, {4, 4}, {5, 5}, {6, 6}, {7, 7}, {8, 8}, {9, 9}, {10, 10},
                {11, 20}, {21, 30}, {31, 50}, {51, 100}};
        List<StatsDtos.Bucket> out = new ArrayList<>();
        for (long[] r : ranges) {
            long sum = 0;
            for (long lv = r[0]; lv <= r[1]; lv++) sum += byLevel.getOrDefault(lv, 0L);
            out.add(new StatsDtos.Bucket(r[0] == r[1] ? String.valueOf(r[0]) : r[0] + "~" + r[1], sum));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatsDtos.Row> hourly(String countryCode, int hours) {
        Country c = countryService.requireOpen(countryCode);
        int n = Math.max(1, Math.min(hours, 24 * 31));
        Instant current = Instant.now(clock).truncatedTo(ChronoUnit.HOURS);
        Instant from = current.minus(Duration.ofHours(n - 1));
        Map<Instant, SiteHourlyStat> saved = hourlyRepo
                .findByCountryIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(c.getId(), from, current)
                .stream().collect(Collectors.toMap(SiteHourlyStat::getRecordedAt, Function.identity(), (a, b) -> a));
        List<StatsDtos.Row> out = new ArrayList<>();
        for (Instant t = from; t.isBefore(current); t = t.plus(Duration.ofHours(1))) {
            SiteHourlyStat s = saved.get(t);
            out.add(s == null ? row(t.toString(), 0, 0, 0, new long[8], false)
                    : row(t.toString(), s.getPeakOnline(), s.getAvgOnline(), s.getActivePlayers(), s.counts(), false));
        }
        OnlineTracker.Hour h = tracker.peekHour(c.getId());
        out.add(row(current.toString(), h.peak(), h.avg(), h.active(), counter.peek(c.getId()), true));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatsDtos.Row> daily(String countryCode, int days) {
        Country c = countryService.requireOpen(countryCode);
        return daily(c, Math.max(1, Math.min(days, 366)), Instant.now(clock));
    }

    private List<StatsDtos.Row> daily(Country c, int n, Instant now) {
        ZoneId zone = zone(c);
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate from = today.minusDays(n - 1);
        Map<LocalDate, SiteDailyStat> saved = dailyRepo.findByCountryIdAndRecordDateGreaterThanEqualOrderByRecordDateAsc(c.getId(), from)
                .stream().collect(Collectors.toMap(SiteDailyStat::getRecordDate, Function.identity(), (a, b) -> a));
        List<StatsDtos.Row> out = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(today); d = d.plusDays(1)) {
            SiteDailyStat s = saved.get(d);
            out.add(s == null ? row(d.toString(), 0, 0, 0, new long[8], false)
                    : row(d.toString(), s.getPeakOnline(), 0, s.getActivePlayers(), s.counts(), false));
        }
        // 오늘: 끝난 시간들의 합(그날 행) + 이번 시간의 메모리 값
        SiteDailyStat s = saved.get(today);
        long[] sum = s == null ? new long[8] : s.counts();
        long[] live = counter.peek(c.getId());
        for (int i = 0; i < sum.length; i++) sum[i] += live[i];
        long peak = Math.max(s == null ? 0 : s.getPeakOnline(), tracker.dayPeak(c.getId(), today));
        long active = Math.max(s == null ? 0 : s.getActivePlayers(), tracker.dayActive(c.getId(), today));
        out.add(row(today.toString(), peak, 0, active, sum, true));
        return out;
    }

    private static StatsDtos.Row row(String t, long peak, double avg, long active, long[] c, boolean live) {
        return new StatsDtos.Row(t, peak, Math.round(avg * 10) / 10.0, active, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], live);
    }

    private static StatsDtos.Period period(StatsDtos.Row r) {
        return new StatsDtos.Period(r.peakOnline(), r.activePlayers(), r.newPlayers(), r.plus(), r.search(), r.orb(),
                r.collision(), r.chat(), r.comment(), r.share());
    }

    private static ZoneId zone(Country c) { return ZoneId.of(c.getTimezone()); }

    private static long n(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
}
