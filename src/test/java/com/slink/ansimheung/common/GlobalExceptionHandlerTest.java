package com.slink.ansimheung.common;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오류가 어디서 나든 {code, message, fields} 모양으로 나오는지 확인.
 * 아래 TestController 는 이 테스트에서만 @Import 로 등록 (실제 서버에는 없음)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void ApiException은_코드와_상태와_메시지를_그대로() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMIT"))
                .andExpect(jsonPath("$.message").value("오늘은 더 제보할 수 없어요 (하루 10건)"))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    void Valid_실패는_VALIDATION과_칸별_메시지() throws Exception {
        mockMvc.perform(post("/test/body").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fields.nickname").value("닉네임은 한글·영문·숫자 2~12자예요"));
    }

    @Test
    void 없는_enum_값은_VALIDATION() throws Exception {
        mockMvc.perform(get("/test/enum").param("type", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fields.type").exists());
    }

    @Test
    void 필수_파라미터_누락은_VALIDATION() throws Exception {
        mockMvc.perform(get("/test/enum"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.type").value("필수 값이에요"));
    }

    @Test
    void 잘못된_JSON은_VALIDATION() throws Exception {
        mockMvc.perform(post("/test/body").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void 없는_주소는_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 예상_못한_오류는_INTERNAL이고_내부_메시지를_숨긴다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL.defaultMessage()));
    }

    @Test
    void CSRF_없는_POST는_FORBIDDEN_JSON() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("보안 토큰이 없거나 만료됐어요. 새로고침 후 다시 시도해 주세요"));
    }

    record Body(@NotBlank(message = "닉네임은 한글·영문·숫자 2~12자예요")
                @Size(min = 2, max = 12, message = "닉네임은 한글·영문·숫자 2~12자예요") String nickname) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/api-exception")
        void apiException() {
            throw new ApiException(ErrorCode.LIMIT, "오늘은 더 제보할 수 없어요 (하루 10건)");
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/enum")
        String enumParam(@RequestParam HazardType type) {
            return type.name();
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("DB 비밀번호가 틀림 같은 내부 정보");
        }
    }
}
