package com.slink.ansimheung.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.risk.RiskService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/routes (DB 필요). 그래프는 Flyway V3 실제 데이터, 위험 지점은 테스트마다 비우고 넣은 뒤 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RouteApiTest {

    /** 정왕초 동쪽 420m, 가장 가까운 노드 14m */
    private static final String EAST = "\"fromLat\": 37.3408, \"fromLng\": 126.74775";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired HazardRepository hazardRepository;
    @Autowired RiskService riskService;
    @Autowired GraphLoader graphLoader;
    @Autowired AnsimheungProperties props;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    /** bootRun 으로 들어간 시연용 시드(#15) 등 DB 에 남아 있는 지점을 비운다. 테스트 트랜잭션 안이라 끝나면 롤백 → 시드는 그대로 */
    @BeforeEach
    void 기존_위험_지점을_비운다() {
        jdbc.update("DELETE FROM hazards");
    }

    private ResultActions request(String body) throws Exception {
        return mockMvc.perform(post("/api/routes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{" + body + "}"));
    }

    private JsonNode routes(String body) throws Exception {
        String res = request(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("routes");
    }

    private static List<String> coords(JsonNode route) {
        List<String> out = new ArrayList<>();
        route.get("path").get("coordinates").forEach(c -> out.add(c.get(0).asDouble() + "," + c.get(1).asDouble()));
        return out;
    }

    @Test
    void 응답이_mock_routes_json과_같은_모양() throws Exception {
        request(EAST + ", \"timeSlot\": \"MORNING\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes.length()").value(3))
                .andExpect(jsonPath("$.routes[0].mode").value("SHORTEST"))
                .andExpect(jsonPath("$.routes[0].lambda").value(0.0))
                .andExpect(jsonPath("$.routes[1].mode").value("BALANCED"))
                .andExpect(jsonPath("$.routes[1].lambda").value(20.0))
                .andExpect(jsonPath("$.routes[2].mode").value("SAFEST"))
                .andExpect(jsonPath("$.routes[2].lambda").value(60.0))
                .andExpect(jsonPath("$.routes[0].distanceM").isNumber())
                .andExpect(jsonPath("$.routes[0].minutes").isNumber())
                .andExpect(jsonPath("$.routes[0].riskExposure").isNumber())
                .andExpect(jsonPath("$.routes[0].hazardIds").isArray())
                .andExpect(jsonPath("$.routes[0].path.type").value("LineString"));
    }

    @Test
    void 시간은_거리_나누기_분속_60m_반올림_경로는_정문_노드에서_끝난다() throws Exception {
        JsonNode r = routes(EAST).get(0);

        long distance = r.get("distanceM").asLong();
        assertThat(distance).isBetween(300L, 1500L);
        assertThat(r.get("minutes").asLong()).isEqualTo(Math.round(distance / props.route().walkMPerMin()));

        Graph g = graphLoader.graph();
        Graph.Vertex gate = g.nearest(props.area().gateLat(), props.area().gateLng()).orElseThrow().vertex();
        JsonNode last = r.get("path").get("coordinates").get(r.get("path").get("coordinates").size() - 1);
        assertThat(last.get(0).asDouble()).isEqualTo(gate.lng());     // GeoJSON [경도, 위도]
        assertThat(last.get(1).asDouble()).isEqualTo(gate.lat());
    }

    @Test
    void 위험_지점이_없으면_세_경로가_같고_위험_노출_0() throws Exception {
        JsonNode rs = routes(EAST);

        assertThat(coords(rs.get(1))).isEqualTo(coords(rs.get(0)));
        assertThat(coords(rs.get(2))).isEqualTo(coords(rs.get(0)));
        assertThat(rs.get(0).get("riskExposure").asDouble()).isZero();
    }

    @Test
    void 최단_경로_골목에_제보를_추가하면_같은_출발지에서_SAFEST가_다른_길로_바뀐다() throws Exception {
        JsonNode before = routes(EAST + ", \"timeSlot\": \"MORNING\"").get(0);
        JsonNode cs = before.get("path").get("coordinates");
        int mid = cs.size() / 2;
        // 최단 경로 가운데 선분의 중점 = 경로 위의 점 → 그 구간에 배정됨
        double lng = (cs.get(mid - 1).get(0).asDouble() + cs.get(mid).get(0).asDouble()) / 2;
        double lat = (cs.get(mid - 1).get(1).asDouble() + cs.get(mid).get(1).asDouble()) / 2;

        Hazard h = Hazard.create(lat, lng, HazardType.ILLEGAL_PARKING, TimeSlot.MORNING);
        h.addReport();
        riskService.refresh(h);
        hazardRepository.save(h);
        em.flush();
        assertThat(h.getEdgeId()).isNotNull();

        JsonNode after = routes(EAST + ", \"timeSlot\": \"MORNING\"");
        JsonNode shortest = after.get(0);
        JsonNode safest = after.get(2);

        // 최단은 그대로 그 지점을 지나고, 위험 노출 = 2.7 × 1.5(등교 일치) = 4.05 → 4.1 (C-13 반올림)
        assertThat(coords(shortest)).isEqualTo(coords(before));
        assertThat(shortest.get("hazardIds").toString()).isEqualTo("[" + h.getId() + "]");
        assertThat(shortest.get("riskExposure").asDouble()).isEqualTo(4.1);
        // 안전은 다른 길 → 그 지점을 피하고, 조금 더 길다
        assertThat(coords(safest)).isNotEqualTo(coords(shortest));
        assertThat(safest.get("hazardIds").size()).isZero();
        assertThat(safest.get("riskExposure").asDouble()).isZero();
        assertThat(safest.get("distanceM").asLong()).isGreaterThan(shortest.get("distanceM").asLong());
    }

    @Test
    void 시범_지역_밖이면_OUT_OF_AREA() throws Exception {
        request("\"fromLat\": 37.3600, \"fromLng\": 126.7430")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OUT_OF_AREA"))
                .andExpect(jsonPath("$.message").value("시범 지역 안에서 출발지를 골라주세요"));
    }

    @Test
    void 길에서_50m_넘게_떨어지면_FAR_FROM_ROAD() throws Exception {
        // 정왕중 운동장 안쪽 (가장 가까운 노드 89m)
        request("\"fromLat\": 37.3397, \"fromLng\": 126.7417")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FAR_FROM_ROAD"));
    }

    @Test
    void 좌표가_없으면_VALIDATION() throws Exception {
        request("\"fromLng\": 126.74775")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fields.fromLat").value("위치를 선택해 주세요"));
    }

    @Test
    void 조회_시간대에_ALWAYS는_불가() throws Exception {
        request(EAST + ", \"timeSlot\": \"ALWAYS\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fields.timeSlot").value("시간대를 선택해 주세요"));
    }

    @Test
    void 로그인_없이_되지만_CSRF_토큰은_필요() throws Exception {
        mockMvc.perform(post("/api/routes").contentType(MediaType.APPLICATION_JSON).content("{" + EAST + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 출발지_좌표는_로그에_남지_않는다(CapturedOutput output) throws Exception {
        request(EAST).andExpect(status().isOk());
        request("\"fromLat\": 37.3397, \"fromLng\": 126.7417").andExpect(status().isBadRequest());

        assertThat(output.getAll()).contains("경로 3종 계산")
                .doesNotContain("126.74775").doesNotContain("37.3397").doesNotContain("126.7417");
    }
}
