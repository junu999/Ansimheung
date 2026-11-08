package com.slink.ansimheung.common;

import com.slink.ansimheung.common.type.RouteMode;
import com.slink.ansimheung.config.AnsimheungProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * GET /api/config — 프론트가 쓰는 공개 설정값.
 * 학교 좌표·반경 같은 값을 프론트에 하드코딩하지 않고 서버 설정(application.yml) 한 곳에서 관리하기 위함.
 */
@RestController
public class ConfigController {

    private final AnsimheungProperties props;

    public ConfigController(AnsimheungProperties props) {
        this.props = props;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        var area = props.area();
        List<RouteModeConfig> modes = Arrays.stream(RouteMode.values())
                .map(mode -> new RouteModeConfig(mode, props.route().lambda(mode)))
                .toList();
        return new PublicConfig(
                new School(area.schoolName(), area.schoolAddress(), area.centerLat(), area.centerLng()),
                area.radiusM(),
                props.merge().radiusM(),
                props.marker().highThreshold(),
                modes,
                props.route().walkMPerMin(),
                new Limits(props.limits().reportsPerDay(), props.limits().empathiesPerDay())
        );
    }

    public record PublicConfig(School school, double radiusM, double mergeRadiusM, double markerHighThreshold,
                               List<RouteModeConfig> routeModes, double walkMPerMin, Limits limits) {
    }

    public record School(String name, String address, double lat, double lng) {
    }

    public record RouteModeConfig(RouteMode mode, double lambda) {
    }

    public record Limits(int reportsPerDay, int empathiesPerDay) {
    }
}
