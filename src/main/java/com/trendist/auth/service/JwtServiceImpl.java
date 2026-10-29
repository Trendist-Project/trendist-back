package com.trendist.auth.service;

import com.trendist.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** 액세스 토큰(HS256 JWT). sub = users.id, iat = 발급 시각, 수명 15분(9.3). 로그아웃하면 그 전에 발급한 토큰은 AccountGuard가 거절한다. */
@Service
public class JwtServiceImpl implements JwtService {
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"(\\d+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");
    private static final Pattern IAT = Pattern.compile("\"iat\":(\\d+)");

    private final byte[] secret;
    private final long ttlSeconds;
    private final Clock clock;

    public JwtServiceImpl(AppProperties props, Clock clock) {
        this.secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("trendist.jwt.secret은 32바이트 이상이어야 합니다");
        this.ttlSeconds = props.jwt().accessTtlMinutes() * 60L;
        this.clock = clock;
    }

    @Override
    public String issue(long userId) {
        long iat = Instant.now(clock).getEpochSecond(), exp = iat + ttlSeconds;
        String payload = ENC.encodeToString(("{\"sub\":\"" + userId + "\",\"iat\":" + iat + ",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        String unsigned = HEADER + "." + payload;
        return unsigned + "." + ENC.encodeToString(sign(unsigned));
    }

    @Override
    public long ttlSeconds() { return ttlSeconds; }

    /** 서명과 만료를 검사하고 사용자 번호와 발급 시각을 돌려준다. iat가 없는 옛 토큰은 발급 시각 0으로 본다. */
    @Override
    public Optional<Verified> verify(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !parts[0].equals(HEADER)) return Optional.empty();
        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] actual;
        try { actual = DEC.decode(parts[2]); } catch (IllegalArgumentException e) { return Optional.empty(); }
        if (!MessageDigest.isEqual(expected, actual)) return Optional.empty();
        String json;
        try { json = new String(DEC.decode(parts[1]), StandardCharsets.UTF_8); } catch (IllegalArgumentException e) { return Optional.empty(); }
        Matcher sub = SUB.matcher(json), exp = EXP.matcher(json);
        if (!sub.find() || !exp.find()) return Optional.empty();
        if (Long.parseLong(exp.group(1)) <= Instant.now(clock).getEpochSecond()) return Optional.empty();
        Matcher iat = IAT.matcher(json);
        return Optional.of(new Verified(Long.parseLong(sub.group(1)), iat.find() ? Long.parseLong(iat.group(1)) : 0));
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
