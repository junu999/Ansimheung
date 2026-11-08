package com.slink.ansimheung.config;

import com.slink.ansimheung.common.type.RouteMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 정책값 (application.yml 의 ansimheung.*).
 * 노션 "상수(C) · 검증 규칙(V)" 표와 1:1 대응 — 값을 바꿀 때는 yml 만 고친다.
 * 사용: 생성자 주입 {@code public FooService(AnsimheungProperties props)}
 */
@ConfigurationProperties(prefix = "ansimheung")
public record AnsimheungProperties(
        Area area,
        Merge merge,
        Risk risk,
        Limits limits,
        Route route,
        Marker marker,
        Hazard hazard,
        Upload upload,
        Paging paging
) {

    /** P-05 시범 지역 */
    public record Area(String schoolName, String schoolAddress, double centerLat, double centerLng, double radiusM) {
    }

    /** P-01 중복 묶기 */
    public record Merge(double radiusM) {
    }

    /** C-02 최근성, C-12 공감도, C-04 시간대 가중치 */
    public record Risk(double recencyMax, double recencyMin, double recencyDecayDays,
                       double empathyMax, double timeMatch, double timeAlways, double timeOther) {
    }

    /** C-05, C-06 */
    public record Limits(int reportsPerDay, int empathiesPerDay) {
    }

    /** C-07 λ, C-09 구간 배정, C-10 스냅, C-11 보행 속도 */
    public record Route(double lambdaShortest, double lambdaBalanced, double lambdaSafest,
                        double edgeAssignMaxM, double snapMaxM, double walkMPerMin) {

        public double lambda(RouteMode mode) {
            return switch (mode) {
                case SHORTEST -> lambdaShortest;
                case BALANCED -> lambdaBalanced;
                case SAFEST -> lambdaSafest;
            };
        }
    }

    /** C-08 */
    public record Marker(double highThreshold) {
    }

    /** P-11 */
    public record Hazard(int resolvedHideDays) {
    }

    /** P-04 (파일 크기 5MB 는 spring.servlet.multipart) */
    public record Upload(String dir, int maxLongSidePx) {
    }

    /** C-14 */
    public record Paging(int defaultSize, int maxSize) {
    }
}
