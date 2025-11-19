// common.js - 修复版本
const BASE_URL = (typeof window !== 'undefined' && window.location ? window.location.origin : 'http://localhost:8080') + '/api';

// 通用API调用函数
async function apiCall(endpoint, options = {}) {
    try {
        const url = `${BASE_URL}${endpoint}`;
        const tenant = (typeof localStorage !== 'undefined') ? localStorage.getItem('selectedTenant') : null;
        if(!options.headers) options.headers = {};
        if(tenant){
            options.headers['X-Shop-Id'] = tenant;
        } else {
            console.warn('[apiCall] 未找到 selectedTenant, 请求将使用 default 数据源:', url);
        }
        console.log(`调用 API: ${options.method || 'GET'} ${url} 租户=${tenant || 'default'}`);

        // 自动序列化 body（如果是普通对象/数组且非 FormData / Blob / string）
        if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData) && !(options.body instanceof Blob)) {
            options.body = JSON.stringify(options.body);
        }
        if (options.body) {
            console.log('发送的请求数据:', options.body);
        }

        const response = await fetch(url, {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        });

        if (!response.ok) {
            let errorDetail = '';
            try {
                const errorResponse = await response.json();
                errorDetail = errorResponse.message || JSON.stringify(errorResponse);
                console.error('错误响应详情:', errorResponse);
            } catch (e) {
                errorDetail = await response.text();
                console.error('错误响应文本:', errorDetail);
            }

            const errorMessage = `HTTP error! status: ${response.status}, 详情: ${errorDetail}`;
            console.error('API 调用失败:', errorMessage);
            throw new Error(errorMessage);
        }

        // 尝试根据 Content-Type 解析响应
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            const result = await response.json();
            console.log('API 调用成功 (JSON):', result);
            return result;
        } else {
            const text = await response.text();
            console.warn('API 返回非 JSON 响应，返回文本:', text);
            return text;
        }
    } catch (error) {
        console.error('API 调用异常:', error);
        throw error;
    }
}

// 药品相关API
const medicineAPI = {
    getAll: () => apiCall('/medicines'),
    getById: (id) => apiCall(`/medicines/${id}`),
    search: (keyword, category = '', page = 1, size = 100) => {
        let url = `/medicines/search?page=${page}&size=${size}`;
        if (keyword) {
            url += `&keyword=${encodeURIComponent(keyword)}`;
        }
        if (category) {
            url += `&category=${encodeURIComponent(category)}`;
        }
        return apiCall(url);
    },
    searchWithStock: function(keyword, category = '', page = 1, size = 100) {
        let url = `/medicines/search-with-stock?page=${page}&size=${size}`;
        if (keyword) {
            url += `&keyword=${encodeURIComponent(keyword)}`;
        }
        if (category) {
            url += `&category=${encodeURIComponent(category)}`;
        }
        console.log('调用 searchWithStock, URL:', url);
        return apiCall(url);
    },
    create: (medicine) => apiCall('/medicines', {
        method: 'POST',
        body: JSON.stringify(medicine)
    }),
    update: (id, medicine) => apiCall(`/medicines/${id}`, {
        method: 'PUT',
        body: JSON.stringify(medicine)
    }),
    delete: (id) => apiCall(`/medicines/${id}`, { method: 'DELETE' })
};

// 订单相关API
const orderAPI = {
    create: (orderData) => apiCall('/orders', {
        method: 'POST',
        body: JSON.stringify(orderData)
    }),
    getOrders: (filters = {}, page = 1, size = 10) => {
        let url = `/orders?page=${page}&size=${size}`;
        if (filters.date) {
            url += `&date=${encodeURIComponent(filters.date)}`;
        }
        if (filters.status) {
            url += `&status=${encodeURIComponent(filters.status)}`;
        }
        if (filters.paymentType) {
            url += `&paymentType=${encodeURIComponent(filters.paymentType)}`;
        }
        if (filters.member) {
            url += `&member=${encodeURIComponent(filters.member)}`;
        }
        return apiCall(url);
    },
    getOrderDetail: (orderId) => apiCall(`/orders/${orderId}`),
    refund: (orderId) => apiCall(`/orders/${orderId}/refund`, {
        method: 'POST'
    }),
    exportOrders: (filters = {}) => {
        let url = '/orders/export';
        const params = new URLSearchParams();
        if (filters.date) params.append('date', filters.date);
        if (filters.status) params.append('status', filters.status);
        if (filters.paymentType) params.append('paymentType', filters.paymentType);
        if (filters.member) params.append('member', filters.member);
        const queryString = params.toString();
        if (queryString) {
            url += `?${queryString}`;
        }
        return apiCall(url);
    }
};

// 分类相关API
const categoryAPI = {
    getAll: () => apiCall('/categories'),
    getById: (id) => apiCall(`/categories/${id}`)
};

// 会员相关API
const memberAPI = {
    getAll: () => apiCall('/members'),
    getById: (id) => apiCall(`/members/${id}`),
    search: (keyword) => apiCall(`/members/search?keyword=${encodeURIComponent(keyword)}`),
    create: (member) => apiCall('/members', {
        method: 'POST',
        body: JSON.stringify(member)
    }),
    update: (id, member) => apiCall(`/members/${id}`, {
        method: 'PUT',
        body: JSON.stringify(member)
    }),
    delete: (id) => apiCall(`/members/${id}`, { method: 'DELETE' }),
    quickSearch: (keyword) => apiCall(`/members/quick-search?keyword=${encodeURIComponent(keyword)}`)
};

// 入库相关API（需要定义这些API）
const stockInAPI = {
    create: (stockInData) => apiCall('/stock-in/orders', {
        method: 'POST',
        body: JSON.stringify(stockInData)
    }),
    getOrders: () => apiCall('/stock-in/orders'),
    getOrderDetail: (orderId) => apiCall(`/stock-in/orders/${orderId}`)
};

// 供应商相关API（需要定义这些API）
const supplierAPI = {
    getAll: () => apiCall('/suppliers'),
    getById: (id) => apiCall(`/suppliers/${id}`),
    create: (supplier) => apiCall('/suppliers', {
        method: 'POST',
        body: JSON.stringify(supplier)
    }),
    update: (id, supplier) => apiCall(`/suppliers/${id}`, {
        method: 'PUT',
        body: JSON.stringify(supplier)
    })
};

// 批次药品相关API（需要定义这些API）
const batchMedicineAPI = {
    // 可以根据需要添加方法
};

// 新增：inventoryAPI 用于库存页面
const inventoryAPI = {
    getAll: () => apiCall('/inventory'),
    getLowStock: () => apiCall('/inventory/low-stock'),
    getExpiringSoon: () => apiCall('/inventory/expiring-soon'),
    getDetail: (id) => apiCall(`/inventory/${id}`),
    getByMedicine: (medicineId) => apiCall(`/inventory/by-medicine/${medicineId}`),
    searchByBatch: async (batchKeyword) => {
        try {
            // 如果后端有接口可直接调用: /inventory/search?batch=xxx
            const direct = await apiCall(`/inventory/search?batch=${encodeURIComponent(batchKeyword)}`);
            if (direct && direct.code === 200) return direct;
        } catch(e){ console.warn('[inventoryAPI.searchByBatch] 后端搜索接口不可用, 使用本地过滤', e.message); }
        // 回退: 全量拉取后端数据再过滤
        const all = await apiCall('/inventory');
        const filtered = (all.data||[]).filter(item => (item.batchNo||'').includes(batchKeyword));
        return { code:200, message:'local-filter', data: filtered };
    }
};

// 新增：员工相关API
const employeesAPI = {
    getAll: () => apiCall('/employees'),
    getById: (id) => apiCall(`/employees/${id}`),
    create: (employee) => apiCall('/employees',{method:'POST',body:JSON.stringify(employee)}),
    update: (id, employee) => apiCall(`/employees/${id}`,{method:'PUT',body:JSON.stringify(employee)}),
    delete: (id) => apiCall(`/employees/${id}`,{method:'DELETE'}),
    getByRole: (roleId) => apiCall(`/employees/role/${roleId}`),
    toggleStatus: async (id, newStatus) => {
        // 获取原记录后仅更新 status 字段
        const emp = await employeesAPI.getById(id);
        if(!emp || !emp.employeeId) throw new Error('员工不���在');
        emp.status = newStatus ? 1 : 0;
        return employeesAPI.update(id, emp);
    }
};

// 新增：系统设置 API
const settingAPI = {
    get: () => apiCall('/settings'),
    update: (data) => apiCall('/settings',{method:'POST',body:JSON.stringify(data)})
};

// 工具函数
const utils = {
    formatPrice: (price) => {
        if (!price && price !== 0) return '¥0.00';
        return '¥' + parseFloat(price).toFixed(2);
    },
    formatDate: (date) => {
        if (!date) return '';
        return new Date(date).toLocaleDateString('zh-CN');
    },
    formatDateTime: (date) => {
        if (!date) return '';
        return new Date(date).toLocaleString('zh-CN');
    }
};

// 显示消息提示
function showMessage(message, type = 'success') {
    const messageDiv = document.createElement('div');
    messageDiv.className = `fixed top-4 right-4 px-4 py-2 rounded-lg shadow-lg z-50 ${
        type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white'
    }`;
    messageDiv.innerHTML = `
        <i class="fa ${type === 'success' ? 'fa-check-circle' : 'fa-exclamation-triangle'} mr-2"></i>
        ${message}
    `;
    document.body.appendChild(messageDiv);
    setTimeout(() => {
        messageDiv.remove();
    }, 3000);
}

// 确保全局导出（合并已存在的 window.api，避免覆盖）
(function() {
    // 如果已有 window.api，合并而不是覆盖
    window.api = window.api || {};

    // 把本模块的命名空间合并到 window.api
    Object.assign(window.api, {
        BASE_URL,
        medicineAPI,
        categoryAPI,
        memberAPI,
        stockInAPI,
        supplierAPI,
        orderAPI,
        utils,
        batchMedicineAPI,
        showMessage,
        inventoryAPI,
        employeesAPI,
        settingAPI
    });

    console.log('=== common.js 加载完成 ===');
    console.log('API 对象已挂载到 window.api');
    try {
        console.log('medicineAPI 方法列表:', Object.keys(window.api.medicineAPI));
    } catch (e) {
        console.warn('medicineAPI 不存在或不可枚举');
    }
})();

// 新增：全局仪表盘刷新函数（若不存在）
if (typeof window.refreshDashboardWidgets !== 'function') {
  window.refreshDashboardWidgets = async function(){
    try {
      const base = window.api?.BASE_URL || 'http://localhost:8080/api';
      const [alertsResp, hotResp] = await Promise.all([
        fetch(base + '/dashboard/stock-alerts').then(r=>r.json()).catch(()=>null),
        fetch(base + '/dashboard/hot-products').then(r=>r.json()).catch(()=>null)
      ]);
      if (typeof updateStockAlerts === 'function' && alertsResp && (alertsResp.data||alertsResp.alerts)) {
        updateStockAlerts(alertsResp.data||alertsResp.alerts);
      }
      if (typeof updateHotProducts === 'function' && hotResp && hotResp.data) {
        updateHotProducts(Array.isArray(hotResp.data)?hotResp.data:hotResp.data.hotProducts||[]);
      }
      console.log('Dashboard widgets refreshed');
    } catch(e){ console.warn('refreshDashboardWidgets failed', e); }
  }
}
