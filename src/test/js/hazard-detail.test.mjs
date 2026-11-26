// DOM 출력과 이벤트를 최소한으로 연결해 실제 app.js와 api.js의 상세 흐름을 검사한다.
import { after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';

const mock = name => JSON.parse(readFileSync(new URL(`../../main/resources/static/mock/${name}`, import.meta.url)));
const saved = Object.fromEntries(['document', 'window', 'location', 'history', 'matchMedia', 'fetch', 'setTimeout'].map(key => [key, globalThis[key]]));
const nodes = new Map(), events = new Map(), pending = new Map(), timers = [];
const node = key => {
  if (!nodes.has(key)) nodes.set(key, { innerHTML: '', hidden: false, classList: { toggle() {} }, setAttribute() {}, contains: () => false, querySelector: () => null });
  return nodes.get(key);
};
globalThis.document = { cookie: '', activeElement: null, querySelector: node, querySelectorAll: () => [], addEventListener: (name, handler) => events.set(name, handler) };
globalThis.window = { addEventListener() {} };
globalThis.location = { search: '', hash: '' };
globalThis.history = { replaceState() {} };
globalThis.matchMedia = () => ({ matches: false });
globalThis.setTimeout = (...args) => { const timer = saved.setTimeout(...args); timers.push(timer); return timer; };
globalThis.fetch = async url => {
  if (url === '/api/config') return Response.json(mock('config.json'));
  if (url === '/api/me') return Response.json({ message: '로그인이 필요해요.' }, { status: 401 });
  if (url.startsWith('/api/hazards?')) return Response.json(mock('hazards.json'));
  return new Promise(resolve => pending.set(url.split('/').at(-1), resolve));
};
await import('../../main/resources/static/js/app.js');
for (let i = 0; !node('#panel').innerHTML.includes('report-list'); i++) { assert.ok(i < 100, '초기 목록 로딩'); await delay(1); }
after(() => {
  timers.forEach(clearTimeout);
  for (const [key, value] of Object.entries(saved)) { if (value === undefined) delete globalThis[key]; else globalThis[key] = value; }
});
const click = (action, id) => events.get('click')({ target: { closest: selector => selector === '[data-action]' ? { dataset: { action, id } } : null } });
const respond = (id, data, status = 200) => { assert.ok(pending.has(id)); pending.get(id)(Response.json(data, { status })); pending.delete(id); };
const detail = (id, title) => ({ ...mock('hazard-detail.json'), id, reports: [{ description: `${title}\n상세 설명`, authorNickname: '노출하면 안 되는 작성자' }] });

test('상세에 사진·이력·위험도·안전신문고를 표시하고 작성자는 노출하지 않는다', async () => {
  const loading = click('open-report', '12');
  assert.match(node('#panel').innerHTML, /제보를 불러오고 있어요/);
  const body = detail(12, '상세 제목');
  body.photos = [{ id: 35, approved: true }];
  body.history = [{ toStatus: 'CHECKING', memo: '<img src=x onerror=alert(1)>', createdAt: '2026-11-26T09:00:00+09:00' }];
  respond('12', body); await loading;
  const html = node('#panel').innerHTML;
  assert.match(html, /detail-score-label[\s\S]*?위험 높음/);
  assert.match(html, /src="\/api\/photos\/35"/);
  assert.match(html, /처리 이력/);
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.ok(!html.includes('노출하면 안 되는 작성자'));
  assert.match(html, /href="https:\/\/www\.safetyreport\.go\.kr\/" target="_blank" rel="noopener noreferrer"/);
});

test('404는 이전 상세와 사진을 지우고 삭제 안내를 표시한다', async () => {
  const loading = click('open-report', '12');
  respond('12', { code: 'NOT_FOUND', message: '찾을 수 없거나 비공개 처리된 항목이에요.' }, 404); await loading;
  const html = node('#panel').innerHTML;
  assert.match(html, /삭제된 제보입니다/);
  assert.match(html, /찾을 수 없거나 비공개 처리된 항목이에요\./);
  assert.ok(!html.includes('detail-photo') && !html.includes('상세 제목'));
  assert.ok(!html.includes('retry-detail'));
});

test('서버 오류 메시지를 유지하고 같은 지점을 재시도한다', async () => {
  const loading = click('open-report', '13');
  respond('13', { message: '잠시 후 다시 확인해 주세요.' }, 500); await loading;
  assert.match(node('#panel').innerHTML, /잠시 후 다시 확인해 주세요\./);
  const retry = click('retry-detail'); respond('13', detail(13, '재시도 성공')); await retry;
  assert.match(node('#panel').innerHTML, /재시도 성공/);
});

test('늦은 응답과 화면을 떠난 뒤의 응답은 현재 화면을 덮지 않는다', async () => {
  const older = click('open-report', '14'), latest = click('open-report', '15');
  respond('15', detail(15, '최신 상세')); await latest;
  respond('14', detail(14, '이전 상세')); await older;
  assert.match(node('#panel').innerHTML, /최신 상세/);
  assert.ok(!node('#panel').innerHTML.includes('이전 상세'));
  const abandoned = click('open-report', '16'); await click('nav-guide');
  respond('16', detail(16, '떠난 상세')); await abandoned;
  assert.match(node('#panel').innerHTML, /우리의 작은 관심이/);
  assert.ok(!node('#panel').innerHTML.includes('떠난 상세'));
});
