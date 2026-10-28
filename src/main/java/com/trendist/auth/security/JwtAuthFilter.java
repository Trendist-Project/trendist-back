package com.trendist.auth.security;

import com.trendist.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authorization: Bearer 토큰을 검증해 AuthUser를 넣는다. 금지·탈퇴 확인은 AuthService가 짧은 캐시로 한다. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AccountGuard guard;

    public JwtAuthFilter(JwtService jwt, AccountGuard guard) {
        this.jwt = jwt;
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            jwt.verify(h.substring(7)).filter(guard::isUsable).ifPresent(uid -> {
                var auth = new UsernamePasswordAuthenticationToken(new AuthUser(uid), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }
}
