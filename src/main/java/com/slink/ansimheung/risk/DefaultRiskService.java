package com.slink.ansimheung.risk;

import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.Hazard;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

/** {@link RiskService} 실제 구현: 구간 배정({@link EdgeAssigner}) + 점수 공식({@link RiskCalculator}) */
@Service
public class DefaultRiskService implements RiskService {

    private final RiskCalculator calculator;
    private final EdgeAssigner edgeAssigner;
    private final double highThreshold;

    public DefaultRiskService(RiskCalculator calculator, EdgeAssigner edgeAssigner, AnsimheungProperties props) {
        this.calculator = calculator;
        this.edgeAssigner = edgeAssigner;
        this.highThreshold = props.marker().highThreshold();
    }

    @Override
    public void refresh(Hazard hazard) {
        hazard.assignEdge(edgeAssigner.nearestEdge(hazard.getLat(), hazard.getLng()).orElse(null));
        hazard.updateBaseScore(calculator.baseScore(hazard.getType(), hazard.getStatus(),
                hazard.getLastActivityAt(), hazard.getEmpathyCount(), OffsetDateTime.now()));
    }

    @Override
    public double scoreAt(Hazard hazard, Optional<TimeSlot> querySlot) {
        return hazard.getBaseScore() * calculator.timeWeight(hazard.getTimeSlot(), querySlot);
    }

    @Override
    public MarkerLevel levelOf(Hazard hazard, double score) {
        return MarkerLevel.of(hazard.getStatus(), score, highThreshold);
    }
}
