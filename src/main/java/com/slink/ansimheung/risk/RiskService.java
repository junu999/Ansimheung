package com.slink.ansimheung.risk;

import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.hazard.Hazard;

import java.util.Optional;

/**
 * 위험도 모듈의 계약. 다른 모듈(제보 #11, 공감 #26, 관리자 #14, 조회 #12, 경로 #13)은 이 인터페이스만 부른다.
 * 구현: {@link DefaultRiskService}. 메서드 모양은 바꾸지 말 것 (다른 모듈이 호출).
 * 공식은 docs/domain.md 6·7장.
 */
public interface RiskService {

    /**
     * 구간 배정(C-09, 가장 가까운 edge 1개, 25m 이내) + baseScore = W×R×E×S 재계산 후 hazard 에 반영.
     * 제보 등록 · 공감 추가/취소 · 상태 변경 직후, 같은 트랜잭션 안에서 호출한다 (저장은 JPA 변경 감지).
     */
    void refresh(Hazard hazard);

    /**
     * 조회 시점 점수 = baseScore × T(C-04). 반올림하지 않은 값 (응답 만들 때 소수 첫째 자리 반올림).
     * @param querySlot 조회 시간대. 비어 있으면(09–14시) 상시 제보만 1.2, 나머지 1.0
     */
    double scoreAt(Hazard hazard, Optional<TimeSlot> querySlot);

    /** 마커 색 (C-08 기준값, 개선완료는 RESOLVED) */
    MarkerLevel levelOf(Hazard hazard, double score);
}
