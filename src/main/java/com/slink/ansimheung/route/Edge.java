package com.slink.ansimheung.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.LineString;

/** 도보 그래프 구간 = 걸을 수 있는 길 토막 (edges). 방향 없음 — 그래프에 올릴 때 양방향으로 넣는다 */
@Entity
@Table(name = "edges")
public class Edge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_node", nullable = false)
    private Long fromNodeId;

    @Column(name = "to_node", nullable = false)
    private Long toNodeId;

    @Column(name = "length_m", nullable = false)
    private double lengthM;

    @Column(nullable = false, columnDefinition = "geometry(LineString,4326)")
    private LineString geom;

    protected Edge() {
    }

    public static Edge of(Long fromNodeId, Long toNodeId, double lengthM, LineString geom) {
        Edge e = new Edge();
        e.fromNodeId = fromNodeId;
        e.toNodeId = toNodeId;
        e.lengthM = lengthM;
        e.geom = geom;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Long getFromNodeId() {
        return fromNodeId;
    }

    public Long getToNodeId() {
        return toNodeId;
    }

    public double getLengthM() {
        return lengthM;
    }

    public LineString getGeom() {
        return geom;
    }
}
