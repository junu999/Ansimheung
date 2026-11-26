package com.slink.ansimheung.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 설정이 프론트(static/js/api.js)와 맞물리는지 확인.
 * 로컬: docker compose up -d 후 실행 / CI: PostGIS 서비스 컨테이너로 실행
 */
@SpringBootTest
@AutoConfigureMockMvc
// 다른 테스트의 with(csrf())가 공유 컨텍스트의 CSRF 저장소를 테스트용(세션)으로 바꿔 두면
// 아래 XSRF-TOKEN 쿠키 검사가 실행 순서에 따라 실패한다 → 이 클래스는 항상 새 컨텍스트로
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void health_API는_로그인_없이_열리고_PostGIS_버전을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("ok"))
                .andExpect(jsonPath("$.postgis").isNotEmpty());
    }

    @Test
    void GET_요청에_XSRF_TOKEN_쿠키가_내려간다() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void CSRF_토큰_없는_POST는_403() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRF_토큰_있는_POST는_보안을_통과한다() throws Exception {
        // /api/health 는 GET 전용이라 405 — 403 이 아니면 CSRF 검사를 통과했다는 뜻
        mockMvc.perform(post("/api/health").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------- 권한 규칙 (docs/api.md "권한" 열) ----------
    // 아직 컨트롤러가 없는 API 는 보안을 통과하면 404 가 난다 → "401/403 이 아니다"로 공개 여부를 확인

    private static ResultMatcher notBlockedBySecurity() {
        return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void 관리자_API_비로그인은_401_공통_오류_형식() throws Exception {
        mockMvc.perform(get("/api/admin/hazards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 관리자_API_일반_회원은_403() throws Exception {
        mockMvc.perform(get("/api/admin/hazards").with(user("u").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자_API_관리자는_통과() throws Exception {
        mockMvc.perform(get("/api/admin/hazards").with(user("a").roles("ADMIN")))
                .andExpect(notBlockedBySecurity());
    }

    @Test
    void 회원_전용_API는_비로그인_401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hazards/nearby").param("lat", "37.34").param("lng", "126.74").param("type", "SPEEDING"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reports").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/hazards/1/empathy").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/hazards/1/empathy").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/reports/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_전용_API는_로그인하면_통과() throws Exception {
        mockMvc.perform(post("/api/reports").with(csrf()).with(user("u").roles("USER")))
                .andExpect(notBlockedBySecurity());
        mockMvc.perform(post("/api/hazards/1/empathy").with(csrf()).with(user("u").roles("USER")))
                .andExpect(notBlockedBySecurity());
    }

    @Test
    void 공개_API와_화면은_비로그인도_통과() throws Exception {
        mockMvc.perform(get("/api/hazards").param("bbox", "126.737,37.336,126.749,37.345")).andExpect(notBlockedBySecurity());
        mockMvc.perform(get("/api/hazards/1")).andExpect(notBlockedBySecurity());
        mockMvc.perform(post("/api/routes").with(csrf())).andExpect(notBlockedBySecurity());
        mockMvc.perform(post("/api/auth/login").with(csrf())).andExpect(notBlockedBySecurity());
        mockMvc.perform(get("/api/config")).andExpect(status().isOk());
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }
}
