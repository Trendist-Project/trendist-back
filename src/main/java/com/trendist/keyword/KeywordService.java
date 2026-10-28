package com.trendist.keyword;

import com.trendist.common.ApiException;
import com.trendist.common.CodeTables;
import com.trendist.domain.*;
import com.trendist.game.GameEvents;
import com.trendist.player.PlayerService;
import com.trendist.repo.*;
import com.trendist.setting.SettingKeys;
import com.trendist.setting.SettingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검색, +, 내 트렌드 키워드, 만료, 충돌로 잃기(4.4~4.8, 9.4). */
@Service
public class KeywordService {
    private final KeywordRepository keywords;
    private final PlayerKeywordRepository playerKeywords;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final TrendFormula formula;
    private final SettingService settings;
    private final CodeTables codes;
    private final List<ExternalTrendSource> sources;
    private final SearchCounter searchCounter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 트렌드 값이 바뀐 키워드. 1분 잡이 보유자 점수를 DB에서 다시 합산한다(9.4). */
    private final Set<Long> dirtyKeywords = ConcurrentHashMap.newKeySet();

    public KeywordService(KeywordRepository keywords, PlayerKeywordRepository playerKeywords, PlayerRepository players,
                          PlayerService playerService, TrendFormula formula, SettingService settings, CodeTables codes,
                          List<ExternalTrendSource> sources, SearchCounter searchCounter,
                          ApplicationEventPublisher events, Clock clock) {
        this.keywords = keywords; this.playerKeywords = playerKeywords; this.players = players;
        this.playerService = playerService; this.formula = formula; this.settings = settings; this.codes = codes;
        this.sources = sources; this.searchCounter = searchCounter; this.events = events; this.clock = clock;
    }

    // ---------------------------------------------------------------- 검색

    @Transactional
    public KeywordDtos.SearchResult search(long userId, String q) {
        Player p = playerService.requireCurrent(userId);
        String text = KeywordNormalizer.display(q);
        String normalized = KeywordNormalizer.validate(text);
        Country country = p.getCountry();
        Instant now = Instant.now(clock);

        Optional<Keyword> found = keywords.findByCountryIdAndNormalized(country.getId(), normalized);
        if (found.isPresent()) {
            Keyword k = found.get();
            if (isBanned(k)) return new KeywordDtos.SearchResult("BANNED", null);
            if (isState(k, CodeTables.KW_INACTIVE)) tryExternalActivate(k, now); // 💤는 외부를 다시 조회한다
            searchCounter.count(userId, k.getId());
            return new KeywordDtos.SearchResult("FOUND", card(k, p, now));
        }
        // DB에 없음: 외부에서 확인되면 🔥로 등록, 아니면 등록하지 않고 💤 카드만 보여 준다(첫 + 때 만든다)
        Optional<Confirmed> ext = externalConfirm(country.getCode(), normalized);
        if (ext.isPresent()) {
            Keyword k = createKeyword(country, text, normalized, ext, now);
            searchCounter.count(userId, k.getId());
            return new KeywordDtos.SearchResult("FOUND", card(k, p, now));
        }
        var preview = new KeywordDtos.KeywordCard(null, text, CodeTables.KW_INACTIVE, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, 0, false, null);
        return new KeywordDtos.SearchResult("NEW", preview);
    }

    // ---------------------------------------------------------------- +

    @Transactional
    public KeywordDtos.PlusResult plus(long userId, String rawText) {
        Player found = playerService.requireCurrent(userId);
        Player p = players.findByIdForUpdate(found.getId()).orElseThrow(); // 잠금 순서: 플레이어 → 키워드
        String text = KeywordNormalizer.display(rawText);
        String normalized = KeywordNormalizer.validate(text);
        Instant now = Instant.now(clock);
        Instant expires = now.plus(Duration.ofHours(settings.getInt(SettingKeys.PLUS_TTL_HOURS)));
        Country country = p.getCountry();

        Keyword k = keywords.findByCountryIdAndNormalized(country.getId(), normalized)
                .orElseGet(() -> createKeyword(country, text, normalized, externalConfirm(country.getCode(), normalized), now));
        k = keywords.findByIdForUpdate(k.getId()).orElseThrow();
        if (isBanned(k)) throw ApiException.badRequest("KEYWORD_BANNED", "검색할 수 없는 키워드예요");

        BigDecimal oldTrend = k.getTrendValue();
        BigDecimal oldScore = p.getCurrentScore();
        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId());
        boolean newHolder;
        if (existing.isPresent()) {
            PlayerKeyword pk = existing.get();
            if (!pk.isExpired(now)) throw ApiException.conflict("ALREADY_PLUSED", "이미 +한 키워드예요");
            pk.renew(now, expires); // 만료 행 재사용: 가진 사람 수는 그대로
            newHolder = false;
        } else {
            playerKeywords.save(new PlayerKeyword(p, k, now, expires));
            k.incHolder();
            p.incHold();
            newHolder = true;
        }
        k.incTotalPlus();
        p.incTotalPlus();
        p.addExp(settings.getInt(SettingKeys.EXP_PER_PLUS)); // EXP는 💤 키워드에도 바로 준다(4.9)
        p.touchPlayed(now);

        if (isState(k, CodeTables.KW_INACTIVE) && k.getHolderCount() >= settings.getInt(SettingKeys.ACTIVATE_HOLDERS)) {
            k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now); // 내부 확산으로 활성(4.6)
        }
        BigDecimal newTrend = updateTrend(k);
        keywords.flush();
        playerKeywords.flush();
        BigDecimal newScore = playerService.recompute(p);
        publishTrendChange(k, oldTrend, newTrend, p.getId());
        if (newHolder) events.publishEvent(new GameEvents.HoldingChanged(country.getId(), p.getId(), k.getId(), true));
        return new KeywordDtos.PlusResult(card(k, p, now), newScore.subtract(oldScore), playerService.summary(p));
    }

    // ---------------------------------------------------------------- 조회

    @Transactional(readOnly = true)
    public List<KeywordDtos.MyKeyword> myKeywords(long userId) {
        Player p = playerService.requireCurrent(userId);
        Instant now = Instant.now(clock);
        return playerKeywords.findActiveByPlayer(p.getId(), now, codes.keywordStateId(CodeTables.KW_BANNED)).stream()
                .map(pk -> {
                    Keyword k = pk.getKeyword();
                    return new KeywordDtos.MyKeyword(k.getId(), k.getText(), k.getKeywordState().getName(),
                            k.getTrendValue(), formula.score(k.getTrendValue()), pk.getCreatedAt(), pk.getExpiresAt());
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> activeKeywordIds(long playerId) {
        return playerKeywords.findActiveByPlayer(playerId, Instant.now(clock), codes.keywordStateId(CodeTables.KW_BANNED))
                .stream().map(pk -> pk.getKeyword().getId()).toList();
    }

    // ---------------------------------------------------------------- 만료, 잃기, 재합산

    /** 만료된 + 하나를 지우고 인원·트렌드 값·점수를 고친다. 지웠으면 true. */
    @Transactional
    public boolean expireOne(long playerKeywordId) {
        Instant now = Instant.now(clock);
        PlayerKeyword pk = playerKeywords.findById(playerKeywordId).orElse(null);
        if (pk == null) return false;
        Player p = players.findByIdForUpdate(pk.getPlayer().getId()).orElseThrow();
        Long keywordId = pk.getKeyword().getId();
        if (playerKeywords.deleteIfExpired(playerKeywordId, now) == 0) return false; // 방금 재사용된 행
        removeHolding(p, keywordId, now);
        return true;
    }

    /** 충돌로 잃기(4.3): 금지되지 않은 키워드 중 하나를 무작위로 지운다. 잃은 키워드 표시명을 돌려준다. */
    @Transactional
    public Optional<String> loseRandom(long playerId) {
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null || p.getHoldCount() == 0) return Optional.empty();
        Instant now = Instant.now(clock);
        List<PlayerKeyword> candidates = playerKeywords.findLosableByPlayer(playerId, now, codes.keywordStateId(CodeTables.KW_BANNED));
        if (candidates.isEmpty()) return Optional.empty();
        PlayerKeyword pk = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        String text = pk.getKeyword().getText();
        Long keywordId = pk.getKeyword().getId();
        playerKeywords.delete(pk);
        playerKeywords.flush();
        removeHolding(p, keywordId, now);
        return Optional.of(text);
    }

    private void removeHolding(Player p, Long keywordId, Instant now) {
        Keyword k = keywords.findByIdForUpdate(keywordId).orElseThrow();
        BigDecimal oldTrend = k.getTrendValue();
        k.decHolder();
        p.decHold();
        // 외부 데이터가 없고 가진 사람이 3명 미만이면 💤(4.7)
        if (isState(k, CodeTables.KW_ACTIVE) && k.getDataSource().isInternal()
                && k.getHolderCount() < settings.getInt(SettingKeys.ACTIVATE_HOLDERS)) {
            k.deactivate(codes.keywordState(CodeTables.KW_INACTIVE), now);
        }
        BigDecimal newTrend = updateTrend(k);
        keywords.flush();
        playerService.recompute(p);
        publishTrendChange(k, oldTrend, newTrend, p.getId());
        events.publishEvent(new GameEvents.HoldingChanged(p.getCountry().getId(), p.getId(), keywordId, false));
    }

    /** 1분 잡: 트렌드 값이 바뀐 키워드의 보유자 점수를 DB 기준으로 다시 합산한다. */
    public Set<Long> drainDirtyKeywords() {
        Set<Long> out = new HashSet<>(dirtyKeywords);
        dirtyKeywords.removeAll(out);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Long> holderPlayerIds(long keywordId) {
        return playerKeywords.findHolderPlayerIds(keywordId, Instant.now(clock));
    }

    // ---------------------------------------------------------------- 내부

    private record Confirmed(DataSource source, double heat) {}

    /** 그 나라의 외부 소스 중 관심도가 기준 이상인 것, 여럿이면 가중치가 가장 큰 것(10.3). */
    private Optional<Confirmed> externalConfirm(String countryCode, String normalized) {
        double threshold = settings.getDouble(SettingKeys.EXTERNAL_CONFIRM_HEAT);
        Confirmed best = null;
        for (ExternalTrendSource s : sources) {
            if (!s.supports(countryCode)) continue;
            OptionalDouble h = s.heat(countryCode, normalized);
            if (h.isEmpty() || h.getAsDouble() < threshold) continue;
            DataSource ds = codes.dataSource(s.sourceName());
            if (ds.getWeight().signum() == 0) continue; // 가중치 0이면 쓰지 않는다
            if (best == null || ds.getWeight().compareTo(best.source().getWeight()) > 0) best = new Confirmed(ds, h.getAsDouble());
        }
        return Optional.ofNullable(best);
    }

    private void tryExternalActivate(Keyword k, Instant now) {
        externalConfirm(k.getCountry().getCode(), k.getNormalized()).ifPresent(c -> {
            BigDecimal old = k.getTrendValue();
            k.setDataSource(c.source());
            k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now);
            BigDecimal nw = updateTrend(k);
            publishTrendChange(k, old, nw, null);
        });
    }

    private Keyword createKeyword(Country country, String text, String normalized, Optional<Confirmed> ext, Instant now) {
        DataSource source = ext.map(Confirmed::source).orElseGet(() -> codes.dataSource(DataSource.INTERNAL));
        KeywordState state = codes.keywordState(ext.isPresent() ? CodeTables.KW_ACTIVE : CodeTables.KW_INACTIVE);
        Keyword k = new Keyword(country, source, state, text, normalized);
        if (ext.isPresent()) k.activate(state, now);
        try {
            k = keywords.saveAndFlush(k);
        } catch (DataIntegrityViolationException e) {
            // 같은 단어를 동시에 만들었다. 이 트랜잭션은 되돌리고 클라이언트가 다시 시도한다(다음 시도는 기존 행을 쓴다)
            throw ApiException.conflict("RETRY", "잠시 후 다시 시도해 주세요");
        }
        updateTrend(k);
        return k;
    }

    private BigDecimal updateTrend(Keyword k) {
        BigDecimal v = formula.trendValue(isState(k, CodeTables.KW_ACTIVE), k.getHolderCount(), k.getDataSource().getWeight().doubleValue());
        k.setTrendValue(v);
        return v;
    }

    private void publishTrendChange(Keyword k, BigDecimal oldTrend, BigDecimal newTrend, Long excludePlayerId) {
        if (oldTrend.compareTo(newTrend) == 0) return;
        dirtyKeywords.add(k.getId());
        events.publishEvent(new GameEvents.KeywordTrendChanged(k.getCountry().getId(), k.getId(),
                formula.score(newTrend).subtract(formula.score(oldTrend)), excludePlayerId));
    }

    private KeywordDtos.KeywordCard card(Keyword k, Player p, Instant now) {
        Optional<PlayerKeyword> mine = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId()).filter(pk -> !pk.isExpired(now));
        return new KeywordDtos.KeywordCard(k.getId(), k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                formula.score(k.getTrendValue()), k.getHolderCount(), k.getTotalPlusCount(),
                k.getSearchCount() + searchCounter.pending(k.getId()), mine.isPresent(),
                mine.map(PlayerKeyword::getExpiresAt).orElse(null));
    }

    private boolean isBanned(Keyword k) { return isState(k, CodeTables.KW_BANNED); }

    private boolean isState(Keyword k, String name) {
        return k.getKeywordState().getId().equals(codes.keywordStateId(name));
    }
}
