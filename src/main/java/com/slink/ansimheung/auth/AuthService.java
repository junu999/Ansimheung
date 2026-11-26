package com.slink.ansimheung.auth;

import com.slink.ansimheung.auth.dto.LoginRequest;
import com.slink.ansimheung.auth.dto.SignupRequest;
import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입 · JSON 세션 로그인 · 로그아웃 (#9, F-AUTH-02 ~ 05). 폼 로그인·JWT 안 씀 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /** V-01 ~ V-05 는 DTO @Valid 에서 끝남. 이메일은 소문자로 저장 (User.normalizeEmail) */
    @Transactional
    public Long signup(SignupRequest req) {
        String email = User.normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_DUP);
        }
        try {
            return users.saveAndFlush(User.create(email, passwordEncoder.encode(req.password()), req.nickname(), Role.USER))
                    .getId();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.EMAIL_DUP);   // 동시에 같은 이메일로 가입 → DB 유니크 제약
        }
    }

    /** 성공하면 세션 ID 를 새로 바꾼 뒤(세션 고정 방지) 세션에 로그인 정보를 저장한다 */
    public LoginUser login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.email(), req.password()));
        } catch (AuthenticationException e) {
            throw new ApiException(ErrorCode.AUTH_FAIL);   // 없는 이메일·틀린 비밀번호를 구분하지 않음
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(auth);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return (LoginUser) auth.getPrincipal();
    }

    /** 세션 무효화 + 로그인 정보 삭제 */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, contextHolder.getContext().getAuthentication());
    }
}
