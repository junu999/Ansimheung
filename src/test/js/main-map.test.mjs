// 실행: node --test src/test/js/*.test.mjs (새 의존성 없이 API 계약과 복귀 경로 검증)
import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { safeReturnPath } from '../../main/resources/static/js/utils.js';

const mock = name => JSON.parse(readFileSync(new URL(`../../main/resources/static/mock/${name}`, import.meta.url)));
const original = { location: globalThis.location, document: globalThis.document, fetch: globalThis.fetch };
const requests = [];
globalThis.location = { search: '', origin: 'https://ansimheung.test' };
globalThis.document = { cookie: '' };
globalThis.fetch = async (url, options) => {
  requests.push({ url, options });
  if (url === '/api/config') return Response.json(mock('config.json'));
  if (url.startsWith('/api/hazards?')) return Response.json(mock('hazards.json'));
  throw new Error(`예상하지 못한 API 요청: ${url}`);
};
const { api } = await import('../../main/resources/static/js/api.js');
after(() => {
  for (const [key, value] of Object.entries(original)) {
    if (value === undefined) delete globalThis[key]; else globalThis[key] = value;
  }
});

test('현재 지도 경계를 서·남·동·북 순서의 bbox로 조회한다', async () => {
  const reports = await api.getReports({ bounds: { west: 126.7412345, south: 37.3387654, east: 126.7456789, north: 37.3432109 } });
  const { url, options } = requests.at(-1);
  assert.equal(new URL(url, location.origin).searchParams.get('bbox'), '126.741235,37.338765,126.745679,37.343211');
  assert.equal(options.method, 'GET');
  assert.equal(options.credentials, 'same-origin');
  assert.deepEqual(reports.map(r => r.level), ['high', 'high', 'medium', 'medium', 'resolved']);
  assert.equal(reports[0].lat, 37.3413);
  assert.equal(reports[0].lng, 126.7436);
});

test('지도 경계가 없으면 서버 설정의 학교·반경으로 조회한다', async () => {
  await api.getReports();
  const bbox = new URL(requests.at(-1).url, location.origin).searchParams.get('bbox').split(',').map(Number);
  const { school, radiusM } = mock('config.json');
  assert.ok(bbox[0] < school.lng && school.lng < bbox[2]);
  assert.ok(bbox[1] < school.lat && school.lat < bbox[3]);
  assert.ok(Math.abs((bbox[3] - school.lat) * 111320 - radiusM) < 1);
});

test('로그인 복귀는 같은 사이트 경로와 쿼리·해시를 유지한다', () => {
  assert.equal(safeReturnPath('/?mock#routes', location.origin), '/?mock#routes');
  assert.equal(safeReturnPath('/guide?from=login#help', location.origin), '/guide?from=login#help');
});

test('외부 주소·프로토콜 상대 주소·역슬래시 우회 복귀를 차단한다', () => {
  for (const value of [null, '', 'https://outside.test/', '//outside.test/', '/\\outside.test/', '/\n/outside.test/', 'javascript:alert(1)', 'routes']) {
    assert.equal(safeReturnPath(value, location.origin), null, String(value));
  }
});
