package com.slink.ansimheung.common.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * JTS 도형 만들기 (WGS84, SRID 4326).
 * ⚠️ JTS 좌표는 (x=경도, y=위도) 순서. 이 클래스의 메서드는 (lat, lng) 로 받아서 헷갈리지 않게 한다.
 */
public final class Geometries {

    public static final int SRID = 4326;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    private Geometries() {
    }

    public static Point point(double lat, double lng) {
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    /** latLngs = [[lat, lng], ...] */
    public static LineString lineString(double[][] latLngs) {
        Coordinate[] coords = new Coordinate[latLngs.length];
        for (int i = 0; i < latLngs.length; i++) {
            coords[i] = new Coordinate(latLngs[i][1], latLngs[i][0]);
        }
        return FACTORY.createLineString(coords);
    }

    public static double lat(Point p) {
        return p.getY();
    }

    public static double lng(Point p) {
        return p.getX();
    }
}
