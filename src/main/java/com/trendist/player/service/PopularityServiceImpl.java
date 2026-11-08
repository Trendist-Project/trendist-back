package com.trendist.player.service;

import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import com.trendist.player.entity.PopularityVote;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.repository.PopularityVoteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopularityServiceImpl implements PopularityService {
    private static final int MAX_HITS = 20;

    private final PlayerService playerService;
    private final PlayerRepository players;
    private final PopularityVoteRepository votes;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional
    public PlayerDtos.PopularityResult vote(long userId, long targetPlayerId, int value) {
        if (value != 1 && value != -1) throw ApiException.badRequest("POPULARITY_VALUE", "올리기(1) 또는 내리기(-1)만 할 수 있어요");
        if (!rateLimiter.tryAcquire("popularity:" + userId, 5, Duration.ofSeconds(1))) {
            throw ApiException.tooMany("POPULARITY_TOO_FAST", "잠시 후 다시 눌러 주세요");
        }
        Player me = playerService.requireCurrent(userId);
        if (me.getId().equals(targetPlayerId)) throw ApiException.badRequest("POPULARITY_SELF", "나에게는 줄 수 없어요");
        Player target = players.findById(targetPlayerId)
                .filter(t -> t.getCountry().getId().equals(me.getCountry().getId()) && !t.getUser().isWithdrawn())
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "찾을 수 없는 플레이어예요"));
        LocalDate today = today(me);
        if (votes.findByVoterPlayerIdAndTargetPlayerIdAndVoteDate(me.getId(), target.getId(), today).isPresent()) throw done();
        try {
            votes.saveAndFlush(new PopularityVote(me.getId(), target.getId(), today, value)); // 유니크 키가 동시 요청도 막는다
        } catch (DataIntegrityViolationException e) {
            throw done();
        }
        players.addPopularity(target.getId(), value);
        return new PlayerDtos.PopularityResult(target.getId(), players.findById(target.getId()).map(Player::getPopularity).orElse(0L), value);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlayerDtos.PlayerHit> search(long userId, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty() || q.length() > 40) return List.of();
        if (!rateLimiter.tryAcquire("playersearch:" + userId, 3, Duration.ofSeconds(1))) {
            throw ApiException.tooMany("SEARCH_TOO_FAST", "잠시 후 다시 검색해 주세요");
        }
        Player me = playerService.requireCurrent(userId);
        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Player> found = players.searchByNickname(me.getCountry().getId(), like, PageRequest.of(0, MAX_HITS));
        Map<Long, Integer> mine = found.isEmpty() ? Map.of()
                : votes.findToday(me.getId(), today(me), found.stream().map(Player::getId).toList()).stream()
                        .collect(Collectors.toMap(PopularityVote::getTargetPlayerId, PopularityVote::getValue));
        return found.stream().map(p -> new PlayerDtos.PlayerHit(p.getId(), p.getNickname(), p.getLevel(), p.getCurrentScore(),
                p.getPopularity(), p.getId().equals(me.getId()), mine.getOrDefault(p.getId(), 0))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int myVoteToday(long userId, Player target) {
        Player me = playerService.requireCurrent(userId);
        return votes.findByVoterPlayerIdAndTargetPlayerIdAndVoteDate(me.getId(), target.getId(), today(me))
                .map(PopularityVote::getValue).orElse(0);
    }

    private LocalDate today(Player me) {
        return LocalDate.now(clock.withZone(ZoneId.of(me.getCountry().getTimezone())));
    }

    private static ApiException done() {
        return ApiException.conflict("POPULARITY_DONE", "오늘은 이미 이 플레이어에게 줬어요. 내일 다시 할 수 있어요");
    }
}
