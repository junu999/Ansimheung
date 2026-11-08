package com.slink.ansimheung.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 보안 필터 단계 오류도 공통 오류 응답 {code, message, fields} 로 내려준다.
 * - 로그인 안 함 → 401 UNAUTHORIZED
 * - 권한 없음 → 403 FORBIDDEN
 * - CSRF 토큰 없음/만료 → 403 FORBIDDEN (안내 문구 다름)
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String CSRF_MESSAGE = "보안 토큰이 없거나 만료됐어요. 새로고침 후 다시 시도해 주세요";

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        write(response, ErrorResponse.of(ErrorCode.UNAUTHORIZED), ErrorCode.UNAUTHORIZED.status().value());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        ErrorResponse body = e instanceof CsrfException
                ? ErrorResponse.of(ErrorCode.FORBIDDEN, CSRF_MESSAGE, null)
                : ErrorResponse.of(ErrorCode.FORBIDDEN);
        write(response, body, ErrorCode.FORBIDDEN.status().value());
    }

    private void write(HttpServletResponse response, ErrorResponse body, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
