package com.slink.ansimheung.route;

import com.slink.ansimheung.route.Dijkstra.Path;
import com.slink.ansimheung.route.Graph.Link;
import com.slink.ansimheung.route.Graph.Segment;
import com.slink.ansimheung.route.Graph.Vertex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 순수 단위 테스트. docs/domain.md 7장 "노드 5개 그래프":
 * A 골목 200m · 위험 15 / B 큰길 350m · 위험 2
 * <pre>
 *        a (골목)
 *   S ──────────── T      A: S─a 100m(위험 15) + a─T 100m
 *    \            /
 *     b1 ──── b2          B: S─b1 100m + b1─b2 150m(위험 2) + b2─T 100m
 * </pre>
 */
class DijkstraTest {

    private static final long S = 1, A = 2, B1 = 3, B2 = 4, T = 5;
    private static final long SA = 10, AT = 11, SB1 = 20, B1B2 = 21, B2T = 22;

    private static final Graph GRAPH = Graph.of(
            List.of(new Vertex(S, 0, 0), new Vertex(A, 0, 1), new Vertex(B1, 1, 0),
                    new Vertex(B2, 1, 1), new Vertex(T, 0, 2)),
            List.of(new Segment(SA, S, A, 100), new Segment(AT, A, T, 100),
                    new Segment(SB1, S, B1, 100), new Segment(B1B2, B1, B2, 150), new Segment(B2T, B2, T, 100)));

    private static final Map<Long, Double> EDGE_RISK = Map.of(SA, 15.0, B1B2, 2.0);

    private static ToDoubleFunction<Link> cost(double lambda) {
        return l -> l.lengthM() + lambda * EDGE_RISK.getOrDefault(l.edgeId(), 0.0);
    }

    @ParameterizedTest(name = "λ={0} → 비용 {1}, {2} 선택")
    @CsvSource({
            "0,  200, A",
            "20, 390, B",
            "60, 470, B",
    })
    void 문서_기대값_그대로(double lambda, double expectedCost, String route) {
        Path p = Dijkstra.shortestPath(GRAPH, S, T, cost(lambda)).orElseThrow();

        assertThat(p.cost()).isEqualTo(expectedCost);
        assertThat(p.nodeIds()).isEqualTo(route.equals("A") ? List.of(S, A, T) : List.of(S, B1, B2, T));
    }

    @Test
    void 지나는_구간이_순서대로_나온다() {
        Path p = Dijkstra.shortestPath(GRAPH, S, T, cost(60)).orElseThrow();

        assertThat(p.links()).extracting(Link::edgeId).containsExactly(SB1, B1B2, B2T);
    }

    @Test
    void 반대_방향도_같은_길() {
        Path p = Dijkstra.shortestPath(GRAPH, T, S, cost(0)).orElseThrow();

        assertThat(p.nodeIds()).containsExactly(T, A, S);
    }

    @Test
    void 출발_도착이_같으면_노드_1개_구간_0개() {
        Path p = Dijkstra.shortestPath(GRAPH, S, S, cost(0)).orElseThrow();

        assertThat(p.nodeIds()).containsExactly(S);
        assertThat(p.links()).isEmpty();
        assertThat(p.cost()).isZero();
    }

    @Test
    void 이어지지_않으면_empty() {
        Graph cut = Graph.of(List.of(new Vertex(1, 0, 0), new Vertex(2, 0, 1), new Vertex(3, 5, 5)),
                List.of(new Segment(1, 1, 2, 10)));

        assertThat(Dijkstra.shortestPath(cut, 1, 3, l -> l.lengthM())).isEmpty();
    }

    @Test
    void 없는_노드면_empty() {
        assertThat(Dijkstra.shortestPath(GRAPH, S, 99, cost(0))).isEmpty();
    }
}
