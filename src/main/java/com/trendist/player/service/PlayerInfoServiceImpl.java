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
    private final PopularityService popularity;
    private final com.trendist.profile.support.ProfileLooks looks;
    private final LevelService levels;

    @Override
    @Transactional(readOnly = true)
    public PlayerDtos.PlayerCard card(long userId, int entityNo) {
        if (!rateLimiter.tryAcquire("info:" + userId, 3, Duration.ofSeconds(1))) {
            throw ApiException.tooMany("INFO_TOO_FAST", "잠시 후 다시 눌러 주세요");
        }
        Room.Live live = target(userId, entityNo);
        Player p = players.findById(live.playerId()).orElseThrow(PlayerInfoServiceImpl::notInRoom);
        // 점수·레벨·닉네임은 방의 실시간 값으로 보여 준다(DB는 몇 초 늦을 수 있다)
        return build(userId, p, entityNo, live.nickname(), live.level(), Math.round(live.score()));
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerDtos.PlayerCard cardById(long userId, long playerId) {
        if (!rateLimiter.tryAcquire("info:" + userId, 3, Duration.ofSeconds(1))) {
            throw ApiException.tooMany("INFO_TOO_FAST", "잠시 후 다시 눌러 주세요");
        }
        Player me = playerService.requireCurrent(userId);
        Player p = players.findById(playerId)
                .filter(t -> t.getCountry().getId().equals(me.getCountry().getId()) && !t.getUser().isWithdrawn())
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "찾을 수 없는 플레이어예요"));
        Double live = rooms.liveScore(p.getCountry().getId(), p.getId()); // 접속 중이면 실시간 점수
        return build(userId, p, -1, p.getNickname(), p.getLevel(), live != null ? Math.round(live) : p.getCurrentScore());
    }

    private PlayerDtos.PlayerCard build(long userId, Player p, int entityNo, String nickname, long level, long score) {
        List<PlayerDtos.KeywordTag> top = playerKeywords.findTopByPlayer(p.getId(), Instant.now(clock),
                        codes.keywordStateId(CodeTables.KW_BANNED), PageRequest.of(0, TOP_KEYWORDS)).stream()
                .map(pk -> new PlayerDtos.KeywordTag(pk.getKeyword().getId(), pk.getKeyword().getText(),
                        pk.getKeyword().getKeywordState().getName()))
                .toList();
        String title = p.getTitleId() == null ? null : titles.findById(p.getTitleId()).map(t -> t.name(com.trendist.common.support.Lang.ofLocale(p.getCountry().getDefaultLocale()))).orElse(null);
        return new PlayerDtos.PlayerCard(entityNo, nickname, title,
                level, score, p.getCommentScore(), top, p.getId(), p.getPopularity(), popularity.myVoteToday(userId, p), p.getCreatedAt(),
                p.getUser().getId().equals(userId), orbLook(p), stats(p));
    }

    /** 내 정보 창과 같은 기록(지금 레벨 기준 EXP와 다음 레벨 필요량 포함) */
    private PlayerDtos.Stats stats(Player p) {
        return new PlayerDtos.Stats(p.getExp(), levels.nextRequired(p.getLevel()), p.getHoldCount(), p.getAddCount(), p.getSearchCount(),
                p.getStealCount(), p.getBotWinCount(), p.getKillCount(), p.getDeathCount(), p.getActiveDays());
    }

    /** 장착한 꾸미기 프로필의 원 모양. 효과·그림은 로그인 사용자만 있다(ProfileLooks) */
    private PlayerDtos.OrbLook orbLook(Player p) {
        var l = looks.equippedLook(p);
        return new PlayerDtos.OrbLook(l.background(), l.borderColor(), l.borderWidth(), l.art(), l.borderEffect());
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
