package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.hazard.dto.EmpathyResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공감은 회원만 (SecurityConfig). 둘 다 멱등이라 200 + 현재 공감 수 */
@RestController
public class EmpathyController {

    private final EmpathyService empathyService;

    public EmpathyController(EmpathyService empathyService) {
        this.empathyService = empathyService;
    }

    @PostMapping("/api/hazards/{id}/empathy")
    public EmpathyResponse add(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) {
        return empathyService.add(user.id(), id);
    }

    @DeleteMapping("/api/hazards/{id}/empathy")
    public EmpathyResponse remove(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) {
        return empathyService.remove(user.id(), id);
    }
}
