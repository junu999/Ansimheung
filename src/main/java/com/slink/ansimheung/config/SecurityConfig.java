package com.slink.ansimheung.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.slink.ansimheung.common.error.SecurityErrorHandler;
import com.slink.ansimheung.common.type.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
 * 보안 설정 — 세션 로그인 + CSRF 쿠키 + 권한 규칙 (docs/api.md "권한" 열과 같음).
 * - /api/admin/** → ADMIN
 * - 제보 등록·수정·삭제, 공감, 묶기 후보, 내 정보(/api/me/**) → 회원
 * - 나머지(지도·상세·경로·설정·화면 파일) → 누구나
 * 새 회원 전용 API 를 만들면 아래 MEMBER 규칙에 한 줄 추가하고 SecurityConfigTest 에도 한 줄 추가할 것.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        // MEMBER — 로그인 필요
                        .requestMatchers("/api/me", "/api/me/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reports").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/reports/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reports/**").authenticated()
                        .requestMatchers("/api/hazards/*/empathy").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/hazards/nearby").authenticated()
                        // 그 외 공개 (GET /api/hazards, /api/hazards/{id}, POST /api/routes, /api/auth/**, 정적 파일)
                        .anyRequest().permitAll())
                // 세션 로그인 + 같은 도메인 프론트 → CSRF 토큰을 XSRF-TOKEN 쿠키로 내려주고
                // 프론트는 POST/PATCH/DELETE 때 X-XSRF-TOKEN 헤더로 돌려보냄 (static/js/http.js 가 자동 처리)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Spring Security 6 은 토큰을 "쓸 때" 만들어서, 그냥 두면 XSRF-TOKEN 쿠키가 안 내려감 → 매 요청마다 강제로 생성
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // 401·403·CSRF 오류도 공통 오류 응답 {code, message, fields} 로
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                // 로그인은 JSON(POST /api/auth/login) 컨트롤러에서 처리 — 기본 폼 로그인 안 씀
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 로그인 API(#9)용. UserDetailsService(LoginUserDetailsService) + PasswordEncoder 로 자동 구성된 것을 꺼내 쓴다.
     * 인증 성공 후 세션 저장은 컨트롤러에서 SecurityContextRepository(HttpSessionSecurityContextRepository)로 한다.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
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
