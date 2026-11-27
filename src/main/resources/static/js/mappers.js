// 서버 응답(노션 API 명세서 · static/mock/*.json 모양) ↔ 화면 모델 변환.
// 서버 필드 이름이 바뀌면 이 파일만 고치면 됩니다. DOM·fetch를 쓰지 않는 순수 함수 → src/test/js 에서 node로 테스트.

// 화면 키 ↔ 서버 enum (common/type/*.java)
export const TYPE = { parking: 'ILLEGAL_PARKING', lighting: 'POOR_LIGHTING', sidewalk: 'SIDEWALK_GAP', construction: 'CONSTRUCTION', speeding: 'SPEEDING' };
export const TIME = { all: 'ALWAYS', morning: 'MORNING', afternoon: 'AFTERNOON', evening: 'NIGHT' };
export const STATUS = { pending: 'RECEIVED', reviewing: 'CHECKING', resolved: 'RESOLVED', rejected: 'REJECTED' };
export const MODE = { shortest: 'SHORTEST', balanced: 'BALANCED', safest: 'SAFEST' };
const invert = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [v, k]));
export const FROM_TYPE = invert(TYPE), FROM_TIME = invert(TIME), FROM_STATUS = invert(STATUS), FROM_MODE = invert(MODE);

// 위험점수 표시 환산: 서버 점수 = W(≤2.0)×R(≤1.5)×E(≤2.0)×S(≤1.0)×T(≤1.5) → 최대 9.0.
// 화면은 "/100점" 막대로 보여주므로 최대값 9.0을 100점으로 환산한다. 마커 색(level)은 서버 값을 그대로 쓴다.
export const SCORE_MAX = 9.0;
export const toDisplayScore = (score) => Math.round(Math.min(100, Math.max(0, (Number(score) || 0) / SCORE_MAX * 100)));

const LABELS = { ILLEGAL_PARKING: '불법주정차', POOR_LIGHTING: '조명부족', SIDEWALK_GAP: '보도단절', CONSTRUCTION: '공사구간', SPEEDING: '과속차량' };
const TIME_LABELS = { ALWAYS: '상시', MORNING: '등교 시간', AFTERNOON: '하교 시간', NIGHT: '저녁·야간' };
const ROUTE_STYLE = {
  SHORTEST: { name: '최단', color: '#8b96a7' },
  BALANCED: { name: '균형', color: '#d99b39' },
  SAFEST: { name: '안전', color: '#17765b' },
};
const LEVEL = { HIGH: 'high', MEDIUM: 'medium', RESOLVED: 'resolved' };

export function distanceM(a, b) {
  const rad = (n) => n * Math.PI / 180;
  const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
}

// 제보 등록 시 화면의 "제목"과 "설명"을 서버 description(최대 200자, V-09) 하나로 합친다: "제목\n설명"
export const DESCRIPTION_MAX = 200;
export function joinDescription(title, description) {
  const t = String(title ?? '').trim(), d = String(description ?? '').trim();
  return (t && d ? `${t}\n${d}` : t || d).slice(0, DESCRIPTION_MAX);
}
export function splitDescription(text) {
  const s = String(text ?? '').trim();
  const i = s.indexOf('\n');
  return i < 0 ? { title: s, description: '' } : { title: s.slice(0, i).trim(), description: s.slice(i + 1).trim() };
}

function photoOf(photos = []) {
  const photo = photos[0];
  if (!photo) return { photo: null, photoStatus: 'none', photoId: null };
  return { photo: `/api/photos/${photo.id}`, photoStatus: photo.approved === false ? 'pending' : 'approved', photoId: photo.id };
}

// 공통: 위험 지점(hazard) 한 건 → 화면 Report
function baseReport(h, school) {
  const lat = h.lat, lng = h.lng;
  const fallbackTitle = `${LABELS[h.type] || '위험'} 제보`;
  const dist = school && Number.isFinite(lat) ? Math.round(distanceM(school, { lat, lng })) : null;
  return {
    id: String(h.id),
    title: fallbackTitle,
    description: '',
    category: FROM_TYPE[h.type] || 'sidewalk',
    timeBand: FROM_TIME[h.timeSlot] || 'all',
    lat, lng,
    address: dist === null ? '' : `${school.name}에서 약 ${dist}m`,
    score: toDisplayScore(h.score),
    rawScore: Number(h.score) || 0,
    level: LEVEL[h.level] || (h.status === 'RESOLVED' ? 'resolved' : null),
    likes: h.empathyCount ?? 0,
    liked: Boolean(h.myEmpathy),
    reportCount: h.reportCount ?? 1,
    status: FROM_STATUS[h.status] || 'pending',
    createdAt: h.createdAt || h.lastActivityAt || null,
    authorId: null,
    authorName: `제보 ${h.reportCount ?? 1}건 · ${TIME_LABELS[h.timeSlot] || ''}`.trim(),
    photo: null, photoStatus: 'none', photoId: null,
    hidden: Boolean(h.hidden),
    result: '',
    history: [],
    allowedNext: [],
  };
}

// GET /api/hazards (GeoJSON FeatureCollection) → Report[]
export function fromHazardCollection(collection, school) {
  return (collection?.features || []).map((f) => {
    const [lng, lat] = f.geometry.coordinates;
    return baseReport({ ...f.properties, lat, lng }, school);
  });
}

// GET /api/hazards/{id} · GET /api/admin/hazards/{id} → Report
export function fromHazardDetail(d, school) {
  const r = baseReport(d, school);
  const first = (d.reports || [])[0];
  if (first?.description) Object.assign(r, splitDescription(first.description));
  if (first?.authorNickname) r.authorName = first.authorNickname;
  else if (d.reportCount > 1) r.authorName = `이웃 ${d.reportCount}명이 제보`;
  else r.authorName = '우리동네 이웃';
  Object.assign(r, photoOf(d.photos));
  r.history = (d.history || []).map((h) => ({ status: FROM_STATUS[h.toStatus] || h.toStatus, result: h.memo || '', at: h.createdAt }));
  r.result = [...(d.history || [])].reverse().find((h) => h.memo)?.memo || '';
  r.allowedNext = (d.allowedNextStatuses || []).map((s) => FROM_STATUS[s]).filter(Boolean);
  r.myReported = Boolean(d.myReported);
  return r;
}

// GET /api/hazards/nearby → 후보 Report[] (0~1개)
export function fromNearby(body, base) {
  const c = body?.candidate;
  if (!c) return [];
  return [{ ...baseReport({ ...c, type: base.type, timeSlot: 'ALWAYS', status: 'RECEIVED', lat: base.lat, lng: base.lng }, null),
    address: `여기서 약 ${c.distanceM}m`, title: `${LABELS[base.type] || '위험'} 제보 (이미 ${c.reportCount ?? 1}건)` }];
}

// POST /api/routes 응답 → 화면 경로 [{id,name,lambda,color,distance,duration,exposure,coordinates:[[lat,lng]]}]
export function fromRoutes(body) {
  return {
    routes: (body?.routes || []).map((r) => ({
      id: FROM_MODE[r.mode] || String(r.mode).toLowerCase(),
      ...(ROUTE_STYLE[r.mode] || { name: r.mode, color: '#17765b' }),
      lambda: r.lambda,
      distance: r.distanceM,
      duration: r.minutes,
      exposure: toDisplayScore(r.riskExposure),
      rawExposure: r.riskExposure,
      hazardIds: (r.hazardIds || []).map(String),
      coordinates: (r.path?.coordinates || []).map(([lng, lat]) => [lat, lng]), // GeoJSON [경도,위도] → Leaflet [위도,경도]
    })),
    updatedAt: new Date().toISOString(),
  };
}

// 같은 LineString은 카드 하나로 묶되 최단·균형·안전 전환은 각각 유지한다.
export function groupRoutes(routes) {
  const groups = new Map();
  for (const route of routes) {
    const key = JSON.stringify(route.coordinates);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(route);
  }
  return [...groups.values()].map(members => ({ ...members[0],
    ids: members.map(route => route.id), name: members.map(route => route.name).join('·'),
  }));
}

// GET /api/me/notifications → Notification[]
export function fromNotifications(body) {
  return (body?.items || []).map((n) => ({
    id: String(n.id), reportId: n.hazardId == null ? null : String(n.hazardId),
    title: '제보 처리결과', message: n.message, createdAt: n.createdAt, read: Boolean(n.read),
  }));
}

// 화면 가입 폼 → POST /api/auth/signup
export const toSignup = (p) => ({ email: p.email, password: p.password, nickname: p.nickname, ageConfirmed: Boolean(p.over14), privacyAgreed: Boolean(p.agree) });

// 화면 제보 → POST /api/reports (multipart 필드)
export function toReportFields(p) {
  const fields = { lat: p.lat, lng: p.lng, type: TYPE[p.category], timeSlot: TIME[p.timeBand], description: joinDescription(p.title, p.description) };
  if (p.mergeHazardId) fields.mergeHazardId = p.mergeHazardId;
  return fields;
}
