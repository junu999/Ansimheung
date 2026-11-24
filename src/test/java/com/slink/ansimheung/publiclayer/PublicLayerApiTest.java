package com.slink.ansimheung.publiclayer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 테스트 (@SpringBootTest + @Transactional). 데이터는 Flyway V2__public_layers.sql 이 넣은 실제 공공데이터 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicLayerApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 어린이보호구역에_정왕초가_GeoJSON_경도_위도_순서로_나온다() throws Exception {
        mockMvc.perform(get("/api/public-layers").param("kind", "SCHOOL_ZONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features", hasSize(11)))
                .andExpect(jsonPath("$.features[*].properties.kind", hasItem("SCHOOL_ZONE")))
                .andExpect(jsonPath("$.features[0].properties.name").value("정왕초등학교"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(126.7432287))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(37.3409187))
                .andExpect(jsonPath("$.features[0].properties.source").value("공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)"));
    }

    @Test
    void 불법주정차_단속_CCTV는_15건() throws Exception {
        mockMvc.perform(get("/api/public-layers").param("kind", "CCTV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features", hasSize(15)));
    }

    @Test
    void kind_없이_부르면_전체_로그인_필요_없음() throws Exception {
        mockMvc.perform(get("/api/public-layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features", hasSize(26)));
    }

    @Test
    void 잘못된_kind는_400_VALIDATION() throws Exception {
        mockMvc.perform(get("/api/public-layers").param("kind", "LAMP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }
}
