package com.trendist.auth.support;

import com.trendist.config.AppProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 구글 ID 토큰 검증: 구글 공개 키(JWKS)로 서명을 확인하고, 발급자(accounts.google.com)·대상(우리 클라이언트 ID)·만료를 본다.
 * 공개 키는 디코더가 받아 두고 필요할 때 다시 받는다. 보안 비밀번호는 필요 없다.
 */
@Component
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
    private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");
    private final NimbusJwtDecoder decoder;

    public NimbusGoogleIdTokenVerifier(AppProperties props) {
        String clientId = props.google().clientId();
        decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), // 만료·발급 시각
                (Jwt jwt) -> {
                    boolean issuerOk = jwt.getIssuer() != null && ISSUERS.contains(jwt.getIssuer().toString());
                    boolean audienceOk = jwt.getAudience() != null && jwt.getAudience().contains(clientId);
                    return issuerOk && audienceOk ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "issuer/audience", null));
                }));
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        try {
            Jwt jwt = decoder.decode(idToken);
            return jwt.getSubject() == null ? Optional.empty() : Optional.of(new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("nonce")));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
