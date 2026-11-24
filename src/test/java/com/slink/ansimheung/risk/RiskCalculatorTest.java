package com.slink.ansimheung.risk;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static com.slink.ansimheung.common.type.TimeSlot.AFTERNOON;
import static com.slink.ansimheung.common.type.TimeSlot.ALWAYS;
import static com.slink.ansimheung.common.type.TimeSlot.MORNING;
import static com.slink.ansimheung.common.type.TimeSlot.NIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 순수 단위 테스트 (Spring·DB 없음). 기대값은 docs/domain.md 6장 표 그대로 */
class RiskCalculatorTest {

    /** application.yml ansimheung.risk 와 같은 값 */
    private static final AnsimheungProperties.Risk RISK =
            new AnsimheungProperties.Risk(1.5, 0.5, 90, 2.0, 1.5, 1.2, 1.0);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-24T08:00:00+09:00");

    private final RiskCalculator calc = new RiskCalculator(RISK);

    /** C-13 소수 첫째 자리 반올림 비교 */
    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    // ---------- docs/domain.md 6장 예시: 후문 앞 불법주정차, 10일 전, 공감 3, 접수, 등교 ----------

    private double exampleBase(HazardStatus status) {
        return calc.baseScore(HazardType.ILLEGAL_PARKING, status, NOW.minusDays(10), 3, NOW);
    }

    @Test
    void 예시_각_계수() {
        assertThat(calc.recency(NOW.minusDays(10), NOW)).isCloseTo(1.39, within(0.005));
        assertThat(calc.empathy(3)).isCloseTo(1.60, within(0.005));
    }

    @Test
    void 예시_baseScore는_4점0() {
        assertThat(round1(exampleBase(HazardStatus.RECEIVED))).isEqualTo(4.0);
    }

    @Test
    void 예시_등교_시간_조회는_6점0() {
        double base = exampleBase(HazardStatus.RECEIVED);
        assertThat(round1(base * calc.timeWeight(MORNING, Optional.of(MORNING)))).isEqualTo(6.0);
    }

    @Test
    void 예시_하교_시간_조회는_4점0() {
        double base = exampleBase(HazardStatus.RECEIVED);
        assertThat(round1(base * calc.timeWeight(MORNING, Optional.of(AFTERNOON)))).isEqualTo(4.0);
    }

    @Test
    void 예시_개선완료_후_등교_조회는_1점8() {
        double base = exampleBase(HazardStatus.RESOLVED);
        assertThat(round1(base * calc.timeWeight(MORNING, Optional.of(MORNING)))).isEqualTo(1.8);
    }

    @Test
    void 내부_값은_반올림하지_않는다() {
        assertThat(exampleBase(HazardStatus.RECEIVED)).isNotEqualTo(4.0).isCloseTo(4.005, within(0.001));
    }

    // ---------- S: 처리상태 ----------

    @Test
    void 반려는_0점() {
        assertThat(exampleBase(HazardStatus.REJECTED)).isZero();
    }

    @Test
    void 확인중은_접수와_같은_점수() {
        assertThat(exampleBase(HazardStatus.CHECKING)).isEqualTo(exampleBase(HazardStatus.RECEIVED));
    }

    // ---------- R: 최근성 하한·상한 ----------

    @Test
    void 최근성_방금_활동이면_상한_1점5() {
        assertThat(calc.recency(NOW, NOW)).isEqualTo(1.5);
    }

    @Test
    void 최근성_미래_시각이어도_상한을_넘지_않는다() {
        assertThat(calc.recency(NOW.plusDays(3), NOW)).isEqualTo(1.5);
    }

    @Test
    void 최근성_90일이면_하한_0점5_그_뒤로도_0점5() {
        assertThat(calc.recency(NOW.minusDays(90), NOW)).isCloseTo(0.5, within(1e-9));
        assertThat(calc.recency(NOW.minusDays(400), NOW)).isEqualTo(0.5);
    }

    @Test
    void 최근성은_하루_안에서도_조금씩_줄어든다() {
        assertThat(calc.recency(NOW.minusHours(12), NOW)).isCloseTo(1.5 - 0.5 / 90, within(1e-9));
    }

    // ---------- E: 공감 하한·상한 ----------

    @Test
    void 공감_0이면_하한_1점0() {
        assertThat(calc.empathy(0)).isEqualTo(1.0);
    }

    @Test
    void 공감_9이면_상한_2점0_그_뒤로도_2점0() {
        assertThat(calc.empathy(9)).isCloseTo(2.0, within(1e-9));
        assertThat(calc.empathy(1000)).isEqualTo(2.0);
    }

    // ---------- W: 유형 ----------

    @Test
    void 유형_가중치가_그대로_곱해진다() {
        for (HazardType t : HazardType.values()) {
            assertThat(calc.baseScore(t, HazardStatus.RECEIVED, NOW, 0, NOW)).isEqualTo(t.weight() * 1.5);
        }
    }

    @Test
    void 점수_범위는_약_0점18에서_9점0() {
        double max = calc.baseScore(HazardType.SIDEWALK_GAP, HazardStatus.RECEIVED, NOW, 1000, NOW)
                * calc.timeWeight(MORNING, Optional.of(MORNING));
        double min = calc.baseScore(HazardType.POOR_LIGHTING, HazardStatus.RESOLVED, NOW.minusDays(400), 0, NOW)
                * calc.timeWeight(MORNING, Optional.of(NIGHT));
        assertThat(max).isCloseTo(9.0, within(1e-9));
        assertThat(min).isCloseTo(0.18, within(1e-9));
    }

    // ---------- T: 시간대 ----------

    @Test
    void 시간대_일치는_1점5_불일치는_1점0() {
        assertThat(calc.timeWeight(NIGHT, Optional.of(NIGHT))).isEqualTo(1.5);
        assertThat(calc.timeWeight(NIGHT, Optional.of(MORNING))).isEqualTo(1.0);
    }

    @Test
    void 상시_제보는_어느_시간대든_1점2() {
        for (TimeSlot q : new TimeSlot[]{MORNING, AFTERNOON, NIGHT}) {
            assertThat(calc.timeWeight(ALWAYS, Optional.of(q))).isEqualTo(1.2);
        }
    }

    @Test
    void 조회_시간대가_없는_09_14시는_상시만_1점2_나머지_1점0() {
        assertThat(calc.timeWeight(ALWAYS, Optional.empty())).isEqualTo(1.2);
        assertThat(calc.timeWeight(MORNING, Optional.empty())).isEqualTo(1.0);
    }
}
