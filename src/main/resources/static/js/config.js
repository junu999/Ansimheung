// 화면 설정. 학교 좌표·반경은 서버(GET /api/config ← application.yml ansimheung.*)에서 받아온다 — 하드코딩 금지.
// ?mock 모드에서는 /mock/config.json 을 쓴다. 서버가 꺼져 있으면 아래 기본값(application.yml과 같은 값)으로 뜬다.
import { http } from './http.js';

const fallback = {
  school: { name: '정왕초등학교', address: '경기 시흥시 중심상가로 257', lat: 37.3408, lng: 126.7430 },
  radiusM: 500, mergeRadiusM: 20, markerHighThreshold: 3.5, walkMPerMin: 60, demoData: false,
};

let server = fallback, serverReachable = true;
try { server = { ...fallback, ...(await http.get('/api/config')) }; }
catch (error) { serverReachable = false; console.warn('[config] /api/config 실패 → 기본값 사용', error); }

export const config = Object.freeze({
  serverReachable,
  school: Object.freeze({ ...server.school, radius: server.radiusM }),
  mergeRadiusM: server.mergeRadiusM,
  markerHighThreshold: server.markerHighThreshold,
  walkMPerMin: server.walkMPerMin,
  demoData: Boolean(server.demoData), // 시연용 시드가 들어간 서버면 true → 'DEMO' 표시 (#16)
  basemap: { mode: 'tiles', tileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png' },
  maxPhotoBytes: 5 * 1024 * 1024, // P-04
});
