-- =====================================================================
-- V2: 공공데이터 레이어 (#34) — scripts/build_public_layers.py 로 생성. 직접 고치지 말고 스크립트를 다시 돌릴 것
-- 범위: 정왕초(37.3408, 126.743) 반경 1000m (지도 표시용, 위험점수·경로에 쓰지 않음)
-- 출처: 공공데이터포털 (이용허락범위 제한 없음)
-- =====================================================================

-- CCTV: 경기도_시흥시_불법주정차단속CCTV현황 · 15건 · 데이터기준일자 2026-09-28
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕1교 식자재마트 앞 (2021.05 변경)', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7482723, 37.3392692), 4326));  -- 496m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕1 금강A113동앞', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7386922, 37.34445549), 4326));  -- 557m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕1 농협공단점앞', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7376125, 37.34368741), 4326));  -- 574m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕본 우체국앞사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7478597, 37.34462313), 4326));  -- 604m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕1 보건지소앞', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7409851, 37.34679981), 4326));  -- 691m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕1 이마트후문앞', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7371797, 37.34527189), 4326));  -- 716m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕본 시화공고사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7507765, 37.34323796), 4326));  -- 739m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕1 유천A사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7493496, 37.33643807), 4326));  -- 742m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '하모니마트 사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.749008, 37.34604437), 4326));  -- 789m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '궁전유치원 사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7474583, 37.34701306), 4326));  -- 795m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕본 군서고사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7422239, 37.34815756), 4326));  -- 821m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '경기스마트고교 삼거리 (2021.05 변경)', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7521431, 37.34424604), 4326));  -- 895m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕2 시화초사거리', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7524161, 37.33773364), 4326));  -- 900m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '정왕2 미관광장마유로길', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7348521, 37.34567758), 4326));  -- 902m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('CCTV', '군서초등학교', '공공데이터포털 경기도_시흥시_불법주정차단속CCTV현황 (2026-09-28)', ST_SetSRID(ST_MakePoint(126.7503342, 37.34731136), 4326));  -- 972m

-- SCHOOL_ZONE: 전국어린이보호구역표준데이터 · 11건 · 데이터기준일자 2026-08-19
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '정왕초등학교', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7432287, 37.3409187), 4326));  -- 24m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '다원유치원', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7476017, 37.34263534), 4326));  -- 455m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '군서미래국제학교', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7432517, 37.34624108), 4326));  -- 605m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '보둠이나눔이어린이집', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7352241, 37.34008901), 4326));  -- 692m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '열린자리어린이집', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7407962, 37.34737444), 4326));  -- 757m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '시화초등학교', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7514768, 37.33929532), 4326));  -- 768m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '궁전유치원', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7471769, 37.3470311), 4326));  -- 785m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '푸른솔어린이집', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7482653, 37.3469117), 4326));  -- 824m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '하늘채어린이집', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7477068, 37.3476608), 4326));  -- 869m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '보람유치원', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7534243, 37.3433344), 4326));  -- 964m
INSERT INTO public_layers (kind, name, source, geom) VALUES ('SCHOOL_ZONE', '시화리라유치원', '공공데이터포털 전국어린이보호구역표준데이터 (2026-08-19)', ST_SetSRID(ST_MakePoint(126.7520099, 37.34603613), 4326));  -- 987m
