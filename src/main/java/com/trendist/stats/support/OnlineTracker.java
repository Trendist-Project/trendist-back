package com.trendist.stats.support;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 접속 인원 표본(13.9). 10초마다 나라별 방에 접속 중인 사람(봇 제외)을 받아 이번 시간의 최고·평균·활동 인원과
 * 그날의 최고·활동 인원을 메모리에 모은다. 매시 정각 잡이 시간 값을 꺼내 비운다.
 */
@Component
public class OnlineTracker {
    /** 이번 시간: 최고, 표본 합·수, 한 번이라도 있던 플레이어 */
    public record Hour(long peak, double avg, long active) {}

    private static final class Acc {
        long peak, sum, samples;
        final Set<Long> hourActive = new HashSet<>();
        final Map<LocalDate, Set<Long>> dayActive = new java.util.HashMap<>();
        final Map<LocalDate, Long> dayPeak = new java.util.HashMap<>();
    }

    private final Map<Long, Acc> accs = new ConcurrentHashMap<>();

    public void sample(long countryId, Collection<Long> playerIds, LocalDate localDate) {
        Acc a = accs.computeIfAbsent(countryId, k -> new Acc());
        synchronized (a) {
            long n = playerIds.size();
            a.peak = Math.max(a.peak, n);
            a.sum += n;
            a.samples++;
            a.hourActive.addAll(playerIds);
            a.dayActive.computeIfAbsent(localDate, d -> new HashSet<>()).addAll(playerIds);
            a.dayPeak.merge(localDate, n, Math::max);
            // 지난 날짜는 이틀만 남긴다(자정 직후 시간 값을 쓸 때 전날 명단이 필요하다)
            a.dayActive.keySet().removeIf(d -> d.isBefore(localDate.minusDays(1)));
            a.dayPeak.keySet().removeIf(d -> d.isBefore(localDate.minusDays(1)));
        }
    }

    /** 이번 시간 값(비우지 않는다). */
    public Hour peekHour(long countryId) {
        Acc a = accs.get(countryId);
        if (a == null) return new Hour(0, 0, 0);
        synchronized (a) { return hourOf(a); }
    }

    /** 이번 시간 값을 꺼내고 비운다. */
    public Hour drainHour(long countryId) {
        Acc a = accs.get(countryId);
        if (a == null) return new Hour(0, 0, 0);
        synchronized (a) {
            Hour h = hourOf(a);
            a.peak = 0; a.sum = 0; a.samples = 0; a.hourActive.clear();
            return h;
        }
    }

    public long dayActive(long countryId, LocalDate date) {
        Acc a = accs.get(countryId);
        if (a == null) return 0;
        synchronized (a) { return a.dayActive.getOrDefault(date, Set.of()).size(); }
    }

    public long dayPeak(long countryId, LocalDate date) {
        Acc a = accs.get(countryId);
        if (a == null) return 0;
        synchronized (a) { return a.dayPeak.getOrDefault(date, 0L); }
    }

    private static Hour hourOf(Acc a) {
        return new Hour(a.peak, a.samples == 0 ? 0 : (double) a.sum / a.samples, a.hourActive.size());
    }
}
