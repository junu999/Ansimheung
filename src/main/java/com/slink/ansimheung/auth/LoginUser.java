package com.slink.ansimheung.auth;

import com.slink.ansimheung.common.type.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 로그인한 사용자 (세션에 저장되는 값). 컨트롤러에서 이렇게 받는다:
 * <pre>
 * {@code @PostMapping("/api/reports")}
 * {@code public ... create(@AuthenticationPrincipal LoginUser user, ...) { user.id() ... }}
 * </pre>
 * 비로그인 요청은 SecurityConfig 권한 규칙에서 먼저 401 로 막히므로, 회원 전용 API 에서는 null 이 아니다.
 */
public record LoginUser(Long id, String email, String passwordHash, String nickname, Role role) implements UserDetails {

    public static LoginUser from(User user) {
        return new LoginUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.getNickname(), user.getRole());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** 비밀번호 해시가 로그·세션 덤프에 찍히지 않게 */
    @Override
    public String toString() {
        return "LoginUser[id=" + id + ", role=" + role + "]";
    }
}
