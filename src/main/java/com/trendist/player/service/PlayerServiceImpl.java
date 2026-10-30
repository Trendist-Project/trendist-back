package com.trendist.player.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.effect.entity.TrailEffect;
import com.trendist.effect.repository.TrailEffectRepository;
import com.trendist.game.event.GameEvents;
import com.trendist.game.room.Room;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.player.dto.PlayerDtos;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.support.NicknameValidator;
import com.trendist.profile.entity.Profile;
import com.trendist.profile.repository.ProfileRepository;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.TitleRepository;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {
    private final PlayerRepository players;
    private final UserRepository users;
    private final CountryService countries;
    private final PlayerKeywordRepository playerKeywords;
    private final LevelService levels;
    private final TrendFormula formula;
    private final CodeTables codes;
    private final ApplicationEventPublisher events;
    private final SettingService settings;
    private final TitleRepository titles;
    private final ProfileRepository profiles;
    private final TrailEffectRepository trailEffects;
    private final Clock clock;

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
        return findCurrent(userId).map(p -> {
            String title = p.getTitleId() == null ? null
                    : titles.findById(p.getTitleId()).filter(Title::isEnabled).map(Title::getName).orElse(null);
            var look = profiles.findFirstByPlayerIdAndEquippedTrue(p.getId());
            // 이동 효과는 로그인 사용자만 쓴다(4.23). 게스트는 예전에 골라 둔 효과가 있어도 보이지 않는다
            String trail = p.getTrailEffectId() == null || p.getUser().isGuest() ? null
                    : trailEffects.findById(p.getTrailEffectId()).filter(TrailEffect::isEnabled).map(TrailEffect::getCode).orElse(null);
            return new JoinInfo(p.getId(), p.getCountry().getId(), p.getNickname(), Math.toIntExact(p.getLevel()), p.getCurrentScore(),
                    p.getTotalPlusCount() == 0, title, look.map(Profile::getBackgroundColor).orElse(Room.DEFAULT_BACKGROUND),
                    look.map(Profile::getBorderColor).orElse(null), look.map(Profile::getBorderWidth).orElse(null), trail);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGuest(long userId) {
        return users.findById(userId).map(com.trendist.user.entity.User::isGuest).orElse(true);
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
        p.touchPlayed(Instant.now(clock), ZoneId.of(c.getTimezone()));
        return p;
    }

    @Override
    @Transactional
    public Player changeNickname(long userId, String rawNickname) {
        String nickname = NicknameValidator.clean(rawNickname);
        Player p = requireCurrent(userId);
        if (nickname.equals(p.getNickname())) throw ApiException.badRequest("NICKNAME_SAME", "지금 닉네임과 같아요");
        Instant now = Instant.now(clock);
        Instant changeableAt = changeableAt(p);
        if (changeableAt != null && now.isBefore(changeableAt)) {
            long minutes = Math.max(1, (Duration.between(now, changeableAt).getSeconds() + 59) / 60);
            throw ApiException.conflict("NICKNAME_TOO_SOON", "닉네임은 " + minutes + "분 뒤에 바꿀 수 있어요");
        }
        p.changeNickname(nickname, now);
        events.publishEvent(new GameEvents.NicknameChanged(p.getCountry().getId(), p.getId(), nickname));
        return p;
    }

    /** 다음에 닉네임을 바꿀 수 있는 시각. 처음 정한 뒤 한 번도 바꾸지 않았으면 null(바로 가능). */
    private Instant changeableAt(Player p) {
        if (p.getNicknameChangedAt() == null) return null;
        return p.getNicknameChangedAt().plus(Duration.ofMinutes(settings.getInt(SettingKeys.NICKNAME_CHANGE_MINUTES)));
    }

    @Override
    public PlayerDtos.PlayerSummary summary(Player p) {
        return new PlayerDtos.PlayerSummary(p.getId(), p.getNickname(), p.getCountry().getCode(), p.getLevel(), p.getExp(),
                levels.nextRequired(p.getLevel()), p.getCurrentScore(), p.getHoldCount(), p.getTotalPlusCount(),
                p.getCommentScore(), p.getSearchCount(), changeableAt(p));
    }

    @Override
    public long recompute(Player p) {
        Instant now = Instant.now(clock);
        // 키워드 점수가 모두 정수라 합도 정수다(4.8)
        long sum = playerKeywords.sumActiveTrendValue(p.getId(), now, codes.keywordStateId(CodeTables.KW_ACTIVE));
        long score = formula.expScore(p.getExp()) + sum;
        p.setCurrentScore(score);
        p.setLevel(levels.levelFor(p.getExp()));
        events.publishEvent(new GameEvents.PlayerScoreChanged(p.getCountry().getId(), p.getId(), score, p.getExp(),
                p.getLevel(), p.getHoldCount()));
        events.publishEvent(new GameEvents.TitleCheck(p.getId())); // 추가·레벨·보유 수·점수가 바뀌었을 수 있다(4.10)
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
