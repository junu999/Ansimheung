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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/hazards · /api/hazards/{id} (#12, DB 필요). 지점은 테스트마다 비우고 넣은 뒤 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HazardApiTest {

    /** 정왕초 반경 500m 를 덮는 범위 (서,남,동,북) */
    private static final String PILOT_BBOX = "126.737,37.336,126.749,37.345";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired PhotoRepository photos;
    @Autowired StatusHistoryRepository histories;
    @Autowired EmpathyRepository empathies;
    @Autowired UserRepository users;
    @Autowired RiskService riskService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    LoginUser member;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");   // bootRun 으로 들어간 시드 등 (롤백되므로 그대로 남음)
        member = LoginUser.from(users.save(User.create("hazard-test@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
    }

    private Hazard hazard(HazardType type, TimeSlot slot, double lat, double lng) {
        Hazard h = Hazard.create(lat, lng, type, slot);
        h.addReport();
        riskService.refresh(h);
        hazards.save(h);
        histories.save(StatusHistory.of(h.getId(), null, HazardStatus.RECEIVED, null, null));
        return h;
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(String query) throws Exception {
        return body(mockMvc.perform(get("/api/hazards?bbox=" + PILOT_BBOX + query)));
    }

    private static List<Long> ids(JsonNode fc) {
        List<Long> ids = new ArrayList<>();
        fc.get("features").forEach(f -> ids.add(f.get("properties").get("id").asLong()));
        return ids;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) names.add(it.next());
        return names;
    }

    private JsonNode mock(String name) throws Exception {
        return json.readTree(getClass().getResourceAsStream("/static/mock/" + name));
    }

    @Test
    void 목록은_mock_hazards_json과_같은_GeoJSON이고_좌표는_경도_위도_순서() throws Exception {
        Hazard h = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.MORNING, 37.3413, 126.7436);
        em.flush();

        JsonNode fc = list("&timeSlot=MORNING");
        JsonNode expected = mock("hazards.json");
        assertThat(fieldNames(fc)).isEqualTo(fieldNames(expected));
        JsonNode f = fc.get("features").get(0);
        JsonNode e = expected.get("features").get(0);
        assertThat(fieldNames(f)).isEqualTo(fieldNames(e));
        assertThat(fieldNames(f.get("properties"))).isEqualTo(fieldNames(e.get("properties")));
        assertThat(fc.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(f.get("geometry").get("type").asText()).isEqualTo("Point");
        assertThat(f.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(126.7436);   // [경도, 위도]
        assertThat(f.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(37.3413);
        assertThat(f.get("properties").get("id").asLong()).isEqualTo(h.getId());
    }

    @Test
    void 점수는_조회_시간대를_곱하고_level은_기준값_3_5로_정한다() throws Exception {
        hazard(HazardType.ILLEGAL_PARKING, TimeSlot.MORNING, 37.3413, 126.7436);   // base 1.8 × 1.5 = 2.7
        em.flush();

        JsonNode morning = list("&timeSlot=MORNING").get("features").get(0).get("properties");
        assertThat(morning.get("score").asDouble()).isEqualTo(4.1);             // 2.7 × 1.5 = 4.05
        assertThat(morning.get("level").asText()).isEqualTo("HIGH");
        JsonNode night = list("&timeSlot=NIGHT").get("features").get(0).get("properties");
        assertThat(night.get("score").asDouble()).isEqualTo(2.7);
        assertThat(night.get("level").asText()).isEqualTo("MEDIUM");
    }

    @Test
    void bbox_밖_숨김_반려_개선완료_30일_지난_지점은_빠지고_types로_거른다() throws Exception {
        Hazard parking = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.ALWAYS, 37.3413, 126.7436);
        Hazard speeding = hazard(HazardType.SPEEDING, TimeSlot.ALWAYS, 37.3405, 126.7440);
        Hazard recentResolved = hazard(HazardType.POOR_LIGHTING, TimeSlot.ALWAYS, 37.3400, 126.7425);
        recentResolved.changeStatus(HazardStatus.CHECKING);
        recentResolved.changeStatus(HazardStatus.RESOLVED);
        Hazard oldResolved = hazard(HazardType.CONSTRUCTION, TimeSlot.ALWAYS, 37.3398, 126.7432);
        oldResolved.changeStatus(HazardStatus.CHECKING);
        oldResolved.changeStatus(HazardStatus.RESOLVED);
        Hazard hidden = hazard(HazardType.SIDEWALK_GAP, TimeSlot.ALWAYS, 37.3410, 126.7420);
        hidden.hide("중복 제보");
        Hazard rejected = hazard(HazardType.SIDEWALK_GAP, TimeSlot.ALWAYS, 37.3415, 126.7428);
        rejected.changeStatus(HazardStatus.REJECTED);
        hazard(HazardType.SPEEDING, TimeSlot.ALWAYS, 37.3500, 126.7600);       // bbox 밖
        em.flush();
        jdbc.update("UPDATE hazards SET resolved_at = now() - interval '31 days' WHERE id = ?", oldResolved.getId());
        em.clear();

        assertThat(ids(list(""))).containsExactlyInAnyOrder(parking.getId(), speeding.getId(), recentResolved.getId());
        assertThat(ids(list("&types=SPEEDING,POOR_LIGHTING"))).containsExactlyInAnyOrder(speeding.getId(), recentResolved.getId());
        JsonNode resolved = list("&types=POOR_LIGHTING").get("features").get(0).get("properties");
        assertThat(resolved.get("level").asText()).isEqualTo("RESOLVED");
    }

    @Test
    void 잘못된_bbox와_timeSlot_ALWAYS는_400() throws Exception {
        for (String bbox : List.of("126.737,37.336,126.749", "a,b,c,d", "126.749,37.336,126.737,37.345", "126.737,37.345,126.749,37.336")) {
            mockMvc.perform(get("/api/hazards").param("bbox", bbox))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.bbox").value("지도 범위가 올바르지 않아요"));
        }
        mockMvc.perform(get("/api/hazards")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        mockMvc.perform(get("/api/hazards").param("bbox", PILOT_BBOX).param("timeSlot", "ALWAYS"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.timeSlot").value("시간대를 선택해 주세요"));
        mockMvc.perform(get("/api/hazards").param("bbox", PILOT_BBOX).param("types", "parking"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void 상세는_mock과_같은_모양이고_작성자는_없고_승인된_사진만_보인다() throws Exception {
        Hazard h = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.MORNING, 37.3413, 126.7436);
        Report mine = reports.save(Report.create(h.getId(), member.id(), 37.3413, 126.7436, TimeSlot.MORNING, "후문 앞 주차"));
        Report anon = reports.save(Report.create(h.getId(), null, 37.3413, 126.7436, TimeSlot.MORNING, null));
        Photo approved = Photo.create(mine.getId(), "uploads/a.jpg", "image/jpeg");
        approved.setApproved(true);
        photos.save(approved);
        photos.save(Photo.create(anon.getId(), "uploads/b.jpg", "image/jpeg"));   // 미승인
        em.flush();

        JsonNode d = body(mockMvc.perform(get("/api/hazards/" + h.getId()).param("timeSlot", "MORNING")));
        JsonNode expected = mock("hazard-detail.json");
        assertThat(fieldNames(d)).isEqualTo(fieldNames(expected));
        assertThat(fieldNames(d.get("reports").get(0))).isEqualTo(fieldNames(expected.get("reports").get(0)));
        assertThat(fieldNames(d.get("history").get(0))).isEqualTo(fieldNames(expected.get("history").get(0)));
        assertThat(d.get("reports")).hasSize(2);
        assertThat(d.get("reports").get(0).has("authorNickname")).isFalse();      // 작성자 노출 금지 (관리자 화면만)
        assertThat(d.get("photos")).hasSize(1);
        assertThat(d.get("photos").get(0).get("id").asLong()).isEqualTo(approved.getId());
        assertThat(d.get("score").asDouble()).isEqualTo(4.1);
        assertThat(d.get("myEmpathy").asBoolean()).isFalse();
        assertThat(d.get("myReported").asBoolean()).isFalse();                   // 비로그인
    }

    @Test
    void 로그인하면_myEmpathy_myReported를_계산하고_숨김_반려는_404() throws Exception {
        Hazard reported = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.ALWAYS, 37.3413, 126.7436);
        reports.save(Report.create(reported.getId(), member.id(), 37.3413, 126.7436, TimeSlot.ALWAYS, null));
        Hazard liked = hazard(HazardType.SPEEDING, TimeSlot.ALWAYS, 37.3405, 126.7440);
        empathies.save(Empathy.of(member.id(), liked.getId()));
        Hazard hidden = hazard(HazardType.SIDEWALK_GAP, TimeSlot.ALWAYS, 37.3410, 126.7420);
        hidden.hide("중복 제보");
        Hazard rejected = hazard(HazardType.POOR_LIGHTING, TimeSlot.ALWAYS, 37.3415, 126.7428);
        rejected.changeStatus(HazardStatus.REJECTED);
        em.flush();

        JsonNode a = body(mockMvc.perform(get("/api/hazards/" + reported.getId()).with(user(member))));
        assertThat(a.get("myReported").asBoolean()).isTrue();
        assertThat(a.get("myEmpathy").asBoolean()).isFalse();
        JsonNode b = body(mockMvc.perform(get("/api/hazards/" + liked.getId()).with(user(member))));
        assertThat(b.get("myEmpathy").asBoolean()).isTrue();
        assertThat(b.get("myReported").asBoolean()).isFalse();

        for (Long id : List.of(hidden.getId(), rejected.getId(), 999999L)) {
            mockMvc.perform(get("/api/hazards/" + id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }
}
