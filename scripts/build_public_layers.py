#!/usr/bin/env python3
"""
공공데이터포털 CSV → Flyway 마이그레이션(public_layers INSERT) 생성 (#34).

원본 CSV는 저장소에 올리지 않는다 (용량·갱신 주기). 받는 곳:
  CCTV        경기도_시흥시_불법주정차단속CCTV현황   https://www.data.go.kr/data/15040618/fileData.do
  SCHOOL_ZONE 전국어린이보호구역표준데이터          https://www.data.go.kr/data/15012891/standard.do  (CSV 전체)
둘 다 CP949 인코딩.

지도 표시용이라 시범 지역(500m)보다 넓은 "지도에 보이는 범위"(기본 1km)까지 넣는다.
위험점수·경로 계산에는 쓰지 않는다.

사용:
  python3 scripts/build_public_layers.py --cctv cctv.csv --zone zone.csv \
      > src/main/resources/db/migration/V2__public_layers.sql
"""
import argparse
import csv
import math
import sys

SCHOOL = (37.3408, 126.7430)  # application.yml ansimheung.area.center-lat/lng 와 같은 값
EARTH_RADIUS_M = 6_371_008.8


def distance_m(lat, lng):
    p = math.radians
    d_lat, d_lng = p(lat - SCHOOL[0]), p(lng - SCHOOL[1])
    a = math.sin(d_lat / 2) ** 2 + math.cos(p(SCHOOL[0])) * math.cos(p(lat)) * math.sin(d_lng / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def read(path):
    for enc in ("cp949", "utf-8-sig"):
        try:
            with open(path, encoding=enc, newline="") as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError:
            continue
    sys.exit(f"인코딩을 읽을 수 없음: {path}")


def sql_str(s):
    return "'" + s.replace("'", "''") + "'"


def rows_within(rows, radius_m, name_col):
    out = []
    for r in rows:
        try:
            lat, lng = float(r["위도"]), float(r["경도"])
        except (KeyError, ValueError):
            continue  # 좌표 없는 행은 건너뜀
        if distance_m(lat, lng) <= radius_m:
            out.append((round(distance_m(lat, lng)), lat, lng, r[name_col].strip(), r["데이터기준일자"].strip()))
    return sorted(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cctv", required=True)
    ap.add_argument("--zone", required=True)
    ap.add_argument("--radius", type=float, default=1000)
    args = ap.parse_args()

    layers = [
        ("CCTV", "경기도_시흥시_불법주정차단속CCTV현황", rows_within(read(args.cctv), args.radius, "현장명")),
        ("SCHOOL_ZONE", "전국어린이보호구역표준데이터", rows_within(read(args.zone), args.radius, "대상시설명")),
    ]

    print("-- =====================================================================")
    print("-- V2: 공공데이터 레이어 (#34) — scripts/build_public_layers.py 로 생성. 직접 고치지 말고 스크립트를 다시 돌릴 것")
    print(f"-- 범위: 정왕초({SCHOOL[0]}, {SCHOOL[1]}) 반경 {int(args.radius)}m (지도 표시용, 위험점수·경로에 쓰지 않음)")
    print("-- 출처: 공공데이터포털 (이용허락범위 제한 없음)")
    print("-- =====================================================================")
    for kind, dataset, rows in layers:
        dates = sorted({r[4] for r in rows})
        print(f"\n-- {kind}: {dataset} · {len(rows)}건 · 데이터기준일자 {', '.join(dates)}")
        for dist, lat, lng, name, ref_date in rows:
            source = f"공공데이터포털 {dataset} ({ref_date})"
            print("INSERT INTO public_layers (kind, name, source, geom) VALUES "
                  f"({sql_str(kind)}, {sql_str(name[:100])}, {sql_str(source[:100])}, "
                  f"ST_SetSRID(ST_MakePoint({lng}, {lat}), 4326));  -- {dist}m")


if __name__ == "__main__":
    main()
