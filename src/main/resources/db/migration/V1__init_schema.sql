-- =====================================================================
-- V1: 안심흥 초기 스키마 (상세 기획서 10장 데이터 설계 + 노션 API 명세 기준)
-- 규칙: 이미 머지된 마이그레이션 파일은 절대 수정하지 않는다.
--       바꿀 게 생기면 V2__설명.sql 처럼 새 파일을 추가한다.
-- 좌표: geometry(..., 4326) = WGS84 경도/위도. 거리 계산은 ::geography 로 캐스팅해서 m 단위.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------
-- 회원
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                BIGSERIAL    PRIMARY KEY,
    email             VARCHAR(100) NOT NULL,                  -- V-01 소문자로 저장
    password_hash     VARCHAR(100) NOT NULL,                  -- BCrypt
    nickname          VARCHAR(12)  NOT NULL,                  -- V-04
    role              VARCHAR(10)  NOT NULL DEFAULT 'USER',
    age_confirmed     BOOLEAN      NOT NULL,                  -- P-10 만 14세 이상
    privacy_agreed_at TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_age CHECK (age_confirmed)
);

-- ---------------------------------------------------------------------
-- 도보 그래프 (F-DATA-01, F-ROUTE-01)
-- ---------------------------------------------------------------------
CREATE TABLE nodes (
    id   BIGSERIAL             PRIMARY KEY,
    geom geometry(Point, 4326) NOT NULL
);
CREATE INDEX ix_nodes_geom ON nodes USING GIST (geom);

CREATE TABLE edges (
    id        BIGSERIAL                  PRIMARY KEY,
    from_node BIGINT                     NOT NULL REFERENCES nodes (id) ON DELETE CASCADE,
    to_node   BIGINT                     NOT NULL REFERENCES nodes (id) ON DELETE CASCADE,
    length_m  DOUBLE PRECISION           NOT NULL,
    geom      geometry(LineString, 4326) NOT NULL,
    CONSTRAINT ck_edges_length CHECK (length_m >= 0),
    CONSTRAINT ck_edges_not_loop CHECK (from_node <> to_node)
);
CREATE INDEX ix_edges_geom ON edges USING GIST (geom);
CREATE INDEX ix_edges_from ON edges (from_node);
CREATE INDEX ix_edges_to ON edges (to_node);

-- ---------------------------------------------------------------------
-- 위험 지점 = 지도 마커 1개 (공감·처리상태·점수는 여기에)
-- ---------------------------------------------------------------------
CREATE TABLE hazards (
    id               BIGSERIAL             PRIMARY KEY,
    location         geometry(Point, 4326) NOT NULL,
    type             VARCHAR(20)           NOT NULL,
    time_slot        VARCHAR(10)           NOT NULL,
    status           VARCHAR(10)           NOT NULL DEFAULT 'RECEIVED',
    hidden           BOOLEAN               NOT NULL DEFAULT FALSE,
    hidden_reason    VARCHAR(200),                            -- V-11
    base_score       DOUBLE PRECISION      NOT NULL DEFAULT 0,  -- W×R×E×S (시간대 T는 조회 시 곱함)
    empathy_count    INTEGER               NOT NULL DEFAULT 0,  -- 캐시값: 공감 변경과 같은 트랜잭션에서 갱신
    report_count     INTEGER               NOT NULL DEFAULT 0,  -- 캐시값: 제보 변경과 같은 트랜잭션에서 갱신
    edge_id          BIGINT                REFERENCES edges (id) ON DELETE SET NULL,  -- C-09 가장 가까운 구간 1개
    created_at       TIMESTAMPTZ           NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ           NOT NULL DEFAULT now(),  -- 최근성 R 기준 (마지막 제보·공감)
    resolved_at      TIMESTAMPTZ,                                   -- P-11 개선완료 30일 후 지도에서 숨김
    CONSTRAINT ck_hazards_type CHECK (type IN ('SIDEWALK_GAP', 'ILLEGAL_PARKING', 'SPEEDING', 'CONSTRUCTION', 'POOR_LIGHTING')),
    CONSTRAINT ck_hazards_time_slot CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'NIGHT', 'ALWAYS')),
    CONSTRAINT ck_hazards_status CHECK (status IN ('RECEIVED', 'CHECKING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_hazards_counts CHECK (empathy_count >= 0 AND report_count >= 0)
);
CREATE INDEX ix_hazards_location ON hazards USING GIST (location);
CREATE INDEX ix_hazards_status_hidden ON hazards (status, hidden);
CREATE INDEX ix_hazards_edge ON hazards (edge_id);

-- ---------------------------------------------------------------------
-- 제보 = 사용자가 등록한 1건 (사진·작성자는 여기에). 여러 제보가 위험 지점 1개로 묶임
-- ---------------------------------------------------------------------
CREATE TABLE reports (
    id          BIGSERIAL             PRIMARY KEY,
    hazard_id   BIGINT                NOT NULL REFERENCES hazards (id) ON DELETE CASCADE,
    user_id     BIGINT                REFERENCES users (id) ON DELETE SET NULL,  -- P-11 탈퇴 시 작성자 없이 유지
    location    geometry(Point, 4326) NOT NULL,                                  -- 제보자가 찍은 원래 위치
    time_slot   VARCHAR(10)           NOT NULL,
    description VARCHAR(200),                                                    -- V-09
    deleted     BOOLEAN               NOT NULL DEFAULT FALSE,                    -- P-09 소프트 삭제
    created_at  TIMESTAMPTZ           NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_time_slot CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'NIGHT', 'ALWAYS'))
);
CREATE INDEX ix_reports_hazard ON reports (hazard_id);
CREATE INDEX ix_reports_user_created ON reports (user_id, created_at);  -- C-05 일일 한도, 내 제보 목록
-- 같은 사람이 같은 지점에 두 번 제보 불가 → DUP_REPORT
CREATE UNIQUE INDEX ux_reports_hazard_user ON reports (hazard_id, user_id) WHERE deleted = FALSE AND user_id IS NOT NULL;

CREATE TABLE photos (
    id           BIGSERIAL    PRIMARY KEY,
    report_id    BIGINT       NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    file_path    VARCHAR(255) NOT NULL,              -- uploads/ 아래 UUID 파일명
    content_type VARCHAR(20)  NOT NULL,              -- image/jpeg | image/png
    approved     BOOLEAN      NOT NULL DEFAULT FALSE, -- P-08 관리자 승인 전 비공개
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_photos_report UNIQUE (report_id),  -- 제보 1건당 사진 0~1장
    CONSTRAINT ck_photos_content_type CHECK (content_type IN ('image/jpeg', 'image/png'))
);

-- ---------------------------------------------------------------------
-- 공감 (1인 1지점 1회 → 복합 PK 로 DB 가 막음, P-03)
-- ---------------------------------------------------------------------
CREATE TABLE empathies (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    hazard_id  BIGINT      NOT NULL REFERENCES hazards (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, hazard_id)
);
CREATE INDEX ix_empathies_hazard ON empathies (hazard_id);
CREATE INDEX ix_empathies_user_created ON empathies (user_id, created_at);  -- C-06 일일 한도

-- ---------------------------------------------------------------------
-- 처리 이력 · 알림
-- ---------------------------------------------------------------------
CREATE TABLE status_histories (
    id          BIGSERIAL   PRIMARY KEY,
    hazard_id   BIGINT      NOT NULL REFERENCES hazards (id) ON DELETE CASCADE,
    from_status VARCHAR(10),                                          -- 최초 생성은 NULL
    to_status   VARCHAR(10) NOT NULL,
    admin_id    BIGINT      REFERENCES users (id) ON DELETE SET NULL, -- 시스템이 만든 이력은 NULL
    memo        VARCHAR(200),                                         -- V-10
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_histories_to CHECK (to_status IN ('RECEIVED', 'CHECKING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_histories_from CHECK (from_status IS NULL OR from_status IN ('RECEIVED', 'CHECKING', 'RESOLVED', 'REJECTED'))
);
CREATE INDEX ix_histories_hazard ON status_histories (hazard_id, created_at);

CREATE TABLE notifications (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    hazard_id  BIGINT       REFERENCES hazards (id) ON DELETE SET NULL,
    message    VARCHAR(200) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, is_read, created_at DESC);

-- ---------------------------------------------------------------------
-- 공공데이터 레이어 (지도 표시만, 위험점수에 넣지 않음)
-- ---------------------------------------------------------------------
CREATE TABLE public_layers (
    id     BIGSERIAL                PRIMARY KEY,
    kind   VARCHAR(20)              NOT NULL,
    name   VARCHAR(100),
    source VARCHAR(100),                       -- 출처 (공공누리 표시용)
    geom   geometry(Geometry, 4326) NOT NULL,  -- CCTV·보안등 = Point, 보호구역 = Polygon
    CONSTRAINT ck_public_layers_kind CHECK (kind IN ('CCTV', 'LIGHT', 'SCHOOL_ZONE'))
);
CREATE INDEX ix_public_layers_geom ON public_layers USING GIST (geom);
CREATE INDEX ix_public_layers_kind ON public_layers (kind);
