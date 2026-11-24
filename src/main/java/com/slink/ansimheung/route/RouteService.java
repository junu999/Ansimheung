package com.slink.ansimheung.route;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.geo.PilotArea;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.RouteMode;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.config.AnsimheungProperties;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.risk.RiskService;
import com.slink.ansimheung.route.Dijkstra.Path;
import com.slink.ansimheung.route.Graph.Link;
import com.slink.ansimheung.route.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 경로 3종 (F-ROUTE-02~04, docs/domain.md 7장 4).
 * 범위 확인 → 출발지 스냅 → 목적지 = 정문 노드 → λ 0/20/60 Dijkstra 3회 → 요약.
 * 사전 계산·캐시 없음. ⚠️ 출발지 좌표는 저장·로그 금지 (P-12) — 로그에는 응답 시간만.
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);
    private static final String OUT_OF_AREA_MESSAGE = "시범 지역 안에서 출발지를 골라주세요";

    private final GraphLoader graphLoader;
    private final HazardRepository hazardRepository;
    private final RiskService riskService;
    private final PilotArea pilotArea;
    private final AnsimheungProperties props;

    public RouteService(GraphLoader graphLoader, HazardRepository hazardRepository, RiskService riskService,
                        PilotArea pilotArea, AnsimheungProperties props) {
        this.graphLoader = graphLoader;
        this.hazardRepository = hazardRepository;
        this.riskService = riskService;
        this.pilotArea = pilotArea;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public RouteResponse routes(double fromLat, double fromLng, Optional<TimeSlot> querySlot) {
        long started = System.nanoTime();
        pilotArea.requireInside(fromLat, fromLng, OUT_OF_AREA_MESSAGE);
        Graph graph = graphLoader.graph();

        Graph.Nearest start = graph.nearest(fromLat, fromLng).orElseThrow(() -> new ApiException(ErrorCode.NO_GRAPH));
        if (start.distanceM() > props.route().snapMaxM()) {
            throw new ApiException(ErrorCode.FAR_FROM_ROAD);
        }
        long target = graph.nearest(props.area().gateLat(), props.area().gateLng())
                .orElseThrow(() -> new ApiException(ErrorCode.NO_GRAPH)).vertex().id();

        EdgeRisks risks = edgeRisks(querySlot);
        List<RouteResponse.Route> routes = new ArrayList<>();
        for (RouteMode mode : RouteMode.values()) {
            double lambda = props.route().lambda(mode);
            Path path = Dijkstra.shortestPath(graph, start.vertex().id(), target,
                            l -> l.lengthM() + lambda * risks.risk(l.edgeId()))
                    .orElseThrow(() -> new ApiException(ErrorCode.NO_ROUTE));
            routes.add(summarize(graph, mode, lambda, path, risks));
        }

        log.info("경로 3종 계산 {}ms", String.format("%.1f", (System.nanoTime() - started) / 1e6));
        return new RouteResponse(routes);
    }

    private RouteResponse.Route summarize(Graph graph, RouteMode mode, double lambda, Path path, EdgeRisks risks) {
        double distance = 0;
        double exposure = 0;
        Set<Long> hazardIds = new LinkedHashSet<>();
        List<double[]> lngLats = new ArrayList<>();
        Graph.Vertex first = graph.vertex(path.nodeIds().get(0));
        lngLats.add(new double[]{first.lng(), first.lat()});

        for (int i = 0; i < path.links().size(); i++) {
            Link link = path.links().get(i);
            distance += link.lengthM();
            exposure += risks.risk(link.edgeId());
            hazardIds.addAll(risks.hazardIds(link.edgeId()));
            List<double[]> shape = graph.shapeFrom(link.edgeId(), path.nodeIds().get(i));
            for (double[] p : shape.subList(1, shape.size())) {     // 첫 점 = 앞 구간의 끝점
                lngLats.add(new double[]{p[1], p[0]});
            }
        }
        if (lngLats.size() == 1) {
            lngLats.add(lngLats.get(0));                           // 출발 = 정문: LineString 은 점 2개 이상
        }
        long distanceM = Math.round(distance);
        long minutes = Math.round(distanceM / props.route().walkMPerMin());
        return new RouteResponse.Route(mode, lambda, distanceM, minutes, round1(exposure),
                List.copyOf(hazardIds), RouteResponse.LineString.of(lngLats));
    }

    /** edgeRisk(e) = 구간 e 에 배정된 지점들의 조회 시점 점수 합 */
    private EdgeRisks edgeRisks(Optional<TimeSlot> querySlot) {
        OffsetDateTime hideBefore = OffsetDateTime.now().minusDays(props.hazard().resolvedHideDays());
        Map<Long, Double> risk = new HashMap<>();
        Map<Long, List<Long>> ids = new HashMap<>();
        for (Hazard h : hazardRepository.findByEdgeIdIsNotNullAndHiddenFalseAndStatusNot(HazardStatus.REJECTED)) {
            if (h.getResolvedAt() != null && h.getResolvedAt().isBefore(hideBefore)) {
                continue;   // P-11 개선완료 30일 지나면 지도에서 숨김 → 경로에서도 제외
            }
            risk.merge(h.getEdgeId(), riskService.scoreAt(h, querySlot), Double::sum);
            ids.computeIfAbsent(h.getEdgeId(), k -> new ArrayList<>()).add(h.getId());
        }
        ids.values().forEach(list -> list.sort(Long::compare));
        return new EdgeRisks(risk, ids);
    }

    private record EdgeRisks(Map<Long, Double> byEdge, Map<Long, List<Long>> hazardsByEdge) {

        double risk(long edgeId) {
            return byEdge.getOrDefault(edgeId, 0.0);
        }

        List<Long> hazardIds(long edgeId) {
            return hazardsByEdge.getOrDefault(edgeId, List.of());
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
