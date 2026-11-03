package com.trendist.dashboard.service;

import com.trendist.common.code.CodeTables;
import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.dashboard.dto.DashboardDtos;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.source.GoogleTrendingSource;
import com.trendist.notice.entity.Notice;
import com.trendist.notice.repository.NoticeRepository;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.ranking.service.RankingService;
import com.trendist.report.repository.ReportRepository;
import com.trendist.share.repository.ShareCardRepository;
import com.trendist.stats.dto.StatsDtos;
import com.trendist.stats.service.StatsService;
import com.trendist.user.repository.SanctionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    /** 구글 실시간 인기는 5분마다 읽는다. 이보다 오래 못 읽었으면 살펴볼 일, 30분이 넘으면 바로 볼 일(10.2) */
    private static final Duration GOOGLE_WARN = Duration.ofMinutes(15), GOOGLE_DANGER = Duration.ofMinutes(30);

    private final CountryService countries;
    private final StatsService stats;
    private final ReportRepository reports;
    private final SanctionRepository sanctions;
    private final KeywordRepository keywords;
    private final PlayerRepository players;
    private final RankingService rankings;
    private final NoticeRepository notices;
    private final ShareCardRepository shareCards;
    private final CodeTables codes;
    private final ObjectProvider<GoogleTrendingSource> google; // 수집을 끈 환경(테스트 등)에는 없다
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DashboardDtos.Dashboard dashboard(String countryCode) {
        Country c = countries.requireOpen(countryCode);
        Instant now = Instant.now(clock);
        ZoneId zone = ZoneId.of(c.getTimezone());
        Instant dayStart = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
        StatsDtos.Summary s = stats.summary(c.getCode());

        // 최근 7일 신고를 많이 받은 사람(플레이어 신고 + 그 사람 댓글·채팅에 들어온 신고)
        Instant week = now.minus(Duration.ofDays(7));
        Map<Long, Long> byPlayer = new HashMap<>();
        for (List<Object[]> rows : List.of(reports.countPlayerReportsSince(week), reports.countCommentReportsSince(week), reports.countChatReportsSince(week))) {
            for (Object[] r : rows) byPlayer.merge(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), Long::sum);
        }
        List<Map.Entry<Long, Long>> top = byPlayer.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(5).toList();
        Map<Long, Player> found = new HashMap<>();
        players.findAllById(top.stream().map(Map.Entry::getKey).toList()).forEach(p -> found.put(p.getId(), p));
        List<DashboardDtos.Reported> reported = top.stream().filter(e -> found.containsKey(e.getKey()))
                .map(e -> { Player p = found.get(e.getKey()); return new DashboardDtos.Reported(p.getUser().getId(), p.getId(), p.getNickname(), e.getValue()); })
                .toList();

        // 처리할 일
        long sanctionsToday = sanctions.countByCreatedAtGreaterThanEqual(dayStart);
        long sanctioned = sanctions.countActiveUsers(now);
        long ending = keywords.countExternalEnding(c.getId(), now, now.plus(Duration.ofHours(24)));
        GoogleTrendingSource g = google.getIfAvailable();
        Instant fetched = g == null ? null : g.fetchedAt();
        List<DashboardDtos.Todo> todos = new ArrayList<>();
        todos.add(new DashboardDtos.Todo("reports", "처리 대기 신고", s.pendingReports(),
                s.pendingReports() >= 10 ? "danger" : s.pendingReports() > 0 ? "warn" : "ok", "신고 탭에서 조치함·문제없음으로 처리", "reports", null));
        long hot = reported.stream().filter(r -> r.reports() >= 3).count();
        todos.add(new DashboardDtos.Todo("reported", "최근 7일 신고 3건 이상 받은 사람", hot, hot > 0 ? "warn" : "ok",
                "아래 목록에서 눌러 사용자 상세로", "users", null));
        todos.add(new DashboardDtos.Todo("sanctioned", "지금 제재 중인 사용자", sanctioned, "ok", "오늘 새로 건 제재 " + sanctionsToday + "건", "users", "banned"));
        todos.add(new DashboardDtos.Todo("ending", "24시간 안에 유지 기간이 끝나는 외부 키워드", ending, "ok",
                "목록에 계속 있으면 저절로 연장, 없으면 internal로 내려가요", "keywords", null));
        if (g != null) {
            Duration since = fetched == null ? null : Duration.between(fetched, now);
            String level = since == null || since.compareTo(GOOGLE_DANGER) > 0 ? "danger" : since.compareTo(GOOGLE_WARN) > 0 ? "warn" : "ok";
            todos.add(new DashboardDtos.Todo("google", "구글 실시간 인기 수집", since == null ? -1 : since.toMinutes(), level,
                    since == null ? "아직 한 번도 읽지 못했어요" : since.toMinutes() + "분 전에 읽음", null, null));
        }

        List<DashboardDtos.HotKeyword> hotKeywords = keywords.topHeld(c.getId(), codes.keywordStateId(CodeTables.KW_ACTIVE), PageRequest.of(0, 5))
                .stream().map(k -> new DashboardDtos.HotKeyword(k.getId(), k.getText(), k.getHolderCount(), k.getTrendValue(), k.getDataSource().getName()))
                .toList();
        List<DashboardDtos.NewPlayer> newPlayers = players.findRecent(c.getId(), PageRequest.of(0, 5)).stream()
                .map(p -> new DashboardDtos.NewPlayer(p.getUser().getId(), p.getId(), p.getNickname(), p.getUser().isGuest() ? "GUEST" : "GOOGLE",
                        p.getLevel(), p.getCreatedAt())).toList();
        List<DashboardDtos.NoticeLine> noticeLines = notices.findByDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(PageRequest.of(0, 5)).stream()
                .map(n -> new DashboardDtos.NoticeLine(n.getId(), n.getTitle(), n.isPinned(), popupNow(n, now), n.getCreatedAt())).toList();
        Object[] t = shareCards.totals().get(0);
        DashboardDtos.Share share = new DashboardDtos.Share(shareCards.countByCreatedAtGreaterThanEqual(dayStart), n(t[0]), n(t[1]), n(t[2]));

        return new DashboardDtos.Dashboard(now, s.onlineNow(), s.today(), s.yesterday(), s.totals(), stats.hourly(c.getCode(), 24),
                todos, reported, s.keywordStates(), hotKeywords, keywords.countByCountryIdAndCreatedAtGreaterThanEqual(c.getId(), dayStart),
                g != null, fetched, rankings.top(c.getCode()).stream().limit(5).toList(), newPlayers, noticeLines, share);
    }

    private static boolean popupNow(Notice n, Instant now) {
        return n.isPopupEnabled() && n.getPopupUntil() != null && n.getPopupUntil().isAfter(now);
    }

    private static long n(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
}
