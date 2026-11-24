package com.slink.ansimheung.route;

import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flyway V3__walk_graph.sql 이 넣은 실제 도보 그래프가 메모리에 제대로 올라오는지 (DB 필요) */
@SpringBootTest
class GraphLoaderTest {

    @Autowired GraphLoader graphLoader;
    @Autowired NodeRepository nodeRepository;
    @Autowired EdgeRepository edgeRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnsimheungProperties props;

    @Test
    void 서버_시작_시_DB의_노드_구간이_모두_메모리에_올라온다() {
        Graph g = graphLoader.graph();

        assertThat(g.nodeCount()).isPositive()
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM nodes", Integer.class));
        assertThat(g.edgeCount()).isPositive()
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM edges", Integer.class));
    }

    @Test
    void 그래프는_하나로_연결되고_고립_노드가_없다() {
        Graph g = graphLoader.graph();

        assertThat(g.isolatedNodeCount()).isZero();
        assertThat(g.isConnected()).isTrue();
    }

    @Test
    void 모든_노드는_시범_지역_반경과_스냅_거리_안에_있다() {
        double keepM = props.area().radiusM() + props.route().snapMaxM();
        Integer outside = jdbc.queryForObject("""
                SELECT count(*) FROM nodes
                WHERE NOT ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                """, Integer.class, props.area().centerLng(), props.area().centerLat(), keepM + 1);

        assertThat(outside).isZero();
    }

    @Test
    void 구간_길이는_PostGIS_geography_길이로_채워져_있다() {
        Integer bad = jdbc.queryForObject("""
                SELECT count(*) FROM edges
                WHERE length_m <= 0 OR abs(length_m - ST_Length(geom::geography)) > 0.01
                """, Integer.class);

        assertThat(bad).isZero();
    }

    @Test
    void 그래프가_비어_있으면_NO_GRAPH() {
        GraphLoader notLoaded = new GraphLoader(nodeRepository, edgeRepository);

        assertThatThrownBy(notLoaded::graph)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NO_GRAPH));
    }
}
