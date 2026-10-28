package com.trendist.player;

import com.trendist.common.ApiException;
import com.trendist.common.CodeTables;
import com.trendist.domain.Country;
import com.trendist.domain.Player;
import com.trendist.domain.User;
import com.trendist.game.GameEvents;
import com.trendist.keyword.TrendFormula;
import com.trendist.repo.CountryRepository;
import com.trendist.repo.PlayerKeywordRepository;
import com.trendist.repo.PlayerRepository;
import com.trendist.repo.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {
    public static final String DEFAULT_COUNTRY = "KR";

    private final PlayerRepository players;
    private final UserRepository users;
    private final CountryRepository countries;
    private final PlayerKeywordRepository playerKeywords;
    private final LevelService levels;
    private final TrendFormula formula;
    private final CodeTables codes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PlayerService(PlayerRepository players, UserRepository users, CountryRepository countries,
                         PlayerKeywordRepository playerKeywords, LevelService levels, TrendFormula formula,
                         CodeTables codes, ApplicationEventPublisher events, Clock clock) {
        this.players = players; this.users = users; this.countries = countries; this.playerKeywords = playerKeywords;
        this.levels = levels; this.formula = formula; this.codes = codes; this.events = events; this.clock = clock;
    }

    public Country country(String code) {
        Country c = countries.findByCode(code == null ? DEFAULT_COUNTRY : code)
                .orElseThrow(() -> ApiException.badRequest("COUNTRY_UNKNOWN", "알 수 없는 나라예요"));
        if (!c.isEnabled()) throw ApiException.badRequest("COUNTRY_CLOSED", "아직 열지 않은 나라예요");
        return c;
    }

    /** 사용자의 현재 나라 플레이어. 마지막으로 플레이한 나라, 없으면 KR. */
    @Transactional(readOnly = true)
    public Optional<Player> findCurrent(long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        Long countryId = u.getCountry() != null ? u.getCountry().getId() : country(DEFAULT_COUNTRY).getId();
        return players.findByUserIdAndCountryId(userId, countryId);
    }

    /** 방 입장에 필요한 값(트랜잭션 안에서 꺼낸다). */
    public record JoinInfo(long playerId, long countryId, String nickname, int level, double score) {}

    @Transactional(readOnly = true)
    public Optional<JoinInfo> joinInfo(long userId) {
        return findCurrent(userId).map(p -> new JoinInfo(p.getId(), p.getCountry().getId(), p.getNickname(),
                p.getLevel(), p.getCurrentScore().doubleValue()));
    }

    public Player requireCurrent(long userId) {
        return findCurrent(userId).orElseThrow(() -> ApiException.notFound("NO_PLAYER", "닉네임을 먼저 정해 주세요"));
    }

    @Transactional
    public Player create(long userId, String rawNickname, String countryCode) {
        String nickname = NicknameValidator.clean(rawNickname);
        Country c = country(countryCode);
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("NO_USER"));
        if (players.findByUserIdAndCountryId(userId, c.getId()).isPresent()) {
            throw ApiException.conflict("PLAYER_EXISTS", "이미 이 나라의 닉네임이 있어요");
        }
        u.setCountry(c);
        Player p = players.save(new Player(u, c, nickname));
        p.touchPlayed(Instant.now(clock));
        return p;
    }

    public PlayerDtos.PlayerSummary summary(Player p) {
        return new PlayerDtos.PlayerSummary(p.getId(), p.getNickname(), p.getCountry().getCode(), p.getLevel(), p.getExp(),
                levels.nextRequired(p.getLevel()), p.getCurrentScore(), p.getHoldCount(), p.getTotalPlusCount(),
                p.getCommentScore());
    }

    /**
     * 현재 유행도를 다시 합산한다(4.8): min(EXP, 100) + Σ 🔥 키워드 trend_value × 계수.
     * 호출하는 쪽이 players 행을 잠근 트랜잭션 안에서 부른다. 결과는 커밋 뒤 방에 전달된다.
     */
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

    /** 다른 트랜잭션에서 한 플레이어를 잠그고 다시 합산한다(재합산 잡, 전체 점검). */
    @Transactional
    public void recomputeLocked(long playerId, boolean fixHoldCount) {
        players.findByIdForUpdate(playerId).ifPresent(p -> {
            if (fixHoldCount) p.setHoldCount((int) playerKeywords.countUnexpired(p.getId(), Instant.now(clock)));
            recompute(p);
        });
    }
}
