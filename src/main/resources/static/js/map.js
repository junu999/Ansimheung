import { config } from './config.js';
import { categories, riskNames } from './labels.js';
import { icon, escapeHtml, riskLevel } from './utils.js';

export function createMap({onReportClick,onSelect,onTileError,onTileReady}) {
  if(!window.L) throw new Error('지도 구성요소를 불러오지 못했어요. 페이지를 새로고침해 주세요.');
  const L=window.L;
  const {lat:sLat,lng:sLng}=config.school;
  // 시범 지역(반경 500m)보다 조금 넓게 이동 범위를 제한
  const map=L.map('map',{zoomControl:false,minZoom:15,maxZoom:19,scrollWheelZoom:true,maxBounds:[[sLat-.009,sLng-.011],[sLat+.009,sLng+.011]],maxBoundsViscosity:.8}).setView([sLat,sLng],17);
  const attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors';
  let loaded=0, failed=0, selectionMode=null, selectedMarker=null, originMarker=null, zonesVisible=false;
  const tiles=L.tileLayer(config.basemap.tileUrl,{maxZoom:19,attribution}).addTo(map);
  tiles.on('tileload',()=>{loaded++;onTileReady();});
  tiles.on('tileerror',()=>{failed++;if(loaded===0 && failed>=3)onTileError();});
  const radius=L.circle([config.school.lat,config.school.lng],{radius:config.school.radius,color:'#4a9679',weight:1.5,dashArray:'5 9',fillColor:'#81b196',fillOpacity:.035,interactive:false}).addTo(map);
  const schoolIcon=L.divIcon({className:'school-div-icon',html:`<div class="school-map-symbol">${icon('school')}</div><div class="school-map-label">${escapeHtml(config.school.name)}<span>우리의 목적지</span></div>`,iconSize:[144,68],iconAnchor:[72,34]});
  const school=L.marker([config.school.lat,config.school.lng],{icon:schoolIcon,zIndexOffset:600,title:config.school.name}).addTo(map);
  school.bindTooltip(escapeHtml(`${config.school.name} · ${config.school.address || ''}`),{direction:'bottom',offset:[0,40]});
  let reportLayer=L.layerGroup().addTo(map), zoneLayer=L.layerGroup(), routeLayer=L.layerGroup().addTo(map);
  const markers=new Map();
  map.on('click',event=>{if(selectionMode)onSelect({lat:event.latlng.lat,lng:event.latlng.lng},selectionMode);});
  map.on('zoomend',()=>document.querySelector('.map-section').classList.toggle('map-zoomed-out',map.getZoom()<15));
  const setSelected=(pos,type='report')=>{
    if(selectedMarker)map.removeLayer(selectedMarker);
    if(!pos){selectedMarker=null;return;}
    selectedMarker=L.marker([pos.lat,pos.lng],{icon:L.divIcon({className:'selected-div-icon',html:`<span class="selected-pin">${icon(type==='route'?'flag':'pin')}</span>`,iconSize:[36,44],iconAnchor:[18,40]}),zIndexOffset:1000}).addTo(map);
  };
  return {
    renderReports(reports,selectedId) {
      reportLayer.clearLayers();zoneLayer.clearLayers();markers.clear();
      reports.forEach(report=>{
        const level=riskLevel(report), category=categories[report.category] || categories.sidewalk;
        const marker=L.marker([report.lat,report.lng],{icon:L.divIcon({className:`report-div-icon ${report.id===selectedId?'is-selected':''}`,html:`<span class="report-map-pin risk-${level}">${icon(report.status==='resolved'?'check':category.icon)}</span>`,iconSize:[38,44],iconAnchor:[19,38]}),title:`${report.title} · ${riskNames[level]}`,alt:report.title,riseOnHover:true});
        marker.bindTooltip(`${escapeHtml(report.title)} <span class="tooltip-risk">${riskNames[level]}</span>`,{direction:'top',offset:[0,-36]});
        marker.on('click',()=>{if(!selectionMode)onReportClick(report.id);});
        marker.addTo(reportLayer);markers.set(report.id,marker);
        if(report.status!=='resolved')L.circle([report.lat,report.lng],{radius:level==='high'?55:35,color:level==='high'?'#db745b':'#d3a153',weight:1,fillOpacity:.1,interactive:false}).addTo(zoneLayer);
      });
    },
    focusReport(report) { map.flyTo([report.lat,report.lng],18,{duration:.6}); markers.get(report.id)?.openTooltip(); },
    center() { map.flyTo([config.school.lat-.00015,config.school.lng-.0004],17,{duration:.6}); },
    select(mode,pos) {selectionMode=mode;document.getElementById('map').classList.toggle('is-selecting',Boolean(mode));setSelected(pos,mode);},
    selectionPoint: setSelected,
    flyTo(pos) {map.flyTo([pos.lat,pos.lng],17,{duration:.5});},
    zoomIn:()=>map.zoomIn(), zoomOut:()=>map.zoomOut(),
    retryTiles() {loaded=0;failed=0;tiles.redraw();},
    toggleZones() {zonesVisible=!zonesVisible;if(zonesVisible)zoneLayer.addTo(map);else map.removeLayer(zoneLayer);return zonesVisible;},
    clearRoutes() {routeLayer.clearLayers();if(originMarker)map.removeLayer(originMarker);originMarker=null;},
    routes(routes,selectedId,origin,{fit=true}={}) {
      routeLayer.clearLayers();
      if(originMarker)map.removeLayer(originMarker);
      const shortest=routes.find(r=>r.id==='shortest'), selected=routes.find(r=>r.id===selectedId);
      if(!shortest || !selected)throw new Error('경로 좌표를 확인할 수 없어요. 다시 조회해 주세요.');
      const same=JSON.stringify(shortest.coordinates)===JSON.stringify(selected.coordinates);
      const visible=selectedId==='shortest'||same?[selected]:[shortest,selected];
      for(const route of visible) {
        if(!Array.isArray(route.coordinates) || route.coordinates.length<2 || !route.coordinates.every(p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite)))throw new Error('서버가 반환한 경로 좌표 형식이 올바르지 않아요.');
        L.polyline(route.coordinates,{color:'#fff',weight:route.id===selectedId?10:8,opacity:.95,interactive:false}).addTo(routeLayer);
        L.polyline(route.coordinates,{color:route.color || '#17765b',weight:route.id===selectedId?6:4,opacity:route.id===selectedId?1:.8,dashArray:route.id===selectedId?null:'7 7'}).addTo(routeLayer).bindTooltip(`${escapeHtml(route.name)} 경로`);
      }
      originMarker=L.marker([origin.lat,origin.lng],{icon:L.divIcon({className:'origin-div-icon',html:'<span class="origin-marker">출발</span>',iconSize:[42,32],iconAnchor:[21,16]}),zIndexOffset:700}).addTo(map);
      if(fit)map.fitBounds(L.latLngBounds(visible.flatMap(route=>route.coordinates)),{padding:[80,100],maxZoom:18});
    },
    invalidate:()=>map.invalidateSize(),
    radius,
  };
}
