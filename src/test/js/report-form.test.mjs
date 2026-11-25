// 실행: node --test src/test/js/*.test.mjs (mock 제보 등록과 지도 재조회 계약)
import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const original = { location: globalThis.location, fetch: globalThis.fetch };
globalThis.location = { search: '?mock' };
globalThis.fetch = async url => {
  assert.ok(url.startsWith('/mock/'), 'mock 모드에서 실제 API를 호출하지 않는다');
  return Response.json(JSON.parse(readFileSync(new URL(`../../main/resources/static${url}`, import.meta.url))));
};
const { api } = await import('../../main/resources/static/js/api.js');
after(() => {
  for (const [key, value] of Object.entries(original)) {
    if (value === undefined) delete globalThis[key]; else globalThis[key] = value;
  }
});

test('mock 등록: 응답 ID로 상세·목록을 조회하면 선택한 위치·유형·시간대가 유지된다', async () => {
  const before = await api.getReports();
  const payload = { lat: 37.3419, lng: 126.7442, category: 'lighting', timeBand: 'evening', description: '가'.repeat(200) };
  const created = await api.createReport(payload);
  assert.deepEqual(Object.keys(created).sort(), ['hazardId', 'merged', 'reportId']);
  assert.equal(created.merged, false);
  const detail = await api.getReport(created.hazardId);
  assert.equal(detail.lat, payload.lat);
  assert.equal(detail.lng, payload.lng);
  assert.equal(detail.category, payload.category);
  assert.equal(detail.timeBand, payload.timeBand);
  assert.equal(detail.title, payload.description);
  assert.equal(detail.myReported, true);
  const after = await api.getReports();
  assert.equal(after.length, before.length + 1);
  assert.equal(after.find(r => r.id === String(created.hazardId)).lat, payload.lat);
  assert.ok(!before.some(r => r.id === String(created.hazardId)), '기존 마커와 다른 ID');
});

test('mock 등록: 선택 설명이 비어 있어도 등록되고 ID가 겹치지 않는다', async () => {
  const payload = { lat: 37.341, lng: 126.743, category: 'sidewalk', timeBand: 'all', description: '' };
  const first = await api.createReport(payload), second = await api.createReport(payload);
  assert.notEqual(first.hazardId, second.hazardId);
  const detail = await api.getReport(second.hazardId);
  assert.equal(detail.description, '');
  assert.equal(detail.title, '보도단절 제보');
  assert.match(detail.createdAt, /\+09:00$/);
});
