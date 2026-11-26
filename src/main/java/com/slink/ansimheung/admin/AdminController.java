package com.slink.ansimheung.admin;

import com.slink.ansimheung.admin.dto.AdminHazardDetail;
import com.slink.ansimheung.admin.dto.AdminHazardPage;
import com.slink.ansimheung.admin.dto.StatusChangeRequest;
import com.slink.ansimheung.admin.dto.StatusChangeResponse;
import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/** /api/admin/** — ADMIN 만 (SecurityConfig). 점수는 조회 시각(Asia/Seoul)의 시간대를 곱한 값 */
@RestController
public class AdminController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminService adminService;
    private final AnsimheungProperties props;

    public AdminController(AdminService adminService, AnsimheungProperties props) {
        this.adminService = adminService;
        this.props = props;
    }

    /** sort 생략 = 최신순, hidden 생략 = 숨김 포함 전체, size 는 C-14 기본 20 · 최대 100 */
    @GetMapping("/api/admin/hazards")
    public AdminHazardPage list(@RequestParam(required = false) HazardStatus status,
                                @RequestParam(required = false) Boolean hidden,
                                @RequestParam(required = false)
                                @Pattern(regexp = AdminService.SORT_OLDEST + "|" + AdminService.SORT_SCORE,
                                        message = "정렬은 OLDEST 또는 SCORE 예요") String sort,
                                @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이에요") int page,
                                @RequestParam(required = false) @Min(value = 1, message = "size 는 1 이상이에요") Integer size) {
        int pageSize = Math.min(size == null ? props.paging().defaultSize() : size, props.paging().maxSize());
        return adminService.list(status, hidden, sort, page, pageSize, nowSlot());
    }

    @GetMapping("/api/admin/hazards/{id}")
    public AdminHazardDetail detail(@PathVariable Long id) {
        return adminService.detail(id, nowSlot());
    }

    @PatchMapping("/api/admin/hazards/{id}/status")
    public StatusChangeResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req,
                                             @AuthenticationPrincipal LoginUser admin) {
        return adminService.changeStatus(id, req.status(), req.memo(), admin.id());
    }

    private static Optional<TimeSlot> nowSlot() {
        return TimeSlot.of(LocalTime.now(SEOUL));
    }
}
