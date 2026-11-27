import { config } from './config.js';
import { api } from './api.js';
import { groupRoutes } from './mappers.js';
import { createMap } from './map.js';
import { categories, timeBands, statuses, adminStatuses, riskNames, starts } from './labels.js';
import { $, icon, escapeHtml as esc, distance, relativeTime, formatDate, formatDistance, riskLevel, coordinateText, safePhoto, resizePhoto, safeReturnPath } from './utils.js';

const freshDraft=()=>({title:'',description:'',category:'parking',timeBand:'morning',position:null,photo:null,photoFile:null,mergeHazardId:null});
const state={view:'map',user:null,reports:[],loading:true,error:'',category:'all',time:'any',sort:'latest',resolved:true,selectedId:null,returnToReport:false,draft:freshDraft(),draftDirty:false,duplicates:[],reportError:'',submitting:false,origin:api.isDemo?{...starts[0]}:null,routes:[],routeSelection:'safest',routeLoading:false,routeError:'',notifications:[],notificationsLoading:false,notificationError:'',adminReports:[],adminFilter:'all',adminSelected:null,adminLoading:false,adminError:'',selectionMode:null};
let mapUI, pendingAfterAuth=null, previousFocus=null, toastTimer, duplicateRequest=0, modalKind=null, lastRenderedView=null, reportPosition=null, reportsRequest=0, moveTimer=null;
let routeRequest=0;
let authReturnPath=safeReturnPath(new URLSearchParams(location.search).get('returnUrl'));
Object.assign(state,{detailId:null,detailLoading:false,detailError:'',detailNotFound:false});
let detailRequest=0;
const panel=$('#panel');
const categoryOf=report=>categories[report.category] || categories.sidewalk;
const categoryOptions=selected=>Object.entries(categories).map(([key,c])=>`<option value="${key}" ${selected===key?'selected':''}>${c.label}</option>`).join('');
const badge=report=>`<span class="risk-badge ${riskLevel(report)}">${riskNames[riskLevel(report)]}</span>`;
const errorBox=(message,retry='')=>message?`<div class="inline-error" role="alert">${icon('info')}<div>${esc(message)}${retry?`<br><button class="text-button" data-action="${retry}">다시 시도 ${icon('arrow')}</button>`:''}</div></div>`:'';
const loader=(text='불러오는 중이에요.')=>`<div class="loading-state"><span class="spinner"></span>${text}</div>`;
function filteredReports(){return state.reports.filter(r=>(state.category==='all'||r.category===state.category)&&(state.time==='any'||r.timeBand==='all'||r.timeBand===state.time)&&(state.resolved||r.status!=='resolved')).sort((a,b)=>state.sort==='risk'?b.score-a.score:new Date(b.createdAt)-new Date(a.createdAt));}
function panelTop(title,action='nav-map'){return `<div class="panel-topline"><button class="back-button" data-action="${action}">${icon('back')}${title}</button><span class="step-label">${api.isDemo?'체험 모드':'안심흥'}</span></div>`;}
function reportCard(report){const category=categoryOf(report);return `<button type="button" class="report-card ${state.selectedId===report.id?'selected':''}" data-action="open-report" data-id="${esc(report.id)}"><div class="report-card-top"><span class="report-type-icon ${report.status==='resolved'?'resolved':esc(report.category)}">${icon(report.status==='resolved'?'check':category.icon)}</span><div class="report-card-heading"><div class="report-kicker">${category.label}<span>·</span>${timeBands[report.timeBand] || '상시'}</div><h3>${esc(report.title)}</h3></div>${badge(report)}</div><div class="report-card-bottom"><span>${icon('clock')}${relativeTime(report.createdAt)}</span><span>${icon('heart')}공감 ${Number(report.likes)||0}</span></div></button>`;}

function mapPanel(){
  const reports=filteredReports(), high=state.reports.filter(r=>riskLevel(r)==='high').length, resolved=state.reports.filter(r=>r.status==='resolved').length;
  return `<div class="panel-scroll"><div class="intro"><div class="eyebrow"><span class="tiny-dot"></span>시흥시 정왕동 · 우리 동네 안전지도</div><h1>매일 걷는 길에<br><span>안심을 더해요.</span></h1><p>작은 제보가 모여, 더 나은 통학길로.</p></div><div class="school-card"><div class="school-card-icon">${icon('school')}</div><div><strong>${esc(config.school.name)}</strong><p>학교를 중심으로 반경 ${config.school.radius}m</p></div><span class="pilot-mini">시범지역</span></div><div class="stats-row"><div class="stat"><span>모인 제보</span><strong>${state.reports.length}<small>건</small></strong></div><div class="stat high"><span>주의가 필요한 곳</span><strong>${high}<small>곳</small></strong></div><div class="stat resolved"><span>처리된 제보</span><strong>${resolved}<small>건</small></strong></div></div><div class="section-title"><h2>우리 동네 위험 제보 <span class="count-pill">${reports.length}</span></h2><span class="muted-small">${api.isDemo?'체험용 예시':'시민 참여'}</span></div><div class="category-filters" aria-label="위험 유형 필터"><button class="filter-chip ${state.category==='all'?'active':''}" data-action="filter-category" data-value="all" aria-pressed="${state.category==='all'}">전체</button>${Object.entries(categories).map(([key,c])=>`<button class="filter-chip ${state.category===key?'active':''}" data-action="filter-category" data-value="${key}" aria-pressed="${state.category===key}">${icon(c.icon)}${c.label}</button>`).join('')}</div><div class="list-options"><select class="quiet-select" id="time-filter" aria-label="발생 시간대 필터"><option value="any">모든 시간대</option>${Object.entries(timeBands).filter(([k])=>k!=='all').map(([key,label])=>`<option value="${key}" ${state.time===key?'selected':''}>${label}</option>`).join('')}</select><label class="inline-check"><input type="checkbox" id="resolved-filter" ${state.resolved?'checked':''}>처리완료 포함</label><select class="quiet-select" id="sort-filter" aria-label="제보 정렬"><option value="latest" ${state.sort==='latest'?'selected':''}>최신순</option><option value="risk" ${state.sort==='risk'?'selected':''}>위험도순</option></select></div>${errorBox(state.error,'retry-reports')}${state.loading?loader():`<div class="report-list">${reports.map(reportCard).join('')}</div>`}${!state.loading&&!state.error&&!reports.length?`<div class="empty-state">${icon('search')}<h3>이 조건에 맞는 제보가 없어요</h3><p>다른 유형이나 시간대를 선택해 보세요.<br>제보가 없다고 안전한 구역인 것은 아니에요.</p><button class="text-button" data-action="reset-filters">필터 초기화 ${icon('arrow')}</button></div>`:''}<p class="report-list-note">${api.isDemo?'지도에 표시된 제보는 실제 위험 정보가 아니에요.':'제보는 시민이 관찰한 정보이며 실제 상황과 다를 수 있어요.'}</p></div><div class="panel-footer"><button class="button secondary" data-action="nav-routes">${icon('route')}통학경로 찾기</button><button class="button primary" data-action="new-report">${icon('plus')}위험 제보하기</button></div>`;
}

function reportPanel(){const d=state.draft;return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}<h1 class="panel-title">작은 발견을<br>안심으로 바꿔주세요.</h1><p class="panel-subtitle">우리 아이들이 걷는 길, 함께 살펴봐요.</p>${errorBox(state.reportError)}<form id="report-form"><div class="form-section"><label class="field-label">제보 위치 <span class="required">*</span></label><button class="location-field ${d.position?'selected':''}" type="button" data-action="select-report-location">${icon('pin')}<span><strong>${d.position?'위치가 선택되었어요':'지도에서 위험한 위치를 골라주세요'}</strong><small>${esc(d.position?coordinateText(d.position):`${config.school.name} 주변 ${config.school.radius}m 이내`)}</small></span>${icon('arrow','field-arrow')}</button><p class="field-hint">${d.position?'다시 누르면 제보 위치를 바꿀 수 있어요.':'지도를 움직여 중심 십자에 위치를 맞춘 뒤 다음을 눌러주세요.'}</p></div><div class="form-section"><span class="field-label" id="category-label">위험 유형 <span class="required">*</span></span><div class="category-grid" role="group" aria-labelledby="category-label">${Object.entries(categories).map(([key,c])=>`<button type="button" class="category-tile ${d.category===key?'active':''}" data-action="draft-category" data-value="${key}" aria-pressed="${d.category===key}">${icon(c.icon)}${c.label}</button>`).join('')}</div></div><div class="form-section"><span class="field-label" id="time-label">위험한 시간대 <span class="required">*</span></span><div class="time-chips" role="group" aria-labelledby="time-label">${Object.entries(timeBands).map(([key,label])=>`<button type="button" class="time-chip ${d.timeBand===key?'active':''}" data-action="draft-time" data-value="${key}" aria-pressed="${d.timeBand===key}">${label}</button>`).join('')}</div></div>${state.duplicates.length?`<div class="duplicate-box">${icon('info')} <strong>근처에 비슷한 제보가 있어요</strong><p>${esc(config.mergeRadiusM)}m 안에 같은 유형의 제보가 있어요. 기존 제보에 공감하거나, 내 제보를 같은 지점에 더할 수 있어요.</p>${state.duplicates.map(r=>{const on=d.mergeHazardId===r.id;return `<button type="button" data-action="duplicate-detail" data-id="${esc(r.id)}">${esc(r.title)} · 기존 제보 보기 →</button><button type="button" class="duplicate-merge ${on?'active':''}" data-action="draft-merge" data-id="${esc(r.id)}" aria-pressed="${on}">${on?`${icon('check')}이 지점에 더해서 등록해요 · 취소`:`${icon('plus')}이 지점에 내 제보 더하기`}</button>`;}).join('')}</div>`:''}<div class="form-section"><label class="field-label" for="report-title">제보 제목 <span class="required">*</span></label><input id="report-title" name="title" class="form-input" placeholder="예: 횡단보도 앞에 차가 서 있어요" maxlength="40" required value="${esc(d.title)}"></div><div class="form-section"><label class="field-label" for="report-description">어떤 점이 위험한가요? <span class="required">*</span></label><textarea id="report-description" name="description" class="form-textarea" placeholder="위험한 상황을 구체적으로 알려주세요.\n개인정보는 적지 말아주세요." maxlength="150" required>${esc(d.description)}</textarea><div class="char-count"><span id="description-count">${d.description.length}</span> / 150</div></div><div class="form-section"><label class="field-label" for="report-photo">현장 사진 <span class="optional">선택</span></label>${d.photo?`<div class="photo-preview"><img src="${esc(safePhoto(d.photo))}" alt="첨부한 제보 사진 미리보기"><button type="button" class="photo-remove" data-action="remove-photo" aria-label="첨부 사진 제거">${icon('close')}</button><div class="photo-pending">${icon('eye')}관리자 확인 후 공개돼요.</div></div>`:`<label class="photo-upload">${icon('camera')}<strong>사진을 선택해주세요</strong><span>JPG, PNG · 1장 · 최대 5MB</span><input id="report-photo" class="file-input" type="file" accept="image/jpeg,image/png" aria-label="현장 사진 선택"></label>`}</div><div class="info-box">${icon('shield')}<span>사람의 얼굴·차량 번호 등 개인정보가 드러나지 않게 주의해 주세요. 사진은 공개 검토를 거쳐요.</span></div>${api.isDemo?`<div class="info-box warning">${icon('info')}<span>가짜 데이터(mock) 모드예요. 등록해도 실제로 저장되지 않아요. 안심흥 제보는 행정 신고로 접수되지 않아요.</span></div>`:''}</form></div><div class="panel-footer"><button class="button secondary" data-action="nav-map">취소</button><button type="submit" form="report-form" class="button primary" ${state.submitting?'disabled':''}>${state.submitting?'<span class="spinner"></span>등록 중':`${icon('plus')}제보 등록하기`}</button></div>`;}

function detailHistory(report){
  return `<section class="status-card"><h3>처리 이력</h3>${report.history.length?`<ol class="status-history">${report.history.map(h=>`<li><strong>${esc(adminStatuses[h.status] || h.status || '상태 변경')}</strong>${h.at?`<time datetime="${esc(h.at)}">${formatDate(h.at)}</time>`:'<span class="muted-small">시간 정보 없음</span>'}${h.result?`<p>${esc(h.result)}</p>`:''}</li>`).join('')}</ol>`:'<p class="field-hint">아직 처리 이력이 없어요.</p>'}</section>`;
}
function detailPanel(){
  const back=panelTop(state.returnToReport?'작성하던 제보로 돌아가기':'위험지도로 돌아가기',state.returnToReport?'back-to-draft':'nav-map');
  if(state.detailLoading)return `<div class="panel-scroll">${back}${loader('제보를 불러오고 있어요.')}</div>`;
  if(state.detailError)return `<div class="panel-scroll">${back}<div class="empty-state" role="alert">${icon('info')}<h3>${state.detailNotFound?'삭제된 제보입니다':'제보를 불러오지 못했어요'}</h3><p>${esc(state.detailError)}</p>${state.detailNotFound?'':`<button class="text-button" data-action="retry-detail">다시 시도 ${icon('arrow')}</button>`}</div></div>`;
  const r=state.reports.find(r=>r.id===state.selectedId);if(!r)return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}<div class="empty-state">${icon('info')}<h3>이 제보를 확인할 수 없어요</h3><p>숨김 처리되었거나 조회할 수 없는 제보예요.</p></div></div>`;const cat=categoryOf(r),level=riskLevel(r),stage=Object.keys(statuses).indexOf(r.status),photo=safePhoto(r.photo);return `<div class="panel-scroll">${panelTop(state.returnToReport?'작성하던 제보로 돌아가기':'위험지도로 돌아가기',state.returnToReport?'back-to-draft':'nav-map')}<div class="detail-top">${badge(r)}<span class="detail-type">${cat.label}</span></div><h1 class="detail-title">${esc(r.title)}</h1><div class="detail-location">${icon('pin')}${esc(r.address || coordinateText(r))}</div><div class="detail-meta"><span>제보 ${Number(r.reportCount)||1}건</span><time datetime="${esc(r.createdAt)}" title="${formatDate(r.createdAt)}">${relativeTime(r.createdAt)}</time></div><p class="detail-content">${esc(r.description)}</p>${photo?`<div class="detail-photo"><img src="${esc(photo)}" alt="${esc(r.title)}의 현장 사진">${r.photoStatus!=='approved'?`<div class="photo-pending">${icon('eye')}${r.photoStatus==='rejected'?'비공개 처리된 사진이에요.':'공개 검토 중 · 작성자와 관리자에게만 보여요.'}</div>`:''}</div>`:`<div class="no-photo">${icon('camera')}${r.photoStatus==='pending'?'사진을 검토하고 있어요. 승인 후 공개돼요.':r.photoStatus==='rejected'?'사진이 비공개 처리되었어요.':'첨부된 사진이 없어요'}</div>`}<div class="score-card"><div class="score-card-top"><span class="detail-score-label">${api.isDemo?'체험용 ':''}위험점수 ${badge(r)}</span><strong class="score-number ${level}">${Number(r.score)||0}<small>/ 100</small></strong></div><div class="score-bar ${level}"><span style="width:${Math.max(0,Math.min(100,Number(r.score)||0))}%"></span></div><p class="score-note">${r.status==='resolved'?'처리완료된 제보예요.':api.isDemo?'화면 체험을 위한 예시 점수예요. 실제 위험도는 서버 연결 후 계산돼요.':'유형·공감·발생 시점 등을 바탕으로 서버에서 평가한 값이에요.'}<br>위험점수는 사고 발생 확률을 의미하지 않아요.</p></div><div class="status-card"><h3>제보 처리상태</h3><div class="status-steps">${Object.entries(statuses).map(([key,label],i)=>`<div class="status-step ${i===stage?'current':i<stage?'done':''}"><span class="step-circle">${i<stage?icon('check'):i+1}</span><span>${label}</span></div>`).join('')}</div>${r.result?`<p class="result-note">${esc(r.result)}</p>`:''}</div>${detailHistory(r)}<div class="detail-location" style="margin-top:22px">${icon('clock')}발생 시간대 · ${timeBands[r.timeBand] || '상시'}</div><a class="external-link" href="https://www.safetyreport.go.kr/" target="_blank" rel="noopener noreferrer">안전신문고에서 직접 신고하기 ${icon('external')}</a><p class="external-caption">안심흥 제보와 별개로, 안전신문고에서 직접 접수해야 해요.</p></div><div class="panel-footer"><button class="button secondary" data-action="focus-report" data-id="${esc(r.id)}">${icon('pin')}지도에서 보기</button><button class="button primary ${r.liked?'liked':''}" data-action="like-report" data-id="${esc(r.id)}" aria-pressed="${Boolean(r.liked)}">${icon('heart')}${r.liked?'공감했어요':'저도 공감해요'} ${Number(r.likes)||0}</button></div>`;}

function routesPanel(){
  const selected=state.routes.find(r=>r.id===state.routeSelection),shortest=state.routes.find(r=>r.id==='shortest'),comparison=state.routeSelection==='shortest'?state.routes.find(r=>r.id==='safest'):selected;
  const groups=groupRoutes(state.routes),allSame=groups.length===1;
  const reduction=shortest?.rawExposure>0&&comparison?Math.round((shortest.rawExposure-comparison.rawExposure)/shortest.rawExposure*100):0;
  const extra=comparison&&shortest?Math.round(comparison.distance-shortest.distance):0;
  const same=shortest&&comparison&&JSON.stringify(shortest.coordinates)===JSON.stringify(comparison.coordinates);
  const routeCard=route=>`<div class="route-card ${route.ids.includes(state.routeSelection)?'selected':''}"><div class="route-card-title">${esc(route.name)} 경로</div><div class="route-duration">${Number(route.duration)||0}<small>분</small></div><div class="route-distance">${formatDistance(route.distance)}</div><div class="route-risk"><span>위험노출</span><strong>${Number(route.exposure)||0}<small> 점</small></strong></div></div>`;
  return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}<div class="intro route-intro"><div class="eyebrow">OUR WAY TO SCHOOL</div><h1>조금 돌아가도,<br><span>더 안심되는 길.</span></h1><p>거리와 위험노출을 함께 비교해 보세요.</p></div><div class="journey-fields"><button class="journey-field" data-action="select-route-origin"><span class="journey-dot"></span><span><small>출발</small><strong>${esc(state.origin?.name || (state.origin?'지도에서 선택한 위치':'지도에서 출발지를 선택하세요'))}</strong></span>${icon('target')}</button><div class="journey-field"><span class="journey-dot destination"></span><span><small>도착</small><strong>${esc(config.school.name)}</strong></span>${icon('school')}</div></div>${api.isDemo?`<div class="sample-starts"><p>먼저 예시 출발지로 체험해 보세요.</p><div>${starts.map(s=>`<button class="sample-start ${state.origin?.id===s.id?'active':''}" data-action="sample-start" data-id="${s.id}">${s.name}</button>`).join('')}</div></div>`:''}<button class="button primary full calculate-button" data-action="calculate-routes" ${state.routeLoading?'disabled':''}>${state.routeLoading?'<span class="spinner"></span>경로를 비교하고 있어요':`${icon('route')}통학경로 비교하기`}</button>${errorBox(state.routeError,'calculate-routes')}${state.routes.length?`<h2 class="route-heading">나에게 맞는 통학길</h2><div class="route-tabs" role="group" aria-label="안전 가중치 선택">${state.routes.map(r=>`<button class="route-tab ${state.routeSelection===r.id?'active':''}" data-action="route-select" data-id="${esc(r.id)}" aria-pressed="${state.routeSelection===r.id}">${esc(r.name)}</button>`).join('')}</div><div class="route-comparison ${allSame?'single':''}">${groups.map(routeCard).join('')}</div><div class="route-insight">${icon('shield')}<div><strong>${allSame?'이 출발지는 최단 경로가 가장 안전해요':same?'선택한 경로는 최단 경로와 같아요.':reduction>0?`최단 경로보다<br>위험노출 ${reduction}% 감소`:'선택한 경로의 거리와 위험노출을 비교해 보세요.'}</strong><p>${allSame?'최단·균형·안전 경로가 모두 같아요.':same?'거리·시간·위험노출이 같은 경로예요.':`${extra>=0?`${formatDistance(extra)} 더 걷고`:`${formatDistance(Math.abs(extra))} 덜 걷고`}, 위험노출 원점수 ${shortest.rawExposure} → ${comparison.rawExposure}<br>${api.isDemo?'예시 점수로 비교한 체험 결과예요.':'제보를 바탕으로 비교한 결과예요.'}`}</p></div></div><p class="route-disclaimer">${api.isDemo?'화면 체험용 예시 경로예요. 실제 보도망을 계산한 경로가 아니며, 실제 보행 안내에 사용하지 마세요.':'제보 기반 비교 결과이며 실제 현장 상황은 다를 수 있어요.'}<br>위험노출은 사고확률이 아니에요.</p>`:`<div class="route-empty">${icon('route')}<h3>통학길에 안심을 한 겹 더</h3><p>출발지를 선택하면 최단·균형·안전 경로의<br>거리, 시간, 위험노출을 비교할 수 있어요.</p></div>`}</div><div class="panel-footer"><button class="button secondary full" data-action="select-route-origin">${icon('pin')}지도에서 출발지 선택</button></div>`;
}

function notificationsPanel(){return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}<h1 class="panel-title">제보 이후의 변화</h1><p class="panel-subtitle">소중한 제보가 어떻게 바뀌었는지 알려드려요.</p><div class="separator"></div>${errorBox(state.notificationError,'retry-notifications')}${state.notificationsLoading?loader():state.notifications.length?state.notifications.map(n=>`<button class="notification-card ${n.read?'':'unread'}" data-action="read-notification" data-id="${esc(n.id)}">${icon('bell')}<div><h3>${esc(n.title)}</h3><p>${esc(n.message)}</p><time>${relativeTime(n.createdAt)}</time></div>${n.read?'':'<span class="unread-dot" aria-label="읽지 않음"></span>'}</button>`).join(''):`<div class="empty-state">${icon('bell')}<h3>아직 새로운 알림이 없어요</h3><p>내 제보의 처리상태가 바뀌면<br>이곳에서 확인할 수 있어요.</p></div>`}</div><div class="panel-footer"><button class="button primary full" data-action="nav-map">${icon('map')}위험지도로 돌아가기</button></div>`;}

function adminPanel(){
  if(state.user?.role!=='ADMIN')return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}${errorBox('관리자 권한이 필요해요.')}</div>`;
  const reports=state.adminReports.filter(r=>state.adminFilter==='all'||state.adminFilter==='hidden'?state.adminFilter==='hidden'?r.hidden:true:!r.hidden&&r.status===state.adminFilter);
  return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}<h1 class="panel-title">더 나은 길을 위한<br>제보 관리</h1><div class="admin-banner">${icon('shield')}관리자${api.isDemo?' 체험':''} · ${state.adminReports.length}건의 제보</div><div class="form-section"><label class="field-label" for="admin-filter">처리상태 필터</label><select class="form-select" id="admin-filter"><option value="all" ${state.adminFilter==='all'?'selected':''}>전체 제보</option>${Object.entries(statuses).map(([key,label])=>`<option value="${key}" ${state.adminFilter===key?'selected':''}>${label}</option>`).join('')}<option value="hidden" ${state.adminFilter==='hidden'?'selected':''}>숨긴 제보</option></select></div>${errorBox(state.adminError,'retry-admin')}${state.adminLoading?loader():reports.map(r=>`<article class="admin-report ${r.hidden?'hidden-report':''}"><div class="report-kicker">${categoryOf(r).label} · ${relativeTime(r.createdAt)}</div><h3>${esc(r.title)}</h3><p>${esc(r.address || coordinateText(r))}</p><div class="admin-report-meta"><span class="risk-badge ${r.status==='resolved'?'resolved':'medium'}">${r.hidden?'비공개':statuses[r.status] || '접수'}</span><button class="text-button" data-action="admin-edit" data-id="${esc(r.id)}">${state.adminSelected===r.id?'접기':'관리하기'} ${icon('arrow')}</button></div>${state.adminSelected===r.id?adminEditor(r):''}</article>`).join('')}${!state.adminLoading&&!reports.length?`<div class="empty-state">${icon('list')}<h3>해당 상태의 제보가 없어요</h3></div>`:''}</div><div class="panel-footer"><button class="button secondary full" data-action="nav-map">${icon('map')}시민 화면 보기</button></div>`;
}
function adminEditor(r){const photo=safePhoto(r.photo);return `<form class="admin-editor" id="admin-editor" data-id="${esc(r.id)}"><h3>제보 검토</h3><p class="detail-content" style="margin:0;font-size:11px">${esc(r.description)}</p>${photo?`<img class="admin-photo" src="${esc(photo)}" alt="검토할 현장 사진"><label class="field-label" for="admin-photo-status">사진 공개</label><select id="admin-photo-status" name="photoStatus" class="form-select"><option value="pending" ${r.photoStatus==='pending'?'selected':''}>공개 검토 중</option><option value="approved" ${r.photoStatus==='approved'?'selected':''}>공개 승인</option><option value="rejected" ${r.photoStatus==='rejected'?'selected':''}>비공개</option></select>`:''}<label class="field-label" for="admin-status">처리상태</label><select id="admin-status" name="status" class="form-select">${[r.status,...(r.allowedNext||[])].map(key=>`<option value="${key}" ${r.status===key?'selected':''}>${adminStatuses[key]||key}${key===r.status?' (현재)':''}</option>`).join('')}</select>${(r.allowedNext||[]).length?'':'<p class="field-hint">더 바꿀 수 있는 상태가 없어요.</p>'}<label class="field-label" for="admin-result">처리결과</label><textarea id="admin-result" name="result" class="form-textarea" maxlength="500" placeholder="확인·조치한 내용을 알려주세요.">${esc(r.result)}</textarea><div class="admin-actions"><button type="button" class="button danger" data-action="admin-hide" data-id="${esc(r.id)}">${r.hidden?'공개 복원':'제보 숨기기'}</button><button type="submit" class="button primary">${icon('check')}상태 저장</button></div><p class="field-hint">저장하면 제보자에게 처리결과 알림이 전달돼요.</p></form>`;}

function guidePanel(){return `<div class="panel-scroll">${panelTop('위험지도로 돌아가기')}<div class="intro"><div class="eyebrow">TOGETHER, A SAFER WAY</div><h1>우리의 작은 관심이<br><span>길을 바꿔요.</span></h1><p>시민의 눈으로 발견하고, 함께 살피는 안심흥.</p></div><div class="form-section"><article class="guide-card">${icon('map')}<span class="guide-step">01 · 살펴보기</span><h3>우리 동네 통학길은 어떤가요?</h3><p>지도에서 위험 유형과 시간대를 골라보세요. 마커를 누르면 제보 내용과 처리상태를 확인할 수 있어요. 비회원도 지도를 볼 수 있어요.</p></article><article class="guide-card">${icon('pin')}<span class="guide-step">02 · 함께 알리기</span><h3>위험한 곳을 발견하면 알려주세요</h3><p>위치·위험 유형·시간대를 선택해 제보해요. 이미 올라온 제보에는 공감을 남겨주세요. 제보·공감은 만 14세 이상 회원이 이용할 수 있어요.</p></article><article class="guide-card">${icon('route')}<span class="guide-step">03 · 비교하고 선택하기</span><h3>가까운 길과 안심되는 길 사이</h3><p>같은 출발지에서 최단·균형·안전 경로를 비교해요. 거리와 시간, 위험노출을 살펴보고 나에게 맞는 통학길을 선택해요.</p></article></div><div class="info-box">${icon('info')}<span>제보가 없는 곳은 안전하다고 단정할 수 없어요. 위험점수와 위험노출은 사고확률이 아니며, 현장 상황을 함께 확인해야 해요.</span></div>${api.isDemo?`<div class="demo-guide"><h3>지금은 안심흥을 미리 체험하는 중이에요</h3><p>주소에 ?mock 이 붙어 있어 서버 대신 가짜 데이터(static/mock)를 보여주고 있어요. 등록·공감·처리 결과는 저장되지 않아요. 주소에서 ?mock 을 빼면 실제 서버와 연결돼요.</p><button class="button secondary full" data-action="demo-admin">${icon('settings')}관리자 화면 체험하기</button></div>`:''}<p class="report-list-note">함께 만드는 안심 통학길 · S-Link</p></div><div class="panel-footer"><button class="button primary full" data-action="nav-map">${icon('map')}우리 동네 살펴보기</button></div>`;}

function updateHeader(){
  const unread=state.notifications.some(n=>!n.read);
  $('#header-actions').innerHTML=`<span class="header-region">${icon('pin')}시흥시 정왕동</span><button class="icon-button" data-action="notifications" aria-label="${unread?'읽지 않은 알림 있음, 알림 열기':'알림 열기'}" title="알림">${icon('bell')}${unread?'<span class="notification-dot"></span>':''}</button>${state.user?`<button class="login-button" data-action="account"><span class="user-avatar">${esc(state.user.nickname?.slice(0,1) || '안')}</span><span class="user-nickname">${esc(state.user.nickname || '이웃')}${state.user.role==='ADMIN'?' · 관리':''}</span></button><button class="login-button" data-action="logout">로그아웃</button>`:`<button class="login-button" data-action="login">${icon('user')}${api.isDemo?'체험 로그인':'로그인'}</button>`}`;
  document.querySelectorAll('.main-nav .nav-button').forEach(button=>{const active=button.dataset.action===`nav-${['report','detail','admin','notifications'].includes(state.view)?'map':state.view}`;button.classList.toggle('active',active);if(active)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});
  const demoLabel=$('.demo-map-label');
  demoLabel.hidden=!(api.isDemo || config.demoData);
  if(demoLabel.hidden===false)demoLabel.innerHTML=`<span class="demo-badge">DEMO</span>${api.isDemo?'가짜 데이터(mock) 모드예요':'시연용 시드 데이터예요'}`;
}
function render(){
  const previousScroll=lastRenderedView===state.view ? $('.panel-scroll',panel)?.scrollTop || 0 : 0;
  const focused=panel.contains(document.activeElement)?document.activeElement:null;
  const focusSelector=focused?.id?`#${CSS.escape(focused.id)}`:focused?.dataset.action?`[data-action="${CSS.escape(focused.dataset.action)}"]${focused.dataset.id?`[data-id="${CSS.escape(focused.dataset.id)}"]`:''}${focused.dataset.value?`[data-value="${CSS.escape(focused.dataset.value)}"]`:''}`:null;
  panel.innerHTML=({map:mapPanel,report:reportPanel,detail:detailPanel,routes:routesPanel,notifications:notificationsPanel,admin:adminPanel,guide:guidePanel}[state.view] || mapPanel)();
  panel.setAttribute('aria-label',({map:'위험지도',report:'위험 제보 등록',detail:'제보 상세',routes:'통학경로 비교',notifications:'알림',admin:'제보 관리',guide:'이용안내'}[state.view] || '위험지도'));
  updateHeader();
  if(mapUI){mapUI.renderReports(state.view==='map'?filteredReports():state.reports,state.selectedId);}
  const routeLegend=$('#route-legend');
  routeLegend.hidden=state.view!=='routes'||!state.routes.length;
  if(!routeLegend.hidden){const selected=state.routes.find(r=>r.id===state.routeSelection),shortest=state.routes.find(r=>r.id==='shortest'),same=JSON.stringify(selected.coordinates)===JSON.stringify(shortest.coordinates);routeLegend.innerHTML=`${selected.id!=='shortest'&&!same?'<span><i class="route-line dashed"></i>최단 경로</span>':''}<span><i class="route-line ${esc(selected.id)}"></i>${esc(selected.name)} 경로</span>`;}
  if(lastRenderedView===state.view&&focusSelector)$(focusSelector,panel)?.focus({preventScroll:true});
  if($('.panel-scroll',panel))$('.panel-scroll',panel).scrollTop=previousScroll;
  lastRenderedView=state.view;
}
function toast(message,error=false){clearTimeout(toastTimer);const node=$('#toast');node.innerHTML=`${icon(error?'info':'check')}<span>${esc(message)}</span>`;node.classList.toggle('error',error);node.hidden=false;toastTimer=setTimeout(()=>node.hidden=true,error?6500:4200);}
function clearDetail(){detailRequest++;state.detailLoading=false;state.detailError='';state.detailNotFound=false;}
function updateReportPosition(pos){
  reportPosition=pos;
  const inside=pos&&distance(config.school,pos)<=config.school.radius;
  const next=$('[data-action="confirm-report-location"]');
  if(next)next.disabled=!inside;
  const hint=$('#report-position-hint');
  if(hint)hint.textContent=!pos?'지도를 움직여 위치를 골라주세요.':inside?'이 위치로 제보를 계속할 수 있어요.':`시범 지역(${config.school.name} 반경 ${config.school.radius}m) 안에서 선택해 주세요.`;
  $('#report-crosshair').classList.toggle('outside',!inside);
}
function setSelection(mode){
  clearTimeout(moveTimer);if(mode){reportsRequest++;state.loading=false;}
  state.selectionMode=mode;
  const node=$('#map-selection');node.hidden=!mode;node.classList.toggle('report-selection',mode==='report');
  $('#report-crosshair').hidden=mode!=='report';
  $('.app-layout').classList.toggle('is-report-selecting',mode==='report');
  if(mode){
    node.innerHTML=mode==='report'?`<div><strong>지도를 움직여 십자에 위치를 맞춰주세요</strong><span id="report-position-hint" aria-live="polite"></span></div><button data-action="confirm-report-location" disabled>다음</button><button data-action="cancel-selection" aria-label="위치 선택 취소">${icon('close')}</button>`:`${icon('pin')}<span>출발할 위치를 지도에서 눌러주세요</span><button data-action="cancel-selection" aria-label="위치 선택 취소">${icon('close')}</button>`;
    mapUI?.select(mode,mode==='report'?state.draft.position:state.origin);
    if(matchMedia('(max-width:760px)').matches)$('.map-section').scrollIntoView({behavior:'smooth',block:'start'});
  }else{reportPosition=null;mapUI?.select(null,state.view==='report'?state.draft.position:null);}
  mapUI?.invalidate();
}
function startReport(){requireLogin(()=>{state.reportError='';navigate('report');if(!state.draft.position)setSelection('report');});}
function navigate(view,{force=false}={}){
  if((state.view==='report'||state.returnToReport)&&state.draftDirty&&view!=='report'&&!force){showConfirm({title:'작성하던 제보를 나갈까요?',message:'입력한 내용을 지우고 이동해요.',confirm:'나가기',onConfirm:()=>{state.draft=freshDraft();state.draftDirty=false;state.duplicates=[];navigate(view,{force:true});}});return;}
  if(view==='admin'&&state.user?.role!=='ADMIN'){toast('관리자 권한이 필요해요.',true);return;}
  clearTimeout(moveTimer);reportsRequest++;state.loading=false;cancelRouteRequest();
  clearDetail();state.view=view;state.returnToReport=false;state.selectionMode=null;setSelection(null);if(view!=='routes')mapUI?.clearRoutes();
  if(view==='map'){state.selectedId=null;}render();history.replaceState(null,'',`#${view}`);panel.querySelector('.panel-scroll')?.scrollTo(0,0);
  if(view==='map')refreshReports();
  if(view==='routes'&&state.routes.length)drawRoutes(false);
  if(matchMedia('(max-width:760px)').matches&&view!=='map')panel.scrollIntoView({behavior:'smooth',block:'start'});
}
// 공공데이터 레이어 (#34): 처음 켤 때 한 번만 불러온다
let publicLoaded=false;
async function togglePublicLayers(button){if(!mapUI)return;if(!publicLoaded){button.disabled=true;try{mapUI.renderPublicLayers(await api.publicLayers());publicLoaded=true;}catch(error){toast(error.message,true);return;}finally{button.disabled=false;}}button.setAttribute('aria-pressed',String(mapUI.togglePublicLayers()));}
function requireLogin(action){if(state.user){action();return;}pendingAfterAuth=action;showAuth();}
async function refreshReports(bounds=mapUI?.getBounds()){
  clearTimeout(moveTimer);
  if(state.view==='detail'&&(state.selectedId||state.detailId)){await openReport(state.selectedId||state.detailId,state.returnToReport);return;}
  if(state.view==='report'||state.selectionMode)return;
  const requestId=++reportsRequest;
  state.loading=true;state.error='';render();
  try{const reports=await api.getReports(bounds?{bounds}:{});if(requestId===reportsRequest)state.reports=reports;}
  catch(error){if(requestId===reportsRequest)state.error=error.message;}
  finally{if(requestId===reportsRequest){state.loading=false;render();}}
}
async function checkDuplicates(){const requestId=++duplicateRequest;if(!state.draft.position)return;try{const results=await api.duplicates({...state.draft.position,category:state.draft.category});if(requestId===duplicateRequest){state.duplicates=results;if(!results.some(r=>r.id===state.draft.mergeHazardId))state.draft.mergeHazardId=null;if(state.view==='report')render();}}catch(error){if(requestId===duplicateRequest)toast(error.message,true);}}
async function openReport(id,fromDraft=false){
  cancelRouteRequest();
  clearTimeout(moveTimer);reportsRequest++;state.loading=false;
  const requestId=++detailRequest;
  state.detailId=String(id);state.selectedId=null;state.detailLoading=true;state.detailError='';state.detailNotFound=false;
  state.view='detail';state.returnToReport=fromDraft||state.returnToReport;setSelection(null);mapUI?.clearRoutes();render();
  if(matchMedia('(max-width:760px)').matches)panel.scrollIntoView({behavior:'smooth',block:'start'});
  let report;
  try{
    report=await api.getReport(id);
    if(requestId!==detailRequest||state.view!=='detail')return;
    const index=state.reports.findIndex(r=>r.id===report.id);
    if(index<0)state.reports.push(report);else state.reports[index]=report;
    state.selectedId=report.id;
  }catch(error){
    if(requestId!==detailRequest||state.view!=='detail')return;
    state.detailError=error.message;state.detailNotFound=error.status===404;
    if(state.detailNotFound)state.reports=state.reports.filter(r=>r.id!==String(id));
  }finally{
    if(requestId===detailRequest&&state.view==='detail'){state.detailLoading=false;render();if(report)mapUI?.focusReport(report);}
  }
}
async function loadNotifications({open=false}={}){state.notificationsLoading=true;state.notificationError='';if(open)navigate('notifications');try{state.notifications=await api.notifications();}catch(error){state.notificationError=error.message;if(error.status===401)state.user=null;}finally{state.notificationsLoading=false;if(state.view==='notifications')render();else updateHeader();}}
async function loadAdmin({open=false}={}){state.adminLoading=true;state.adminError='';if(open)navigate('admin');try{state.adminReports=await api.getReports({admin:true});}catch(error){state.adminError=error.message;}finally{state.adminLoading=false;if(state.view==='admin')render();}}
function drawRoutes(fit=true){try{mapUI?.routes(state.routes,state.routeSelection,state.origin,{fit});}catch(error){state.routeError=error.message;state.routes=[];mapUI?.clearRoutes();render();}}
function cancelRouteRequest(){routeRequest++;state.routeLoading=false;}
function resetRoutes(){cancelRouteRequest();state.routes=[];state.routeError='';mapUI?.clearRoutes();}

function showModal(content,kind){
  if(!modalKind)previousFocus=document.activeElement;modalKind=kind;
  $('#modal-root').innerHTML=`<div class="modal-backdrop"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><button class="icon-button close-modal" data-action="close-modal" aria-label="창 닫기">${icon('close')}</button>${content}</section></div>`;
  $('.app-header').inert=true;$('.app-layout').inert=true;document.body.style.overflow='hidden';
  requestAnimationFrame(()=>$('.modal input:not([type=checkbox]), .modal textarea, .modal button:not(.close-modal)')?.focus());
}
function closeModal(){ $('#modal-root').innerHTML='';modalKind=null;$('.app-header').inert=false;$('.app-layout').inert=false;document.body.style.overflow='';if(previousFocus?.isConnected)previousFocus.focus(); }
function showAuth(signup=false,email=''){
  const demo=api.isDemo;
  showModal(`<div class="modal-brand">${icon('shield')}</div><h2 id="modal-title">${signup?'같이 걸어요, 안심흥':'다시 만나 반가워요'}</h2><p>${signup?'우리 동네 통학길을 함께 살펴볼 이웃을 기다려요.':'로그인하고 우리 동네에 작은 변화를 남겨주세요.'}</p>${demo?`<div class="auth-demo-note">체험용 화면이에요. 실제 비밀번호를 입력하지 마세요.<br>체험 계정으로 제보·공감 기능을 확인할 수 있어요.</div>`:''}<form id="auth-form" data-signup="${signup}"><div class="form-section"><label class="field-label" for="auth-email">이메일</label><input id="auth-email" name="email" class="form-input" type="email" autocomplete="${demo?'off':'username'}" maxlength="100" required placeholder="name@example.com" value="${esc(email || (demo?'hello@ansimheung.demo':''))}"></div>${signup?'<div class="form-section"><label class="field-label" for="auth-nickname">닉네임</label><input id="auth-nickname" name="nickname" class="form-input" required minlength="2" maxlength="12" pattern="[가-힣A-Za-z0-9]{2,12}" title="닉네임은 한글·영문·숫자 2~12자예요" placeholder="이웃에게 보여줄 이름"></div>':''}<div class="form-section"><label class="field-label" for="auth-password">${demo?'체험 비밀번호':'비밀번호'}</label><input id="auth-password" name="password" class="form-input" type="password" minlength="8" maxlength="64" ${signup?'pattern="(?=.*[A-Za-z])(?=.*[0-9]).{8,64}" title="비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요"':''} required autocomplete="${demo?'off':signup?'new-password':'current-password'}" placeholder="영문·숫자 포함 8~64자" value="${demo?'demo1234':''}"></div>${signup?`<div class="form-section"><label class="field-label" for="auth-confirm">비밀번호 확인</label><input id="auth-confirm" name="confirm" class="form-input" type="password" minlength="8" maxlength="64" required autocomplete="${demo?'off':'new-password'}" value="${demo?'demo1234':''}"></div><div class="checkboxes"><label class="check-field"><input type="checkbox" name="over14" required>만 14세 이상입니다. (필수)</label><label class="check-field"><input type="checkbox" name="agree" required>${demo?'(mock) 가짜 데이터 모드 — 가입 정보는 저장되지 않아요.':'서비스 이용과 제보 처리에 필요한 개인정보 처리에 동의합니다.'} (필수)</label></div>`:''}<p class="field-error modal-error" id="auth-error" role="alert"></p><button class="button primary full" type="submit">${signup?demo?'체험 회원가입':'회원가입':demo?'체험 계정으로 로그인':'로그인'} ${icon('arrow')}</button></form><div class="modal-footer-text">${signup?'이미 이웃이신가요?':'처음 오셨나요?'}<button data-action="${signup?'auth-login':'auth-signup'}">${signup?'로그인':'회원가입'}</button></div>${demo&&!signup?`<div class="divider-text">입력 없이 기능 살펴보기</div><button class="button secondary full" data-action="demo-member">일반 회원 체험 시작</button><button class="button quiet full demo-admin-button" data-action="demo-admin">${icon('settings')}관리자 계정 체험</button>`:''}`,'auth');
}
function showAccount(){showModal(`<div class="modal-brand">${icon('user')}</div><h2 id="modal-title">안녕하세요, ${esc(state.user.nickname)}님</h2><p>오늘도 우리 동네에 관심을 나눠주셔서 고마워요.</p><div class="account-chip"><span class="user-avatar">${esc(state.user.nickname.slice(0,1))}</span><span><strong>${esc(state.user.nickname)}</strong><small>${esc(state.user.email || '')} · ${state.user.role==='ADMIN'?'관리자':'이웃'}</small></span></div><div class="account-actions">${api.isDemo&&state.user.role==='ADMIN'?'<button class="button secondary full" data-action="demo-member">일반 회원으로 체험</button>':''}${state.user.role==='ADMIN'?'<button class="button primary full" data-action="open-admin">제보 관리 화면으로 이동</button>':''}${api.isDemo&&state.user.role!=='ADMIN'?`<button class="button secondary full" data-action="demo-admin">${icon('settings')}관리자 화면 체험</button>`:''}<button class="button quiet full" data-action="logout">로그아웃</button></div>`,'account');}
let confirmation=null;
function showConfirm(options){confirmation=options;showModal(`<div class="modal-brand">${icon('info')}</div><h2 id="modal-title">${esc(options.title)}</h2><p>${esc(options.message)}</p><form id="confirm-form">${options.reason?'<div class="form-section"><label class="field-label" for="confirm-reason">비공개 사유</label><textarea id="confirm-reason" name="reason" class="form-textarea" required maxlength="500" placeholder="제보자에게 전달할 사유를 입력해 주세요."></textarea></div>':''}<button type="submit" class="button primary full">${esc(options.confirm || '확인')}</button>${options.secondary?`<button type="button" class="button secondary full" data-action="confirm-secondary">${esc(options.secondary)}</button>`:''}<button type="button" class="button quiet full" data-action="close-modal">취소</button></form>`,'confirm');}

async function signIn(payload){state.user=await api.login(payload);closeModal();render();await refreshReports();await loadNotifications();toast(`${state.user.nickname}님, 반가워요.`);if(pendingAfterAuth){const after=pendingAfterAuth;pendingAfterAuth=null;authReturnPath=null;await after();}else if(authReturnPath){const path=authReturnPath;authReturnPath=null;location.assign(path);}}
async function submitReport(){
  if(state.submitting)return;
  const d=state.draft;
  if(!d.position){state.reportError='위치를 선택해 주세요';render();return;}
  if(distance(config.school,d.position)>config.school.radius){state.reportError=`시범 지역(${config.school.name} 반경 ${config.school.radius}m) 안에서 선택해 주세요.`;render();return;}
  if(!d.title.trim()||!d.description.trim()){state.reportError='제목과 위험 상황을 입력해 주세요.';render();return;}
  if(!state.user){requireLogin(submitReport);return;}
  // 묶기(#25, P-02): 20m 안 같은 유형 지점이 있으면 조용히 새 지점을 만들지 않고 고르게 한다
  if(state.duplicates.length&&!d.mergeHazardId){const dup=state.duplicates[0];showConfirm({title:'이미 제보된 곳이에요',message:`${config.mergeRadiusM}m 안에 같은 유형의 제보가 있어요. 같은 위험은 한 지점으로 모아요. 내 제보를 이 지점에 더하거나, 기존 제보에 공감해 주세요.`,confirm:'이 지점에 내 제보 더하기',onConfirm:()=>{state.draft.mergeHazardId=dup.id;return submitReport();},secondary:'기존 제보 보고 공감하기',onSecondary:()=>openReport(dup.id,true)});return;}
  state.submitting=true;state.reportError='';render();
  let created;
  try{created=await api.createReport({...d,...d.position});}
  catch(error){state.reportError=error.message;if(error.status===401){state.user=null;requireLogin(submitReport);}}
  finally{state.submitting=false;render();}
  if(!created)return;
  // POST가 성공하면 후속 조회 실패와 관계없이 작성 화면을 닫아 중복 전송을 막는다.
  state.draft=freshDraft();state.draftDirty=false;state.duplicates=[];state.category='all';state.time='any';state.resolved=true;
  navigate('map',{force:true});await refreshReports();
  try{let report=state.reports.find(r=>r.id===String(created.hazardId));if(!report){report=await api.getReport(created.hazardId);state.reports.push(report);}state.selectedId=report.id;render();mapUI?.focusReport(report);}
  catch(error){toast(error.message,true);return;}
  toast(api.isDemo?'(mock) 제보를 지도에 표시했어요. 새로고침하면 사라져요.':created.merged?'기존 지점에 내 제보를 더했어요. 관심을 나눠주셔서 고마워요.':'제보가 등록되었어요. 관심을 나눠주셔서 고마워요.');
}
async function calculateRoutes(){
  if(state.routeLoading)return;
  if(!state.origin){state.routeError='출발지를 먼저 선택해 주세요.';render();return;}
  const requestId=++routeRequest;
  state.routeLoading=true;state.routeError='';state.routes=[];mapUI?.clearRoutes();setSelection(null);render();
  try{const response=await api.routes({lat:state.origin.lat,lng:state.origin.lng});if(requestId!==routeRequest||state.view!=='routes')return;if(!Array.isArray(response.routes)||!['shortest','balanced','safest'].every(id=>response.routes.some(r=>r.id===id)))throw new Error('세 가지 경로를 모두 받지 못했어요. 다시 조회해 주세요.');state.routes=response.routes;state.routeSelection='safest';drawRoutes();}
  catch(error){if(requestId===routeRequest)state.routeError=error.message;}
  finally{if(requestId===routeRequest){state.routeLoading=false;if(state.view==='routes')render();}}
}

document.addEventListener('input',event=>{
  if(event.target.id==='report-title'){state.draft.title=event.target.value;state.draftDirty=true;}
  if(event.target.id==='report-description'){state.draft.description=event.target.value;state.draftDirty=true;$('#description-count').textContent=event.target.value.length;}
});
document.addEventListener('change',async event=>{
  const node=event.target;
  if(node.id==='time-filter'){state.time=node.value;render();}
  if(node.id==='sort-filter'){state.sort=node.value;render();}
  if(node.id==='resolved-filter'){state.resolved=node.checked;render();}
  if(node.id==='admin-filter'){state.adminFilter=node.value;state.adminSelected=null;render();}
  if(node.id==='report-photo'){
    const file=node.files?.[0];if(!file)return;
    if(!['image/jpeg','image/png'].includes(file.type)){toast('JPG, PNG 사진만 첨부할 수 있어요.',true);node.value='';return;}
    if(file.size>config.maxPhotoBytes){toast('5MB 이하의 사진을 선택해 주세요.',true);node.value='';return;}
    try{const photo=await resizePhoto(file);state.draft.photo=photo;state.draft.photoFile=file;state.draftDirty=true;render();}catch(error){toast(error.message,true);}
  }
});

document.addEventListener('submit',async event=>{
  event.preventDefault();
  const form=event.target;
  if(form.id==='report-form'){await submitReport();return;}
  if(form.id==='auth-form'){
    const fields=new FormData(form),submit=form.querySelector('[type=submit]');
    const payload={email:fields.get('email').trim(),password:fields.get('password'),nickname:fields.get('nickname')?.trim(),over14:fields.has('over14'),agree:fields.has('agree')};
    if(form.dataset.signup==='true'&&payload.password!==fields.get('confirm')){$('#auth-error').textContent='비밀번호가 일치하지 않아요';return;}
    submit.disabled=true;
    try{if(form.dataset.signup==='true'){await api.register(payload);toast(api.isDemo?'체험 가입을 확인했어요. 체험 계정으로 로그인해 주세요.':'가입했어요. 로그인해 주세요.');showAuth(false,payload.email);}else await signIn(payload);}
    catch(error){if($('#auth-error'))$('#auth-error').textContent=error.message;else toast(error.message,true);}
    finally{submit.disabled=false;}
  }
  if(form.id==='confirm-form'){const options=confirmation, reason=new FormData(form).get('reason')?.trim();if(options.reason&&!reason)return;closeModal();confirmation=null;await options.onConfirm(reason);}
  if(form.id==='admin-editor'){
    const submit=form.querySelector('[type=submit]');if(submit.disabled)return;submit.disabled=true;
    const fields=new FormData(form),payload={status:fields.get('status'),result:fields.get('result')};if(fields.has('photoStatus'))payload.photoStatus=fields.get('photoStatus');
    try{await api.updateReport(form.dataset.id,payload);toast('처리상태를 저장하고 제보자에게 알렸어요.');await refreshReports();await loadAdmin();await loadNotifications();}catch(error){toast(error.message,true);}finally{submit.disabled=false;}
  }
});

document.addEventListener('click',async event=>{
  const brand=event.target.closest('.brand');if(brand){event.preventDefault();navigate('map');return;}
  const button=event.target.closest('[data-action]');if(!button||button.disabled)return;
  const action=button.dataset.action,id=button.dataset.id,value=button.dataset.value;
  try{
    switch(action){
      case 'nav-map':navigate('map');break;
      case 'nav-routes':navigate('routes');break;
      case 'nav-guide':navigate('guide');break;
      case 'new-report':startReport();break;
      case 'filter-category':state.category=value;render();break;
      case 'reset-filters':state.category='all';state.time='any';state.resolved=true;render();break;
      case 'open-report':await openReport(id);break;
      case 'retry-detail':await openReport(state.detailId,state.returnToReport);break;
      case 'duplicate-detail':await openReport(id,true);break;
      case 'confirm-secondary':{const options=confirmation;closeModal();confirmation=null;await options?.onSecondary?.();break;}
      case 'draft-merge':state.draft.mergeHazardId=state.draft.mergeHazardId===id?null:id;state.draftDirty=true;render();break;
      case 'back-to-draft':clearDetail();state.view='report';state.returnToReport=false;render();mapUI?.selectionPoint(state.draft.position);break;
      case 'draft-category':state.draft.category=value;state.draftDirty=true;render();await checkDuplicates();break;
      case 'draft-time':state.draft.timeBand=value;state.draftDirty=true;render();break;
      case 'select-report-location':setSelection('report');break;
      case 'confirm-report-location':if(reportPosition&&distance(config.school,reportPosition)<=config.school.radius){state.draft.position={...reportPosition};state.draftDirty=true;state.reportError='';setSelection(null);render();await checkDuplicates();if(matchMedia('(max-width:760px)').matches)panel.scrollIntoView({behavior:'smooth',block:'start'});}break;
      case 'cancel-selection':setSelection(null);break;
      case 'select-route-origin':resetRoutes();setSelection('route');render();break;
      case 'remove-photo':state.draft.photo=null;state.draft.photoFile=null;state.draftDirty=true;render();break;
      case 'sample-start':resetRoutes();state.origin={...starts.find(s=>s.id===id)};setSelection(null);render();mapUI?.selectionPoint(state.origin,'route');mapUI?.flyTo(state.origin);break;
      case 'calculate-routes':await calculateRoutes();break;
      case 'route-select':state.routeSelection=id;render();drawRoutes(false);break;
      case 'focus-report':{const report=state.reports.find(r=>r.id===id);if(report)mapUI?.focusReport(report);if(matchMedia('(max-width:760px)').matches)$('.map-section').scrollIntoView({behavior:'smooth',block:'start'});break;}
      case 'like-report':requireLogin(async()=>{button.disabled=true;try{const result=await api.like(id);const index=state.reports.findIndex(r=>r.id===id);if(index>=0)state.reports[index]=result;render();toast(result.liked?'공감이 더해졌어요. 관심을 나눠주셔서 고마워요.':'공감을 취소했어요.');}catch(error){toast(error.message,true);if(error.status===401){state.user=null;showAuth();}}finally{button.disabled=false;}});break;
      case 'login':showAuth();break;
      case 'auth-signup':showAuth(true);break;
      case 'auth-login':showAuth();break;
      case 'close-modal':closeModal();pendingAfterAuth=null;authReturnPath=null;break;
      case 'account':showAccount();break;
      case 'logout':await api.logout();state.user=null;state.notifications=[];closeModal();if(['admin','notifications','report'].includes(state.view))navigate('map',{force:true});await refreshReports();toast('로그아웃했어요.');break;
      case 'demo-admin':if(api.isDemo){pendingAfterAuth=null;await signIn({email:'admin@ansimheung.demo',role:'ADMIN'});await loadAdmin({open:true});}break;
      case 'demo-member':if(api.isDemo){await signIn({email:'hello@ansimheung.demo',role:'USER'});if(state.view==='admin')navigate('map');}break;
      case 'open-admin':closeModal();await loadAdmin({open:true});break;
      case 'notifications':requireLogin(()=>loadNotifications({open:true}));break;
      case 'retry-notifications':await loadNotifications();break;
      case 'read-notification':{const n=state.notifications.find(n=>n.id===id);if(!n)break;await api.readNotification(id);n.read=true;await openReport(n.reportId);updateHeader();break;}
      case 'retry-reports':await refreshReports();break;
      case 'retry-admin':await loadAdmin();break;
      case 'admin-edit':state.adminSelected=state.adminSelected===id?null:id;render();break;
      case 'admin-hide':{const report=state.adminReports.find(r=>r.id===id);if(!report)break;showConfirm({title:report.hidden?'이 제보를 다시 공개할까요?':'제보를 숨길까요?',message:report.hidden?`“${report.title}”이 시민 지도에 다시 표시돼요.`:`“${report.title}”이 시민 지도와 상세 화면에서 보이지 않게 돼요.`,reason:!report.hidden,confirm:report.hidden?'공개 복원':'비공개로 변경',onConfirm:async reason=>{try{await api.updateReport(id,{hidden:!report.hidden,...(reason?{result:reason}:{})});await refreshReports();await loadAdmin();await loadNotifications();toast(report.hidden?'제보를 다시 공개했어요.':'제보를 비공개 처리했어요.');}catch(error){toast(error.message,true);}}});break;}
      case 'toggle-zones':button.setAttribute('aria-pressed',String(mapUI?.toggleZones() || false));break;
      case 'toggle-public':await togglePublicLayers(button);break;
      case 'zoom-in':mapUI?.zoomIn();break;
      case 'zoom-out':mapUI?.zoomOut();break;
      case 'school-center':mapUI?.center();break;
      case 'retry-tiles':$('#map-error').hidden=true;mapUI?.retryTiles();break;
      case 'my-location':if(!navigator.geolocation){toast('이 브라우저에서는 위치를 확인할 수 없어요.',true);break;}button.disabled=true;navigator.geolocation.getCurrentPosition(position=>{button.disabled=false;const pos={lat:position.coords.latitude,lng:position.coords.longitude};if(distance(pos,config.school)>config.school.radius){toast('현재 위치가 시범지역 밖이에요. 학교 주변 지도를 이용해 주세요.',true);return;}mapUI?.flyTo(pos);mapUI?.selectionPoint(pos,'route');toast('현재 위치로 이동했어요.');},()=>{button.disabled=false;toast('위치 권한이 없거나 위치를 확인할 수 없어요. 지도에서 직접 선택할 수 있어요.',true);},{enableHighAccuracy:false,timeout:8000,maximumAge:60000});break;
    }
  }catch(error){toast(error.message || '처리하지 못했어요. 다시 시도해 주세요.',true);}
});
document.addEventListener('keydown',event=>{
  if(event.key==='Escape'){if(modalKind){closeModal();pendingAfterAuth=null;authReturnPath=null;}else if(state.selectionMode)setSelection(null);}
  if(event.key==='Tab'&&modalKind){const nodes=[...$('.modal').querySelectorAll('button:not([disabled]),input:not([disabled]),textarea,select,a[href]')].filter(node=>!node.hidden);const first=nodes[0],last=nodes[nodes.length-1];if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}}
});
window.addEventListener('beforeunload',event=>{if(state.draftDirty){event.preventDefault();event.returnValue='';}});

async function initialize(){
  if($('#area-school'))$('#area-school').textContent=config.school.name;
  if($('#area-radius'))$('#area-radius').textContent=`주변 ${config.school.radius}m`;
  $('.map-section').setAttribute('aria-label',`${config.school.name} 주변 지도`);
  if(!config.serverReachable)toast('서버에 연결하지 못했어요. 서버를 켜거나 주소 끝에 ?mock 을 붙여 확인해 주세요.',true);
  try{mapUI=createMap({onReportPositionChange:updateReportPosition,onReportClick:id=>{if(state.view==='report'&&state.draftDirty)return;openReport(id);},onSelect:async(pos,mode)=>{if(distance(config.school,pos)>config.school.radius){toast(`시범 지역(${config.school.name} 반경 ${config.school.radius}m) 안에서 선택해 주세요.`,true);return;}if(mode==='report'){state.draft.position=pos;state.draftDirty=true;state.reportError='';setSelection(null);render();mapUI.selectionPoint(pos);await checkDuplicates();}else{resetRoutes();state.origin={...pos,name:'지도에서 선택한 위치'};setSelection(null);mapUI.selectionPoint(pos,'route');render();}toast('위치를 선택했어요.');if(matchMedia('(max-width:760px)').matches)panel.scrollIntoView({behavior:'smooth',block:'start'});},onTileError:()=>$('#map-error').hidden=false,onTileReady:()=>$('#map-error').hidden=true,onMoveEnd:bounds=>{clearTimeout(moveTimer);if(state.view==='map'&&!state.selectionMode)moveTimer=setTimeout(()=>{if(state.view==='map'&&!state.selectionMode)refreshReports(bounds);},300);}});}catch(error){$('#map-error').hidden=false;toast(error.message,true);}
  try{state.user=await api.getSession();}catch(error){toast(error.message,true);}
  const initialView=location.hash.slice(1);if(['map','routes','guide'].includes(initialView))state.view=initialView;
  await refreshReports();if(state.user)await loadNotifications();
  if(initialView==='report')startReport();
  if(!state.user&&new URLSearchParams(location.search).has('returnUrl'))showAuth();
  window.addEventListener('resize',()=>mapUI?.invalidate());
}
initialize();
