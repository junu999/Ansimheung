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
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공감 추가·취소 (#26, DB 필요). 지점은 테스트마다 비우고 넣은 뒤 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmpathyApiTest {

    private static final double LAT = 37.3413;
    private static final double LNG = 126.7436;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired EmpathyRepository empathies;
    @Autowired UserRepository users;
    @Autowired RiskService riskService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    LoginUser member;
    LoginUser reporter;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");   // bootRun 으로 들어간 시드 등 (롤백되므로 그대로 남음)
        member = LoginUser.from(users.save(User.create("empathy-test@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
        reporter = LoginUser.from(users.save(User.create("empathy-test2@ansimheung.test", "{noop}x", "학부모2", Role.USER)));
    }

    /** reporter 가 제보한 접수 지점 */
    private Hazard hazard() {
        Hazard h = Hazard.create(LAT, LNG, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
        h.addReport();
        riskService.refresh(h);
        hazards.save(h);
        reports.save(Report.create(h.getId(), reporter.id(), LAT, LNG, TimeSlot.MORNING, null));
        em.flush();
        return h;
    }

    private ResultActions add(Hazard h, LoginUser as) throws Exception {
        return mockMvc.perform(post("/api/hazards/" + h.getId() + "/empathy").with(user(as)).with(csrf()));
    }

    private ResultActions remove(Hazard h, LoginUser as) throws Exception {
        return mockMvc.perform(delete("/api/hazards/" + h.getId() + "/empathy").with(user(as)).with(csrf()));
    }

    private double baseScore(Hazard h) {
        em.flush();
        em.clear();
        return hazards.findById(h.getId()).orElseThrow().getBaseScore();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) names.add(it.next());
        return names;
    }

    @Test
    void 공감하면_수가_늘고_점수가_오르며_mock과_같은_모양이고_다시_눌러도_그대로() throws Exception {
        Hazard h = hazard();
        double before = baseScore(h);

        String res = add(h, member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empathyCount").value(1))
                .andExpect(jsonPath("$.myEmpathy").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/static/mock/empathy-on.json"));
        assertThat(fieldNames(json.readTree(res))).isEqualTo(fieldNames(expected));
        assertThat(baseScore(h)).isGreaterThan(before);

        add(h, member).andExpect(status().isOk()).andExpect(jsonPath("$.empathyCount").value(1));   // 멱등
        em.flush();
        assertThat(empathies.count()).isEqualTo(1);
        mockMvc.perform(get("/api/hazards/" + h.getId()).with(user(member)))
                .andExpect(jsonPath("$.empathyCount").value(1))
                .andExpect(jsonPath("$.myEmpathy").value(true));
    }

    @Test
    void 취소하면_수와_점수가_돌아가고_안_한_공감을_취소해도_그대로() throws Exception {
        Hazard h = hazard();
        double before = baseScore(h);
        add(h, member).andExpect(status().isOk());

        String res = remove(h, member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empathyCount").value(0))
                .andExpect(jsonPath("$.myEmpathy").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/static/mock/empathy-off.json"));
        assertThat(fieldNames(json.readTree(res))).isEqualTo(fieldNames(expected));
        assertThat(baseScore(h)).isCloseTo(before, within(0.01));

        remove(h, member).andExpect(status().isOk()).andExpect(jsonPath("$.empathyCount").value(0));   // 멱등
        assertThat(empathies.count()).isZero();
    }

    @Test
    void 내_제보_지점은_400_OWN_HAZARD_개선완료는_추가만_409() throws Exception {
        Hazard h = hazard();
        add(h, reporter).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OWN_HAZARD"));

        add(h, member).andExpect(status().isOk());
        h = hazards.findById(h.getId()).orElseThrow();
        h.changeStatus(HazardStatus.CHECKING);
        h.changeStatus(HazardStatus.RESOLVED);
        em.flush();

        LoginUser other = LoginUser.from(users.save(User.create("empathy-test3@ansimheung.test", "{noop}x", "학부모3", Role.USER)));
        add(h, other).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        remove(h, member).andExpect(status().isOk()).andExpect(jsonPath("$.empathyCount").value(0));   // 취소는 가능
    }

    @Test
    void 없음_숨김_반려는_404() throws Exception {
        mockMvc.perform(post("/api/hazards/999999/empathy").with(user(member)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        Hazard hidden = hazard();
        hidden.hide("테스트");
        Hazard rejected = hazard();
        rejected.changeStatus(HazardStatus.REJECTED);
        em.flush();
        for (Hazard h : List.of(hidden, rejected)) {
            add(h, member).andExpect(status().isNotFound());
            remove(h, member).andExpect(status().isNotFound());
        }
    }

    @Test
    void 하루_30회를_넘으면_429_LIMIT() throws Exception {
        for (int i = 0; i < 30; i++) {
            empathies.save(Empathy.of(member.id(), hazard().getId()));
        }
        em.flush();
        add(hazard(), member).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LIMIT"));
    }

    @Test
    void 비로그인은_401_CSRF_없으면_403() throws Exception {
        Hazard h = hazard();
        mockMvc.perform(post("/api/hazards/" + h.getId() + "/empathy").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/hazards/" + h.getId() + "/empathy").with(user(member))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/hazards/" + h.getId() + "/empathy").with(csrf())).andExpect(status().isUnauthorized());
    }
}
