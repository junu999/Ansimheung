package com.slink.ansimheung.seed;

import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.Role;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.Empathy;
import com.slink.ansimheung.hazard.EmpathyRepository;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.hazard.Report;
import com.slink.ansimheung.hazard.ReportRepository;
import com.slink.ansimheung.hazard.StatusHistory;
import com.slink.ansimheung.hazard.StatusHistoryRepository;
import com.slink.ansimheung.risk.RiskService;
import com.slink.ansimheung.seed.DemoSeedData.SeedHazard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 시드 (#15, F-DATA-02 · F-AUTH-07). local 프로필 + {@code ansimheung.seed.enabled=true} 일 때만 서버 시작 시 실행.
 * 시연 관리자 계정이 이미 있으면 아무것도 하지 않는다 → 서버를 여러 번 켜도 중복되지 않음.
 * 처음부터 다시 넣으려면 {@code docker compose down -v} 로 DB 를 비운다.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "ansimheung.seed", name = "enabled", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {

    /** 팀 전체가 쓰는 시연 계정 (팀 합의 11/24 — 다른 곳에서 계정을 만들지 않는다) */
    public static final String ADMIN_EMAIL = "admin@ansimheung.local";
    public static final String PARENT_EMAIL = "parent@ansimheung.local";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final UserRepository users;
    private final HazardRepository hazards;
    private final ReportRepository reports;
    private final EmpathyRepository empathies;
    private final StatusHistoryRepository histories;
    private final RiskService riskService;
    private final PasswordEncoder passwordEncoder;
    private final AnsimheungProperties props;

    public DemoSeeder(UserRepository users, HazardRepository hazards, ReportRepository reports,
                      EmpathyRepository empathies, StatusHistoryRepository histories, RiskService riskService,
                      PasswordEncoder passwordEncoder, AnsimheungProperties props) {
        this.users = users;
        this.hazards = hazards;
        this.reports = reports;
        this.empathies = empathies;
        this.histories = histories;
        this.riskService = riskService;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seed();
    }

    /** @return 새로 넣었으면 true, 이미 있어서 건너뛰었으면 false */
    public boolean seed() {
        if (users.existsByEmail(ADMIN_EMAIL)) {
            log.info("시연용 시드: 이미 있어서 건너뜀");
            return false;
        }
        var seed = props.seed();
        User admin = users.save(User.create(ADMIN_EMAIL, passwordEncoder.encode(seed.adminPassword()), "관리자", Role.ADMIN));
        User parent = users.save(User.create(PARENT_EMAIL, passwordEncoder.encode(seed.parentPassword()), "학부모", Role.USER));

        for (SeedHazard s : DemoSeedData.HAZARDS) {
            insert(s, admin.getId(), parent.getId());
        }
        log.info("시연용 시드: 계정 2개, 위험 지점 {}곳 추가", DemoSeedData.HAZARDS.size());
        return true;
    }

    private void insert(SeedHazard s, Long adminId, Long parentId) {
        Hazard h = Hazard.create(s.lat(), s.lng(), s.type(), s.timeSlot());
        h.addReport();
        if (s.empathized()) {
            h.addEmpathy();
        }
        boolean checked = s.status() != HazardStatus.RECEIVED;
        if (checked) {
            h.changeStatus(HazardStatus.CHECKING);
        }
        if (s.status() == HazardStatus.RESOLVED) {
            h.changeStatus(HazardStatus.RESOLVED);
        }
        riskService.refresh(h);
        hazards.save(h);

        reports.save(Report.create(h.getId(), s.byParent() ? parentId : null, s.lat(), s.lng(), s.timeSlot(), s.description()));
        if (s.empathized()) {
            empathies.save(Empathy.of(parentId, h.getId()));
        }
        histories.save(StatusHistory.of(h.getId(), null, HazardStatus.RECEIVED, null, null));
        if (checked) {
            String checkingMemo = s.status() == HazardStatus.CHECKING ? s.memo() : null;
            histories.save(StatusHistory.of(h.getId(), HazardStatus.RECEIVED, HazardStatus.CHECKING, adminId, checkingMemo));
        }
        if (s.status() == HazardStatus.RESOLVED) {
            histories.save(StatusHistory.of(h.getId(), HazardStatus.CHECKING, HazardStatus.RESOLVED, adminId, s.memo()));
        }
    }
}
