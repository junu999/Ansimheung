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
  const payload = { lat: 37.3419, lng: 126.7442, category: 'lighting', timeBand: 'evening', title: '제목'.repeat(20), description: '가'.repeat(150) };
  const created = await api.createReport(payload);
  assert.deepEqual(Object.keys(created).sort(), ['hazardId', 'merged', 'reportId']);
  assert.equal(created.merged, false);
  const detail = await api.getReport(created.hazardId);
  assert.equal(detail.lat, payload.lat);
  assert.equal(detail.lng, payload.lng);
  assert.equal(detail.category, payload.category);
  assert.equal(detail.timeBand, payload.timeBand);
  assert.equal(detail.title, payload.title);
  assert.equal(detail.description, payload.description);
  assert.equal(detail.myReported, true);
  const after = await api.getReports();
  assert.equal(after.length, before.length + 1);
  assert.equal(after.find(r => r.id === String(created.hazardId)).lat, payload.lat);
  assert.ok(!before.some(r => r.id === String(created.hazardId)), '기존 마커와 다른 ID');
});

test('mock 등록: 여러 제보의 ID와 제목·설명이 겹치지 않는다', async () => {
  const payload = { lat: 37.341, lng: 126.743, category: 'sidewalk', timeBand: 'all', title: '보도 위험 제보', description: '보도에서 위험한 곳을 발견했어요' };
  const first = await api.createReport(payload), second = await api.createReport(payload);
  assert.notEqual(first.hazardId, second.hazardId);
  const detail = await api.getReport(second.hazardId);
  assert.equal(detail.description, payload.description);
  assert.equal(detail.title, payload.title);
  assert.match(detail.createdAt, /\+09:00$/);
});

test('mock 묶기: mergeHazardId를 보내면 새 마커 없이 기존 지점에 더한다 (#25)', async () => {
  const base = { lat: 37.341, lng: 126.743, category: 'parking', timeBand: 'morning', title: '후문 주차', description: '차가 서 있어요' };
  const first = await api.createReport(base);
  const before = await api.getReports();
  const merged = await api.createReport({ ...base, mergeHazardId: String(first.hazardId) });
  assert.equal(merged.merged, true);
  assert.equal(merged.hazardId, first.hazardId);
  const after = await api.getReports();
  assert.equal(after.length, before.length, '마커 수 그대로');
  assert.equal(after.find(r => r.id === String(first.hazardId)).reportCount, 2, '제보 수 2');
});

