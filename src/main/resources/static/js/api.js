// 서버 API 호출 도우미.
// POST·PATCH·DELETE 요청에 CSRF 토큰(X-XSRF-TOKEN 헤더)을 자동으로 붙이고,
// 오류 응답({ code, message })은 Error 로 던져서 화면에서 message 를 그대로 띄울 수 있게 합니다.
//
// 사용 예:
//   const health = await api.get('/api/health');
//   await api.post('/api/reports', { lat, lng, type, timeSlot });

const api = (() => {
  function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
  }

  async function request(method, url, body) {
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
    const data = res.headers.get('content-type')?.includes('application/json') ? await res.json() : null;
    if (!res.ok) {
      throw new Error(data?.message || `요청 실패 (${res.status})`);
    }
    return data;
  }

  return {
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    patch: (url, body) => request('PATCH', url, body),
    delete: (url) => request('DELETE', url),
  };
})();
