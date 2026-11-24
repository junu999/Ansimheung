package com.slink.ansimheung.auth;

import com.slink.ansimheung.common.type.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 로그인 API(#9)가 쓸 AuthenticationManager 배선 확인 */
@SpringBootTest
@Transactional
class LoginUserDetailsServiceTest {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthenticationManager authenticationManager;

    @Test
    void 이메일_대소문자와_상관없이_로그인되고_LoginUser가_나온다() {
        users.save(User.create("admin@ansimheung.test", passwordEncoder.encode("password123"), "관리자", Role.ADMIN));

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("Admin@Ansimheung.TEST", "password123"));

        LoginUser me = (LoginUser) auth.getPrincipal();
        assertThat(me.isAdmin()).isTrue();
        assertThat(me.nickname()).isEqualTo("관리자");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(me.toString()).doesNotContain("$2"); // 해시가 로그에 안 찍힘
    }

    @Test
    void 비밀번호가_틀리면_BadCredentialsException() {
        users.save(User.create("user@ansimheung.test", passwordEncoder.encode("password123"), "주민", Role.USER));

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("user@ansimheung.test", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void 없는_이메일도_BadCredentialsException_어느_쪽이_틀렸는지_알려주지_않음() {
        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("nobody@ansimheung.test", "password123")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
