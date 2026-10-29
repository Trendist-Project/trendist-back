package com.trendist.auth;

import com.trendist.auth.support.GoogleIdTokenVerifier;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 테스트용: 구글에 묻지 않고 "test-google:<sub>[:<nonce>]" 형식의 토큰만 받아들인다. */
@Component
@Primary
@Profile("test")
public class FakeGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        // "test-google:<sub>" 또는 "test-google:<sub>:<nonce>"
        if (idToken == null || !idToken.startsWith("test-google:")) return Optional.empty();
        String[] p = idToken.substring(12).split(":", 2);
        return Optional.of(new GoogleIdentity(p[0], p.length > 1 ? p[1] : null));
    }
}
