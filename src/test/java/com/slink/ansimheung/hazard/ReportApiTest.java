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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/reports 새 지점 제보 (#11, DB 필요). 지점은 테스트마다 비우고 넣은 뒤 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

    /** 정왕초 후문 근처 (시범 지역 안) */
    private static final double LAT = 37.3413;
    private static final double LNG = 126.7436;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired StatusHistoryRepository histories;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    LoginUser member;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");   // bootRun 으로 들어간 시드 등 (롤백되므로 그대로 남음)
        member = LoginUser.from(users.save(User.create("report-test@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
    }

    private static MockMultipartHttpServletRequestBuilder form(Map<String, String> fields) {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/reports");
        fields.forEach(req::param);
        return req;
    }

    private static Map<String, String> valid() {
        return Map.of("lat", String.valueOf(LAT), "lng", String.valueOf(LNG),
                "type", "ILLEGAL_PARKING", "timeSlot", "MORNING", "description", "  후문 앞 주차\n아이들이 안 보여요  ");
    }

    private ResultActions post(Map<String, String> fields) throws Exception {
        return mockMvc.perform(form(fields).with(user(member)).with(csrf()));
    }

    private static Map<String, String> with(String key, String value) {
        Map<String, String> m = new HashMap<>(valid());
        if (value == null) m.remove(key); else m.put(key, value);
        return m;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) names.add(it.next());
        return names;
    }

    @Test
    void 등록하면_201_mock과_같은_모양이고_접수_지점_제보_이력이_저장된다() throws Exception {
        String res = post(valid())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merged").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(res);
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/static/mock/reports-created.json"));
        assertThat(fieldNames(body)).isEqualTo(fieldNames(expected));
        em.flush();
        em.clear();

        Hazard h = hazards.findById(body.get("hazardId").asLong()).orElseThrow();
        assertThat(h.getStatus()).isEqualTo(HazardStatus.RECEIVED);
        assertThat(h.getType()).isEqualTo(HazardType.ILLEGAL_PARKING);
        assertThat(h.getTimeSlot()).isEqualTo(TimeSlot.MORNING);
        assertThat(h.getReportCount()).isEqualTo(1);
        assertThat(h.getBaseScore()).isPositive();

        Report r = reports.findById(body.get("reportId").asLong()).orElseThrow();
        assertThat(r.getHazardId()).isEqualTo(h.getId());
        assertThat(r.getUserId()).isEqualTo(member.id());
        assertThat(r.getDescription()).isEqualTo("후문 앞 주차\n아이들이 안 보여요");

        assertThat(histories.findAll()).filteredOn(s -> s.getHazardId().equals(h.getId())).singleElement()
                .satisfies(s -> {
                    assertThat(s.getFromStatus()).isNull();
                    assertThat(s.getToStatus()).isEqualTo(HazardStatus.RECEIVED);
                    assertThat(s.getAdminId()).isNull();
                });
    }

    @Test
    void 등록_직후_지도_조회에_마커가_나온다() throws Exception {
        long hazardId = json.readTree(post(valid()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("hazardId").asLong();
        em.flush();

        mockMvc.perform(get("/api/hazards?bbox=126.737,37.336,126.749,37.345&timeSlot=MORNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.id").value(hazardId))
                .andExpect(jsonPath("$.features[0].properties.status").value("RECEIVED"))
                .andExpect(jsonPath("$.features[0].properties.reportCount").value(1))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(LNG));
    }

    @Test
    void 설명이_비었거나_없으면_null로_저장() throws Exception {
        for (String blank : new String[]{"   ", null}) {
            long reportId = json.readTree(post(with("description", blank)).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString()).get("reportId").asLong();
            assertThat(reports.findById(reportId).orElseThrow().getDescription()).isNull();
        }
    }

    @Test
    void 검증_메시지는_V06_V09_문구_그대로() throws Exception {
        record Case(Map<String, String> fields, String field, String message) {
        }
        List<Case> cases = List.of(
                new Case(with("lat", null), "lat", "위치를 선택해 주세요"),
                new Case(with("lng", null), "lng", "위치를 선택해 주세요"),
                new Case(with("type", null), "type", "위험 유형을 선택해 주세요"),
                new Case(with("timeSlot", null), "timeSlot", "시간대를 선택해 주세요"),
                new Case(with("description", "가".repeat(201)), "description", "설명은 200자까지 쓸 수 있어요"),
                new Case(with("type", "parking"), "type", "값이 올바르지 않아요"),
                new Case(with("lat", "abc"), "lat", "값이 올바르지 않아요"));
        for (Case c : cases) {
            post(c.fields())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.fields." + c.field()).value(c.message()));
        }
        post(with("description", " " + "가".repeat(200) + " ")).andExpect(status().isCreated());   // 공백 지우고 200자
        assertThat(hazards.count()).isEqualTo(1);
    }

    @Test
    void 시범_지역_밖이면_400_OUT_OF_AREA() throws Exception {
        post(with("lat", "37.3500"))   // 정왕초에서 북쪽으로 약 1km
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OUT_OF_AREA"));
        assertThat(hazards.count()).isZero();
    }

    @Test
    void 하루_10건을_넘으면_429_LIMIT() throws Exception {
        for (int i = 0; i < 10; i++) {
            post(valid()).andExpect(status().isCreated());
        }
        post(valid())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LIMIT"));
        assertThat(hazards.count()).isEqualTo(10);
    }

    @Test
    void 비로그인은_401_CSRF_없으면_403_사진은_아직_무시() throws Exception {
        mockMvc.perform(form(valid()).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(form(valid()).with(user(member))).andExpect(status().isForbidden());

        MockMultipartFile photo = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(form(valid()).file(photo).with(user(member)).with(csrf()))
                .andExpect(status().isCreated());   // 사진 저장은 #24
        assertThat(hazards.count()).isEqualTo(1);
    }
}
