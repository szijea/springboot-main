// api.js
class PharmacyAPI {
    constructor() {
        this.baseURL = 'http://localhost:8080/api';
    }

    async request(endpoint, options = {}) {
        const url = `${this.baseURL}${endpoint}`;

        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers,
            },
            ...options,
        };

        if (config.body && typeof config.body === 'object') {
            config.body = JSON.stringify(config.body);
        }

        try {
            const response = await fetch(url, config);

            // 检查响应状态
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            // 尝试解析JSON响应
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const data = await response.json();
                return data;
            } else {
                // 如果不是JSON，返回文本
                const text = await response.text();
                return text;
            }
        } catch (error) {
            console.error('API request failed:', error);
            throw error;
        }
    }

    // 设置API
    settingAPI = {
        get: () => this.request('/settings'),
        update: (data) => this.request('/settings', {
            method: 'POST',
            body: data,
        }),
    };

    // 用户API
    userAPI = {
        changePassword: (oldPassword, newPassword) => this.request('/user/change-password', {
            method: 'POST',
            body: {
                oldPassword,
                newPassword,
                confirmPassword: newPassword,
            },
        }),
    };

    // 药品API
    medicineAPI = {
        // 获取全部药品（分页/非分页兼容）
        getAll: (page = 0, size = 100) => this.request(`/medicines?page=${page}&size=${size}`),
        // 按关键字和分类搜索并包含库存
        searchWithStock: (keyword = '', category = '', page = 1, size = 100) => this.request(`/medicines/search-with-stock?keyword=${encodeURIComponent(keyword)}&category=${encodeURIComponent(category)}&page=${page}&size=${size}`),
        // 详情
        getById: (id) => this.request(`/medicines/${encodeURIComponent(id)}`),
        // 更新
        update: (id, data) => this.request(`/medicines/${encodeURIComponent(id)}`, { method: 'PUT', body: data }),
        // 删除
        delete: (id) => this.request(`/medicines/${encodeURIComponent(id)}`, { method: 'DELETE' }),
        // 新增
        create: (data) => this.request('/medicines', { method: 'POST', body: data })
    };

    // 库存API
    inventoryAPI = {
        // 获取全部库存（DTO）
        getAll: () => this.request('/inventory'),
        // 详情（按库存ID）
        getDetail: (id) => this.request(`/inventory/${encodeURIComponent(id)}`),
        // 按药品ID获取所有批次
        getByMedicine: (medicineId) => this.request(`/inventory/by-medicine/${encodeURIComponent(medicineId)}`),
        // 按批次号搜索库存
        searchByBatch: (batchNo) => this.request(`/inventory/search-batch?batchNo=${encodeURIComponent(batchNo)}`)
    };

    // 消息提示
    showMessage(message, type = 'success') {
        // 创建消息元素
        const messageEl = document.createElement('div');
        messageEl.className = `fixed top-4 right-4 px-6 py-3 rounded-lg shadow-lg z-50 transform transition-all duration-300 ${
            type === 'success' ? 'bg-green-500 text-white' :
            type === 'error' ? 'bg-red-500 text-white' :
            type === 'warning' ? 'bg-yellow-500 text-white' : 'bg-blue-500 text-white'
        }`;
        messageEl.textContent = message;

        // 添加到页面
        document.body.appendChild(messageEl);

        // 3秒后自动移除
        setTimeout(() => {
            messageEl.style.opacity = '0';
            messageEl.style.transform = 'translateX(100%)';
            setTimeout(() => {
                if (document.body.contains(messageEl)) {
                    document.body.removeChild(messageEl);
                }
            }, 300);
        }, 3000);
    }
}

// 创建全局API实例
window.api = new PharmacyAPI();