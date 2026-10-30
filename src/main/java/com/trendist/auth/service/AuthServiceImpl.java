package com.trendist.auth.service;

import java.util.HexFormat;
import java.util.Base64;
import java.time.Instant;
import java.time.Duration;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.trendist.common.exception.ApiException;
import com.trendist.auth.support.GoogleIdTokenVerifier;
import com.trendist.auth.repository.RefreshTokenRepository;
import com.trendist.auth.entity.RefreshToken;
import com.trendist.auth.dto.TokenResponse;
import com.trendist.auth.security.AccountGuard;
import com.trendist.auth.support.VisitorIds;
import com.trendist.common.code.CodeTables;
import com.trendist.user.entity.User;
import com.trendist.user.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository users;
    private final CodeTables codes;
    private final JwtService jwt;
    private final AccountGuard guard;
    private final Clock clock;
    private final GoogleIdTokenVerifier google;
    private final RefreshTokenRepository refreshTokens;

    private static final Duration REFRESH_TTL = Duration.ofDays(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public GuestStart startGuest(Optional<String> visitorId) {
        Optional<User> existing = visitorId.flatMap(users::findByVisitorId).filter(this::isGuestUsable);
        if (existing.isPresent()) return new GuestStart(token(existing.get()), null);
        String vid = VisitorIds.create(clock);
        User u = users.save(User.guest(vid, codes.userState(CodeTables.USER_NORMAL)));
        return new GuestStart(token(u), vid);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TokenResponse> resumeGuest(String visitorId) {
        return users.findByVisitorId(visitorId).filter(this::isGuestUsable).map(this::token);
    }

    /** 방문자 ID로는 구글이 연결되지 않은, 정상 상태의 게스트만 토큰을 받는다. */
    private boolean isGuestUsable(User u) {
        return u.getOauthId() == null && guard.isUsable(u.getId());
    }

    private TokenResponse token(User u) {
        return new TokenResponse(jwt.issue(u.getId()), jwt.ttlSeconds());
    }

    // ---------------------------------------------------------------- 구글 로그인과 갱신 토큰

    @Override
    @Transactional
    public Login google(String idToken, String nonce, Optional<String> visitorId) {
        String oauthId = verifiedOauthId(idToken, nonce);
        Instant now = Instant.now(clock);
        Optional<User> account = users.findByOauthTypeAndOauthId(User.OAUTH_GOOGLE, oauthId);
        User u;
        String result;
        if (account.isPresent()) {
            u = account.get();
            if (u.isWithdrawn()) return new Login(null, null, "WITHDRAWN"); // 복구할지는 사용자가 정한다(4.1)
            if (!guard.isUsable(u.getId())) throw ApiException.forbiddenCode("ACCOUNT_BANNED", "이용이 제한된 계정이에요");
            result = "EXISTING";
        } else {
            Optional<User> guest = visitorId.flatMap(users::findByVisitorId).filter(this::isGuestUsable);
            if (guest.isPresent()) {
                u = guest.get();
                u.linkGoogle(oauthId); // OAuth 값만 채운다. 플레이어·점수·보관함은 그대로 이어진다
                result = "LINKED";
            } else {
                u = users.save(User.google(oauthId, codes.userState(CodeTables.USER_NORMAL)));
                result = "CREATED";
            }
        }
        u.touchLogin(now);
        return new Login(token(u), issueRefresh(u, now), result);
    }

    @Override
    @Transactional
    public Login restore(String idToken, String nonce) {
        String oauthId = verifiedOauthId(idToken, nonce);
        User u = users.findByOauthTypeAndOauthId(User.OAUTH_GOOGLE, oauthId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없어요"));
        if (!u.isWithdrawn()) throw ApiException.conflict("NOT_WITHDRAWN", "탈퇴한 계정이 아니에요");
        u.restore(codes.userState(CodeTables.USER_NORMAL));
        guard.evict(u.getId());
        if (!guard.isUsable(u.getId())) throw ApiException.forbiddenCode("ACCOUNT_BANNED", "이용이 제한된 계정이에요");
        Instant now = Instant.now(clock);
        u.touchLogin(now);
        return new Login(token(u), issueRefresh(u, now), "RESTORED");
    }

    @Override
    @Transactional
    public void withdraw(long userId, String phrase) {
        if (!WITHDRAW_PHRASE.equals(phrase)) throw ApiException.badRequest("WITHDRAW_PHRASE", "확인 문구를 정확히 입력해 주세요");
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없어요"));
        if (u.isGuest()) throw ApiException.badRequest("WITHDRAW_GUEST", "게스트는 탈퇴할 수 없어요. Google로 로그인한 계정만 탈퇴할 수 있어요");
        if (u.isWithdrawn()) return;
        Instant now = Instant.now(clock);
        u.withdraw(codes.userState(CodeTables.USER_WITHDRAWN), now);
        refreshTokens.revokeAll(u.getId(), now);
        guard.evict(u.getId());
    }

    /** 구글 ID 토큰을 검증하고 구글 사용자 ID의 해시를 돌려준다. nonce를 주면 토큰 안의 nonce와 같아야 한다. */
    private String verifiedOauthId(String idToken, String nonce) {
        GoogleIdTokenVerifier.GoogleIdentity id = google.verify(idToken).orElseThrow(() -> ApiException.unauthorized("GOOGLE_TOKEN_INVALID"));
        if (nonce != null && !nonce.equals(id.nonce())) throw ApiException.unauthorized("GOOGLE_TOKEN_INVALID");
        return sha256(id.sub()); // 구글 사용자 ID는 해시로만 저장한다
    }

    @Override
    @Transactional
    public Optional<Login> refresh(String raw) {
        Instant now = Instant.now(clock);
        Optional<RefreshToken> found = refreshTokens.findByTokenHash(sha256(raw));
        if (found.isEmpty()) return Optional.empty();
        RefreshToken t = found.get();
        if (t.isRevoked()) {
            // 이미 쓴(폐기된) 토큰이 다시 왔다: 누가 훔쳐 쓴 것으로 보고 그 사용자의 세션을 모두 끊는다
            refreshTokens.revokeAll(t.getUser().getId(), now);
            return Optional.empty();
        }
        if (!t.getExpiresAt().isAfter(now) || !guard.isUsable(t.getUser().getId())) return Optional.empty();
        t.revoke(now);
        User u = t.getUser();
        return Optional.of(new Login(token(u), issueRefresh(u, now), "REFRESHED"));
    }

    @Override
    @Transactional
    public void logout(Optional<String> raw, Optional<Long> userId) {
        Instant now = Instant.now(clock);
        Optional<Long> who = userId;
        if (raw.isPresent()) {
            Optional<RefreshToken> t = refreshTokens.findByTokenHash(sha256(raw.get()));
            t.ifPresent(x -> x.revoke(now));
            if (who.isEmpty()) who = t.map(x -> x.getUser().getId());
        }
        who.flatMap(users::findById).ifPresent(u -> {
            u.revokeTokens(now); // 이 시각 이전에 발급한 JWT는 이제 거절된다
            guard.evict(u.getId());
        });
    }

    /** 32바이트 무작위 값을 만들어 해시만 저장하고, 원본은 쿠키로 내려 준다. 1일 뒤 만료(쓸 때마다 새로 1일). */
    private String issueRefresh(User u, Instant now) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        refreshTokens.save(new RefreshToken(u, sha256(raw), now.plus(REFRESH_TTL)));
        return raw;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
