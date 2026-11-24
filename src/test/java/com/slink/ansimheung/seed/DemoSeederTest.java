package com.slink.ansimheung.seed;

import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.MarkerLevel;
import com.slink.ansimheung.common.type.Role;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.EmpathyRepository;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.hazard.ReportRepository;
import com.slink.ansimheung.hazard.StatusHistoryRepository;
import com.slink.ansimheung.risk.RiskService;
import com.slink.ansimheung.route.RouteService;
import com.slink.ansimheung.route.dto.RouteResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.slink.ansimheung.seed.DemoSeedData.DEMO_REPORT_LAT;
import static com.slink.ansimheung.seed.DemoSeedData.DEMO_REPORT_LNG;
import static com.slink.ansimheung.seed.DemoSeedData.DEMO_START_LAT;
import static com.slink.ansimheung.seed.DemoSeedData.DEMO_START_LNG;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시연용 시드 (#15, DB 필요). 테스트에서는 시더가 자동 실행되지 않으므로(test config/application-local.yml) 직접 부른다.
 * DB 에 이미 들어간 시드·지점을 트랜잭션 안에서 비우고 시작 → 끝나면 롤백.
 */
@SpringBootTest
@Transactional
class DemoSeederTest {

    @Autowired UserRepository users;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired EmpathyRepository empathies;
    @Autowired StatusHistoryRepository histories;
    @Autowired RiskService riskService;
    @Autowired RouteService routeService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AnsimheungProperties props;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    DemoSeeder seeder;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM hazards");
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", DemoSeeder.ADMIN_EMAIL, DemoSeeder.PARENT_EMAIL);
        seeder = new DemoSeeder(users, hazards, reports, empathies, histories, riskService, passwordEncoder, props);
    }

    private List<RouteResponse.Route> demoRoutes(Optional<TimeSlot> slot) {
        em.flush();
        return routeService.routes(DEMO_START_LAT, DEMO_START_LNG, slot).routes();
    }

    private static List<String> coords(RouteResponse.Route r) {
        return r.path().coordinates().stream().map(Arrays::toString).toList();
    }

    @Test
    void 시연_계정_2개와_위험_지점_30곳을_넣고_두_번째_실행은_건너뛴다() {
        assertThat(seeder.seed()).isTrue();
        em.flush();

        User admin = users.findByEmail(DemoSeeder.ADMIN_EMAIL).orElseThrow();
        User parent = users.findByEmail(DemoSeeder.PARENT_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(parent.getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches(props.seed().adminPassword(), admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(props.seed().parentPassword(), parent.getPasswordHash())).isTrue();
        assertThat(hazards.count()).isEqualTo(30);
        assertThat(reports.count()).isEqualTo(30);
        assertThat(hazards.findAll()).allSatisfy(h -> assertThat(h.getEdgeId()).isNotNull());

        assertThat(seeder.seed()).isFalse();
        em.flush();
        assertThat(hazards.count()).isEqualTo(30);
    }

    @Test
    void 시연_출발지에서_세_경로가_서로_다른_길로_나오고_시간대와_관계없이_같다() {
        seeder.seed();

        for (Optional<TimeSlot> slot : List.of(Optional.<TimeSlot>empty(), Optional.of(TimeSlot.MORNING),
                Optional.of(TimeSlot.AFTERNOON), Optional.of(TimeSlot.NIGHT))) {
            List<RouteResponse.Route> rs = demoRoutes(slot);
            RouteResponse.Route shortest = rs.get(0), balanced = rs.get(1), safest = rs.get(2);

            // #15 이슈에 기록한 실제 값: 619m·10분 / 813m·14분 / 915m·15분
            assertThat(rs).extracting(RouteResponse.Route::distanceM).containsExactly(619L, 813L, 915L);
            assertThat(rs).extracting(RouteResponse.Route::minutes).containsExactly(10L, 14L, 15L);
            assertThat(shortest.riskExposure()).isGreaterThan(balanced.riskExposure());
            assertThat(balanced.riskExposure()).isGreaterThan(safest.riskExposure());
            assertThat(safest.riskExposure()).isZero();
            assertThat(coords(safest)).doesNotContainAnyElementsOf(coords(shortest).subList(1, coords(shortest).size() - 1));
        }
    }

    @Test
    void 마커_빨강_기준값은_시드_점수_상위_30퍼센트() {
        seeder.seed();
        em.flush();

        long high = hazards.findAll().stream()
                .filter(h -> riskService.levelOf(h, riskService.scoreAt(h, Optional.empty())) == MarkerLevel.HIGH)
                .count();
        assertThat(high).isEqualTo(9);   // 30곳 × 30% (C-08)
    }

    @Test
    void 시연_시나리오_제보하면_안전_경로가_바뀌고_개선완료하면_돌아온다() {
        seeder.seed();
        List<String> before = coords(demoRoutes(Optional.empty()).get(2));

        Hazard live = Hazard.create(DEMO_REPORT_LAT, DEMO_REPORT_LNG, HazardType.ILLEGAL_PARKING, TimeSlot.ALWAYS);
        live.addReport();
        riskService.refresh(live);
        hazards.save(live);
        List<RouteResponse.Route> reported = demoRoutes(Optional.empty());
        assertThat(coords(reported.get(2))).isNotEqualTo(before);
        assertThat(coords(reported.get(2))).isEqualTo(coords(reported.get(1)));   // 안전 = 균형 경로

        live.changeStatus(HazardStatus.CHECKING);
        live.changeStatus(HazardStatus.RESOLVED);
        riskService.refresh(live);
        assertThat(coords(demoRoutes(Optional.empty()).get(2))).isEqualTo(before);
    }
}
