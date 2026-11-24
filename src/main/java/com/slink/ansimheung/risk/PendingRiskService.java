package com.slink.ansimheung.risk;

import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.Hazard;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 임시 구현 — #10(위험점수 엔진)이 머지되기 전까지 다른 기능이 막히지 않게 하는 껍데기.
 * refresh 는 아무것도 하지 않고, scoreAt 은 baseScore 를 그대로 돌려준다.
 * TODO(#10): 이 클래스를 지우고 RiskService 실제 구현(RiskCalculator + 구간 배정)으로 교체.
 */
@Service
public class PendingRiskService implements RiskService {

    private final AnsimheungProperties props;

    public PendingRiskService(AnsimheungProperties props) {
        this.props = props;
    }

    @Override
    public void refresh(Hazard hazard) {
        // #10 전까지는 계산하지 않음
    }

    @Override
    public double scoreAt(Hazard hazard, Optional<TimeSlot> querySlot) {
        return hazard.getBaseScore();
    }

    @Override
    public MarkerLevel levelOf(Hazard hazard, double score) {
        return MarkerLevel.of(hazard.getStatus(), score, props.marker().highThreshold());
    }
}
