package com.slink.ansimheung.hazard;

import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.geo.Geometries;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.PublicLayerKind;
import com.slink.ansimheung.common.type.Role;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.notification.Notification;
import com.slink.ansimheung.notification.NotificationRepository;
import com.slink.ansimheung.publiclayer.PublicLayer;
import com.slink.ansimheung.publiclayer.PublicLayerRepository;
import com.slink.ansimheung.route.Edge;
import com.slink.ansimheung.route.EdgeRepository;
import com.slink.ansimheung.route.Node;
import com.slink.ansimheung.route.NodeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엔티티 ↔ V1 스키마 저장·조회 확인 (API·DB 테스트 = @SpringBootTest + @Transactional → 끝나면 롤백).
 * 서버가 뜨는 것 자체가 ddl-auto: validate 로 매핑 검사를 한 번 한다.
 */
@SpringBootTest
@Transactional
class EntityMappingTest {

    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired HazardRepository hazards;
    @Autowired ReportRepository reports;
    @Autowired PhotoRepository photos;
    @Autowired EmpathyRepository empathies;
    @Autowired StatusHistoryRepository histories;
    @Autowired NotificationRepository notifications;
    @Autowired NodeRepository nodes;
    @Autowired EdgeRepository edges;
    @Autowired PublicLayerRepository publicLayers;

    @Test
    void 제보_흐름의_엔티티를_저장하고_다시_읽는다() {
        User user = users.save(User.create("  Parent@Example.COM ", "{bcrypt}hash", "학부모1", Role.USER));
        Hazard hazard = Hazard.create(37.3413, 126.7436, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
        hazard.addReport();
        hazards.save(hazard);
        Report report = reports.save(Report.create(hazard.getId(), user.getId(), 37.34131, 126.74362, TimeSlot.MORNING, "후문 앞 불법주정차"));
        photos.save(Photo.create(report.getId(), "uploads/test.jpg", "image/jpeg"));
        empathies.save(Empathy.of(user.getId(), hazard.getId()));
        histories.save(StatusHistory.of(hazard.getId(), null, HazardStatus.RECEIVED, null, null));
        notifications.save(Notification.of(user.getId(), hazard.getId(), "제보하신 지점이 '확인중'으로 바뀌었어요"));
        em.flush();
        em.clear();

        User u = users.findByEmail("parent@example.com").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.USER);

        Hazard h = hazards.findById(hazard.getId()).orElseThrow();
        assertThat(h.getLat()).isEqualTo(37.3413);
        assertThat(h.getLng()).isEqualTo(126.7436);
        assertThat(h.getType()).isEqualTo(HazardType.ILLEGAL_PARKING);
        assertThat(h.getStatus()).isEqualTo(HazardStatus.RECEIVED);
        assertThat(h.getReportCount()).isEqualTo(1);

        assertThat(reports.findByHazardIdAndDeletedFalseOrderByCreatedAtAsc(h.getId())).hasSize(1);
        assertThat(reports.existsByHazardIdAndUserIdAndDeletedFalse(h.getId(), u.getId())).isTrue();
        assertThat(reports.countByUserIdAndCreatedAtGreaterThanEqual(u.getId(), OffsetDateTime.now().minusDays(1))).isEqualTo(1);
        assertThat(photos.findByReportId(report.getId()).orElseThrow().isApproved()).isFalse();
        assertThat(empathies.existsById(new EmpathyId(u.getId(), h.getId()))).isTrue();
        assertThat(empathies.countByIdUserIdAndCreatedAtGreaterThanEqual(u.getId(), OffsetDateTime.now().minusDays(1))).isEqualTo(1);
        assertThat(histories.findByHazardIdOrderByCreatedAtAsc(h.getId())).extracting(StatusHistory::getToStatus)
                .containsExactly(HazardStatus.RECEIVED);
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(u.getId())).hasSize(1);
    }

    @Test
    void 도보_그래프와_공공레이어를_저장하고_다시_읽는다() {
        Node a = nodes.save(Node.at(37.3432, 126.7405));
        Node b = nodes.save(Node.at(37.3421, 126.7418));
        Edge e = edges.save(Edge.of(a.getId(), b.getId(), 168.5,
                Geometries.lineString(new double[][]{{37.3432, 126.7405}, {37.3421, 126.7418}})));
        publicLayers.save(PublicLayer.of(PublicLayerKind.CCTV, "정왕동 CCTV", "공공데이터포털", Geometries.point(37.3410, 126.7425)));
        em.flush();
        em.clear();

        Edge loaded = edges.findById(e.getId()).orElseThrow();
        assertThat(loaded.getFromNodeId()).isEqualTo(a.getId());
        assertThat(loaded.getGeom().getNumPoints()).isEqualTo(2);
        assertThat(nodes.findById(b.getId()).orElseThrow().getLat()).isEqualTo(37.3421);
        // V2 공공데이터(#34)가 이미 들어 있을 수 있으므로 개수 대신 방금 저장한 행이 있는지만 본다
        assertThat(publicLayers.findByKind(PublicLayerKind.CCTV)).extracting(PublicLayer::getName).contains("정왕동 CCTV");
    }
}
