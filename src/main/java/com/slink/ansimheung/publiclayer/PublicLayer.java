package com.slink.ansimheung.publiclayer;

import com.slink.ansimheung.common.type.PublicLayerKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Geometry;

/** 공공데이터 레이어 (public_layers) — 지도 표시만, 위험점수에 넣지 않음. 이번 제출 범위 제외(#28) */
@Entity
@Table(name = "public_layers")
public class PublicLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicLayerKind kind;

    @Column(length = 100)
    private String name;

    /** 출처 (공공누리 표시용) */
    @Column(length = 100)
    private String source;

    /** CCTV·보안등 = Point, 보호구역 = Polygon */
    @Column(nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geom;

    protected PublicLayer() {
    }

    public static PublicLayer of(PublicLayerKind kind, String name, String source, Geometry geom) {
        PublicLayer p = new PublicLayer();
        p.kind = kind;
        p.name = name;
        p.source = source;
        p.geom = geom;
        return p;
    }

    public Long getId() {
        return id;
    }

    public PublicLayerKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public Geometry getGeom() {
        return geom;
    }
}
