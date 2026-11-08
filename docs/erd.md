# ERD · DB 스키마

> 실제 스키마의 기준은 `src/main/resources/db/migration/V1__init_schema.sql` 입니다. 이 문서는 그림으로 보는 용도예요.
> 스키마를 바꾸려면 V1 을 고치지 말고 `V2__설명.sql` 을 새로 추가하세요.

```mermaid
erDiagram
    users ||--o{ reports : "작성 (탈퇴 시 NULL)"
    users ||--o{ empathies : "공감"
    users ||--o{ notifications : "받음"
    users ||--o{ status_histories : "처리(관리자)"
    hazards ||--|{ reports : "묶임"
    hazards ||--o{ empathies : ""
    hazards ||--o{ status_histories : ""
    hazards ||--o{ notifications : ""
    edges |o--o{ hazards : "가장 가까운 구간 1개 (25m)"
    reports ||--o| photos : "0~1장"
    nodes ||--o{ edges : "from / to"

    users {
        bigserial id PK
        varchar email UK "소문자 저장 (V-01)"
        varchar password_hash "BCrypt"
        varchar nickname "2~12자 (V-04)"
        varchar role "USER | ADMIN"
        boolean age_confirmed "항상 true (P-10)"
        timestamptz privacy_agreed_at
        timestamptz created_at
    }
    hazards {
        bigserial id PK
        geometry location "Point 4326, GiST"
        varchar type "5종"
        varchar time_slot "MORNING|AFTERNOON|NIGHT|ALWAYS"
        varchar status "RECEIVED|CHECKING|RESOLVED|REJECTED"
        boolean hidden
        varchar hidden_reason
        double base_score "W x R x E x S"
        int empathy_count "캐시"
        int report_count "캐시"
        bigint edge_id FK
        timestamptz created_at
        timestamptz last_activity_at "최근성 R 기준"
        timestamptz resolved_at "30일 후 숨김 (P-11)"
    }
    reports {
        bigserial id PK
        bigint hazard_id FK
        bigint user_id FK "NULL 가능"
        geometry location "제보자가 찍은 위치"
        varchar time_slot
        varchar description "200자 (V-09)"
        boolean deleted "소프트 삭제"
        timestamptz created_at
    }
    photos {
        bigserial id PK
        bigint report_id FK,UK
        varchar file_path "uploads/ UUID"
        varchar content_type "jpeg | png"
        boolean approved "승인 전 비공개 (P-08)"
        timestamptz created_at
    }
    empathies {
        bigint user_id PK,FK
        bigint hazard_id PK,FK
        timestamptz created_at
    }
    status_histories {
        bigserial id PK
        bigint hazard_id FK
        varchar from_status "최초 NULL"
        varchar to_status
        bigint admin_id FK
        varchar memo "200자 (V-10)"
        timestamptz created_at
    }
    notifications {
        bigserial id PK
        bigint user_id FK
        bigint hazard_id FK
        varchar message
        boolean is_read
        timestamptz created_at
    }
    nodes {
        bigserial id PK
        geometry geom "Point, GiST"
    }
    edges {
        bigserial id PK
        bigint from_node FK
        bigint to_node FK
        double length_m
        geometry geom "LineString, GiST"
    }
    public_layers {
        bigserial id PK
        varchar kind "CCTV | LIGHT | SCHOOL_ZONE"
        varchar name
        varchar source
        geometry geom "Point 또는 Polygon"
    }
```

## 설계 포인트

| 포인트 | 이유 |
|---|---|
| **제보(reports) ≠ 위험 지점(hazards)** | 공감·처리상태·점수는 지점에, 사진·작성자는 제보에. 지도 마커 1개 = 지점 1개 |
| **`base_score`만 저장** | 시간대 가중치 T는 조회 시각마다 달라서 조회할 때 곱함 |
| **`empathies` 복합 PK** | 같은 사람이 같은 지점에 두 번 공감 못 하게 DB가 막음 (P-03) |
| **`ux_reports_hazard_user` 부분 유니크** | 같은 사람이 같은 지점에 두 번 제보 → `DUP_REPORT` (삭제된 제보는 제외) |
| **`empathy_count`·`report_count` 캐시** | 지도 조회 때마다 COUNT 안 하려고. 변경과 **같은 트랜잭션**에서 갱신 |
| **`reports.user_id` NULL 허용** | 탈퇴해도 제보는 작성자 없이 남김 (P-11) |
| **GiST 공간 인덱스** | 반경 20m 묶기, bbox 조회, 가장 가까운 구간 찾기를 빠르게 |
| **enum 은 VARCHAR + CHECK** | JPA `@Enumerated(EnumType.STRING)` 과 맞추고, 잘못된 값은 DB가 거부 |
| **경로 출발지 테이블 없음** | 출발지(사실상 집 주소)는 저장하지 않음 (P-12) |

## JPA 엔티티 만들 때

- 좌표: `org.locationtech.jts.geom.Point` + `@Column(columnDefinition = "geometry(Point,4326)")` (hibernate-spatial)
- enum: `@Enumerated(EnumType.STRING)` + `com.slink.ansimheung.common.type.*` 사용
- 시간: `OffsetDateTime` (응답이 `+09:00` 형식으로 나감)
- `ddl-auto: validate` 라서 엔티티와 테이블이 다르면 서버가 안 뜸 → 마이그레이션을 먼저 추가
