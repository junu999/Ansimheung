package com.slink.ansimheung.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 초기 세팅용 보안 설정.
 * 지금은 모든 요청을 허용해서 개발을 막지 않게 해두었습니다.
 * 로그인 기능을 만들 때 기획서 4장 권한 매트릭스대로 authorizeHttpRequests 를 채웁니다.
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
                // 프론트는 POST/PATCH/DELETE 때 X-XSRF-TOKEN 헤더로 돌려보냄
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
