package com.slink.ansimheung.route;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버 시작 시 DB nodes·edges(Flyway V3) → 메모리 {@link Graph} 로 한 번 적재 (docs/domain.md 7장 5).
 * 경로 API(#13)는 요청마다 {@link #graph()} 로 꺼내 Dijkstra 를 돌린다. 사전 계산·캐시 없음.
 */
@Component
public class GraphLoader {

    private static final Logger log = LoggerFactory.getLogger(GraphLoader.class);

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private volatile Graph graph = Graph.EMPTY;

    public GraphLoader(NodeRepository nodeRepository, EdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        Graph loaded = Graph.of(
                nodeRepository.findAll().stream()
                        .map(n -> new Graph.Vertex(n.getId(), n.getLat(), n.getLng()))
                        .toList(),
                edgeRepository.findAll().stream()
                        .map(e -> new Graph.Segment(e.getId(), e.getFromNodeId(), e.getToNodeId(), e.getLengthM()))
                        .toList());
        graph = loaded;

        if (loaded.isEmpty()) {
            log.warn("도보 그래프가 비어 있음 — 경로 API는 NO_GRAPH(503). Flyway V3__walk_graph.sql 적용 여부 확인");
            return;
        }
        log.info("도보 그래프 적재: 노드 {}개 · 구간 {}개 · 고립 노드 {}개 · 연결 {}",
                loaded.nodeCount(), loaded.edgeCount(), loaded.isolatedNodeCount(),
                loaded.isConnected() ? "하나로 연결됨" : "⚠️ 끊긴 덩어리 있음");
    }

    /** @throws ApiException NO_GRAPH(503) 그래프가 비어 있을 때 */
    public Graph graph() {
        Graph g = graph;
        if (g.isEmpty()) {
            throw new ApiException(ErrorCode.NO_GRAPH);
        }
        return g;
    }
}
