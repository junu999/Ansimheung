// 서버 API 호출 도우미.
// - POST·PATCH·DELETE 요청에 CSRF 토큰(X-XSRF-TOKEN 헤더)을 자동으로 붙임
// - 오류 응답 { code, message, fields } 를 ApiError 로 던짐 → 화면에서 e.message 를 그대로 띄우면 됨
// - 주소 끝에 ?mock 을 붙여 열면 (예: http://localhost:8080/?mock) 서버 대신 /mock/*.json 가짜 데이터를 씀
//   → 백엔드 API 가 아직 없어도 화면 작업 가능. 가짜 데이터 모양 = 노션 API 명세서 응답 예시
//
// 사용 예:
//   const config = await api.get('/api/config');
//   try { await api.post('/api/reports', formData); }
//   catch (e) { alert(e.message); if (e.fields) { /* 칸별 메시지 표시 */ } }

class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `요청 실패 (${status})`);
    this.status = status;
    this.code = body?.code ?? null;
    this.fields = body?.fields ?? null;
  }
}

const api = (() => {
  const MOCK = new URLSearchParams(location.search).has('mock');

  // [메서드, 주소 패턴, 가짜 데이터 파일] — 파일이 null 이면 본문 없는 성공(204)으로 응답
  const MOCK_ROUTES = [
    ['GET', /^\/api\/config$/, 'config.json'],
    ['GET', /^\/api\/me$/, 'me.json'],
    ['POST', /^\/api\/auth\/login$/, 'me.json'],
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
    const options = { method, headers, credentials: 'same-origin' };

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

    const res = await fetch(url, options);
    const data = res.headers.get('content-type')?.includes('json') ? await res.json() : null;
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }

  return {
    isMock: MOCK,
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    patch: (url, body) => request('PATCH', url, body),
    delete: (url) => request('DELETE', url),
  };
})();
