// 화면 표시용 상수 (라벨·아이콘·색). 서버 enum과의 대응은 mappers.js.
export const categories = {
  parking: { label:'불법주정차', icon:'car', color:'#dd7057' },
  lighting: { label:'조명부족', icon:'light', color:'#bc8c37' },
  sidewalk: { label:'보도단절·파손', icon:'walk', color:'#7688a1' },
  construction: { label:'공사구간', icon:'construction', color:'#ce8550' },
  speeding: { label:'과속·교통위험', icon:'speed', color:'#a97c91' },
};
export const timeBands = { all:'상시', morning:'등교 시간', afternoon:'하교 시간', evening:'저녁·야간' };
export const statuses = { pending:'접수', reviewing:'확인 중', resolved:'처리완료' };
// 관리자 화면에서만 쓰는 상태 (반려된 지점은 시민 지도에 안 보임)
export const adminStatuses = { ...statuses, rejected:'반려' };
export const riskNames = { high:'위험 높음', medium:'위험 보통', resolved:'처리완료' };
// 경로 화면의 예시 출발지 (정왕초 기준 300~400m). 시연 출발지는 #15(시드 데이터)에서 확정 후 교체.
export const starts = [
  { id:'west', name:'서쪽 주거지 입구', caption:'학교까지 약 350m · 예시 위치', lat:37.3412, lng:126.7391 },
  { id:'north', name:'북쪽 골목 입구', caption:'정문까지 최단 약 620m · 시연 출발지', lat:37.344404, lng:126.743579 },
  { id:'south', name:'남쪽 큰길 입구', caption:'학교까지 약 350m · 예시 위치', lat:37.3377, lng:126.7419 },
];
