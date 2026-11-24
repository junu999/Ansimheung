#!/usr/bin/env python3
"""
OpenStreetMap 도보 길 → 도보 그래프(nodes·edges) Flyway 마이그레이션 생성 (#8, F-DATA-01).

규칙
  - 노드 = 교차점(길 2개 이상이 만나는 점)과 길 끝점. 구간 = 노드와 노드 사이 길 토막(LineString).
  - 정왕초 반경 KEEP_RADIUS_M 안에 양 끝 노드가 모두 있는 구간만 남긴다.
    시범 지역은 500m 지만 출발지 스냅(C-10)이 50m 까지라 경계 바깥 50m 까지 남긴다.
  - 가장 큰 연결 덩어리만 남긴다 → 고립 노드 0개.
  - MAX_EDGE_M 보다 긴 구간은 같은 길이로 잘라 중간 노드를 넣는다 (출발지 스냅이 "가장 가까운 노드" 기준이라).
  - 구간 길이(length_m)는 SQL 에서 ST_Length(geom::geography) 로 계산한다.
  - 표준 라이브러리만 사용 (pip 설치 없음).

원본 OSM JSON 은 저장소에 올리지 않는다 (결과 SQL 만 커밋). 받는 방법:
  python3 scripts/build_walk_graph.py --fetch scripts/osm_ways.json      # Overpass API 에서 받아 저장
  python3 scripts/build_walk_graph.py --osm scripts/osm_ways.json \\
      > src/main/resources/db/migration/V3__walk_graph.sql
  (--geojson preview.geojson 을 주면 지도 확인용 GeoJSON 도 저장)

데이터 출처: © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright)
"""
import argparse
import json
import math
import sys
import urllib.parse
import urllib.request
from collections import defaultdict

SCHOOL = (37.3408, 126.7430)  # application.yml ansimheung.area.center-lat/lng 와 같은 값
AREA_RADIUS_M = 500           # ansimheung.area.radius-m
SNAP_MAX_M = 50               # ansimheung.route.snap-max-m
KEEP_RADIUS_M = AREA_RADIUS_M + SNAP_MAX_M
# 출발지는 "가장 가까운 노드"에 스냅하므로, 긴 구간 가운데를 누르면 길 위인데도 50m 를 넘는다.
# 구간을 이 길이 이하로 잘라 중간 노드를 넣는다 → 길 위 어느 점이든 노드까지 40m 이내
MAX_EDGE_M = 80
FETCH_RADIUS_M = 600          # 경계에 걸친 길도 끝까지 받으려고 조금 넓게
EARTH_RADIUS_M = 6_371_008.8

# 사람이 걸을 수 있는 길. motorway·trunk 는 이 지역에 없고, 있어도 보행 불가라 제외
WALKABLE = ("footway|pedestrian|path|steps|living_street|residential|service|unclassified|"
            "tertiary|tertiary_link|secondary|secondary_link|primary|primary_link|track|cycleway")
OVERPASS_URL = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = (f'[out:json][timeout:60];'
                  f'way(around:{FETCH_RADIUS_M},{SCHOOL[0]},{SCHOOL[1]})[highway~"^({WALKABLE})$"];'
                  f'out body geom;')


def distance_m(a, b):
    p = math.radians
    d_lat, d_lng = p(b[0] - a[0]), p(b[1] - a[1])
    h = math.sin(d_lat / 2) ** 2 + math.cos(p(a[0])) * math.cos(p(b[0])) * math.sin(d_lng / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


def walkable(tags):
    if tags.get("area") == "yes":                       # 광장 테두리는 길이 아님
        return False
    if tags.get("service") == "parking_aisle":          # 주차장 통로
        return False
    if tags.get("access") in ("private", "no") and tags.get("foot") not in ("yes", "designated"):
        return False
    return tags.get("foot") != "no"


def fetch(path):
    body = urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode()
    req = urllib.request.Request(OVERPASS_URL, data=body, headers={"User-Agent": "ansimheung-graph-builder"})
    with urllib.request.urlopen(req, timeout=120) as res, open(path, "wb") as f:
        f.write(res.read())
    print(f"저장: {path}", file=sys.stderr)


def build(ways):
    coord = {}                      # OSM 노드 id → (lat, lng)
    use = defaultdict(int)          # OSM 노드가 길에 몇 번 쓰였나 (교차점 판정)
    for w in ways:
        for nid, g in zip(w["nodes"], w["geometry"]):
            coord[nid] = (round(g["lat"], 7), round(g["lon"], 7))
        for i, nid in enumerate(w["nodes"]):
            use[nid] += 1 if 0 < i < len(w["nodes"]) - 1 else 2   # 끝점은 항상 노드

    # 길을 노드에서 잘라 구간으로
    segments = []                   # [OSM 노드 id, ...] 양 끝이 그래프 노드
    for w in ways:
        cur = [w["nodes"][0]]
        for nid in w["nodes"][1:]:
            cur.append(nid)
            if use[nid] >= 2:
                segments.append(cur)
                cur = [nid]
    # 시작=끝인 고리 구간은 DB 제약(from <> to)에 걸리므로 가운데 점을 노드로 만들어 둘로 나눈다
    split = []
    for s in segments:
        if s[0] == s[-1]:
            if len(s) < 3:
                continue
            m = len(s) // 2
            split += [s[:m + 1], s[m:]]
        else:
            split.append(s)

    # 반경 안 + 중복(같은 OSM 노드열) 제거
    seen, edges = set(), []
    for s in split:
        if any(distance_m(SCHOOL, coord[n]) > KEEP_RADIUS_M for n in (s[0], s[-1])):
            continue
        key = tuple(s) if s[0] < s[-1] else tuple(reversed(s))
        if key in seen:
            continue
        seen.add(key)
        edges.append(list(key))

    # 가장 큰 연결 덩어리만
    adj = defaultdict(set)
    for s in edges:
        adj[s[0]].add(s[-1])
        adj[s[-1]].add(s[0])
    best, visited = set(), set()
    for start in adj:
        if start in visited:
            continue
        comp, stack = {start}, [start]
        while stack:
            for nxt in adj[stack.pop()]:
                if nxt not in comp:
                    comp.add(nxt)
                    stack.append(nxt)
        visited |= comp
        if len(comp) > len(best):
            best = comp
    dropped = len(visited) - len(best)
    edges = [s for s in edges if s[0] in best]

    # 긴 구간 쪼개기. 새 점은 좌표 자체를 키로 쓴다 (OSM 노드 id 와 겹치지 않게 튜플)
    pieces = []                     # (시작 키, 끝 키, [(lat, lng), ...])
    for s in edges:
        pts = [coord[n] for n in s]
        for a, b, part in densify(pts):
            pieces.append((s[0] if a is None else a, s[-1] if b is None else b, part))
    for a, b, part in pieces:
        for key, p in ((a, part[0]), (b, part[-1])):
            coord.setdefault(key, p)
    keys = {k for a, b, _ in pieces for k in (a, b)}

    # DB id 는 학교에서 가까운 순으로 1부터 (읽기 쉽게, 실행마다 같은 결과)
    ordered = sorted(keys, key=lambda n: (round(distance_m(SCHOOL, coord[n]), 3), str(n)))
    node_id = {n: i + 1 for i, n in enumerate(ordered)}
    nodes = [(node_id[n], coord[n]) for n in ordered]
    out_edges = []
    for a, b, pts in sorted(pieces, key=lambda e: (min(node_id[e[0]], node_id[e[1]]),
                                                   max(node_id[e[0]], node_id[e[1]]), e[2])):
        length = sum(distance_m(p, q) for p, q in zip(pts, pts[1:]))
        out_edges.append((len(out_edges) + 1, node_id[a], node_id[b], pts, length))
    return nodes, out_edges, dropped


def densify(pts):
    """구간을 MAX_EDGE_M 이하 같은 길이 조각으로. 반환 (시작 키, 끝 키, 점들) — 원래 끝점은 None"""
    total = sum(distance_m(p, q) for p, q in zip(pts, pts[1:]))
    n = max(1, math.ceil(total / MAX_EDGE_M))
    if n == 1:
        return [(None, None, pts)]
    step, out, cur, walked, target = total / n, [], [pts[0]], 0.0, total / n
    for p, q in zip(pts, pts[1:]):
        d = distance_m(p, q)
        while len(out) < n - 1 and walked + d >= target:
            t = (target - walked) / d if d else 0
            cut = (round(p[0] + (q[0] - p[0]) * t, 7), round(p[1] + (q[1] - p[1]) * t, 7))
            cur.append(cut)
            out.append(cur)
            cur, target = [cut], target + step
        cur.append(q)
        walked += d
    out.append(cur)
    keys = [None] + [("cut", piece[-1]) for piece in out[:-1]] + [None]
    return [(keys[i], keys[i + 1], piece) for i, piece in enumerate(out)]


def to_sql(nodes, edges, dropped, ways):
    total_m = sum(e[4] for e in edges)
    kinds = defaultdict(int)
    for w in ways:
        kinds[w["tags"]["highway"]] += 1
    out = [
        "-- =====================================================================",
        "-- V3: 도보 그래프 (#8, F-DATA-01) — scripts/build_walk_graph.py 로 생성. 직접 고치지 말고 스크립트를 다시 돌릴 것",
        f"-- 범위: 정왕초({SCHOOL[0]}, {SCHOOL[1]}) 반경 {KEEP_RADIUS_M}m (시범 지역 {AREA_RADIUS_M}m + 출발지 스냅 {SNAP_MAX_M}m)",
        "-- 출처: © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright)",
        f"-- 원본 길 {len(ways)}개 (" + ", ".join(f"{k} {v}" for k, v in sorted(kinds.items(), key=lambda x: -x[1])) + ")",
        f"-- 결과: 노드 {len(nodes)}개 · 구간 {len(edges)}개 · 총 {total_m / 1000:.2f}km · 떨어진 덩어리에서 버린 노드 {dropped}개",
        f"-- 노드 = 교차점 · 길 끝 · {MAX_EDGE_M}m 넘는 구간의 중간 분할점 (출발지 스냅용). id 는 학교에서 가까운 순",
        "-- =====================================================================",
        "",
        "INSERT INTO nodes (id, geom) VALUES",
        ",\n".join(f"    ({i}, ST_SetSRID(ST_MakePoint({lng}, {lat}), 4326))" for i, (lat, lng) in nodes) + ";",
        "",
        "INSERT INTO edges (id, from_node, to_node, length_m, geom)",
        "SELECT id, from_node, to_node, ST_Length(geom::geography), geom FROM (VALUES",
        ",\n".join(
            f"    ({i}, {a}, {b}, ST_GeomFromText('LINESTRING(" + ", ".join(f"{lng} {lat}" for lat, lng in pts) + ")', 4326))"
            for i, a, b, pts, _ in edges) + "",
        ") AS v (id, from_node, to_node, geom);",
        "",
        "-- id 를 직접 넣었으므로 시퀀스를 맞춘다 (이후 JPA save 가 1번부터 다시 시작하지 않게)",
        "SELECT setval('nodes_id_seq', (SELECT max(id) FROM nodes));",
        "SELECT setval('edges_id_seq', (SELECT max(id) FROM edges));",
        "",
    ]
    return "\n".join(out)


def to_geojson(nodes, edges):
    feats = [{"type": "Feature", "properties": {"edge": i, "from": a, "to": b, "lengthM": round(m, 1)},
              "geometry": {"type": "LineString", "coordinates": [[lng, lat] for lat, lng in pts]}}
             for i, a, b, pts, m in edges]
    feats += [{"type": "Feature", "properties": {"node": i},
               "geometry": {"type": "Point", "coordinates": [lng, lat]}} for i, (lat, lng) in nodes]
    return {"type": "FeatureCollection", "features": feats}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fetch", metavar="OUT", help="Overpass API 에서 받아 OUT 에 저장하고 끝")
    ap.add_argument("--osm", metavar="JSON", help="Overpass 결과 JSON (out body geom)")
    ap.add_argument("--geojson", metavar="OUT", help="지도 확인용 GeoJSON 저장")
    args = ap.parse_args()
    if args.fetch:
        fetch(args.fetch)
        return
    if not args.osm:
        ap.error("--osm 또는 --fetch 가 필요합니다")
    with open(args.osm, encoding="utf-8") as f:
        ways = [w for w in json.load(f)["elements"] if w["type"] == "way" and walkable(w.get("tags", {}))]
    nodes, edges, dropped = build(ways)
    sys.stdout.write(to_sql(nodes, edges, dropped, ways))
    if args.geojson:
        with open(args.geojson, "w", encoding="utf-8") as f:
            json.dump(to_geojson(nodes, edges), f, ensure_ascii=False)
    print(f"노드 {len(nodes)}개 · 구간 {len(edges)}개 · 버린 노드 {dropped}개", file=sys.stderr)


if __name__ == "__main__":
    main()
