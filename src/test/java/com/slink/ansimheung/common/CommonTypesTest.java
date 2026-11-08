package com.slink.ansimheung.common;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.geo.GeoUtils;
import com.slink.ansimheung.common.geo.PilotArea;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 공통 enum·좌표 도우미 단위 테스트 (DB 없이 실행) */
class CommonTypesTest {

    @Test
    void 유형_가중치_C01() {
        assertThat(HazardType.SIDEWALK_GAP.weight()).isEqualTo(2.0);
        assertThat(HazardType.ILLEGAL_PARKING.weight()).isEqualTo(1.8);
        assertThat(HazardType.POOR_LIGHTING.weight()).isEqualTo(1.2);
    }

    @Test
    void 시간대_구간_P07() {
        assertThat(TimeSlot.of(LocalTime.of(7, 0))).contains(TimeSlot.MORNING);
        assertThat(TimeSlot.of(LocalTime.of(8, 59))).contains(TimeSlot.MORNING);
        assertThat(TimeSlot.of(LocalTime.of(14, 0))).contains(TimeSlot.AFTERNOON);
        assertThat(TimeSlot.of(LocalTime.of(17, 59))).contains(TimeSlot.AFTERNOON);
        assertThat(TimeSlot.of(LocalTime.of(18, 0))).contains(TimeSlot.NIGHT);
        assertThat(TimeSlot.of(LocalTime.of(6, 59))).contains(TimeSlot.NIGHT);
        assertThat(TimeSlot.of(LocalTime.of(11, 0))).isEmpty();
        assertThat(TimeSlot.ALWAYS.isQueryable()).isFalse();
    }

    @Test
    void 처리상태_전이_규칙() {
        assertThat(HazardStatus.RECEIVED.allowedNext()).containsExactlyInAnyOrder(HazardStatus.CHECKING, HazardStatus.REJECTED);
        assertThat(HazardStatus.CHECKING.canTransitionTo(HazardStatus.RESOLVED)).isTrue();
        assertThat(HazardStatus.RECEIVED.canTransitionTo(HazardStatus.RESOLVED)).isFalse();
        assertThat(HazardStatus.RESOLVED.allowedNext()).isEmpty();
        assertThat(HazardStatus.RESOLVED.weight()).isEqualTo(0.3);
        assertThat(HazardStatus.CHECKING.isOpen()).isTrue();
    }

    @Test
    void 마커_색_C08() {
        assertThat(MarkerLevel.of(HazardStatus.RECEIVED, 3.0, 3.0)).isEqualTo(MarkerLevel.HIGH);
        assertThat(MarkerLevel.of(HazardStatus.RECEIVED, 2.9, 3.0)).isEqualTo(MarkerLevel.MEDIUM);
        assertThat(MarkerLevel.of(HazardStatus.RESOLVED, 9.0, 3.0)).isEqualTo(MarkerLevel.RESOLVED);
    }

    @Test
    void 하버사인_거리() {
        // 정왕초에서 경도 0.001도 동쪽 ≈ 89m
        assertThat(GeoUtils.distanceM(37.3408, 126.7430, 37.3408, 126.7440)).isCloseTo(88.5, within(1.0));
    }

    @Test
    void 시범_지역_500m_P05() {
        var area = new AnsimheungProperties.Area("정왕초등학교", "경기 시흥시 중심상가로 257", 37.3408, 126.7430, 500);
        var props = new AnsimheungProperties(area, null, null, null, null, null, null, null, null);
        PilotArea pilot = new PilotArea(props);

        assertThat(pilot.contains(37.3408, 126.7430)).isTrue();   // 학교
        assertThat(pilot.contains(37.3432, 126.7405)).isTrue();   // 약 350m
        assertThat(pilot.contains(37.4326, 126.7914)).isFalse();  // 옛 시범지역(신일초), 약 11km
        assertThat(pilot.contains(999, 999)).isFalse();

        assertThatThrownBy(() -> pilot.requireInside(37.4326, 126.7914))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.OUT_OF_AREA);
    }
}
