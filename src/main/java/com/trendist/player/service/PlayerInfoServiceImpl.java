package com.trendist.player.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.common.support.RateLimiter;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.title.repository.TitleRepository;
import com.trendist.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerInfoServiceImpl implements PlayerInfoService {
    private static final int TOP_KEYWORDS = 3;

    private final PlayerService playerService;
    private final PlayerRepository players;
    private final PlayerKeywordRepository playerKeywords;
    private final TitleRepository titles;
    private final ScoreHistoryService scoreHistory;
    private final RoomManager rooms;
    private final CodeTables codes;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PlayerDtos.PlayerCard card(long userId, int entityNo) {
        if (!rateLimiter.tryAcquire("info:" + userId, 2, Duration.ofSeconds(1))) {
            throw ApiException.tooMany("INFO_TOO_FAST", "잠시 후 다시 눌러 주세요");
        }
        Room.Live live = target(userId, entityNo);
        Player p = players.findById(live.playerId()).orElseThrow(PlayerInfoServiceImpl::notInRoom);
        List<PlayerDtos.KeywordTag> top = playerKeywords.findTopByPlayer(p.getId(), Instant.now(clock),
                        codes.keywordStateId(CodeTables.KW_BANNED), PageRequest.of(0, TOP_KEYWORDS)).stream()
                .map(pk -> new PlayerDtos.KeywordTag(pk.getKeyword().getId(), pk.getKeyword().getText(),
                        pk.getKeyword().getKeywordState().getName()))
                .toList();
        String title = p.getTitleId() == null ? null : titles.findById(p.getTitleId()).map(t -> t.getName()).orElse(null);
        // 점수·레벨·닉네임은 방의 실시간 값으로 보여 준다(DB는 몇 초 늦을 수 있다)
        return new PlayerDtos.PlayerCard(entityNo, live.nickname(), User.ROLE_ADMIN.equals(p.getUser().getRole()), title,
                live.level(), Math.round(live.score()), p.getCommentScore(), top);
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerDtos.ScoreHistory week(long userId, int entityNo) {
        return scoreHistory.weekOf(target(userId, entityNo).playerId());
    }

    /** 내가 접속한 방에 지금 있는 그 번호의 플레이어. 나도 그 방에 있어야 한다. */
    private Room.Live target(long userId, int entityNo) {
        Player me = playerService.requireCurrent(userId);
        long countryId = me.getCountry().getId();
        if (rooms.liveScore(countryId, me.getId()) == null) throw notInRoom();
        return rooms.livePlayerByEntity(countryId, entityNo).orElseThrow(PlayerInfoServiceImpl::notInRoom);
    }

    private static ApiException notInRoom() {
        return ApiException.notFound("PLAYER_NOT_IN_ROOM", "지금 방에 없는 플레이어예요");
    }
}
