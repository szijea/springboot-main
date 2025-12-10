// cashier.js - 收银页面逻辑
// 依赖 common.js 中的 BASE_URL 以及 medicineAPI/memberAPI/orderAPI

(function(){
  const cart = []; // { medicineId, name, spec, price, quantity }
  let selectedMember = null;
  let hangOrders = []; // 简单前端缓存挂单

  // 工具函数
  function formatMoney(v){ return '¥' + (Number(v||0).toFixed(2)); }
  function $(id){ return document.getElementById(id); }
  function show(el){ if(el) el.classList.remove('hidden'); }
  function hide(el){ if(el) el.classList.add('hidden'); }
  function safeInner(el, html){ if(el) el.innerHTML = html; }

  function renderCart(){
    const body = $('cart-body');
    const empty = $('cart-empty');
    if(!body) return;
    if(cart.length === 0){
      safeInner(body,'');
      show(empty);
      updateSummary();
      return;
    }
    hide(empty);
    safeInner(body, cart.map(item => {
      const subtotal = item.price * item.quantity;
      return `<tr data-id="${item.medicineId}">
        <td class="px-4 py-2 whitespace-nowrap">${item.name || item.tradeName || item.genericName || item.medicineId}</td>
        <td class="px-4 py-2 whitespace-nowrap">${item.spec || '-'}</td>
        <td class="px-4 py-2 whitespace-nowrap">${formatMoney(item.price)}</td>
        <td class="px-4 py-2 whitespace-nowrap">
          <div class="flex items-center gap-1">
            <button class="btn btn-outline text-xs px-2 py-1" data-action="dec" data-id="${item.medicineId}">-</button>
            <input type="number" min="1" class="input w-16 text-center text-xs" data-action="qty" data-id="${item.medicineId}" value="${item.quantity}" />
            <button class="btn btn-outline text-xs px-2 py-1" data-action="inc" data-id="${item.medicineId}">+</button>
          </div>
        </td>
        <td class="px-4 py-2 whitespace-nowrap">${formatMoney(subtotal)}</td>
        <td class="px-4 py-2 whitespace-nowrap">
          <button class="btn btn-outline text-xs" data-action="remove" data-id="${item.medicineId}"><i class="fa fa-times"></i></button>
        </td>
      </tr>`;
    }).join(''));
    updateSummary();
  }

  function updateSummary(){
    const original = cart.reduce((sum,i)=> sum + i.price * i.quantity, 0);
    const discountInput = $('discount-amount');
    const discount = Number(discountInput && discountInput.value || 0);
    const pointsInput = $('use-points');
    const pointsUsed = Number(pointsInput && pointsInput.value || 0);
    // 简单规则：1 积分 = 0.01 元
    const pointsAmount = pointsUsed * 0.01;
    const payable = Math.max(original - discount - pointsAmount, 0);
    safeInner($('summary-original'), formatMoney(original));
    safeInner($('summary-discount'), formatMoney(discount));
    safeInner($('summary-points'), formatMoney(pointsAmount));
    safeInner($('summary-payable'), formatMoney(payable));
  }

  function addToCart(med){
    if(!med || !med.medicineId) return;
    const existing = cart.find(i=> i.medicineId === med.medicineId);
    if(existing){ existing.quantity += 1; } else {
      cart.push({
        medicineId: med.medicineId,
        name: med.genericName || med.tradeName || med.name,
        spec: med.spec,
        price: Number(med.retailPrice || med.price || med.unitPrice || 0),
        quantity: 1
      });
    }
    renderCart();
    showRecentAddedHint(med);
  }

  function showRecentAddedHint(med){
    const hint = $('recent-added-hint');
    if(!hint) return;
    safeInner(hint, `<i class="fa fa-plus-circle text-primary mr-1"></i> 已添加：${med.genericName || med.tradeName || med.name || med.medicineId}`);
    show(hint);
    clearTimeout(hint._timer);
    hint._timer = setTimeout(()=> hide(hint), 3000);
  }

  function bindCartEvents(){
    const body = $('cart-body');
    if(!body) return;
    body.addEventListener('click', e => {
      const btn = e.target.closest('button');
      if(!btn) return;
      const action = btn.getAttribute('data-action');
      const id = btn.getAttribute('data-id');
      if(!action || !id) return;
      const item = cart.find(i=> i.medicineId === id);
      if(!item) return;
      if(action === 'remove'){ cart.splice(cart.indexOf(item),1); renderCart(); }
      if(action === 'inc'){ item.quantity += 1; renderCart(); }
      if(action === 'dec'){ item.quantity = Math.max(1, item.quantity - 1); renderCart(); }
    });
    body.addEventListener('input', e => {
      const input = e.target;
      if(input && input.getAttribute('data-action') === 'qty'){
        const id = input.getAttribute('data-id');
        const item = cart.find(i=> i.medicineId === id);
        if(item){ item.quantity = Math.max(1, Number(input.value || 1)); renderCart(); }
      }
    });
  }

  // 药品搜索
  async function searchMedicines(){
    const keyword = $('medicine-search-input').value.trim();
    const category = $('medicine-category-filter').value.trim();
    const resultBox = $('medicine-search-result');
    if(!resultBox) return;
    resultBox.classList.remove('hidden');
    safeInner(resultBox, '<div class="p-3 text-center text-gray-400"><i class="fa fa-spinner fa-spin"></i> 搜索中...</div>');
    try {
      const res = await medicineAPI.search(keyword, category, 1, 30);
      const list = res && res.data ? res.data : [];
      if(list.length === 0){
        safeInner(resultBox,'<div class="p-3 text-center text-gray-400">未找到匹配药品</div>');
        return;
      }
      safeInner(resultBox, list.map(m => `<div class="px-3 py-2 hover:bg-blue-50 cursor-pointer text-sm" data-id="${m.medicineId}">
        <div class="flex justify-between"><span>${m.genericName || m.tradeName || m.name}</span><span class="text-gray-500">${formatMoney(m.retailPrice || m.price)}</span></div>
        <div class="text-xs text-gray-400">${m.spec || ''} ${m.manufacturer || ''}</div>
      </div>`).join(''));
    } catch(err){
      safeInner(resultBox, `<div class='p-3 text-center text-red-500 text-xs'>搜索失败: ${err.message}</div>`);
    }
  }

  function bindMedicineSearchEvents(){
    const resultBox = $('medicine-search-result');
    if(resultBox){
      resultBox.addEventListener('click', e => {
        const itemDiv = e.target.closest('[data-id]');
        if(!itemDiv) return;
        const id = itemDiv.getAttribute('data-id');
        // 简单再次查找单个药品详情
        medicineAPI.getById(id).then(m => { addToCart(m); }).catch(()=>{});
      });
    }
    $('medicine-search-btn').addEventListener('click', searchMedicines);
    $('medicine-search-input').addEventListener('keydown', e => { if(e.key==='Enter'){ e.preventDefault(); searchMedicines(); }});
  }

  // 会员搜索
  async function searchMembers(){
    const keyword = $('member-search-input').value.trim();
    const resultBox = $('member-search-result');
    if(!resultBox) return;
    if(!keyword){ hide(resultBox); return; }
    show(resultBox);
    safeInner(resultBox,'<div class="p-3 text-center text-gray-400"><i class="fa fa-spinner fa-spin"></i> 搜索中...</div>');
    try {
      const res = await memberAPI.search(keyword);
      const list = res && res.data ? res.data : [];
      if(list.length === 0){ safeInner(resultBox,'<div class="p-3 text-center text-gray-400">无匹配会员</div>'); return; }
      safeInner(resultBox, list.map(m => `<div class="px-3 py-2 hover:bg-blue-50 cursor-pointer text-sm" data-id="${m.memberId}">
        <div class="flex justify-between"><span>${m.name || '会员'}</span><span class="text-gray-500">积分:${m.points||0}</span></div>
        <div class="text-xs text-gray-400">${m.phone || ''} 等级:${m.levelName || ''}</div>
      </div>`).join(''));
    } catch(err){
      safeInner(resultBox, `<div class='p-3 text-center text-red-500 text-xs'>会员搜索失败: ${err.message}</div>`);
    }
  }

  function bindMemberSearchEvents(){
    const resultBox = $('member-search-result');
    if(resultBox){
      resultBox.addEventListener('click', e => {
        const div = e.target.closest('[data-id]');
        if(!div) return;
        const memberId = div.getAttribute('data-id');
        memberAPI.getById(memberId).then(m => {
          selectedMember = m;
          hide(resultBox);
          const info = $('selected-member-info');
          if(info){
            info.classList.remove('hidden');
            safeInner(info, `<i class='fa fa-user text-primary mr-1'></i> 已选择会员：<span class='font-medium'>${m.name}</span> (积分:${m.points||0})`);
          }
          const clearBtn = $('clear-member-btn');
          if(clearBtn){ clearBtn.classList.remove('hidden'); }
        }).catch(()=>{});
      });
    }
    $('member-search-input').addEventListener('input', () => { searchMembers(); });
    $('clear-member-btn').addEventListener('click', () => {
      selectedMember = null;
      hide($('selected-member-info'));
      hide($('clear-member-btn'));
    });
  }

  // 提交订单
  async function submitOrder(){
    if(cart.length === 0){ feedback('购物车为空，无法提交', 'error'); return; }
    const orderData = {
      customerName: selectedMember ? selectedMember.name : '散客',
      memberId: selectedMember ? selectedMember.memberId : null,
      paymentMethod: $('payment-method').value,
      originalAmount: cart.reduce((s,i)=> s + i.price * i.quantity, 0),
      discountAmount: Number($('discount-amount').value || 0),
      totalAmount: cart.reduce((s,i)=> s + i.price * i.quantity, 0),
      items: cart.map(i => ({ productId: i.medicineId, quantity: i.quantity, unitPrice: Number(i.price.toFixed? i.price.toFixed(2): i.price) }))
    };
    feedback('<i class="fa fa-spinner fa-spin"></i> 正在提交订单...', 'info');
    try {
      const res = await orderAPI.create(orderData);
      feedback(`<i class='fa fa-check-circle text-green-600'></i> 订单创建成功：${res.orderId || (res.data && res.data.orderNumber)||''}`, 'success');
      // 清空购物车
      cart.length = 0; renderCart();
    } catch(err){
      feedback(`<i class='fa fa-times-circle text-red-600'></i> 提交失败: ${err.message}`, 'error');
    }
  }

  function feedback(msg, type){
    const box = $('order-feedback');
    if(!box) return;
    box.className = 'mt-4 text-sm';
    if(type==='error') box.classList.add('text-red-600');
    if(type==='success') box.classList.add('text-green-600');
    if(type==='info') box.classList.add('text-gray-600');
    safeInner(box, msg);
  }

  // 挂单（前端模拟）
  function hangCurrentOrder(){
    if(cart.length === 0){ feedback('当前没有可挂起的购物车', 'error'); return; }
    const snapshot = JSON.parse(JSON.stringify(cart));
    const hangId = 'H' + Date.now();
    hangOrders.push({ hangId, cart: snapshot, time: new Date() });
    feedback(`<i class='fa fa-pause-circle text-blue-600'></i> 已挂单：${hangId}`, 'info');
    renderHangOrders();
    cart.length = 0; renderCart();
  }

  function renderHangOrders(){
    const container = $('hang-orders-container');
    const empty = $('hang-orders-empty');
    if(!container) return;
    if(hangOrders.length === 0){ show(empty); safeInner(container,''); return; }
    hide(empty);
    safeInner(container, hangOrders.map(h => `<div class='border rounded p-2 flex justify-between items-center'>
      <div class='text-xs'>挂单号: <span class='font-mono'>${h.hangId}</span><br><span class='text-gray-500'>${h.cart.length} 件商品</span></div>
      <div class='flex gap-2'>
        <button class='btn btn-outline text-xs' data-action='restore' data-id='${h.hangId}'>恢复</button>
        <button class='btn btn-outline text-xs' data-action='discard' data-id='${h.hangId}'><i class='fa fa-trash'></i></button>
      </div>
    </div>`).join(''));
  }

  function bindHangEvents(){
    const container = $('hang-orders-container');
    if(container){
      container.addEventListener('click', e => {
        const btn = e.target.closest('button');
        if(!btn) return;
        const action = btn.getAttribute('data-action');
        const id = btn.getAttribute('data-id');
        const hang = hangOrders.find(h => h.hangId === id);
        if(!hang) return;
        if(action === 'restore'){
          cart.length = 0;
          hang.cart.forEach(item => cart.push(item));
          renderCart();
          hangOrders.splice(hangOrders.indexOf(hang),1);
          renderHangOrders();
          feedback(`<i class='fa fa-undo text-primary'></i> 已恢复挂单 ${id}`, 'success');
        }
        if(action === 'discard'){
          hangOrders.splice(hangOrders.indexOf(hang),1);
          renderHangOrders();
          feedback(`<i class='fa fa-trash text-gray-600'></i> 已删除挂单 ${id}`, 'info');
        }
      });
    }
    $('hang-order-btn').addEventListener('click', hangCurrentOrder);
    $('refresh-hang-btn').addEventListener('click', renderHangOrders);
  }

  function initDiscountAndPoints(){
    ['discount-amount','use-points'].forEach(id => {
      const el = $(id); if(el){ el.addEventListener('input', updateSummary); }
    });
  }

  function initSubmit(){
    $('submit-order-btn').addEventListener('click', submitOrder);
    $('quick-pay-btn').addEventListener('click', submitOrder);
  }

  document.addEventListener('DOMContentLoaded', () => {
    bindCartEvents();
    bindMedicineSearchEvents();
    bindMemberSearchEvents();
    bindHangEvents();
    initDiscountAndPoints();
    initSubmit();
    renderCart();
  });
})();
