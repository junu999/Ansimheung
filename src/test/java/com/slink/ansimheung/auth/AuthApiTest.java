package com.slink.ansimheung.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slink.ansimheung.common.type.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원가입 · 로그인(JSON 세션) · 로그아웃 · 내 정보 (#9, DB 필요). 끝나면 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    private static final String EMAIL = "auth-test@ansimheung.test";
    private static final String PASSWORD = "abcd1234";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String signupBody(String email, String password, String nickname, boolean age, boolean privacy) {
        return """
                {"email": %s, "password": %s, "nickname": %s, "ageConfirmed": %s, "privacyAgreed": %s}
                """.formatted(quote(email), quote(password), quote(nickname), age, privacy);
    }

    private static String quote(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private User member() {
        return users.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "학부모1", Role.USER));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) names.add(it.next());
        return names;
    }

    @Test
    void 가입하면_201과_id_이메일은_공백을_지우고_소문자로_저장_비밀번호는_BCrypt() throws Exception {
        String res = postJson("/api/auth/signup", signupBody("  New-User@Ansimheung.TEST ", PASSWORD, " 새이웃 ", true, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();

        User saved = users.findById(json.readTree(res).get("id").asLong()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("new-user@ansimheung.test");
        assertThat(saved.getNickname()).isEqualTo("새이웃");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void 가입_검증_메시지는_V01_V05_문구_그대로() throws Exception {
        record Case(String body, String field, String message) {
        }
        List<Case> cases = List.of(
                new Case(signupBody("not-an-email", PASSWORD, "새이웃", true, true), "email", "이메일 형식이 아니에요"),
                new Case(signupBody("", PASSWORD, "새이웃", true, true), "email", "이메일 형식이 아니에요"),
                new Case(signupBody(EMAIL, "abcdefgh", "새이웃", true, true), "password", "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요"),
                new Case(signupBody(EMAIL, "abc123", "새이웃", true, true), "password", "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요"),
                new Case(signupBody(EMAIL, PASSWORD, "이", true, true), "nickname", "닉네임은 한글·영문·숫자 2~12자예요"),
                new Case(signupBody(EMAIL, PASSWORD, "새 이웃!", true, true), "nickname", "닉네임은 한글·영문·숫자 2~12자예요"),
                new Case(signupBody(EMAIL, PASSWORD, "새이웃", false, true), "ageConfirmed", "만 14세 이상 확인과 개인정보 동의가 필요해요"),
                new Case(signupBody(EMAIL, PASSWORD, "새이웃", true, false), "privacyAgreed", "만 14세 이상 확인과 개인정보 동의가 필요해요"));
        for (Case c : cases) {
            postJson("/api/auth/signup", c.body())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.fields." + c.field()).value(c.message()));
        }
    }

    @Test
    void 이미_가입된_이메일은_대소문자가_달라도_409_EMAIL_DUP() throws Exception {
        member();
        postJson("/api/auth/signup", signupBody(EMAIL.toUpperCase(), PASSWORD, "새이웃", true, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUP"));
    }

    @Test
    void 로그인하면_me_json_모양을_주고_세션_ID를_바꾸고_같은_세션으로_내_정보를_본다() throws Exception {
        User user = member();
        MockHttpSession before = new MockHttpSession();
        String beforeId = before.getId();

        MvcResult login = mockMvc.perform(post("/api/auth/login").with(csrf()).session(before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.nickname").value("학부모1"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/static/mock/me.json"));
        assertThat(fieldNames(json.readTree(login.getResponse().getContentAsString()))).isEqualTo(fieldNames(expected));

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(beforeId);   // 세션 고정 방지

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void 틀린_비밀번호와_없는_이메일은_같은_401_AUTH_FAIL() throws Exception {
        member();
        postJson("/api/auth/login", "{\"email\": \"" + EMAIL + "\", \"password\": \"wrong1234\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAIL"));
        postJson("/api/auth/login", "{\"email\": \"nobody@ansimheung.test\", \"password\": \"" + PASSWORD + "\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAIL"));
        postJson("/api/auth/login", "{\"email\": \"\", \"password\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("이메일을 입력해 주세요"));
    }

    @Test
    void 로그아웃하면_204이고_세션이_무효화된다() throws Exception {
        member();
        MockHttpSession session = (MockHttpSession) postJson("/api/auth/login",
                "{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}")
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
