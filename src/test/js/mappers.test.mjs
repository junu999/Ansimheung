// 프론트 어댑터(mappers.js)가 mock JSON(= 노션 API 명세 응답 예시)을 화면 모델로 제대로 바꾸는지 확인.
// 실행: node --test src/test/js/mappers.test.mjs   (Node 20+, 설치 필요 없음)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  fromHazardCollection, fromHazardDetail, fromNearby, fromRoutes, fromNotifications,
  toReportFields, toSignup, joinDescription, splitDescription, toDisplayScore, TYPE,
} from '../../main/resources/static/js/mappers.js';

const mock = (name) => JSON.parse(readFileSync(new URL(`../../main/resources/static/mock/${name}`, import.meta.url)));
const school = { name: '정왕초등학교', lat: 37.3408, lng: 126.743, radius: 500 };

test('위험 지점 목록: GeoJSON [경도,위도] → lat/lng, enum → 화면 키, 서버 level 유지', () => {
  const list = fromHazardCollection(mock('hazards.json'), school);
  const first = list[0];
  assert.equal(first.id, '12');
  assert.equal(first.lat, 37.3413);
  assert.equal(first.lng, 126.7436);
  assert.equal(first.category, 'parking');
  assert.equal(first.timeBand, 'morning');
  assert.equal(first.status, 'pending');
  assert.equal(first.level, 'high');
  assert.equal(first.likes, 3);
  assert.ok(first.createdAt, '목록 정렬(최신순)에 createdAt 필요');
  assert.match(first.address, /정왕초등학교에서 약 \d+m/);
  assert.ok(list.every((r) => r.score >= 0 && r.score <= 100));
});

test('상세: 첫 제보 설명을 제목/설명으로 나누고 이력·공감 여부를 옮김', () => {
  const r = fromHazardDetail(mock('hazard-detail.json'), school);
  assert.equal(r.title, '등교 시간 후문 앞 배송차량이 양쪽에 서 있어요');
  assert.equal(r.liked, false);
  assert.equal(r.history[0].status, 'pending');
  assert.equal(r.photoStatus, 'none');
});

test('시드 형식의 상세: 첫 줄은 제목, 다음 줄은 설명으로 유지한다', () => {
  const r = fromHazardDetail({ ...mock('hazard-detail.json'), reports: [{ description: '골목 입구 불법주차\n골목으로 들어가는 입구 양쪽에 차가 늘 서 있어서 아이들이 차 사이로 나와요' }] }, school);
  assert.equal(r.title, '골목 입구 불법주차');
  assert.equal(r.description, '골목으로 들어가는 입구 양쪽에 차가 늘 서 있어서 아이들이 차 사이로 나와요');
});

test('관리자 상세: allowedNextStatuses → 화면 상태 키, 작성자 닉네임', () => {
  const r = fromHazardDetail(mock('admin-hazard-detail.json'), school);
  assert.deepEqual(r.allowedNext, ['reviewing', 'rejected']);
  assert.equal(r.authorName, '준우');
});

test('사진: /api/photos/{id}, 미승인은 pending', () => {
  const r = fromHazardDetail({ ...mock('hazard-detail.json'), photos: [{ id: 9, approved: false }] }, school);
  assert.equal(r.photo, '/api/photos/9');
  assert.equal(r.photoStatus, 'pending');
});

test('묶기 후보: candidate 있으면 1개, null 이면 빈 배열', () => {
  const base = { lat: 37.3413, lng: 126.7436, type: TYPE.parking };
  assert.equal(fromNearby(mock('hazards-nearby.json'), base).length, 1);
  assert.deepEqual(fromNearby({ candidate: null }, base), []);
});

test('경로: mode → shortest/balanced/safest, LineString [경도,위도] → [위도,경도]', () => {
  const { routes } = fromRoutes(mock('routes.json'));
  assert.deepEqual(routes.map((r) => r.id), ['shortest', 'balanced', 'safest']);
  const s = routes[0];
  assert.deepEqual(s.coordinates[0], [37.3432, 126.7405]);
  assert.equal(s.distance, 340);
  assert.equal(s.duration, 6);
  assert.equal(s.exposure, toDisplayScore(5.4));
});

test('알림: items → 화면 알림, hazardId → reportId', () => {
  const [n] = fromNotifications(mock('me-notifications.json'));
  assert.equal(n.reportId, '7');
  assert.equal(n.read, false);
});

test('제보 등록 필드: 명세(lat,lng,type,timeSlot,description) 이름과 enum, 200자 제한', () => {
  const f = toReportFields({ lat: 1, lng: 2, category: 'sidewalk', timeBand: 'evening', title: '제목', description: 'x'.repeat(300) });
  assert.equal(f.type, 'SIDEWALK_GAP');
  assert.equal(f.timeSlot, 'NIGHT');
  assert.equal(f.description.length, 200);
  assert.ok(f.description.startsWith('제목\n'));
});

test('제보 화면: 제목 40자와 설명 150자를 줄바꿈으로 합쳐 API에 보낸다', () => {
  const payload = { lat: 1, lng: 2, category: 'parking', timeBand: 'all', title: '제목'.repeat(20), description: '가'.repeat(150) };
  const fields = toReportFields(payload);
  assert.equal(fields.description, `${payload.title}\n${payload.description}`);
  assert.equal(fields.description.length, 191);
  assert.deepEqual(Object.keys(fields).sort(), ['description', 'lat', 'lng', 'timeSlot', 'type']);
});

test('제목/설명 왕복', () => {
  assert.deepEqual(splitDescription(joinDescription('횡단보도 앞', '차가 서 있어요')), { title: '횡단보도 앞', description: '차가 서 있어요' });
});

test('가입: 명세 필드 이름(ageConfirmed, privacyAgreed)', () => {
  assert.deepEqual(toSignup({ email: 'a@b.c', password: 'pw', nickname: 'n', over14: true, agree: true }),
    { email: 'a@b.c', password: 'pw', nickname: 'n', ageConfirmed: true, privacyAgreed: true });
});
