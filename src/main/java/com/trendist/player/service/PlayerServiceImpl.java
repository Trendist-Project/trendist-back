package com.trendist.player.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.game.event.GameEvents;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.support.NicknameValidator;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerServiceImpl implements PlayerService {
    private final PlayerRepository players;
    private final UserRepository users;
    private final CountryService countries;
    private final PlayerKeywordRepository playerKeywords;
    private final LevelService levels;
    private final TrendFormula formula;
    private final CodeTables codes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PlayerServiceImpl(PlayerRepository players, UserRepository users, CountryService countries,
                             PlayerKeywordRepository playerKeywords, LevelService levels, TrendFormula formula,
                             CodeTables codes, ApplicationEventPublisher events, Clock clock) {
        this.players = players; this.users = users; this.countries = countries; this.playerKeywords = playerKeywords;
        this.levels = levels; this.formula = formula; this.codes = codes; this.events = events; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Player> findCurrent(long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        Long countryId = u.getCountry() != null ? u.getCountry().getId()
                : countries.requireOpen(CountryService.DEFAULT_COUNTRY).getId();
        return players.findByUserIdAndCountryId(userId, countryId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JoinInfo> joinInfo(long userId) {
        return findCurrent(userId).map(p -> new JoinInfo(p.getId(), p.getCountry().getId(), p.getNickname(),
                p.getLevel(), p.getCurrentScore().doubleValue()));
    }

    @Override
    public Player requireCurrent(long userId) {
        return findCurrent(userId).orElseThrow(() -> ApiException.notFound("NO_PLAYER", "닉네임을 먼저 정해 주세요"));
    }

    @Override
    @Transactional
    public Player create(long userId, String rawNickname, String countryCode) {
        String nickname = NicknameValidator.clean(rawNickname);
        Country c = countries.requireOpen(countryCode);
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        if (players.findByUserIdAndCountryId(userId, c.getId()).isPresent()) {
            throw ApiException.conflict("PLAYER_EXISTS", "이미 이 나라의 닉네임이 있어요");
        }
        u.setCountry(c);
        Player p = players.save(new Player(u, c, nickname));
        p.touchPlayed(Instant.now(clock));
        return p;
    }

    @Override
    public PlayerDtos.PlayerSummary summary(Player p) {
        return new PlayerDtos.PlayerSummary(p.getId(), p.getNickname(), p.getCountry().getCode(), p.getLevel(), p.getExp(),
                levels.nextRequired(p.getLevel()), p.getCurrentScore(), p.getHoldCount(), p.getTotalPlusCount(),
                p.getCommentScore());
    }

    @Override
    public BigDecimal recompute(Player p) {
        Instant now = Instant.now(clock);
        BigDecimal sum = playerKeywords.sumActiveTrendValue(p.getId(), now, codes.keywordStateId(CodeTables.KW_ACTIVE));
        BigDecimal score = BigDecimal.valueOf(formula.expScore(p.getExp()))
                .add(sum.multiply(BigDecimal.valueOf(formula.scoreFactor())))
                .setScale(2, RoundingMode.HALF_UP);
        p.setCurrentScore(score);
        p.setLevel(levels.levelFor(p.getExp()));
        events.publishEvent(new GameEvents.PlayerScoreChanged(p.getCountry().getId(), p.getId(), score, p.getExp(),
                p.getLevel(), p.getHoldCount()));
        return score;
    }

    @Override
    @Transactional
    public void recomputeLocked(long playerId, boolean fixHoldCount) {
        players.findByIdForUpdate(playerId).ifPresent(p -> {
            if (fixHoldCount) p.setHoldCount((int) playerKeywords.countUnexpired(p.getId(), Instant.now(clock)));
            recompute(p);
        });
    }
}
