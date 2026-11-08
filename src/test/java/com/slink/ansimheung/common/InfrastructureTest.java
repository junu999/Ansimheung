package com.slink.ansimheung.common;

import com.slink.ansimheung.config.AnsimheungProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 설정값 바인딩 · /api/config · Flyway 스키마가 제대로 올라오는지 (DB 필요) */
@SpringBootTest
@AutoConfigureMockMvc
class InfrastructureTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AnsimheungProperties props;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void application_yml_정책값이_바인딩된다() {
        assertThat(props.area().schoolName()).isEqualTo("정왕초등학교");
        assertThat(props.area().radiusM()).isEqualTo(500);
        assertThat(props.merge().radiusM()).isEqualTo(20);
        assertThat(props.limits().reportsPerDay()).isEqualTo(10);
        assertThat(props.route().lambdaSafest()).isEqualTo(60);
        assertThat(props.marker().highThreshold()).isEqualTo(3.0);
    }

    @Test
    void config_API는_학교_좌표와_경로_모드를_준다() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.school.name").value("정왕초등학교"))
                .andExpect(jsonPath("$.school.lat").value(37.3408))
                .andExpect(jsonPath("$.school.lng").value(126.7430))
                .andExpect(jsonPath("$.radiusM").value(500.0))
                .andExpect(jsonPath("$.routeModes[2].mode").value("SAFEST"))
                .andExpect(jsonPath("$.routeModes[2].lambda").value(60.0));
    }

    @Test
    void Flyway_V1_테이블이_모두_생성된다() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("users", "nodes", "edges", "hazards", "reports", "photos",
                "empathies", "status_histories", "notifications", "public_layers", "flyway_schema_history");
    }

    @Test
    void 위치_컬럼에_GiST_공간_인덱스가_있다() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexdef ILIKE '%USING gist%'", String.class);
        assertThat(indexes).contains("ix_hazards_location", "ix_nodes_geom", "ix_edges_geom", "ix_public_layers_geom");
    }

    @Test
    void 같은_사용자는_같은_지점에_한_번만_공감할_수_있다() {
        Integer pk = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_name = 'empathies' AND constraint_type = 'PRIMARY KEY'", Integer.class);
        assertThat(pk).isEqualTo(1);
    }
}
