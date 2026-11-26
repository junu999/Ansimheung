package com.slink.ansimheung.notification;

import com.slink.ansimheung.auth.LoginUser;
import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.Role;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.hazard.Report;
import com.slink.ansimheung.hazard.ReportRepository;
import com.slink.ansimheung.risk.RiskService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 처리결과 알림 (#35, DB 필요). 관리자 상태 변경 API → 제보자 알림 → /api/me/notifications */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired RiskService riskService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    LoginUser admin;
    LoginUser reporter;
    LoginUser other;
    Hazard hazard;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");
        admin = LoginUser.from(users.save(User.create("admin@ansimheung.test", "{noop}x", "관리자1", Role.ADMIN)));
        reporter = LoginUser.from(users.save(User.create("reporter@ansimheung.test", "{noop}x", "학부모1", Role.USER)));
        other = LoginUser.from(users.save(User.create("other@ansimheung.test", "{noop}x", "학부모2", Role.USER)));

        hazard = Hazard.create(37.3413, 126.7436, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
        hazard.addReport();
        riskService.refresh(hazard);
        hazards.save(hazard);
        reports.save(Report.create(hazard.getId(), reporter.id(), 37.3413, 126.7436, TimeSlot.MORNING, "후문 앞 주차"));
        reports.save(Report.create(hazard.getId(), null, 37.3413, 126.7436, TimeSlot.MORNING, null));          // 작성자 없음
        em.flush();
    }

    private void changeStatus(HazardStatus next) throws Exception {
        mockMvc.perform(patch("/api/admin/hazards/" + hazard.getId() + "/status").with(user(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + next + "\"}"))
                .andExpect(status().isOk());
        em.flush();
    }

    @Test
    void 관리자가_상태를_바꾸면_제보자에게_알림이_1건_생기고_mock과_같은_모양으로_보인다() throws Exception {
        changeStatus(HazardStatus.CHECKING);

        assertThat(notifications.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getUserId()).isEqualTo(reporter.id()));
        mockMvc.perform(get("/api/me/notifications").with(user(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").isNumber())
                .andExpect(jsonPath("$.items[0].hazardId").value(hazard.getId()))
                .andExpect(jsonPath("$.items[0].message").value("제보하신 지점이 '확인중'으로 바뀌었어요"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].createdAt").isString());
        mockMvc.perform(get("/api/me/notifications").with(user(other)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 알림_문구는_받침에_맞춰_으로_로를_붙이고_최신순이다() throws Exception {
        changeStatus(HazardStatus.CHECKING);
        changeStatus(HazardStatus.RESOLVED);

        mockMvc.perform(get("/api/me/notifications").with(user(reporter)))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].message").value("제보하신 지점이 '개선완료'로 바뀌었어요"))
                .andExpect(jsonPath("$.items[1].message").value("제보하신 지점이 '확인중'으로 바뀌었어요"));
        assertThat(NotificationService.message(HazardStatus.REJECTED)).isEqualTo("제보하신 지점이 '반려'로 바뀌었어요");
    }

    @Test
    void 읽음_처리는_204이고_본인_것만_된다() throws Exception {
        changeStatus(HazardStatus.CHECKING);
        Long id = notifications.findAll().get(0).getId();

        mockMvc.perform(patch("/api/me/notifications/" + id + "/read").with(user(other)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/me/notifications/999999/read").with(user(reporter)).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/me/notifications/" + id + "/read").with(user(reporter)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/me/notifications/" + id + "/read").with(user(reporter)).with(csrf()))
                .andExpect(status().isNoContent());   // 다시 보내도 그대로
        em.flush();
        mockMvc.perform(get("/api/me/notifications").with(user(reporter)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));
    }

    @Test
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get("/api/me/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/me/notifications/1/read").with(csrf())).andExpect(status().isUnauthorized());
    }
}
