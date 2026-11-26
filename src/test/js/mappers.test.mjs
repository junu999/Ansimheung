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

test('상세: 승인 사진과 여러 처리 이력의 상태·메모·시간을 유지한다', () => {
  const history = [
    { toStatus: 'RECEIVED', memo: null, createdAt: '2026-11-01T08:00:00+09:00' },
    { toStatus: 'CHECKING', memo: '현장 확인 중', createdAt: '2026-11-02T09:00:00+09:00' },
    { toStatus: 'RESOLVED', memo: '보도 정비 완료', createdAt: '2026-11-03T10:00:00+09:00' },
  ];
  const r = fromHazardDetail({ ...mock('hazard-detail.json'), photos: [{ id: 35, approved: true }], history }, school);
  assert.equal(r.photo, '/api/photos/35');
  assert.equal(r.photoStatus, 'approved');
  assert.deepEqual(r.history, history.map((h, i) => ({ status: ['pending', 'reviewing', 'resolved'][i], result: h.memo || '', at: h.createdAt })));
  assert.equal(r.result, '보도 정비 완료');
});

test('상세: 빈 사진·이력도 화면에서 빈 목록으로 다룬다', () => {
  const r = fromHazardDetail({ ...mock('hazard-detail.json'), photos: [], history: [] }, school);
  assert.equal(r.photo, null);
  assert.deepEqual(r.history, []);
  assert.equal(r.result, '');
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

test('제목/설명 왕복', () => {
  assert.deepEqual(splitDescription(joinDescription('횡단보도 앞', '차가 서 있어요')), { title: '횡단보도 앞', description: '차가 서 있어요' });
});

test('가입: 명세 필드 이름(ageConfirmed, privacyAgreed)', () => {
  assert.deepEqual(toSignup({ email: 'a@b.c', password: 'pw', nickname: 'n', over14: true, agree: true }),
    { email: 'a@b.c', password: 'pw', nickname: 'n', ageConfirmed: true, privacyAgreed: true });
});
