package com.slink.ansimheung.route;

import com.slink.ansimheung.route.Graph.Link;
import com.slink.ansimheung.route.Graph.Segment;
import com.slink.ansimheung.route.Graph.Vertex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 순수 단위 테스트 (Spring·DB 없음) */
class GraphTest {

    private static final List<Vertex> THREE = List.of(
            new Vertex(1, 37.3408, 126.7430),
            new Vertex(2, 37.3410, 126.7432),
            new Vertex(3, 37.3412, 126.7434));

    @Test
    void 구간은_양방향으로_들어간다() {
        Graph g = Graph.of(THREE, List.of(new Segment(10, 1, 2, 30.0), new Segment(11, 2, 3, 25.5)));

        assertThat(g.nodeCount()).isEqualTo(3);
        assertThat(g.edgeCount()).isEqualTo(2);
        assertThat(g.links(1)).containsExactly(new Link(10, 2, 30.0));
        assertThat(g.links(2)).containsExactlyInAnyOrder(new Link(10, 1, 30.0), new Link(11, 3, 25.5));
        assertThat(g.links(3)).containsExactly(new Link(11, 2, 25.5));
        assertThat(g.vertex(2).lat()).isEqualTo(37.3410);
    }

    @Test
    void 모두_이어지면_연결됨_고립_0() {
        Graph g = Graph.of(THREE, List.of(new Segment(10, 1, 2, 30.0), new Segment(11, 2, 3, 25.5)));

        assertThat(g.isConnected()).isTrue();
        assertThat(g.isolatedNodeCount()).isZero();
    }

    @Test
    void 구간이_없는_노드는_고립으로_센다() {
        Graph g = Graph.of(THREE, List.of(new Segment(10, 1, 2, 30.0)));

        assertThat(g.isConnected()).isFalse();
        assertThat(g.isolatedNodeCount()).isEqualTo(1);
    }

    @Test
    void 없는_노드를_가리키는_구간은_거부() {
        assertThatThrownBy(() -> Graph.of(THREE, List.of(new Segment(10, 1, 99, 30.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈_그래프() {
        assertThat(Graph.EMPTY.isEmpty()).isTrue();
        assertThat(Graph.EMPTY.isConnected()).isFalse();
        assertThat(Graph.EMPTY.links(1)).isEmpty();
        assertThat(Graph.EMPTY.vertex(1)).isNull();
    }
}
