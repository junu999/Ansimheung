import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const saved = Object.fromEntries(['window', 'document', 'location', 'fetch'].map(key => [key, globalThis[key]]));
const config = JSON.parse(readFileSync(new URL('../../main/resources/static/mock/config.json', import.meta.url)));
const handlers = new Map(), positions = [], queries = [], selections = [];
const emit = (event, value) => { for (const handler of handlers.get(event) || []) handler(value); };
let center = { lat: config.school.lat, lng: config.school.lng }, focused;
const layer = () => ({ addTo() { return this; }, on() { return this; }, bindTooltip() { return this; }, clearLayers() {} });
const map = {
  setView() { return this; }, on(event, handler) { handlers.set(event, [...(handlers.get(event) || []), handler]); },
  getCenter: () => center, getBounds: () => ({ getWest: () => 126.74, getSouth: () => 37.34, getEast: () => 126.75, getNorth: () => 37.35 }),
  panTo(pos) { center = Array.isArray(pos) ? { lat: pos[0], lng: pos[1] } : pos; emit('moveend'); },
  flyTo(pos) { focused = pos; }, removeLayer() {},
};
globalThis.window = { L: { map: () => map, tileLayer: layer, circle: layer, marker: layer, layerGroup: layer, divIcon: value => value,
  latLng: () => ({ toBounds: () => ({ pad() { return this; } }) }) } };
globalThis.document = { getElementById: () => ({ classList: { toggle() {} } }) };
globalThis.location = { search: '' };
globalThis.fetch = async () => Response.json(config);
const { createMap } = await import('../../main/resources/static/js/map.js');
const ui = createMap({ onReportPositionChange: pos => positions.push(pos), onMoveEnd: bounds => queries.push(bounds), onSelect: (...args) => selections.push(args) });
after(() => { for (const [key, value] of Object.entries(saved)) { if (value === undefined) delete globalThis[key]; else globalThis[key] = value; } });

test('제보 위치 선택 중에는 중심을 알리고 목록 재조회 콜백은 실행하지 않는다', () => {
  ui.select('report');
  emit('movestart');
  assert.equal(positions.at(-1), null);
  center = { lat: 37.3419, lng: 126.7442 }; emit('moveend');
  assert.deepEqual(positions.at(-1), center);
  assert.equal(queries.length, 0);
});

test('선택을 마치면 bbox 재조회 콜백이 다시 동작하고 경로 위치 선택도 유지한다', () => {
  ui.select('route'); emit('moveend');
  assert.equal(queries.length, 0);
  emit('click', { latlng: center });
  assert.deepEqual(selections.at(-1), [center, 'route']);
  ui.select(null); emit('moveend');
  assert.deepEqual(queries.at(-1), { west: 126.74, south: 37.34, east: 126.75, north: 37.35 });
});

test('학교 중심 복귀는 설정 좌표를 그대로 사용한다', () => {
  ui.center();
  assert.deepEqual(focused, [config.school.lat, config.school.lng]);
});
