package com.trendist.dashboard.dto;

import com.trendist.ranking.dto.RankingEntry;
import com.trendist.stats.dto.StatsDtos;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 관리자 대시보드(13.11): 한 화면에 보는 요약. */
public final class DashboardDtos {
    private DashboardDtos() {}

    /**
     * 처리할 일 한 줄. level: danger(바로 볼 것) / warn(살펴볼 것) / ok(문제없음), tab: 눌렀을 때 열 관리자 탭.
     * filter는 그 탭에 넘길 거르기 값(없으면 null).
     */
    public record Todo(String key, String label, long count, String level, String detail, String tab, String filter) {}

    /** 최근 7일 신고를 많이 받은 사람 */
    public record Reported(long userId, long playerId, String nickname, long reports) {}

    public record HotKeyword(long id, String text, long holderCount, long trendValue, String source) {}

    public record NewPlayer(long userId, long playerId, String nickname, String type, long level, Instant createdAt) {}

    public record NoticeLine(long id, String title, boolean pinned, boolean popup, Instant createdAt) {}

    /** 자랑하기 카드: 오늘 만든 수, 전체 카드·조회·클릭 */
    public record Share(long today, long cards, long views, long plays) {}

    /**
     * 대시보드. hourly는 최근 24시간(마지막은 집계 중), keywordStates는 상태별 키워드 수,
     * googleFetchedAt은 구글 실시간 인기를 마지막으로 읽은 시각(수집을 끈 환경이면 null, googleEnabled=false).
     */
    public record Dashboard(Instant at, long onlineNow, StatsDtos.Period today, StatsDtos.Period yesterday, StatsDtos.Totals totals,
                            List<StatsDtos.Row> hourly, List<Todo> todos, List<Reported> reported,
                            Map<String, Long> keywordStates, List<HotKeyword> hotKeywords, long newKeywordsToday,
                            boolean googleEnabled, Instant googleFetchedAt,
                            List<RankingEntry> leaderboard, List<NewPlayer> newPlayers, List<NoticeLine> notices, Share share) {}
}
