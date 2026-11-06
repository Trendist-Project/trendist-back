package com.trendist.user.service;

import com.trendist.auth.repository.RefreshTokenRepository;
import com.trendist.auth.security.AccountGuard;
import com.trendist.chat.repository.ChatMessageRepository;
import com.trendist.comment.entity.Comment;
import com.trendist.comment.repository.CommentRepository;
import com.trendist.common.exception.ApiException;
import com.trendist.country.entity.Country;
import com.trendist.country.service.CountryService;
import com.trendist.game.event.GameEvents;
import com.trendist.game.room.Room;
import com.trendist.game.service.RoomManager;
import com.trendist.keyword.service.KeywordService;
import com.trendist.player.entity.Player;
import com.trendist.player.repository.PlayerRepository;
import com.trendist.player.service.PlayerService;
import com.trendist.player.support.NicknameValidator;
import com.trendist.profile.service.ProfileService;
import com.trendist.report.entity.Report;
import com.trendist.report.entity.ReportType;
import com.trendist.report.repository.ReportRepository;
import com.trendist.title.entity.Title;
import com.trendist.title.repository.PlayerTitleRepository;
import com.trendist.title.repository.TitleRepository;
import com.trendist.user.dto.AdminUserDtos;
import com.trendist.user.entity.Sanction;
import com.trendist.user.entity.SanctionType;
import com.trendist.user.entity.User;
import com.trendist.user.repository.SanctionRepository;
import com.trendist.user.repository.SanctionTypeRepository;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminUserServiceImpl.class);
    private static final int PAGE_SIZE = 30;
    private static final Set<String> FILTERS = Set.of("all", "guest", "google", "admin", "banned", "withdrawn", "online");

    private final UserRepository users;
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final CountryService countries;
    private final RoomManager rooms;
    private final ReportRepository reports;
    private final CommentRepository comments;
    private final ChatMessageRepository chats;
    private final PlayerTitleRepository playerTitles;
    private final TitleRepository titles;
    private final SanctionRepository sanctions;
    private final SanctionTypeRepository sanctionTypes;
    private final RefreshTokenRepository refreshTokens;
    private final AccountGuard guard;
    private final KeywordService keywords;
    private final ProfileService profiles;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SanctionService sanctionService;

    @Override
    @Transactional(readOnly = true)
    public AdminUserDtos.Page search(String countryCode, String q, String filter, String sort, String dir, int page) {
        Country c = countries.requireOpen(countryCode);
        String f = filter == null || !FILTERS.contains(filter) ? "all" : filter;
        String text = q == null || q.isBlank() ? null : q.trim();
        long qid = -1;
        if (text != null) { try { qid = Long.parseLong(text); } catch (NumberFormatException ignored) { /* 닉네임 검색 */ } }
        Set<Long> online = onlineIds(c.getId());
        // 내림차순(desc)이 기본이고 오름차순(asc)도 고를 수 있다. 같은 값이면 번호 순서도 같은 방향
        boolean asc = "asc".equalsIgnoreCase(dir);
        java.util.function.Function<String, Sort.Order> by = p -> asc ? Sort.Order.asc(p) : Sort.Order.desc(p);
        Sort s = switch (sort == null ? "" : sort) {
            case "created" -> Sort.by(by.apply("createdAt"));
            case "score" -> Sort.by(by.apply("currentScore"), by.apply("id"));
            case "level" -> Sort.by(by.apply("level.level"), by.apply("exp"), by.apply("id"));
            case "plus" -> Sort.by(by.apply("addCount"), by.apply("id"));
            default -> Sort.by(by.apply("lastSeenAt").nullsLast(), by.apply("id")); // 접속 기록이 없는 사람은 어느 방향이든 맨 뒤
        };
        var result = players.searchForAdmin(c.getId(), text == null ? null : "%" + text.toLowerCase(Locale.ROOT) + "%", qid, f,
                Instant.now(clock), online.isEmpty() ? List.of(-1L) : online, PageRequest.of(Math.max(0, page), PAGE_SIZE, s));
        List<Player> list = result.getContent();
        Map<Long, Long> reportCounts = reportCounts(list.stream().map(Player::getId).toList());
        List<AdminUserDtos.Row> rows = list.stream().map(p -> row(p, online, reportCounts.getOrDefault(p.getId(), 0L))).toList();
        return new AdminUserDtos.Page(rows, result.getTotalElements(), result.getNumber(), PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDtos.Detail detail(long userId) { return detail(userId, null); }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDtos.Detail detail(long userId, String countryCode) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자가 없어요"));
        Player p = playerOf(userId, countryCode);
        Set<Long> online = onlineIds(p.getCountry().getId());
        long reportCount = reportCounts(List.of(p.getId())).getOrDefault(p.getId(), 0L);
        Double live = rooms.liveScore(p.getCountry().getId(), p.getId());
        String title = p.getTitleId() == null ? null : titles.findById(p.getTitleId()).map(Title::getName).orElse(null);
        AdminUserDtos.Stats stats = new AdminUserDtos.Stats(p.getExp(), p.getLevel(), live != null ? Math.round(live) : p.getCurrentScore(),
                p.getHoldCount(), p.getAddCount(), p.getSearchCount(), p.getCommentScore(), p.getStealCount(), p.getBotWinCount(),
                p.getOrbCount(), p.getLoseCount(), p.getActiveDays(), p.getKillCount(), p.getDefeatCount(), playerTitles.findByPlayerId(p.getId()).size(), title,
                comments.countByPlayerId(p.getId()), chats.countByPlayerId(p.getId()));
        Instant now = Instant.now(clock);
        List<AdminUserDtos.CommentLine> commentLines = comments.findRecentByPlayer(p.getId(), PageRequest.of(0, 10)).stream()
                .map(c -> new AdminUserDtos.CommentLine(c.getId(), c.getKeyword().getText(), c.getBody(), c.getLikeCount(),
                        c.getDislikeCount(), c.getDeletedAt() != null, c.getCreatedAt())).toList();
        List<AdminUserDtos.ChatLine> chatLines = chats.findByPlayerIdOrderByIdDesc(p.getId(), PageRequest.of(0, 10)).stream()
                .map(m -> new AdminUserDtos.ChatLine(m.getId(), m.getBody(), m.getCreatedAt())).toList();
        List<AdminUserDtos.ReportLine> reportLines = reports.findAgainstPlayer(p.getId(), PageRequest.of(0, 20)).stream()
                .map(this::reportLine).toList();
        List<AdminUserDtos.SanctionLine> sanctionLines = sanctions.findByUser(userId).stream()
                .map(s -> new AdminUserDtos.SanctionLine(s.getId(), s.getSanctionType().getName(), s.getReason(), s.getCreatedAt(),
                        s.getExpiresAt(), s.getReleasedAt(), s.isActive(now))).toList();
        return new AdminUserDtos.Detail(row(p, online, reportCount), u.getLastLoginAt(), u.getDeletedAt(), p.getNicknameChangedAt(),
                p.getLastPlayedAt(), stats, profiles.lookOf(p.getId()), profiles.equippedArt(p.getId()).orElse(null),
                keywords.myKeywordsOfPlayer(p.getId()), commentLines, chatLines, reportLines, sanctionLines);
    }

    @Override
    @Transactional
    public AdminUserDtos.Detail sanction(long userId, AdminUserDtos.SanctionRequest req) { return sanction(userId, req, null); }

    @Override
    @Transactional
    public AdminUserDtos.Detail sanction(long userId, AdminUserDtos.SanctionRequest req, String countryCode) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자가 없어요"));
        String typeName = switch (req.type() == null ? "" : req.type()) {
            case "BAN" -> SanctionType.BAN;
            case "UPLOAD" -> SanctionType.UPLOAD;
            case "COMMENT" -> SanctionType.COMMENT;
            case "CHAT" -> SanctionType.CHAT;
            default -> throw ApiException.badRequest("SANCTION_TYPE", "제재 종류는 BAN, UPLOAD, COMMENT, CHAT 중 하나예요");
        };
        if (req.days() != null && (req.days() < 1 || req.days() > 3650)) throw ApiException.badRequest("SANCTION_DAYS", "기간은 1~3650일이에요");
        String reason = req.reason() == null ? "" : req.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) throw ApiException.badRequest("SANCTION_REASON", "사유를 1~500자로 적어 주세요");
        if (User.ROLE_ADMIN.equals(u.getRole()) && SanctionType.BAN.equals(typeName)) {
            throw ApiException.badRequest("SANCTION_ADMIN", "관리자 계정은 먼저 관리자 권한을 빼야 금지할 수 있어요");
        }
        Instant now = Instant.now(clock);
        Instant expires = req.days() == null ? Sanction.FOREVER : now.plus(Duration.ofDays(req.days()));
        SanctionType type = sanctionTypes.findByName(typeName).orElseThrow();
        sanctions.save(new Sanction(u, type, reason, expires));
        sanctions.flush();
        syncUser(u, now);
        sanctionService.evictCache();
        if (SanctionType.BAN.equals(typeName)) {
            // 토큰이 살아 있는 15분 동안도 막고(30초 캐시를 비움), 갱신 토큰을 모두 폐기하고, 접속 중이면 내보낸다(9.3)
            refreshTokens.revokeAll(userId, now);
            guard.evict(userId);
            // 계정 금지는 모든 나라의 방에서 내보낸다(플레이어가 나라마다 따로 있다)
            for (Player p : players.findByUserId(userId)) rooms.kick(p.getCountry().getId(), p.getId(), "운영 정책 위반으로 이용이 정지된 계정이에요");
        }
        return detail(userId, countryCode);
    }

    @Override
    @Transactional
    public AdminUserDtos.Detail release(long sanctionId, String countryCode) { return release0(sanctionId, countryCode); }

    @Override
    @Transactional
    public AdminUserDtos.Detail release(long sanctionId) { return release0(sanctionId, null); }

    private AdminUserDtos.Detail release0(long sanctionId, String countryCode) {
        Sanction s = sanctions.findById(sanctionId).orElseThrow(() -> ApiException.notFound("SANCTION_NOT_FOUND", "제재 기록이 없어요"));
        Instant now = Instant.now(clock);
        if (!s.isActive(now)) throw ApiException.conflict("SANCTION_NOT_ACTIVE", "이미 끝난 제재예요");
        s.release(now);
        sanctions.flush();
        User u = s.getUser();
        syncUser(u, now);
        sanctionService.evictCache();
        guard.evict(u.getId());
        return detail(u.getId(), countryCode);
    }

    /** users의 제재 칸을 sanctions에서 다시 정한다: 같은 종류의 제재 중 가장 늦게 끝나는 시각, 없으면 null(13.5). */
    private void syncUser(User u, Instant now) {
        u.changeBannedUntil(sanctions.activeUntil(u.getId(), SanctionType.BAN, now));
        u.changeUploadBannedUntil(sanctions.activeUntil(u.getId(), SanctionType.UPLOAD, now));
    }

    @Override
    @Transactional
    public AdminUserDtos.Detail rename(long userId, String nickname) { return rename(userId, nickname, null); }

    @Override
    @Transactional
    public AdminUserDtos.Detail rename(long userId, String nickname, String countryCode) {
        Player p = playerOf(userId, countryCode);
        String clean = NicknameValidator.clean(nickname);
        log.info("admin rename user {}: {} -> {}", userId, p.getNickname(), clean); // 조치 기록은 서버 로그에(13.10)
        p.adminRename(clean);
        players.flush();
        events.publishEvent(new GameEvents.NicknameChanged(p.getCountry().getId(), p.getId(), clean));
        return detail(userId, countryCode);
    }

    @Override
    @Transactional
    public AdminUserDtos.Detail resetLook(long userId) { return resetLook(userId, null); }

    @Override
    @Transactional
    public AdminUserDtos.Detail resetLook(long userId, String countryCode) {
        Player p = playerOf(userId, countryCode);
        log.info("admin reset look of user {}", userId);
        profiles.adminReset(p.getId());
        return detail(userId, countryCode);
    }

    @Override
    @Transactional
    public AdminUserDtos.Detail setAdmin(long actorUserId, long userId, boolean admin) { return setAdmin(actorUserId, userId, admin, null); }

    @Override
    @Transactional
    public AdminUserDtos.Detail setAdmin(long actorUserId, long userId, boolean admin, String countryCode) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자가 없어요"));
        if (u.isGuest()) throw ApiException.badRequest("ROLE_GUEST", "관리자 권한은 Google로 로그인한 계정에만 줄 수 있어요");
        if (!admin && actorUserId == userId) throw ApiException.badRequest("ROLE_SELF", "내 관리자 권한은 뺄 수 없어요");
        log.info("admin role of user {} -> {} by {}", userId, admin, actorUserId);
        u.changeRole(admin ? User.ROLE_ADMIN : "USER");
        users.flush();
        guard.evict(userId);
        return detail(userId, countryCode);
    }

    @Override
    @Transactional(readOnly = true)
    public void kick(long userId) { kick(userId, null); }

    @Override
    @Transactional(readOnly = true)
    public void kick(long userId, String countryCode) {
        Player p = playerOf(userId, countryCode);
        if (!onlineIds(p.getCountry().getId()).contains(p.getId())) throw ApiException.conflict("NOT_ONLINE", "지금 접속 중이 아니에요");
        log.info("admin kick user {}", userId);
        rooms.kick(p.getCountry().getId(), p.getId(), "관리자가 접속을 끊었어요");
    }

    private AdminUserDtos.Row row(Player p, Set<Long> online, long reportCount) {
        User u = p.getUser();
        Instant now = Instant.now(clock);
        Instant ban = u.isBanned(now) ? u.getBannedUntil() : null;
        Instant upload = u.isUploadBanned(now) ? u.getUploadBannedUntil() : null;
        Instant comment = sanctions.activeUntil(u.getId(), SanctionType.COMMENT, now);
        Instant chat = sanctions.activeUntil(u.getId(), SanctionType.CHAT, now);
        Double live = rooms.liveScore(p.getCountry().getId(), p.getId());
        return new AdminUserDtos.Row(u.getId(), p.getId(), p.getNickname(), u.isGuest() ? "GUEST" : "GOOGLE",
                User.ROLE_ADMIN.equals(u.getRole()), u.isWithdrawn(), ban, upload, comment, p.getLevel(),
                live != null ? Math.round(live) : p.getCurrentScore(), p.getHoldCount(), p.getAddCount(), reportCount,
                p.getCreatedAt(), online.contains(p.getId()) ? now : p.getLastSeenAt(), online.contains(p.getId()), chat);
    }

    private Map<Long, Long> reportCounts(Collection<Long> playerIds) {
        Map<Long, Long> out = new HashMap<>();
        if (playerIds.isEmpty()) return out;
        for (List<Object[]> rows : List.of(reports.countPlayerReports(playerIds), reports.countCommentReports(playerIds), reports.countChatReports(playerIds))) {
            for (Object[] r : rows) out.merge(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), Long::sum);
        }
        return out;
    }

    private AdminUserDtos.ReportLine reportLine(Report r) {
        String type = r.getReportType().getName();
        String content = switch (type) {
            case ReportType.COMMENT -> comments.findById(r.getTargetId()).map(Comment::getBody).orElse(null);
            case ReportType.CHAT -> chats.findById(r.getTargetId()).map(m -> m.getBody()).orElse(null);
            default -> null;
        };
        return new AdminUserDtos.ReportLine(r.getId(), type, r.getReason(), r.getStatus(), r.getPlayer().getNickname(), content, r.getCreatedAt());
    }

    /** 관리자가 고른 나라의 플레이어. 나라를 안 주면 사용자가 마지막으로 플레이한 나라. */
    private Player playerOf(long userId, String countryCode) {
        Optional<Player> p = countryCode == null ? playerService.findCurrent(userId)
                : players.findByUserIdAndCountryId(userId, countries.requireOpen(countryCode).getId());
        return p.orElseThrow(() -> ApiException.notFound("NO_PLAYER", "닉네임을 정하지 않은 사용자예요"));
    }

    private Set<Long> onlineIds(long countryId) {
        Set<Long> ids = new HashSet<>();
        for (Room.Live l : rooms.livePlayers(countryId)) ids.add(l.playerId());
        return ids;
    }
}
