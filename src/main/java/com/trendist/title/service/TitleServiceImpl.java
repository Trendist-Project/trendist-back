package com.trendist.title.service;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.country.entity.Country;
import com.trendist.country.repository.CountryRepository;
import com.trendist.game.event.GameEvents;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.title.dto.TitleDtos;
import com.trendist.title.entity.PlayerTitle;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.PlayerTitleRepository;
import com.trendist.title.repository.TitleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TitleServiceImpl implements TitleService {
    private static final Logger log = LoggerFactory.getLogger(TitleServiceImpl.class);
    private static final Pattern METRIC = Pattern.compile("\"metric\"\\s*:\\s*\"([a-z_]+)\"");
    private static final Pattern GTE = Pattern.compile("\"gte\"\\s*:\\s*(\\d+)");
    private static final Pattern WITHIN = Pattern.compile("\"within\"\\s*:\\s*(\\d+)");

    private final TitleRepository titles;
    private final PlayerTitleRepository playerTitles;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final CountryRepository countries;
    private final RoomManager rooms;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    /** 조건을 풀어 둔 칭호. target은 METRIC이면 기준값(gte), RANK면 순위(within). */
    private record Cond(Title title, String type, String metric, long target) {}

    private List<Cond> conditions() {
        List<Cond> out = new ArrayList<>();
        for (Title t : titles.findByEnabledTrueOrderBySortOrderAsc()) {
            // 테스트용 H2는 JSON 컬럼에 넣은 글을 따옴표를 이스케이프한 JSON 문자열로 돌려준다. 역슬래시를 빼고 읽는다
            String p = t.getConditionParams().replace("\\", "");
            Matcher m = METRIC.matcher(p);
            if (!m.find()) continue;
            Matcher v = ("RANK".equals(t.getConditionType()) ? WITHIN : GTE).matcher(p);
            if (!v.find()) continue;
            out.add(new Cond(t, t.getConditionType(), m.group(1), Long.parseLong(v.group(1))));
        }
        return out;
    }

    /** players의 수치(4.10). 새 지표는 players에 컬럼을 추가하고 여기에 이름을 늘린다. */
    private static long metric(Player p, String name) {
        return switch (name) {
            case "total_plus_count" -> p.getTotalPlusCount();
            case "level" -> p.getLevel();
            case "hold_count" -> p.getHoldCount();
            case "current_score" -> p.getCurrentScore();
            case "comment_score" -> p.getCommentScore();
            default -> 0;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public TitleDtos.Box box(long userId) {
        Player p = playerService.requireCurrent(userId);
        Map<Long, Instant> owned = new HashMap<>();
        for (PlayerTitle pt : playerTitles.findByPlayerId(p.getId())) owned.put(pt.getTitleId(), pt.getCreatedAt());
        List<TitleDtos.Item> items = new ArrayList<>();
        for (Cond c : conditions()) {
            long id = c.title().getId();
            long current = "METRIC".equals(c.type()) ? metric(p, c.metric()) : 0;
            items.add(new TitleDtos.Item(id, c.title().getName(), c.title().getDescription(), c.type(), c.metric(),
                    current, c.target(), owned.containsKey(id), owned.get(id), Objects.equals(p.getTitleId(), id)));
        }
        return new TitleDtos.Box(p.getTitleId(), items);
    }

    @Override
    @Transactional
    public TitleDtos.Box activate(long userId, Long titleId) {
        Player p = playerService.requireCurrent(userId);
        String name = null;
        if (titleId != null) {
            if (!playerTitles.existsByPlayerIdAndTitleId(p.getId(), titleId)) throw ApiException.badRequest("TITLE_NOT_OWNED", "아직 받지 않은 칭호예요");
            name = activeTitleName(titleId);
        }
        // 거절된 요청은 세지 않는다(검사를 통과한 변경만 5초에 1번)
        if (!rateLimiter.tryAcquire("title:" + p.getId(), 1, Duration.ofSeconds(5))) {
            throw ApiException.tooMany("TITLE_TOO_FAST", "칭호는 5초에 한 번 바꿀 수 있어요");
        }
        p.changeTitle(titleId);
        events.publishEvent(new GameEvents.TitleChanged(p.getCountry().getId(), p.getId(), name));
        return box(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String activeTitleName(Long titleId) {
        if (titleId == null) return null;
        return titles.findById(titleId).filter(Title::isEnabled).map(Title::getName).orElse(null);
    }

    /** 수치가 바뀐 트랜잭션이 끝난 뒤에 검사한다(커밋된 값으로). 칭호 검사가 실패해도 원래 요청에는 영향이 없다. */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(GameEvents.TitleCheck e) {
        try { checkMetrics(e.playerId()); } catch (RuntimeException ex) { log.warn("title check {} failed", e.playerId(), ex); }
    }

    @Override
    @Transactional
    public void checkMetrics(long playerId) {
        Player p = players.findById(playerId).orElse(null);
        if (p == null) return;
        List<Cond> reached = conditions().stream()
                .filter(c -> "METRIC".equals(c.type()) && metric(p, c.metric()) >= c.target()).toList();
        grantAll(p, reached);
    }

    /**
     * RANK 칭호(4.10): 그 나라 리더보드(접속 중인 사람끼리의 순위, 4.14)를 10초마다 보고 N위 안의 사람에게 준다.
     * 리더보드는 방 메모리 값이라 DB를 보지 않는다.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void checkRanks() {
        List<Cond> rank = conditions().stream().filter(c -> "RANK".equals(c.type()) && "current_score".equals(c.metric())).toList();
        if (rank.isEmpty()) return;
        long widest = rank.stream().mapToLong(Cond::target).max().orElse(0);
        for (Country c : countries.findAll()) {
            if (!c.isEnabled()) continue;
            List<Room.Live> top = rooms.livePlayers(c.getId()).stream()
                    .sorted(Comparator.comparingDouble(Room.Live::score).reversed().thenComparingLong(Room.Live::playerId))
                    .limit(widest).toList();
            for (int i = 0; i < top.size(); i++) {
                int place = i + 1;
                long playerId = top.get(i).playerId();
                List<Cond> reached = rank.stream().filter(r -> place <= r.target()).toList();
                try {
                    tx.executeWithoutResult(s -> players.findById(playerId).ifPresent(p -> grantAll(p, reached)));
                } catch (RuntimeException ex) {
                    log.warn("rank title {} failed", playerId, ex);
                }
            }
        }
    }

    private void grantAll(Player p, List<Cond> reached) {
        if (reached.isEmpty()) return;
        Set<Long> owned = new HashSet<>();
        for (PlayerTitle pt : playerTitles.findByPlayerId(p.getId())) owned.add(pt.getTitleId());
        for (Cond c : reached) {
            long id = c.title().getId();
            if (owned.contains(id)) continue;
            try {
                playerTitles.saveAndFlush(new PlayerTitle(p.getId(), id));
            } catch (DataIntegrityViolationException dup) {
                continue; // 동시에 두 번 검사했다. 이미 받았다
            }
            // 새로 얻어도 자동으로 활성화하지 않는다(4.10). 알림만 보낸다
            events.publishEvent(new GameEvents.TitleGranted(p.getCountry().getId(), p.getId(), c.title().getName(), c.title().getDescription()));
        }
    }
}
