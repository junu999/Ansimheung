// 화면용 API 어댑터. app.js 는 이 파일의 메서드만 부른다.
// 실제 요청은 http.js, 서버 ↔ 화면 모양 변환은 mappers.js 가 맡는다.
// 엔드포인트·필드는 노션 "API 명세서"와 static/mock/*.json 기준 (백엔드 이슈 #9 #11 #12 #13 #14).
import { config } from './config.js';
import { http, MOCK } from './http.js';
import {
  STATUS, TYPE, fromHazardCollection, fromHazardDetail, fromNearby, fromRoutes,
  fromNotifications, toSignup, toReportFields,
} from './mappers.js';

const school = () => config.school;
const liked = new Map(); // hazardId → 내가 공감했는지 (공감 버튼이 POST/DELETE 중 무엇을 보낼지 결정)
const remember = (r) => { liked.set(r.id, r.liked); return r; };

// 현재 지도 영역의 bbox (서,남,동,북). 지도가 없으면 시범 지역 전체를 조회한다.
function toBbox(bounds) {
  if (bounds) return [bounds.west, bounds.south, bounds.east, bounds.north].map((n) => n.toFixed(6)).join(',');
  const { lat, lng, radius } = school();
  const dLat = radius / 111320, dLng = radius / (111320 * Math.cos(lat * Math.PI / 180));
  return [lng - dLng, lat - dLat, lng + dLng, lat + dLat].map((n) => n.toFixed(6)).join(',');
}

// ?mock 모드에서는 /api/me 가 항상 로그인 상태(me.json)라서, 로그인 여부만 이 탭에 따로 기억한다.
const MOCK_SESSION = 'ansimheung-mock-session';
function mockSession(user) {
  try {
    if (user === undefined) return JSON.parse(sessionStorage.getItem(MOCK_SESSION));
    if (user === null) sessionStorage.removeItem(MOCK_SESSION); else sessionStorage.setItem(MOCK_SESSION, JSON.stringify(user));
  } catch { /* 저장소를 못 쓰는 브라우저 → 새로고침 시 로그아웃 */ }
  return user ?? null;
}
const toUser = (me) => (me ? { id: String(me.id), nickname: me.nickname, role: me.role, email: me.email || '' } : null);

export const api = {
  isDemo: MOCK,

  // ---------- 인증 (#9) ----------
  async getSession() {
    if (MOCK) return mockSession();
    try { return toUser(await http.get('/api/me')); }
    catch (e) { if (e.status === 401) return null; throw e; }
  },
  async login({ email, password, role }) {
    const me = toUser(await http.post('/api/auth/login', { email, password }));
    if (MOCK) return mockSession({ ...me, role: role || me.role, email: email || '' }); // mock: 체험 버튼으로 관리자 전환
    return me;
  },
  async register(payload) {
    if (!payload.over14 || !payload.agree) throw new Error('만 14세 이상 확인과 개인정보 수집·이용 동의가 필요해요.');
    return http.post('/api/auth/signup', toSignup(payload));
  },
  async logout() {
    await http.post('/api/auth/logout');
    if (MOCK) mockSession(null);
    return null;
  },

  // ---------- 위험 지점 (#12) ----------
  async getReports({ admin = false, bounds } = {}) {
    if (!admin) {
      const fc = await http.get(`/api/hazards?bbox=${toBbox(bounds)}`);
      return fromHazardCollection(fc, school()).map(remember);
    }
    // 관리자 목록은 설명·사진·다음 상태가 필요해서 상세를 함께 받는다 (시범 규모 30건 내외)
    const page = await http.get('/api/admin/hazards?size=100');
    return Promise.all((page?.content || []).map(async (h) => {
      const detail = await http.get(`/api/admin/hazards/${h.id}`);
      return fromHazardDetail({ ...h, ...detail, id: h.id, hidden: detail.hidden ?? h.hidden }, school());
    }));
  },
  async getReport(id) {
    return remember(fromHazardDetail(await http.get(`/api/hazards/${encodeURIComponent(id)}`), school()));
  },

  // ---------- 제보 (#11, 묶기는 #25) ----------
  async duplicates({ lat, lng, category }) {
    const q = new URLSearchParams({ lat, lng, type: TYPE[category] });
    try { return fromNearby(await http.get(`/api/hazards/nearby?${q}`), { lat, lng, type: TYPE[category] }); }
    catch (e) { if (e.status === 404 || e.status === 405) return []; throw e; } // 묶기 API(#25) 전에는 조용히 생략
  },
  async createReport(payload) {
    const form = new FormData();
    for (const [key, value] of Object.entries(toReportFields(payload))) form.append(key, value);
    if (payload.photoFile) form.append('photo', payload.photoFile);
    const created = await http.post('/api/reports', form);
    return this.getReport(created.hazardId);
  },

  // ---------- 공감 (#26) ----------
  async like(id) {
    if (!liked.has(id)) await this.getReport(id);
    const path = `/api/hazards/${encodeURIComponent(id)}/empathy`;
    const body = liked.get(id) ? await http.delete(path) : await http.post(path);
    const report = await this.getReport(id);
    if (MOCK && body) Object.assign(report, { likes: body.empathyCount, liked: body.myEmpathy });
    return remember(report);
  },

  // ---------- 경로 (#13) ----------
  async routes(origin) {
    return fromRoutes(await http.post('/api/routes', { fromLat: origin.lat, fromLng: origin.lng }));
  },

  // ---------- 알림 (범위 제외 #28 — API가 없으면 빈 목록) ----------
  async notifications() {
    try { return fromNotifications(await http.get('/api/me/notifications')); }
    catch (e) { if (e.status === 404 || e.status === 405) return []; throw e; }
  },
  async readNotification(id) {
    return http.patch(`/api/me/notifications/${encodeURIComponent(id)}/read`);
  },

  // ---------- 관리자 (#14) ----------
  // 화면 폼 하나({status,result,hidden,photoStatus})를 명세의 개별 API로 나눠 보낸다.
  async updateReport(id, { status, result, hidden, photoStatus }) {
    const current = fromHazardDetail(await http.get(`/api/admin/hazards/${encodeURIComponent(id)}`), school());
    let changed = false;
    if (hidden !== undefined && hidden !== current.hidden) {
      await http.patch(`/api/admin/hazards/${id}/hidden`, { hidden, reason: result || (hidden ? '관리자 숨김' : '공개 복원') });
      changed = true;
    }
    if (photoStatus !== undefined && current.photoId && photoStatus !== current.photoStatus) {
      await http.patch(`/api/admin/photos/${current.photoId}`, { approved: photoStatus === 'approved' });
      changed = true;
    }
    if (status !== undefined && status !== current.status) {
      await http.patch(`/api/admin/hazards/${id}/status`, { status: STATUS[status], memo: result?.trim() || null });
      changed = true;
    }
    if (!changed && hidden === undefined) throw new Error('바꿀 처리상태를 선택해 주세요. (처리결과만 따로 저장할 수는 없어요)');
    return current;
  },
};
