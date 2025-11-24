// kpi.js - 统一 KPI 计算与渲染管理 (订单类 KPI)
(function(global){
  const KPI = global.KPI || {};
  // Smooth number animation
  function animateNumber(el, target, prefix='', suffix='', duration=600){
    if(!el) return; const startVal=parseFloat(el.textContent.replace(/[^0-9.]/g,''))||0; const endVal=parseFloat(target)||0; const start=performance.now();
    function frame(now){ const p=Math.min(1,(now-start)/duration); const val=startVal+(endVal-startVal)*p; el.textContent=prefix+val.toFixed( (endVal%1!==0)?2:0 )+suffix; if(p<1) requestAnimationFrame(frame); }
    requestAnimationFrame(frame);
  }
  // Threshold highlight utility
  function applyThreshold(el, value, th){ if(!el) return; if(th && typeof th==='object'){ const v=parseFloat(value)||0; let cls=''; if(th.danger!=null && v>=th.danger) cls='ring-2 ring-red-400'; else if(th.warning!=null && v>=th.warning) cls='ring-2 ring-amber-300'; el.classList.remove('ring-2','ring-red-400','ring-amber-300'); if(cls){ el.classList.add(...cls.split(' ')); } }
  }
  // Order KPIs extended with animation & threshold config
  const origUpdateOrder = KPI.updateOrderKpis;
  KPI.updateOrderKpis = function(list,cfg={}){ origUpdateOrder.call(KPI,list,cfg); const map=Object.assign({salesToday:'kpi-sales-today',ordersToday:'kpi-orders-today',pendingCount:'kpi-pending-count',refundCount:'kpi-refund-count',avgTicket:'kpi-avg-ticket'}, cfg.mapping||{}); if(Array.isArray(list)&&list.length){
      // Animate key numbers
      ['salesToday','ordersToday','avgTicket'].forEach(k=>{ const id=map[k]; const el=document.getElementById(id); if(el){ const raw=el.textContent.replace(/^¥/,''); animateNumber(el, raw, k==='salesToday'||k==='avgTicket'?'¥':''); }});
      // Thresholds
      applyThreshold(document.getElementById(map.pendingCount), document.getElementById(map.pendingCount)?.textContent, {warning:5,danger:10});
      applyThreshold(document.getElementById(map.refundCount), document.getElementById(map.refundCount)?.textContent, {warning:2,danger:5});
    }
  };
  // Inventory KPIs
  KPI.updateInventoryKpis = function(data,cfg={}){ const map=Object.assign({total:'kpi-total-medicines',low:'kpi-lowstock',expiry:'kpi-expiry'}, cfg.mapping||{}); if(!data){ ['total','low','expiry'].forEach(k=>{const el=document.getElementById(map[k]); if(el) el.textContent='--';}); return; } const total=data.totalMedicines||0; const low=data.lowStock||0; const expiry=data.expiry||0; const totalEl=document.getElementById(map.total); if(totalEl){ animateNumber(totalEl,total); } const lowEl=document.getElementById(map.low); if(lowEl){ animateNumber(lowEl,low); applyThreshold(lowEl,low,{warning:10,danger:30}); } const expEl=document.getElementById(map.expiry); if(expEl){ animateNumber(expEl,expiry); applyThreshold(expEl,expiry,{warning:5,danger:15}); } };
  // Stock-in KPIs
  KPI.updateStockInKpis = function(data,cfg={}){ const map=Object.assign({count:'kpi-item-count',cost:'kpi-total-cost',price:'kpi-total-price',profit:'kpi-total-profit'},cfg.mapping||{}); if(!data){ Object.values(map).forEach(id=>{const el=document.getElementById(id); if(el) el.textContent='--';}); return; } animateNumber(document.getElementById(map.count), data.itemCount||0,'',''); animateNumber(document.getElementById(map.cost), data.totalCost||0,'¥'); animateNumber(document.getElementById(map.price), data.totalPrice||0,'¥'); animateNumber(document.getElementById(map.profit), data.totalProfit||0,'¥'); };
  // Member KPIs
  KPI.updateMemberKpis = function(data,cfg={}){ const map=Object.assign({total:'total-members',vip:'vip-members',new:'new-members',sleep:'sleeping-members'},cfg.mapping||{}); if(!data){ Object.values(map).forEach(id=>{const el=document.getElementById(id); if(el) el.textContent='--';}); return; } const total=data.totalMembers||0; const vip=data.vipMembers||0; const neu=data.newMembers||0; const sleep=data.sleepingMembers||0; animateNumber(document.getElementById(map.total),total,'',' 人'); animateNumber(document.getElementById(map.vip),vip,'',' 人'); animateNumber(document.getElementById(map.new),neu,'',' 人'); animateNumber(document.getElementById(map.sleep),sleep,'',' 人'); applyThreshold(document.getElementById(map.sleep),sleep,{warning:50,danger:100}); };
  // Horizontal KPI scroll enhancements (fades + arrows)
  function enhanceHorizontal(container){ if(!container || container.__enhanced) return; container.__enhanced=true; const left=document.createElement('div'); const right=document.createElement('div'); left.className='kpi-fade kpi-fade-left hidden'; right.className='kpi-fade kpi-fade-right'; container.parentNode.insertBefore(left,container); container.parentNode.insertBefore(right,container.nextSibling); const arrowL=document.createElement('button'); const arrowR=document.createElement('button'); arrowL.className='kpi-arrow kpi-arrow-left'; arrowR.className='kpi-arrow kpi-arrow-right'; arrowL.innerHTML='\u25C0'; arrowR.innerHTML='\u25B6'; container.parentNode.appendChild(arrowL); container.parentNode.appendChild(arrowR); function update(){ const maxScroll=container.scrollWidth - container.clientWidth; const sc=container.scrollLeft; left.classList.toggle('hidden', sc<=5); right.classList.toggle('hidden', sc>=maxScroll-5); } arrowL.onclick=()=>{ container.scrollBy({left:-240,behavior:'smooth'}); }; arrowR.onclick=()=>{ container.scrollBy({left:240,behavior:'smooth'}); }; container.addEventListener('scroll',update,{passive:true}); update(); }
  KPI.initHorizontalKpis = function(){ document.querySelectorAll('.dashboard-kpis.kpi-horizontal').forEach(enhanceHorizontal); };
  global.KPI = KPI;
  document.addEventListener('DOMContentLoaded',()=>{ KPI.initHorizontalKpis(); });
})(window);
