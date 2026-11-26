package com.slink.ansimheung.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.Role;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.EmpathyRepository;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.hazard.Report;
import com.slink.ansimheung.hazard.ReportRepository;
import com.slink.ansimheung.hazard.StatusHistory;
import com.slink.ansimheung.hazard.StatusHistoryRepository;
import com.slink.ansimheung.risk.RiskService;
import com.slink.ansimheung.seed.DemoSeeder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.slink.ansimheung.seed.DemoSeedData.DEMO_REPORT_LAT;
import static com.slink.ansimheung.seed.DemoSeedData.DEMO_REPORT_LNG;
import static com.slink.ansimheung.seed.DemoSeedData.DEMO_START_LAT;
import static com.slink.ansimheung.seed.DemoSeedData.DEMO_START_LNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/admin/** (#14, DB 필요). 지점은 테스트마다 비우고 직접 넣은 뒤 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired StatusHistoryRepository histories;
    @Autowired UserRepository users;
    @Autowired RiskService riskService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AnsimheungProperties props;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired EmpathyRepository empathies;

    LoginUser admin;
    LoginUser member;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");   // bootRun 으로 들어간 시드 등 (롤백되므로 그대로 남음)
        admin = LoginUser.from(users.save(User.create("admin@ansimheung.test", "{noop}x", "관리자1", Role.ADMIN)));
        member = LoginUser.from(users.save(User.create("parent@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
    }

    private Hazard hazard(HazardType type, TimeSlot slot, double lat, double lng) {
        Hazard h = Hazard.create(lat, lng, type, slot);
        h.addReport();
        riskService.refresh(h);
        hazards.save(h);
        histories.save(StatusHistory.of(h.getId(), null, HazardStatus.RECEIVED, null, null));
        return h;
    }

    private ResultActions getAsAdmin(String url) throws Exception {
        return mockMvc.perform(get(url).with(user(admin)));
    }

    private ResultActions changeStatus(Long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/admin/hazards/" + id + "/status").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
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
    void 비로그인은_401_일반_회원은_403() throws Exception {
        mockMvc.perform(get("/api/admin/hazards")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/admin/hazards").with(user(member))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 목록은_mock_admin_hazards_json과_같은_모양이고_counts는_필터와_관계없다() throws Exception {
        hazard(HazardType.ILLEGAL_PARKING, TimeSlot.ALWAYS, 37.3413, 126.7436);
        Hazard checking = hazard(HazardType.SPEEDING, TimeSlot.ALWAYS, 37.3405, 126.7440);
        checking.changeStatus(HazardStatus.CHECKING);
        Hazard hidden = hazard(HazardType.POOR_LIGHTING, TimeSlot.ALWAYS, 37.3400, 126.7425);
        hidden.hide("중복 제보");
        em.flush();

        JsonNode page = body(getAsAdmin("/api/admin/hazards?status=RECEIVED").andExpect(status().isOk()));
        JsonNode expected = mock("admin-hazards.json");
        assertThat(fieldNames(page)).isEqualTo(fieldNames(expected));
        assertThat(fieldNames(page.get("counts"))).isEqualTo(fieldNames(expected.get("counts")));
        assertThat(fieldNames(page.get("content").get(0))).isEqualTo(fieldNames(expected.get("content").get(0)));

        // 상태별은 숨김 제외, 숨김은 HIDDEN 에 따로 (필터 status=RECEIVED 와 무관)
        assertThat(page.get("counts").get("RECEIVED").asLong()).isEqualTo(1);
        assertThat(page.get("counts").get("CHECKING").asLong()).isEqualTo(1);
        assertThat(page.get("counts").get("HIDDEN").asLong()).isEqualTo(1);
        // status=RECEIVED 는 숨김 지점도 포함 (hidden 생략 = 전체)
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("size").asInt()).isEqualTo(props.paging().defaultSize());

        JsonNode visible = body(getAsAdmin("/api/admin/hazards?status=RECEIVED&hidden=false"));
        assertThat(visible.get("totalElements").asLong()).isEqualTo(1);
        assertThat(visible.get("content").get(0).get("score").asDouble()).isEqualTo(3.2);   // 1.8×1.5×1.2 = 3.24
        assertThat(visible.get("content").get(0).get("level").asText()).isEqualTo("MEDIUM"); // < 3.5 (C-08)
    }

    @Test
    void 정렬은_기본_최신순_OLDEST_SCORE이고_size는_최대값으로_자른다() throws Exception {
        Hazard first = hazard(HazardType.POOR_LIGHTING, TimeSlot.ALWAYS, 37.3413, 126.7436);   // 2.16
        Hazard second = hazard(HazardType.SIDEWALK_GAP, TimeSlot.ALWAYS, 37.3405, 126.7440);   // 3.6
        Hazard third = hazard(HazardType.SPEEDING, TimeSlot.ALWAYS, 37.3400, 126.7425);        // 2.88
        em.flush();

        assertThat(ids(body(getAsAdmin("/api/admin/hazards")))).containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(ids(body(getAsAdmin("/api/admin/hazards?sort=OLDEST")))).containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(ids(body(getAsAdmin("/api/admin/hazards?sort=SCORE")))).containsExactly(second.getId(), third.getId(), first.getId());

        JsonNode paged = body(getAsAdmin("/api/admin/hazards?sort=OLDEST&page=1&size=2"));
        assertThat(ids(paged)).containsExactly(third.getId());
        assertThat(paged.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body(getAsAdmin("/api/admin/hazards?size=1000")).get("size").asInt()).isEqualTo(props.paging().maxSize());

        getAsAdmin("/api/admin/hazards?sort=LATEST").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    @Test
    void 상세는_mock과_같은_모양이고_작성자_닉네임과_다음_상태를_준다() throws Exception {
        Hazard h = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.MORNING, 37.3413, 126.7436);
        reports.save(Report.create(h.getId(), member.id(), 37.3413, 126.7436, TimeSlot.MORNING, "후문 앞 주차"));
        reports.save(Report.create(h.getId(), null, 37.3413, 126.7436, TimeSlot.MORNING, null));
        em.flush();

        JsonNode d = body(getAsAdmin("/api/admin/hazards/" + h.getId()).andExpect(status().isOk()));
        JsonNode expected = mock("admin-hazard-detail.json");
        assertThat(fieldNames(d)).isEqualTo(fieldNames(expected));
        assertThat(fieldNames(d.get("reports").get(0))).isEqualTo(fieldNames(expected.get("reports").get(0)));
        assertThat(fieldNames(d.get("history").get(0))).isEqualTo(fieldNames(expected.get("history").get(0)));

        assertThat(d.get("reports").get(0).get("authorNickname").asText()).isEqualTo("학부모1");
        assertThat(d.get("reports").get(1).get("authorNickname").isNull()).isTrue();   // 작성자 없음 → null (필드는 있음)
        assertThat(d.get("allowedNextStatuses")).extracting(JsonNode::asText).containsExactly("CHECKING", "REJECTED");
        assertThat(d.get("history").get(0).get("toStatus").asText()).isEqualTo("RECEIVED");

        getAsAdmin("/api/admin/hazards/999999").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 상태_변경은_허용된_전이만_하고_이력에_관리자와_메모를_남긴다() throws Exception {
        Hazard h = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.ALWAYS, 37.3413, 126.7436);
        em.flush();

        changeStatus(h.getId(), "{\"status\": \"RESOLVED\"}")                  // 접수 → 개선완료 바로 ❌
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        changeStatus(h.getId(), "{\"status\": \"CHECKING\", \"memo\": \"" + "가".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.memo").value("메모는 200자까지 쓸 수 있어요"));   // V-10

        changeStatus(h.getId(), "{\"status\": \"CHECKING\", \"memo\": \"현장 확인 예정\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(h.getId()))
                .andExpect(jsonPath("$.status").value("CHECKING"));
        changeStatus(999999L, "{\"status\": \"CHECKING\"}").andExpect(status().isNotFound());

        JsonNode d = body(getAsAdmin("/api/admin/hazards/" + h.getId()));
        JsonNode last = d.get("history").get(d.get("history").size() - 1);
        assertThat(last.get("fromStatus").asText()).isEqualTo("RECEIVED");
        assertThat(last.get("toStatus").asText()).isEqualTo("CHECKING");
        assertThat(last.get("memo").asText()).isEqualTo("현장 확인 예정");
        assertThat(last.get("adminNickname").asText()).isEqualTo("관리자1");
        assertThat(d.get("allowedNextStatuses")).extracting(JsonNode::asText).containsExactly("RESOLVED", "REJECTED");
    }

    @Test
    void 시연_마지막_장면_개선완료하면_같은_출발지의_안전_경로가_원래_길로_돌아온다() throws Exception {
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", DemoSeeder.ADMIN_EMAIL, DemoSeeder.PARENT_EMAIL);
        new DemoSeeder(users, hazards, reports, empathies, histories, riskService, passwordEncoder, props).seed();
        String route = "{\"fromLat\": " + DEMO_START_LAT + ", \"fromLng\": " + DEMO_START_LNG + "}";
        JsonNode before = safest(route);

        Hazard live = hazard(HazardType.ILLEGAL_PARKING, TimeSlot.ALWAYS, DEMO_REPORT_LAT, DEMO_REPORT_LNG);
        em.flush();
        assertThat(safest(route)).isNotEqualTo(before);

        changeStatus(live.getId(), "{\"status\": \"CHECKING\"}").andExpect(status().isOk());
        changeStatus(live.getId(), "{\"status\": \"RESOLVED\", \"memo\": \"단속 요청 완료\"}").andExpect(status().isOk());
        em.flush();
        assertThat(safest(route)).isEqualTo(before);
    }

    private JsonNode safest(String route) throws Exception {
        JsonNode res = body(mockMvc.perform(post("/api/routes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(route)).andExpect(status().isOk()));
        return res.get("routes").get(2).get("path");
    }
}
