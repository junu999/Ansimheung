package com.slink.ansimheung.hazard;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 순수 단위 테스트 (DB·Spring 없음) — Hazard 도메인 규칙 */
class HazardTest {

    private Hazard newHazard() {
        return Hazard.create(37.3413, 126.7436, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
    }

    @Test
    void 새_지점은_접수_상태이고_좌표가_위도_경도로_읽힌다() {
        Hazard h = newHazard();
        assertThat(h.getStatus()).isEqualTo(HazardStatus.RECEIVED);
        assertThat(h.getLat()).isEqualTo(37.3413);
        assertThat(h.getLng()).isEqualTo(126.7436);
        assertThat(h.getLocation().getSRID()).isEqualTo(4326);
    }

    @Test
    void 접수_확인중_개선완료_순서로만_바뀌고_개선완료_시각이_기록된다() {
        Hazard h = newHazard();
        assertThat(h.changeStatus(HazardStatus.CHECKING)).isEqualTo(HazardStatus.RECEIVED);
        h.changeStatus(HazardStatus.RESOLVED);
        assertThat(h.getStatus()).isEqualTo(HazardStatus.RESOLVED);
        assertThat(h.getResolvedAt()).isNotNull();
    }

    @Test
    void 접수에서_개선완료로_바로_가면_409_STATE_CONFLICT() {
        Hazard h = newHazard();
        assertThatThrownBy(() -> h.changeStatus(HazardStatus.RESOLVED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.STATE_CONFLICT));
        assertThat(h.getStatus()).isEqualTo(HazardStatus.RECEIVED);
    }

    @Test
    void 카운트는_0_아래로_내려가지_않는다() {
        Hazard h = newHazard();
        h.addEmpathy();
        h.removeEmpathy();
        h.removeEmpathy();
        h.removeReport();
        assertThat(h.getEmpathyCount()).isZero();
        assertThat(h.getReportCount()).isZero();
    }

    @Test
    void 숨김과_해제() {
        Hazard h = newHazard();
        h.hide("허위 제보");
        assertThat(h.isHidden()).isTrue();
        assertThat(h.getHiddenReason()).isEqualTo("허위 제보");
        h.unhide();
        assertThat(h.isHidden()).isFalse();
        assertThat(h.getHiddenReason()).isNull();
    }
}
