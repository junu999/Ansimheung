// 실제 app.js·api.js·map.js를 연결해 요청 순서와 Leaflet에 전달하는 선 스타일을 검사한다.
import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
const mock = name => JSON.parse(readFileSync(new URL(`../../main/resources/static/mock/${name}.json`, import.meta.url)));
const saved = Object.fromEntries(['document', 'window', 'location', 'history', 'matchMedia', 'fetch', 'setTimeout'].map(k => [k, globalThis[k]]));
const nodes = new Map(), events = new Map(), mapEvents = new Map(), pending = [], requests = [], lines = [], timers = [];
let listRequests = 0;
const node = key => {
  if (!nodes.has(key)) nodes.set(key, { innerHTML: '', hidden: false, classList: { toggle() {} }, setAttribute() {}, querySelector: () => null, scrollIntoView() {}, contains: () => false });
  return nodes.get(key);
};
const layer = () => ({ addTo() { return this; }, bindTooltip() { return this; }, on() {}, clearLayers() {}, openTooltip() {} });
const bounds = { pad() { return this; }, getWest: () => 126.737, getSouth: () => 37.336, getEast: () => 126.749, getNorth: () => 37.345 };
const map = { setView() { return this; }, on: (name, fn) => mapEvents.set(name, fn), getBounds: () => bounds, flyTo() {}, removeLayer() {}, fitBounds() {}, invalidateSize() {} };
globalThis.document = { cookie: '', activeElement: null, querySelector: node, querySelectorAll: () => [], getElementById: node, addEventListener: (name, fn) => events.set(name, fn) };
globalThis.window = { addEventListener() {}, L: { map: () => map, tileLayer: layer, circle: layer, marker: layer, layerGroup: layer, divIcon: o => o, latLng: () => ({ toBounds: () => bounds }), latLngBounds: p => p, polyline: (coordinates, options) => { lines.push({ coordinates, ...options }); return layer(); } } };
globalThis.location = { search: '', hash: '' };
globalThis.history = { replaceState(_state, _title, path) { assert.ok(!path.includes('?')); } };
globalThis.matchMedia = () => ({ matches: false });
globalThis.setTimeout = (...args) => { const id = saved.setTimeout(...args); timers.push(id); return id; };
globalThis.fetch = async (url, options) => {
  if (url === '/api/config') return Response.json(mock('config'));
  if (url === '/api/me') return Response.json({ message: '로그인이 필요해요' }, { status: 401 });
  if (url.startsWith('/api/hazards?')) { listRequests++; return Response.json(mock('hazards')); }
  if (/^\/api\/hazards\/\d+$/.test(url)) return Response.json(mock('hazard-detail'));
  requests.push({ url, ...options });
  return new Promise(resolve => pending.push(resolve));
};
await import('../../main/resources/static/js/app.js');
for (let i = 0; !node('#panel').innerHTML.includes('report-list'); i++) { assert.ok(i < 100); await delay(1); }
after(() => { timers.forEach(clearTimeout); for (const [k, v] of Object.entries(saved)) { if (v === undefined) delete globalThis[k]; else globalThis[k] = v; } });
const click = (action, id) => events.get('click')({ target: { closest: s => s === '[data-action]' ? { dataset: { action, id } } : null } });
const respond = (body, status = 200) => { assert.ok(pending.length); pending.shift()(Response.json(body, { status })); };
const html = () => node('#panel').innerHTML;
const colors = () => lines.filter(line => line.color !== '#fff');
const chooseOrigin = async (lat = 37.3408, lng = 126.743) => { await click('select-route-origin'); mapEvents.get('click')({ latlng: { lat, lng } }); };

test('지도 이동 재조회가 대기 중이어도 경로 화면으로 이동하면 취소한다', async () => {
  mapEvents.get('moveend')(); const before = listRequests;
  await click('nav-routes'); await delay(350);
  assert.equal(listRequests, before);
});

test('출발지 없이 요청하지 않고, 지도 선택 후 POST로만 계산하며 중복 전송을 막는다', async () => {
  await click('nav-routes'); await click('calculate-routes');
  assert.match(html(), /출발지를 먼저 선택해 주세요/); assert.equal(requests.length, 0);
  await chooseOrigin(); const running = click('calculate-routes'); await click('calculate-routes');
  assert.equal(pending.length, 1); assert.match(html(), /경로를 비교하고 있어요/);
  assert.equal(requests[0].url, '/api/routes'); assert.equal(requests[0].method, 'POST');
  assert.deepEqual(JSON.parse(requests[0].body), { fromLat: 37.3408, fromLng: 126.743 });
  respond(mock('routes')); await running;
  assert.equal((html().match(/class="route-card /g) || []).length, 2);
  assert.equal((html().match(/class="route-tab /g) || []).length, 3); assert.match(html(), /균형·안전 경로/);
});

test('선택 경로는 진한 실선, 최단 비교선은 회색 점선이며 최단 선택 시 한 선만 그린다', async () => {
  lines.length = 0; await click('route-select', 'balanced');
  assert.equal(colors().length, 2);
  assert.deepEqual(colors().map(l => [l.color, l.weight, l.dashArray]), [['#8b96a7', 4, '7 7'], ['#d99b39', 6, null]]);
  assert.match(node('#route-legend').innerHTML, /dashed/);
  lines.length = 0; await click('route-select', 'shortest');
  assert.deepEqual(colors().map(l => [l.color, l.weight, l.dashArray]), [['#8b96a7', 6, null]]);
  assert.ok(!node('#route-legend').innerHTML.includes('dashed'));
});

test('모두 같은 경로는 카드와 선을 하나로 합치고 지정 안내를 표시한다', async () => {
  const body = mock('routes'), first = body.routes[0];
  body.routes = body.routes.map(r => ({ ...first, mode: r.mode, lambda: r.lambda }));
  lines.length = 0; const running = click('calculate-routes'); respond(body); await running;
  assert.equal((html().match(/class="route-card /g) || []).length, 1);
  assert.match(html(), /이 출발지는 최단 경로가 가장 안전해요/);
  assert.equal(colors().length, 1); assert.equal(colors()[0].dashArray, null);
});

test('환산 점수가 둘 다 100이어도 원점수로 감소율을 계산한다', async () => {
  const body = mock('routes'); body.routes.forEach((r, i) => { r.riskExposure = i ? 10 : 15; });
  const running = click('calculate-routes'); respond(body); await running;
  assert.match(html(), /위험노출 33% 감소/); assert.match(html(), /원점수 15 → 10/);
});

test('범위·도로 거리·서버 오류는 원문을 안전하게 출력하고 같은 출발지로 재시도한다', async () => {
  for (const [code, message] of [['OUT_OF_AREA', '시범 지역 안에서 출발지를 골라주세요'], ['FAR_FROM_ROAD', '가까운 길 위를 눌러주세요'], ['INTERNAL', '<img src=x> 다시 확인해 주세요']]) {
    const running = click('calculate-routes'); respond({ code, message }, code === 'INTERNAL' ? 500 : 400); await running;
    assert.match(html(), /data-action="calculate-routes"/); assert.ok(!html().includes('class="route-card '));
    assert.ok(html().includes(message.replace('<', '&lt;').replace('>', '&gt;')));
    assert.deepEqual(JSON.parse(requests.at(-1).body), { fromLat: 37.3408, fromLng: 126.743 });
  }
  const retry = click('calculate-routes'); respond(mock('routes')); await retry; assert.match(html(), /균형·안전 경로/);
});

test('출발지 변경 또는 화면 이탈 뒤에 도착한 응답은 결과를 되살리지 않는다', async () => {
  const old = click('calculate-routes'); await chooseOrigin(37.341, 126.743);
  const latest = click('calculate-routes');
  respond(mock('routes')); await old; assert.ok(!html().includes('class="route-card '));
  const body = mock('routes'); body.routes[0].distanceM = 999;
  respond(body); await latest; assert.match(html(), /999 m/);
  const abandoned = click('calculate-routes'); await click('nav-guide'); await click('nav-routes');
  respond({ message: '이전 요청 오류' }, 500); await abandoned;
  assert.ok(!html().includes('이전 요청 오류') && !html().includes('class="route-card '));
  const incomplete = click('calculate-routes'); respond({ routes: [] }); await incomplete;
  assert.match(html(), /세 가지 경로를 모두 받지 못했어요/);
});

test('경로 조회 중 마커 상세를 열면 늦은 오류가 복귀한 경로 화면에 남지 않는다', async () => {
  const running=click('calculate-routes');await click('open-report','12');
  assert.match(html(),/detail-title/);
  respond({message:'이전 경로 요청 오류'},500);await running;
  await click('nav-routes');
  assert.ok(!html().includes('이전 경로 요청 오류'));
});
