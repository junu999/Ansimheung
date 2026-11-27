package com.slink.ansimheung.hazard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.Role;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.risk.RiskService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.test.json.JsonCompareMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 20m 중복 제보 묶기 (#25, DB 필요): GET /api/hazards/nearby · POST /api/reports mergeHazardId. 끝나면 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportMergeApiTest {

    /** 정왕초 후문 근처 (시범 지역 안). 위도 0.0001 ≈ 11m */
    private static final double LAT = 37.3413;
    private static final double LNG = 126.7436;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired UserRepository users;
    @Autowired RiskService riskService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    LoginUser member;
    LoginUser neighbor;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");   // bootRun 으로 들어간 시드 등 (롤백되므로 그대로 남음)
        member = LoginUser.from(users.save(User.create("merge-test@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
        neighbor = LoginUser.from(users.save(User.create("merge-test2@ansimheung.test", "{noop}x", "학부모2", Role.USER)));
    }

    /** 이웃이 먼저 제보한 지점 */
    private Hazard hazard(HazardType type, double lat) {
        Hazard h = Hazard.create(lat, LNG, type, TimeSlot.MORNING);
        h.addReport();
        riskService.refresh(h);
        hazards.save(h);
        reports.save(Report.create(h.getId(), neighbor.id(), lat, LNG, TimeSlot.MORNING, "먼저 올린 제보"));
        em.flush();
        return h;
    }

    private ResultActions nearby(String query, LoginUser as) throws Exception {
        return mockMvc.perform(get("/api/hazards/nearby?" + query).with(user(as)));
    }

    private ResultActions nearbyHere(HazardType type) throws Exception {
        return nearby("lat=" + LAT + "&lng=" + LNG + "&type=" + type, member).andExpect(status().isOk());
    }

    private ResultActions report(LoginUser as, Long mergeHazardId) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/reports");
        req.param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                .param("type", "ILLEGAL_PARKING").param("timeSlot", "NIGHT");
        if (mergeHazardId != null) req.param("mergeHazardId", String.valueOf(mergeHazardId));
        return mockMvc.perform(req.with(user(as)).with(csrf()));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) names.add(it.next());
        return names;
    }

    @Test
    void 후보는_20m_안_같은_유형_중_가장_가까운_1곳이고_mock과_같은_모양() throws Exception {
        hazard(HazardType.ILLEGAL_PARKING, LAT + 0.00015);              // 약 17m
        Hazard nearest = hazard(HazardType.ILLEGAL_PARKING, LAT + 0.0001);   // 약 11m

        String res = nearbyHere(HazardType.ILLEGAL_PARKING)
                .andExpect(jsonPath("$.candidate.id").value(nearest.getId()))
                .andExpect(jsonPath("$.candidate.distanceM").value(11))
                .andExpect(jsonPath("$.candidate.empathyCount").value(0))
                .andExpect(jsonPath("$.candidate.reportCount").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/static/mock/hazards-nearby.json"));
        assertThat(fieldNames(json.readTree(res).get("candidate"))).isEqualTo(fieldNames(expected.get("candidate")));
    }

    @Test
    void 다른_유형_20m_밖_개선완료_반려_숨김은_후보가_아니고_확인중은_후보() throws Exception {
        hazard(HazardType.SIDEWALK_GAP, LAT);
        hazard(HazardType.ILLEGAL_PARKING, LAT + 0.0003);              // 약 33m
        Hazard resolved = hazard(HazardType.ILLEGAL_PARKING, LAT);
        resolved.changeStatus(HazardStatus.CHECKING);
        resolved.changeStatus(HazardStatus.RESOLVED);
        hazard(HazardType.ILLEGAL_PARKING, LAT).changeStatus(HazardStatus.REJECTED);
        hazard(HazardType.ILLEGAL_PARKING, LAT).hide("중복 테스트");
        em.flush();

        nearbyHere(HazardType.ILLEGAL_PARKING).andExpect(content().json("{\"candidate\": null}", JsonCompareMode.STRICT));

        Hazard checking = hazard(HazardType.ILLEGAL_PARKING, LAT + 0.0001);
        checking.changeStatus(HazardStatus.CHECKING);
        em.flush();
        nearbyHere(HazardType.ILLEGAL_PARKING).andExpect(jsonPath("$.candidate.id").value(checking.getId()));
    }

    @Test
    void 같은_지점에_두_번_제보하면_마커_1개_제보_수_2() throws Exception {
        long hazardId = json.readTree(report(neighbor, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("hazardId").asLong();
        em.flush();

        report(member, hazardId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hazardId").value(hazardId))
                .andExpect(jsonPath("$.merged").value(true));
        em.flush();
        em.clear();

        assertThat(hazards.count()).isEqualTo(1);
        assertThat(hazards.findById(hazardId).orElseThrow().getReportCount()).isEqualTo(2);
        assertThat(reports.findByHazardIdAndDeletedFalseOrderByCreatedAtAsc(hazardId)).hasSize(2);
        mockMvc.perform(get("/api/hazards?bbox=126.737,37.336,126.749,37.345"))
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.reportCount").value(2));
    }

    @Test
    void 묶을_수_없으면_404_NOT_FOUND_409_STATE_CONFLICT_409_DUP_REPORT() throws Exception {
        report(member, 999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Hazard hidden = hazard(HazardType.ILLEGAL_PARKING, LAT);
        hidden.hide("중복 테스트");
        Hazard rejected = hazard(HazardType.ILLEGAL_PARKING, LAT);
        rejected.changeStatus(HazardStatus.REJECTED);
        Hazard otherType = hazard(HazardType.SIDEWALK_GAP, LAT);
        Hazard resolved = hazard(HazardType.ILLEGAL_PARKING, LAT);
        resolved.changeStatus(HazardStatus.CHECKING);
        resolved.changeStatus(HazardStatus.RESOLVED);
        Hazard far = hazard(HazardType.ILLEGAL_PARKING, LAT + 0.0003);
        em.flush();

        for (Hazard h : List.of(hidden, rejected)) {
            report(member, h.getId()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        for (Hazard h : List.of(otherType, resolved, far)) {
            report(member, h.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        }
        Hazard mine = hazard(HazardType.ILLEGAL_PARKING, LAT);
        report(neighbor, mine.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUP_REPORT"));

        em.flush();
        assertThat(reports.count()).isEqualTo(6);   // 이웃이 먼저 올린 6건뿐, 실패한 묶기는 저장 안 됨
    }

    @Test
    void nearby_검증과_권한() throws Exception {
        mockMvc.perform(get("/api/hazards/nearby?lat=" + LAT + "&lng=" + LNG + "&type=ILLEGAL_PARKING"))
                .andExpect(status().isUnauthorized());
        nearby("lng=" + LNG + "&type=ILLEGAL_PARKING", member)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.lat").value("위치를 선택해 주세요"));
        nearby("lat=" + LAT + "&lng=" + LNG, member)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.type").value("위험 유형을 선택해 주세요"));
        nearby("lat=" + LAT + "&lng=" + LNG + "&type=parking", member)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.type").value("값이 올바르지 않아요"));
        nearby("lat=37.3500&lng=" + LNG + "&type=ILLEGAL_PARKING", member)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OUT_OF_AREA"));
    }
}
