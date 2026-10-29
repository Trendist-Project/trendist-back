package com.trendist.keyword.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.entity.KeywordLog24h;
import com.trendist.keyword.entity.KeywordLogDaily;
import com.trendist.keyword.repository.KeywordLog24hRepository;
import com.trendist.keyword.repository.KeywordLogDailyRepository;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.keyword.support.SearchCounter;
import com.trendist.player.entity.Player;
import com.trendist.player.service.PlayerService;
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
public class KeywordActivityServiceImpl implements KeywordActivityService {
    private static final int KEEP_DAYS = 180;

    private final KeywordRepository keywords;
    private final KeywordLog24hRepository hourly;
    private final KeywordLogDailyRepository daily;
    private final PlayerKeywordRepository playerKeywords;
    private final PlayerService playerService;
    private final SearchCounter searchCounter;
    private final Clock clock;

    // ---------------------------------------------------------------- 기록(매시 정각)

    @Override
    @Transactional
    public void recordHour(Instant hourStart, Map<Long, Long> searched) {
        Instant hourEnd = hourStart.plus(1, ChronoUnit.HOURS);
        // 빈 IN 목록을 피하려고 없는 번호 하나를 넣는다
        Collection<Long> ids = searched.isEmpty() ? List.of(-1L) : searched.keySet();
        for (Keyword k : keywords.findLogTargets(hourStart, ids)) {
            ZoneId zone = ZoneId.of(k.getCountry().getTimezone());
            Base base = base(k, hourStart);
            long searchInc = searched.getOrDefault(k.getId(), 0L);
            int slot = hourStart.atZone(zone).getHour();
            // 새 칸은 값을 채운 뒤에 저장한다(recorded_at은 NOT NULL)
            KeywordLog24h row = hourly.findByKeywordIdAndHourSlot(k.getId(), slot).orElseGet(() -> new KeywordLog24h(k, slot));
            row.overwrite(hourStart, k.getSearchCount(), k.getTotalPlusCount(), k.getHolderCount(),
                    searchInc, Math.max(0, k.getTotalPlusCount() - base.plus()), k.getHolderCount() - base.holders());
            hourly.saveAndFlush(row);
            rebuildDay(k, hourStart.atZone(zone).toLocalDate(), zone, hourEnd);
        }
        daily.deleteOlderThan(LocalDate.now(clock).minusDays(KEEP_DAYS));
    }

    /** 그 현지 날짜의 시간별 칸에서 하루 행을 다시 만든다(증분은 합, 누적값은 마지막 칸). 매번 덮어써서 중복 합산이 없다. */
    private void rebuildDay(Keyword k, LocalDate date, ZoneId zone, Instant until) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        List<KeywordLog24h> rows = hourly.findByKeywordIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAt(
                k.getId(), dayStart, until);
        if (rows.isEmpty()) return;
        KeywordLog24h last = rows.getLast();
        KeywordLogDaily d = daily.findByKeywordIdAndRecordDate(k.getId(), date).orElseGet(() -> new KeywordLogDaily(k, date));
        daily.save(d);
        d.overwrite(last.getSearchCount(), last.getTotalPlusCount(), last.getHolderCount(),
                rows.stream().mapToLong(KeywordLog24h::getSearchInc).sum(),
                rows.stream().mapToLong(KeywordLog24h::getTotalPlusInc).sum(),
                rows.stream().mapToLong(KeywordLog24h::getHolderInc).sum());
    }

    /** 그 시각 직전의 누적 + 수와 가진 사람 수. 증분은 지금 값에서 이것을 뺀다. */
    private record Base(long plus, long holders) {}

    private Base base(Keyword k, Instant since) {
        Optional<KeywordLog24h> prev = hourly.findTopByKeywordIdAndRecordedAtBeforeOrderByRecordedAtDesc(k.getId(), since);
        if (prev.isPresent()) return new Base(prev.get().getTotalPlusCount(), prev.get().getHolderCount());
        // 직전 기록이 없다(새 키워드거나 기록을 시작하기 전부터 있던 키워드). 그 시간 뒤에 +되어 남아 있는 행만 이번 증분으로 본다.
        // 옛 누적값이 한 시간에 몰려 그래프가 튀지 않게 하려는 것이다
        long recent = playerKeywords.countPlusedSince(k.getId(), since);
        return new Base(Math.max(0, k.getTotalPlusCount() - recent), Math.max(0, k.getHolderCount() - recent));
    }

    // ---------------------------------------------------------------- 조회

    @Override
    @Transactional(readOnly = true)
    public KeywordDtos.Activity activity(long userId, long keywordId, String range) {
        Player p = playerService.requireCurrent(userId);
        Keyword k = keywords.findById(keywordId)
                .filter(x -> x.getCountry().getId().equals(p.getCountry().getId()))
                .filter(x -> !CodeTables.KW_BANNED.equals(x.getKeywordState().getName()))
                .orElseThrow(() -> ApiException.notFound("KEYWORD_NOT_FOUND", "키워드를 찾을 수 없어요"));
        ZoneId zone = ZoneId.of(k.getCountry().getTimezone());
        Instant now = Instant.now(clock);
        Instant thisHour = now.truncatedTo(ChronoUnit.HOURS);

        // 이번 시간: 아직 정각 잡이 기록하지 않았으므로 지금 누적값과 메모리 검색 카운터로 만든다("집계 중")
        Base base = base(k, thisHour);
        long liveSearch = searchCounter.pending(k.getId());
        long livePlus = Math.max(0, k.getTotalPlusCount() - base.plus());

        List<KeywordDtos.ActivityPoint> points = switch (range) {
            case "24h" -> hourlyPoints(k, thisHour, liveSearch, livePlus);
            case "7d" -> dailyPoints(k, zone, thisHour, 7, liveSearch, livePlus);
            case "30d" -> dailyPoints(k, zone, thisHour, 30, liveSearch, livePlus);
            case "180d" -> dailyPoints(k, zone, thisHour, 180, liveSearch, livePlus);
            default -> throw ApiException.badRequest("ACTIVITY_RANGE", "기간은 24h, 7d, 30d, 180d 중 하나예요");
        };
        return new KeywordDtos.Activity(range, zone.getId(), points,
                k.getSearchCount() + liveSearch, k.getTotalPlusCount(), k.getHolderCount());
    }

    /** 최근 24시간: 지난 23시간의 칸(없으면 0) + 이번 시간. 가진 사람 수는 기록이 없는 시간에 직전 값을 이어 쓴다. */
    private List<KeywordDtos.ActivityPoint> hourlyPoints(Keyword k, Instant thisHour, long liveSearch, long livePlus) {
        Instant from = thisHour.minus(23, ChronoUnit.HOURS);
        Map<Instant, KeywordLog24h> rows = new HashMap<>();
        for (KeywordLog24h r : hourly.findByKeywordIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAt(k.getId(), from, thisHour)) {
            rows.put(r.getRecordedAt(), r);
        }
        long holders = hourly.findTopByKeywordIdAndRecordedAtBeforeOrderByRecordedAtDesc(k.getId(), from)
                .map(KeywordLog24h::getHolderCount).orElse(0L);
        List<KeywordDtos.ActivityPoint> out = new ArrayList<>();
        for (Instant t = from; t.isBefore(thisHour); t = t.plus(1, ChronoUnit.HOURS)) {
            KeywordLog24h r = rows.get(t);
            if (r != null) holders = r.getHolderCount();
            out.add(new KeywordDtos.ActivityPoint(t, r == null ? 0 : r.getSearchInc(), r == null ? 0 : r.getTotalPlusInc(), holders, false));
        }
        out.add(new KeywordDtos.ActivityPoint(thisHour, liveSearch, livePlus, k.getHolderCount(), true));
        return out;
    }

    /** 최근 N일(현지 날짜): 지난 날은 하루 행(없으면 0), 오늘은 오늘의 시간별 칸 + 이번 시간. */
    private List<KeywordDtos.ActivityPoint> dailyPoints(Keyword k, ZoneId zone, Instant thisHour, int days, long liveSearch, long livePlus) {
        LocalDate today = thisHour.atZone(zone).toLocalDate();
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, KeywordLogDaily> rows = new HashMap<>();
        long holders = 0;
        for (KeywordLogDaily d : daily.findByKeywordIdAndRecordDateGreaterThanEqualOrderByRecordDate(k.getId(), from.minusDays(1))) {
            if (d.getRecordDate().isBefore(from)) holders = d.getHolderCount(); // 구간 바로 앞날의 가진 사람 수
            else rows.put(d.getRecordDate(), d);
        }
        List<KeywordDtos.ActivityPoint> out = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(today); date = date.plusDays(1)) {
            KeywordLogDaily d = rows.get(date);
            if (d != null) holders = d.getHolderCount();
            out.add(new KeywordDtos.ActivityPoint(date.atStartOfDay(zone).toInstant(),
                    d == null ? 0 : d.getSearchInc(), d == null ? 0 : d.getTotalPlusInc(), holders, false));
        }
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        long search = liveSearch, plus = livePlus;
        for (KeywordLog24h r : hourly.findByKeywordIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAt(k.getId(), todayStart, thisHour)) {
            search += r.getSearchInc();
            plus += r.getTotalPlusInc();
        }
        out.add(new KeywordDtos.ActivityPoint(todayStart, search, plus, k.getHolderCount(), true));
        return out;
    }
}
