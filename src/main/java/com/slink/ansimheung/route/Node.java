package com.slink.ansimheung.route;

import com.slink.ansimheung.common.geo.Geometries;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

/** 도보 그래프 노드 = 교차로·횡단보도 끝 (nodes). 데이터는 #8 에서 Flyway 로 적재 */
@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point geom;

    protected Node() {
    }

    public static Node at(double lat, double lng) {
        Node n = new Node();
        n.geom = Geometries.point(lat, lng);
        return n;
    }

    public Long getId() {
        return id;
    }

    public Point getGeom() {
        return geom;
    }

    public double getLat() {
        return Geometries.lat(geom);
    }

    public double getLng() {
        return Geometries.lng(geom);
    }
}
