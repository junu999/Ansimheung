package com.slink.ansimheung.auth;

import com.slink.ansimheung.common.type.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Locale;

/** 회원 (users). 비밀번호는 BCrypt 해시만 저장. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 12)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(name = "age_confirmed", nullable = false)
    private boolean ageConfirmed;

    @Column(name = "privacy_agreed_at", nullable = false)
    private OffsetDateTime privacyAgreedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected User() {
    }

    /**
     * 회원 생성. 검증(V-01~V-05)은 호출하는 쪽(DTO @Valid)에서 끝낸 뒤 부른다.
     * @param passwordHash 이미 PasswordEncoder 로 암호화한 값
     */
    public static User create(String email, String passwordHash, String nickname, Role role) {
        User u = new User();
        u.email = normalizeEmail(email);
        u.passwordHash = passwordHash;
        u.nickname = nickname;
        u.role = role;
        u.ageConfirmed = true; // P-10: 만 14세 이상만 가입 가능 (DB CHECK 제약)
        OffsetDateTime now = OffsetDateTime.now();
        u.privacyAgreedAt = now;
        u.createdAt = now;
        return u;
    }

    /** V-01: 앞뒤 공백 제거 + 소문자 */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAgeConfirmed() {
        return ageConfirmed;
    }

    public OffsetDateTime getPrivacyAgreedAt() {
        return privacyAgreedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
