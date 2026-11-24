package com.slink.ansimheung.risk;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 위험점수 공식 (docs/domain.md 6장). DB·시계에 의존하지 않는 순수 계산 — 현재 시각은 호출하는 쪽이 넘긴다.
 * <pre>
 * baseScore = W(유형) × R(최근성) × E(공감) × S(처리)     ← DB hazards.base_score
 * 조회 점수 = baseScore × T(시간대)                        ← 조회할 때만
 * </pre>
 * 계수: W = {@link HazardType#weight()}, S = {@link HazardStatus#weight()}, R·E·T = application.yml ansimheung.risk.
 * 반올림하지 않는다 (응답 만들 때 소수 첫째 자리, C-13).
 */
@Component
public class RiskCalculator {

    private static final double MINUTES_PER_DAY = 24 * 60;

    private final AnsimheungProperties.Risk risk;

    @Autowired
    public RiskCalculator(AnsimheungProperties props) {
        this(props.risk());
    }

    /** 단위 테스트용 */
    public RiskCalculator(AnsimheungProperties.Risk risk) {
        this.risk = risk;
    }

    /** W×R×E×S. 반려면 S=0 이라 0 */
    public double baseScore(HazardType type, HazardStatus status, OffsetDateTime lastActivityAt,
                            int empathyCount, OffsetDateTime now) {
        return type.weight() * recency(lastActivityAt, now) * empathy(empathyCount) * status.weight();
    }

    /** C-02 R = max(min, max − 경과일/decay). 경과일은 마지막 제보·공감부터 (소수 포함), 미래 시각이면 0일 */
    public double recency(OffsetDateTime lastActivityAt, OffsetDateTime now) {
        double days = Math.max(0, Duration.between(lastActivityAt, now).toMinutes() / MINUTES_PER_DAY);
        return Math.max(risk.recencyMin(), risk.recencyMax() - days / risk.recencyDecayDays());
    }

    /** C-12 E = min(max, 1 + log10(공감 수 + 1)) */
    public double empathy(int empathyCount) {
        return Math.min(risk.empathyMax(), 1 + Math.log10(Math.max(0, empathyCount) + 1));
    }

    /**
     * C-04 T. 제보가 상시면 1.2 · 조회 시간대 = 제보 시간대면 1.5 · 그 외 1.0
     * @param querySlot 조회 시간대. 09–14시는 비어 있음 ({@link TimeSlot#of})
     */
    public double timeWeight(TimeSlot reportSlot, Optional<TimeSlot> querySlot) {
        if (reportSlot == TimeSlot.ALWAYS) {
            return risk.timeAlways();
        }
        return querySlot.filter(reportSlot::equals).isPresent() ? risk.timeMatch() : risk.timeOther();
    }
}
