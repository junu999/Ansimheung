package com.slink.ansimheung.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 초기 세팅용 보안 설정.
 * 지금은 모든 요청을 허용해서 개발을 막지 않게 해두었습니다.
 * 로그인 기능을 만들 때 상세 기획서 4장 권한 매트릭스대로 authorizeHttpRequests 를 채웁니다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // TODO 로그인 구현 시: /api/admin/** → hasRole("ADMIN"), 제보·공감 → authenticated()
                        .anyRequest().permitAll())
                // 세션 로그인 + 같은 도메인 프론트 → CSRF 토큰을 XSRF-TOKEN 쿠키로 내려주고
                // 프론트는 POST/PATCH/DELETE 때 X-XSRF-TOKEN 헤더로 돌려보냄 (static/js/api.js 가 자동 처리)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Spring Security 6 은 토큰을 "쓸 때" 만들어서, 그냥 두면 XSRF-TOKEN 쿠키가 안 내려감 → 매 요청마다 강제로 생성
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CSRF 토큰을 읽어서 XSRF-TOKEN 쿠키가 응답에 실리게 하는 필터 */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
