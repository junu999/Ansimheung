package com.slink.ansimheung.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        var response = mockMvc.perform(get("/api/health")).andReturn().getResponse();
        StringBuilder dump = new StringBuilder();
        response.getHeaderNames().forEach(n -> dump.append(n).append('=').append(response.getHeaders(n)).append("; "));
        for (var c : response.getCookies()) dump.append("cookie:").append(c.getName()).append("; ");
        org.assertj.core.api.Assertions.assertThat(response.getCookie("XSRF-TOKEN")).as("DEBUG " + dump).isNotNull();
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
}
