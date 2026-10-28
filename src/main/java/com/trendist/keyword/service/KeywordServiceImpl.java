package com.trendist.keyword.service;

import com.trendist.common.code.CodeTables;
import com.trendist.common.exception.ApiException;
import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.game.event.GameEvents;
import com.trendist.keyword.dto.KeywordDtos;
import com.trendist.keyword.entity.DataSource;
import com.trendist.keyword.entity.Keyword;
import com.trendist.keyword.entity.KeywordState;
import com.trendist.keyword.entity.PlayerKeyword;
import com.trendist.keyword.repository.KeywordRepository;
import com.trendist.keyword.repository.PlayerKeywordRepository;
import com.trendist.keyword.source.ExternalTrendSource;
import com.trendist.keyword.support.KeywordNormalizer;
import com.trendist.keyword.support.SearchCounter;
import com.trendist.keyword.support.TrendFormula;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.setting.service.SettingService;
import com.trendist.setting.support.SettingKeys;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 검색, +, 보관함, 만료, 충돌로 잃기(4.4~4.8, 9.4). */
@Service
@RequiredArgsConstructor
public class KeywordServiceImpl implements KeywordService {
    private static final Logger log = LoggerFactory.getLogger(KeywordServiceImpl.class);
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
    private final TransactionTemplate tx;
    private final CountryService countries;
    private final Clock clock;

    /** 트렌드 값이 바뀐 키워드. 1분 잡이 보유자 점수를 DB에서 다시 합산한다(9.4). */
    private final Set<Long> dirtyKeywords = ConcurrentHashMap.newKeySet();

    // ---------------------------------------------------------------- 검색

    @Override
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
        var preview = new KeywordDtos.KeywordCard(null, text, CodeTables.KW_INACTIVE, BigDecimal.ZERO,
                0, 0, 0, false, null);
        return new KeywordDtos.SearchResult("NEW", preview);
    }

    // ---------------------------------------------------------------- +

    @Override
    @Transactional
    public KeywordDtos.PlusResult plus(long userId, String rawText) {
        Player found = playerService.requireCurrent(userId);
        Player p = players.findByIdForUpdate(found.getId()).orElseThrow(); // 잠금 순서: 플레이어 → 키워드
        String text = KeywordNormalizer.display(rawText);
        String normalized = KeywordNormalizer.validate(text);
        Instant now = Instant.now(clock);
        Country country = p.getCountry();

        Keyword k = keywords.findByCountryIdAndNormalized(country.getId(), normalized)
                .orElseGet(() -> createKeyword(country, text, normalized, externalConfirm(country.getCode(), normalized), now));
        k = keywords.findByIdForUpdate(k.getId()).orElseThrow();
        if (isBanned(k)) throw ApiException.badRequest("KEYWORD_BANNED", "검색할 수 없는 키워드예요");

        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId());
        if (existing.isPresent() && !existing.get().isExpired(now)) throw ApiException.conflict("ALREADY_PLUSED", "이미 보유중인 키워드예요");
        return addHolding(p, k, existing, now);
    }

    /** +의 본체: 보관함에 넣고 인원·EXP·트렌드 값·점수를 고친다. 플레이어와 키워드 행을 잠근 상태에서 부른다. */
    private KeywordDtos.PlusResult addHolding(Player p, Keyword k, Optional<PlayerKeyword> existing, Instant now) {
        Instant expires = now.plus(Duration.ofHours(settings.getInt(SettingKeys.PLUS_TTL_HOURS)));
        Country country = p.getCountry();
        BigDecimal oldTrend = k.getTrendValue();
        BigDecimal oldScore = p.getCurrentScore();
        boolean newHolder;
        if (existing.isPresent()) {
            existing.get().renew(now, expires); // 만료 행 재사용: 가진 사람 수는 그대로
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

    // ---------------------------------------------------------------- 구슬(4.20)

    @Override
    @Transactional
    public KeywordDtos.OrbResult openOrb(long playerId) {
        Player p = players.findByIdForUpdate(playerId).orElse(null);
        if (p == null) return new KeywordDtos.OrbResult("NONE", null, null, null, 0, null, null);
        // 설정 확률(기본 33%)로만 🔥활성 키워드가 나오고, 나머지는 꽝이다
        if (ThreadLocalRandom.current().nextDouble(100) >= settings.getDouble(SettingKeys.ORB_ACTIVE_PERCENT)) {
            return new KeywordDtos.OrbResult("MISS", null, null, null, 0, null, null);
        }
        List<Long> ids = keywords.findIdsByCountryAndState(p.getCountry().getId(), codes.keywordStateId(CodeTables.KW_ACTIVE));
        if (ids.isEmpty()) return new KeywordDtos.OrbResult("NONE", null, null, null, 0, null, null);
        Keyword k = keywords.findByIdForUpdate(ids.get(ThreadLocalRandom.current().nextInt(ids.size()))).orElseThrow();
        Instant now = Instant.now(clock);
        Optional<PlayerKeyword> existing = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId());
        if (existing.isPresent() && !existing.get().isExpired(now)) {
            // 이미 보관함에 있는 키워드가 나오면 아무것도 바뀌지 않는다
            return new KeywordDtos.OrbResult("DUPLICATE", k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                    k.getHolderCount(), BigDecimal.ZERO, existing.get().getExpiresAt());
        }
        KeywordDtos.PlusResult r = addHolding(p, k, existing, now);
        return new KeywordDtos.OrbResult("GAINED", k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                k.getHolderCount(), r.gained(), r.keyword().myExpiresAt());
    }

    // ---------------------------------------------------------------- 목록형 외부 소스(10.2)

    @Override
    public int applyExternalTitles(String countryCode, String sourceName, List<String> titles) {
        int held = 0;
        for (String title : titles) {
            String text = KeywordNormalizer.fromExternal(title);
            String normalized;
            try {
                normalized = KeywordNormalizer.validate(text); // 4.6 등록 조건을 통과하지 못하면 버린다
            } catch (ApiException e) {
                log.debug("skip external title '{}': {}", title, e.code());
                continue;
            }
            try {
                Boolean r = tx.execute(st -> holdFromList(countryCode, sourceName, text, normalized));
                if (Boolean.TRUE.equals(r)) held++;
            } catch (RuntimeException e) {
                log.warn("external title '{}' failed", title, e);
            }
        }
        return held;
    }

    /** 목록에 오른 단어 하나: 없으면 🔥로 만들고, 있으면 그 소스로 지금부터 유지 기간(기본 7일)을 다시 센다. 새로 유지를 시작했으면 true. */
    private boolean holdFromList(String countryCode, String sourceName, String text, String normalized) {
        Instant now = Instant.now(clock);
        DataSource source = codes.dataSource(sourceName);
        if (source.getWeight().signum() == 0) return false; // 가중치 0이면 쓰지 않는다
        Optional<Keyword> found = keywords.findByCountryIdAndNormalized(countryOf(countryCode).getId(), normalized);
        if (found.isEmpty()) {
            createKeyword(countryOf(countryCode), text, normalized, Optional.of(new Confirmed(source, 100)), now);
            return true;
        }
        Keyword k = keywords.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (isBanned(k)) return false;
        boolean holding = k.getDataSource().getId().equals(source.getId()) && k.getExternalUntil() != null
                && k.getExternalUntil().isAfter(now);
        k.holdExternal(source, externalHoldUntil(now)); // 목록에 보일 때마다 지금부터 다시 7일
        if (holding) return false;
        BigDecimal old = k.getTrendValue();
        k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now);
        BigDecimal nw = updateTrend(k);
        publishTrendChange(k, old, nw, null);
        return true;
    }

    @Override
    public int expireExternal(String sourceName, Set<String> currentNormalized) {
        DataSource source = codes.dataSource(sourceName);
        int dropped = 0;
        for (Keyword stale : keywords.findByDataSourceIdAndExternalUntilBefore(source.getId(), Instant.now(clock))) {
            try {
                Boolean r = tx.execute(st -> expireOneExternal(stale.getId(), source, currentNormalized));
                if (Boolean.TRUE.equals(r)) dropped++;
            } catch (RuntimeException e) {
                log.warn("expire external keyword {} failed", stale.getId(), e);
            }
        }
        return dropped;
    }

    private boolean expireOneExternal(long keywordId, DataSource source, Set<String> currentNormalized) {
        Instant now = Instant.now(clock);
        Keyword k = keywords.findByIdForUpdate(keywordId).orElse(null);
        if (k == null || !k.getDataSource().getId().equals(source.getId())
                || k.getExternalUntil() == null || k.getExternalUntil().isAfter(now)) return false;
        if (currentNormalized.contains(k.getNormalized())) {
            k.holdExternal(source, externalHoldUntil(now)); // 유지 기간이 끝나도 아직 목록에 있으면 지금부터 다시
            return false;
        }
        BigDecimal old = k.getTrendValue();
        k.dropExternal(codes.dataSource(DataSource.INTERNAL));
        // 내부 데이터만으로는 가진 사람이 3명 이상이어야 🔥(4.7)
        if (isState(k, CodeTables.KW_ACTIVE) && k.getHolderCount() < settings.getInt(SettingKeys.ACTIVATE_HOLDERS)) {
            k.deactivate(codes.keywordState(CodeTables.KW_INACTIVE), now);
        }
        BigDecimal nw = updateTrend(k);
        publishTrendChange(k, old, nw, null);
        return true;
    }

    // ---------------------------------------------------------------- 조회

    @Override
    @Transactional(readOnly = true)
    public List<KeywordDtos.MyKeyword> myKeywords(long userId) {
        Player p = playerService.requireCurrent(userId);
        Instant now = Instant.now(clock);
        return playerKeywords.findActiveByPlayer(p.getId(), now, codes.keywordStateId(CodeTables.KW_BANNED)).stream()
                .map(pk -> {
                    Keyword k = pk.getKeyword();
                    return new KeywordDtos.MyKeyword(k.getId(), k.getText(), k.getKeywordState().getName(),
                            k.getTrendValue(), pk.getCreatedAt(), pk.getExpiresAt());
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> activeKeywordIds(long playerId) {
        return playerKeywords.findActiveByPlayer(playerId, Instant.now(clock), codes.keywordStateId(CodeTables.KW_BANNED))
                .stream().map(pk -> pk.getKeyword().getId()).toList();
    }

    // ---------------------------------------------------------------- 만료, 잃기, 재합산

    /** 만료된 + 하나를 지우고 인원·트렌드 값·점수를 고친다. 지웠으면 true. */
    @Override
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
    @Override
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
    @Override
    public Set<Long> drainDirtyKeywords() {
        Set<Long> out = new HashSet<>(dirtyKeywords);
        dirtyKeywords.removeAll(out);
        return out;
    }

    @Override
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
            if (isListSource(c.source())) k.holdExternal(c.source(), externalHoldUntil(now));
            else k.setDataSource(c.source());
            k.activate(codes.keywordState(CodeTables.KW_ACTIVE), now);
            BigDecimal nw = updateTrend(k);
            publishTrendChange(k, old, nw, null);
        });
    }

    private Keyword createKeyword(Country country, String text, String normalized, Optional<Confirmed> ext, Instant now) {
        DataSource source = ext.map(Confirmed::source).orElseGet(() -> codes.dataSource(DataSource.INTERNAL));
        KeywordState state = codes.keywordState(ext.isPresent() ? CodeTables.KW_ACTIVE : CodeTables.KW_INACTIVE);
        Keyword k = new Keyword(country, source, state, text, normalized);
        if (ext.isPresent()) {
            k.activate(state, now);
            if (isListSource(source)) k.holdExternal(source, externalHoldUntil(now));
        }
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
                newTrend.subtract(oldTrend), excludePlayerId));
    }

    private KeywordDtos.KeywordCard card(Keyword k, Player p, Instant now) {
        Optional<PlayerKeyword> mine = playerKeywords.findByPlayerIdAndKeywordId(p.getId(), k.getId()).filter(pk -> !pk.isExpired(now));
        return new KeywordDtos.KeywordCard(k.getId(), k.getText(), k.getKeywordState().getName(), k.getTrendValue(),
                k.getHolderCount(), k.getTotalPlusCount(),
                k.getSearchCount() + searchCounter.pending(k.getId()), mine.isPresent(),
                mine.map(PlayerKeyword::getExpiresAt).orElse(null));
    }

    /** 목록에 오른 동안만 확인되는 소스(google). 유지 기간(기본 7일)을 두고 끝나면 다시 본다. */
    /** 목록형 외부 소스 유지 끝 시각: 지금 + 설정 시간(기본 168시간 = 7일). */
    private Instant externalHoldUntil(Instant now) {
        return now.plus(Duration.ofHours(settings.getInt(SettingKeys.EXTERNAL_HOLD_HOURS)));
    }

    private boolean isListSource(DataSource s) { return DataSource.GOOGLE.equals(s.getName()); }

    private Country countryOf(String code) {
        return countries.requireOpen(code);
    }

    private boolean isBanned(Keyword k) { return isState(k, CodeTables.KW_BANNED); }

    private boolean isState(Keyword k, String name) {
        return k.getKeywordState().getId().equals(codes.keywordStateId(name));
    }
}
