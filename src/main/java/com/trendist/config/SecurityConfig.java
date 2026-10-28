package com.trendist.config;

import com.trendist.auth.AccountGuard;
import com.trendist.auth.JwtAuthFilter;
import com.trendist.auth.JwtService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public Clock clock() { return Clock.systemUTC(); }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, AccountGuard guard) throws Exception {
        http
                // Bearer 토큰 인증이고 쿠키를 쓰는 /api/auth는 POST + SameSite=Lax라 다른 사이트에서 보낼 수 없다(9.3)
                .csrf(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ws").permitAll() // WebSocket은 연결 직후 첫 메시지로 인증한다
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwt, guard), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 기본 사용자 자동 생성을 막는다(폼 로그인을 쓰지 않는다). */
    @Bean
    public UserDetailsService userDetailsService() { return new InMemoryUserDetailsManager(); }
}
