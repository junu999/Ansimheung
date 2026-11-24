// 서버 API 호출 도우미 (기존 js/api.js 를 ES 모듈로 옮긴 것 — 동작은 같음).
// - 같은 출처(/api) 호출, 세션 쿠키 자동 전송
// - POST·PATCH·DELETE 요청에 CSRF 토큰(X-XSRF-TOKEN 헤더)을 자동으로 붙임
// - 오류 응답 { code, message, fields } 를 ApiError 로 던짐 → 화면에서 e.message 를 그대로 띄우면 됨
// - 주소 끝에 ?mock 을 붙여 열면 (예: http://localhost:8080/?mock) 서버 대신 /mock/*.json 가짜 데이터를 씀
//   → 백엔드 API 가 아직 없어도 화면 작업 가능. 가짜 데이터 모양 = 노션 API 명세서 응답 예시
//
// 화면 코드는 이 파일을 직접 쓰지 말고 js/api.js(화면용 어댑터)를 쓰세요.

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || defaultMessage(status));
    this.status = status;
    this.code = body?.code ?? null;
    this.fields = body?.fields ?? null;
  }
}

function defaultMessage(status) {
  if (status === 401) return '로그인 후 이용할 수 있어요.';
  if (status === 403) return '이 기능을 사용할 권한이 없어요.';
  if (status === 404) return '찾을 수 없거나 비공개 처리된 항목이에요.';
  return `요청에 실패했어요 (${status}). 잠시 후 다시 시도해 주세요.`;
}

export const MOCK = new URLSearchParams(location.search).has('mock');

// [메서드, 주소 패턴, 가짜 데이터 파일] — 파일이 null 이면 본문 없는 성공(204)으로 응답
const MOCK_ROUTES = [
  ['GET', /^\/api\/config$/, 'config.json'],
  ['GET', /^\/api\/me$/, 'me.json'],
  ['POST', /^\/api\/auth\/login$/, 'me.json'],
  ['POST', /^\/api\/auth\/signup$/, null],
  ['POST', /^\/api\/auth\/logout$/, null],
  ['GET', /^\/api\/hazards$/, 'hazards.json'],
  ['GET', /^\/api\/hazards\/nearby$/, 'hazards-nearby.json'],
  ['GET', /^\/api\/hazards\/\d+$/, 'hazard-detail.json'],
  ['POST', /^\/api\/hazards\/\d+\/empathy$/, 'empathy-on.json'],
  ['DELETE', /^\/api\/hazards\/\d+\/empathy$/, 'empathy-off.json'],
  ['POST', /^\/api\/reports$/, 'reports-created.json'],
  ['PATCH', /^\/api\/reports\/\d+$/, null],
  ['DELETE', /^\/api\/reports\/\d+$/, null],
  ['GET', /^\/api\/me\/reports$/, 'me-reports.json'],
  ['GET', /^\/api\/me\/notifications$/, 'me-notifications.json'],
  ['PATCH', /^\/api\/me\/notifications\/\d+\/read$/, null],
  ['POST', /^\/api\/routes$/, 'routes.json'],
  ['GET', /^\/api\/public-layers$/, 'public-layers.json'],
  ['GET', /^\/api\/admin\/hazards$/, 'admin-hazards.json'],
  ['GET', /^\/api\/admin\/hazards\/\d+$/, 'admin-hazard-detail.json'],
  ['PATCH', /^\/api\/admin\/.+$/, null],
];

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function mockRequest(method, url) {
  const path = url.split('?')[0];
  const route = MOCK_ROUTES.find(([m, pattern]) => m === method && pattern.test(path));
  if (!route) return undefined; // 가짜 데이터 없음 → 진짜 서버로
  await new Promise((resolve) => setTimeout(resolve, 200)); // 로딩 화면 확인용 지연
  if (route[2] === null) return null;
  const res = await fetch(`/mock/${route[2]}`);
  return res.json();
}

async function request(method, url, body) {
  if (MOCK) {
    const mocked = await mockRequest(method, url);
    if (mocked !== undefined) return mocked;
  }

  const headers = {};
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);
  const options = { method, headers, credentials: 'same-origin', signal: controller.signal };

  if (method !== 'GET') {
    const token = csrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  if (body instanceof FormData) {
    options.body = body; // 사진 업로드: Content-Type 은 브라우저가 지정
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(url, options);
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('서버 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.');
    throw new Error('서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요.');
  } finally {
    clearTimeout(timer);
  }
  const data = res.headers.get('content-type')?.includes('json') ? await res.json() : null;
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

export const http = {
  isMock: MOCK,
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  patch: (url, body) => request('PATCH', url, body),
  delete: (url) => request('DELETE', url),
};
