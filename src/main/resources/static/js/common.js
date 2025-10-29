// common.js - 完整修复版本
const BASE_URL = 'http://localhost:8080/api';

// 通用API调用函数
async function apiCall(endpoint, options = {}) {
    try {
        const url = `${BASE_URL}${endpoint}`;
        console.log(`调用 API: ${options.method || 'GET'} ${url}`);

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

        const result = await response.json();
        console.log('API 调用成功:', result);
        return result;
    } catch (error) {
        console.error('API 调用异常:', error);
        throw error;
    }
}

// 药品相关API - 完整修复版本
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
    // 修复：确保 searchWithStock 方法正确定义
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

// 订单相关API - 完整定义
const orderAPI = {
    // 创建订单
    create: (orderData) => apiCall('/orders', {
        method: 'POST',
        body: JSON.stringify(orderData)
    }),

    // 获取订单列表
    getOrders: (filters = {}, page = 1, size = 10) => {
        let url = `/orders?page=${page}&size=${size}`;

        // 添加筛选条件
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

    // 获取订单详情
    getOrderDetail: (orderId) => apiCall(`/orders/${orderId}`),

    // 退款
    refund: (orderId) => apiCall(`/orders/${orderId}/refund`, {
        method: 'POST'
    }),

    // 导出订单
    exportOrders: (filters = {}) => {
        let url = '/orders/export';
        const params = new URLSearchParams();

        // 添加筛选条件
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
// 在 common.js 的 API 定义部分添加：

// 入库相关API
const stockInAPI = {
    // 创建入库单
    create: (stockInData) => apiCall('/stock-ins', {
        method: 'POST',
        body: JSON.stringify(stockInData)
    }),

    // 获取入库单列表
    getAll: (filters = {}, page = 1, size = 10) => {
        let url = `/stock-ins?page=${page}&size=${size}`;

        if (filters.batchCode) {
            url += `&batchCode=${encodeURIComponent(filters.batchCode)}`;
        }
        if (filters.supplier) {
            url += `&supplier=${encodeURIComponent(filters.supplier)}`;
        }
        if (filters.date) {
            url += `&date=${encodeURIComponent(filters.date)}`;
        }
        if (filters.status) {
            url += `&status=${encodeURIComponent(filters.status)}`;
        }

        return apiCall(url);
    },

    // 获取入库单详情
    getById: (id) => apiCall(`/stock-ins/${id}`),

    // 审核入库单
    approve: (id) => apiCall(`/stock-ins/${id}/approve`, {
        method: 'POST'
    }),

    // 驳回入库单
    reject: (id) => apiCall(`/stock-ins/${id}/reject`, {
        method: 'POST'
    }),

    // 删除入库单
    delete: (id) => apiCall(`/stock-ins/${id}`, {
        method: 'DELETE'
    })
};
// 在 common.js 的 API 部分添加：

// 药品批量创建API
const batchMedicineAPI = {
    // 批量创建药品
    batchCreate: (medicines) => apiCall('/medicines/batch', {
        method: 'POST',
        body: JSON.stringify(medicines)
    }),

    // 检查药品是否存在
    checkExists: (names) => apiCall('/medicines/check-exists', {
        method: 'POST',
        body: JSON.stringify({ names: names })
    })
};

// 供应商相关API
const supplierAPI = {
    getAll: () => apiCall('/suppliers'),
    getById: (id) => apiCall(`/suppliers/${id}`),
    create: (supplier) => apiCall('/suppliers', {
        method: 'POST',
        body: JSON.stringify(supplier)
    })
};

// 其他API定义...
const categoryAPI = {
    getAll: () => apiCall('/categories'),
    getById: (id) => apiCall(`/categories/${id}`)
};

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
    delete: (id) => apiCall(`/members/${id}`, { method: 'DELETE' })
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


// 确保全局导出 - 使用立即执行函数
(function() {
    // 创建全局 api 对象
    window.api = {
        BASE_URL,
        medicineAPI,
        categoryAPI,
        memberAPI,
        stockInAPI,
        supplierAPI,
        orderAPI,
        utils,
        batchMedicineAPI,
        showMessage
    };

    console.log('=== common.js 加载完成 ===');
    console.log('API 对象已挂载到 window.api');
    console.log('medicineAPI 方法列表:', Object.keys(window.api.medicineAPI));
    console.log('searchWithStock 类型:', typeof window.api.medicineAPI.searchWithStock);
    console.log('完整的 medicineAPI:', window.api.medicineAPI);
})();