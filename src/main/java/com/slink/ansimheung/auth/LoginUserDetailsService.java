package com.slink.ansimheung.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일로 회원을 찾아 {@link LoginUser} 로 바꾼다.
 * Spring Security 가 이 빈 + PasswordEncoder 로 AuthenticationManager 를 자동 구성한다 (SecurityConfig).
 * 로그인 API(#9)는 AuthenticationManager.authenticate(...) 만 부르면 된다.
 */
@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public LoginUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(User.normalizeEmail(email))
                .map(LoginUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("no user"));
    }
}
