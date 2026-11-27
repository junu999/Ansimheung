package com.slink.ansimheung.auth;

import com.slink.ansimheung.auth.dto.LoginRequest;
import com.slink.ansimheung.auth.dto.MeResponse;
import com.slink.ansimheung.auth.dto.SignupRequest;
import com.slink.ansimheung.auth.dto.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 권한: signup·login 은 누구나, logout·me 는 회원 (SecurityConfig) */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(authService.signup(req)));
    }

    @PostMapping("/api/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        return MeResponse.from(authService.login(req, request, response));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal LoginUser user) {
        return MeResponse.from(user);
    }
}
