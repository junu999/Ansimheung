export const $ = (q, root = document) => root.querySelector(q);
export const icon = (name, cls = '') => `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
export const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function distance(a, b) {
  const rad = n => n * Math.PI / 180;
  const dLat = rad(b.lat-a.lat), dLng = rad(b.lng-a.lng);
  const x = Math.sin(dLat/2)**2 + Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLng/2)**2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1-x));
}
export function relativeTime(value) {
  const minutes = Math.max(0, Math.floor((Date.now()-new Date(value).getTime())/60000));
  if (minutes < 1) return '방금 전';
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 1440) return `${Math.floor(minutes/60)}시간 전`;
  if (minutes < 10080) return `${Math.floor(minutes/1440)}일 전`;
  return new Intl.DateTimeFormat('ko-KR', {month:'long',day:'numeric'}).format(new Date(value));
}
export const formatDate = value => new Intl.DateTimeFormat('ko-KR', {year:'numeric',month:'long',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'Asia/Seoul'}).format(new Date(value));
export const formatDistance = n => n >= 1000 ? `${(n/1000).toFixed(1)} km` : `${Math.round(n)} m`;
// 마커 색은 서버가 준 level(C-08 기준값)을 그대로 쓴다. level이 없을 때만 표시 점수로 추정.
export function riskLevel(report) { if (report.status === 'resolved') return 'resolved'; if (report.level) return report.level; return report.score >= 33 ? 'high' : 'medium'; }
export function coordinateText(pos) { return `${Number(pos.lat).toFixed(5)}, ${Number(pos.lng).toFixed(5)}`; }
export function safePhoto(value) { return typeof value === 'string' && (/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(value) || /^https:\/\//.test(value) || /^\/api\/photos\/\d+$/.test(value)) ? value : ''; }
export async function resizePhoto(file) {
  const data = await new Promise((resolve,reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(new Error('사진을 읽을 수 없어요. 다른 사진을 선택해 주세요.')); reader.readAsDataURL(file); });
  const img = await new Promise((resolve,reject) => { const image = new Image(); image.onload = () => resolve(image); image.onerror = () => reject(new Error('이 사진 형식을 읽을 수 없어요. JPG 또는 PNG를 선택해 주세요.')); image.src = data; });
  const scale = Math.min(1, 1200/Math.max(img.width,img.height));
  const canvas = document.createElement('canvas'); canvas.width = Math.round(img.width*scale); canvas.height = Math.round(img.height*scale);
  const context = canvas.getContext('2d'); context.fillStyle='#fff'; context.fillRect(0,0,canvas.width,canvas.height); context.drawImage(img,0,0,canvas.width,canvas.height);
  return canvas.toDataURL('image/jpeg',0.76);
}
