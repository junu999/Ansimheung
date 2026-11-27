package com.slink.ansimheung.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/auth/signup 요청. 메시지는 docs/domain.md V-01 ~ V-05 그대로. 이메일·닉네임은 앞뒤 공백을 지운 뒤 검증 */
public record SignupRequest(
        @NotBlank(message = EMAIL) @Email(message = EMAIL) @Size(max = 100, message = EMAIL) String email,
        @NotNull(message = PASSWORD) @Size(min = 8, max = 64, message = PASSWORD)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = PASSWORD) String password,
        @NotNull(message = NICKNAME) @Pattern(regexp = "^[가-힣A-Za-z0-9]{2,12}$", message = NICKNAME) String nickname,
        @AssertTrue(message = AGREE) boolean ageConfirmed,
        @AssertTrue(message = AGREE) boolean privacyAgreed
) {
    static final String EMAIL = "이메일 형식이 아니에요";                                   // V-01
    static final String PASSWORD = "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요";      // V-02
    static final String NICKNAME = "닉네임은 한글·영문·숫자 2~12자예요";                      // V-04
    static final String AGREE = "만 14세 이상 확인과 개인정보 동의가 필요해요";                // V-05

    public SignupRequest {
        email = email == null ? null : email.strip();
        nickname = nickname == null ? null : nickname.strip();
    }
}
