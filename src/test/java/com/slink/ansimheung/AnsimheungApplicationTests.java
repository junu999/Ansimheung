package com.slink.ansimheung;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링이 뜨고 DB · PostGIS 에 연결되는지 확인.
 * 로컬: docker compose up -d 후 실행 / CI: GitHub Actions 가 PostGIS 컨테이너를 띄워서 실행
 */
@SpringBootTest
class AnsimheungApplicationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndPostgisWorks() {
        String version = jdbcTemplate.queryForObject("SELECT PostGIS_Lib_Version()", String.class);
        assertThat(version).isNotBlank();
    }
}
