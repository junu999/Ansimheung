package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.dto.EmpathyResponse;
import com.slink.ansimheung.risk.RiskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 공감 추가·취소 (#26, P-03). 둘 다 멱등: 이미 공감했으면 추가해도, 안 했으면 취소해도 그대로.
 * 바뀔 때만 empathy_count 갱신 + 점수 재계산 (같은 트랜잭션)
 */
@Service
public class EmpathyService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HazardRepository hazards;
    private final EmpathyRepository empathies;
    private final ReportRepository reports;
    private final RiskService riskService;
    private final AnsimheungProperties props;

    public EmpathyService(HazardRepository hazards, EmpathyRepository empathies, ReportRepository reports,
                          RiskService riskService, AnsimheungProperties props) {
        this.hazards = hazards;
        this.empathies = empathies;
        this.reports = reports;
        this.riskService = riskService;
        this.props = props;
    }

    /** 내 제보 지점 → OWN_HAZARD, 개선완료 → STATE_CONFLICT, 하루 한도(C-06) → LIMIT */
    @Transactional
    public EmpathyResponse add(Long userId, Long hazardId) {
        Hazard hazard = visibleHazard(hazardId);
        EmpathyId id = new EmpathyId(userId, hazardId);
        if (empathies.existsById(id)) {
            return new EmpathyResponse(hazard.getEmpathyCount(), true);
        }
        if (reports.existsByHazardIdAndUserIdAndDeletedFalse(hazardId, userId)) {
            throw new ApiException(ErrorCode.OWN_HAZARD);
        }
        if (!hazard.getStatus().isOpen()) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        OffsetDateTime todayStart = LocalDate.now(SEOUL).atStartOfDay(SEOUL).toOffsetDateTime();
        if (empathies.countByIdUserIdAndCreatedAtGreaterThanEqual(userId, todayStart) >= props.limits().empathiesPerDay()) {
            throw new ApiException(ErrorCode.LIMIT);
        }
        empathies.save(Empathy.of(userId, hazardId));
        hazard.addEmpathy();
        riskService.refresh(hazard);
        return new EmpathyResponse(hazard.getEmpathyCount(), true);
    }

    /** 상태와 관계없이 취소 가능 */
    @Transactional
    public EmpathyResponse remove(Long userId, Long hazardId) {
        Hazard hazard = visibleHazard(hazardId);
        EmpathyId id = new EmpathyId(userId, hazardId);
        if (empathies.existsById(id)) {
            empathies.deleteById(id);
            hazard.removeEmpathy();
            riskService.refresh(hazard);
        }
        return new EmpathyResponse(hazard.getEmpathyCount(), false);
    }

    /** 없음·숨김·반려 → NOT_FOUND (공개 상세와 같은 기준) */
    private Hazard visibleHazard(Long hazardId) {
        return hazards.findById(hazardId)
                .filter(h -> !h.isHidden() && h.getStatus() != HazardStatus.REJECTED)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
