// stock-in.js - 药品入库功能
class StockInManager {
    constructor() {
        this.selectedMedicines = new Map();
        this.categories = [];
        this.suppliers = [];
        this.currentPage = 1;
        this.pageSize = 10;
        this.init();
    }

    async init() {
        await this.loadCategories();
        await this.loadSuppliers();
        await this.loadRecentRecords();
        this.setupEventListeners();
        this.setupSearch();
    }

    // 加载药品分类
    async loadCategories() {
        try {
            const response = await fetch('/api/categories');
            const result = await response.json();

            if (result.code === 200) {
                this.categories = result.data;
                this.renderCategories();
            } else {
                console.error('加载分类失败:', result.message);
            }
        } catch (error) {
            console.error('加载分类失败:', error);
        }
    }

    // 渲染分类下拉框
    renderCategories() {
        const categoryFilter = document.getElementById('category-filter');
        const detailSupplier = document.getElementById('detail-supplier');

        this.categories.forEach(category => {
            const option = document.createElement('option');
            option.value = category.categoryId;
            option.textContent = category.categoryName;
            categoryFilter.appendChild(option.cloneNode(true));
        });
    }

    // 加载供应商
    async loadSuppliers() {
        try {
            // 这里需要根据您的实际情况调整API端点
            const response = await fetch('/api/suppliers');
            const result = await response.json();

            if (result.code === 200) {
                this.suppliers = result.data;
                this.renderSuppliers();
            } else {
                // 使用模拟数据
                this.suppliers = [
                    { supplierId: 1, supplierName: '北京医药股份有限公司' },
                    { supplierId: 2, supplierName: '上海医药集团' },
                    { supplierId: 3, supplierName: '广州医药有限公司' }
                ];
                this.renderSuppliers();
            }
        } catch (error) {
            console.error('加载供应商失败:', error);
            // 使用模拟数据
            this.suppliers = [
                { supplierId: 1, supplierName: '北京医药股份有限公司' },
                { supplierId: 2, supplierName: '上海医药集团' },
                { supplierId: 3, supplierName: '广州医药有限公司' }
            ];
            this.renderSuppliers();
        }
    }

    // 渲染供应商下拉框
    renderSuppliers() {
        const supplierSelect = document.getElementById('supplier-select');
        const detailSupplier = document.getElementById('detail-supplier');

        this.suppliers.forEach(supplier => {
            const option = document.createElement('option');
            option.value = supplier.supplierId;
            option.textContent = supplier.supplierName;
            supplierSelect.appendChild(option.cloneNode(true));
            detailSupplier.appendChild(option.cloneNode(true));
        });
    }

    // 设置事件监听器
    setupEventListeners() {
        // 提交入库
        document.getElementById('submit-stock-in').addEventListener('click', () => this.submitStockIn());

        // 刷新记录
        document.getElementById('refresh-records').addEventListener('click', () => this.loadRecentRecords());

        // 分页
        document.getElementById('prev-page').addEventListener('click', () => this.changePage(-1));
        document.getElementById('next-page').addEventListener('click', () => this.changePage(1));

        // 模态框关闭
        document.getElementById('close-detail-modal').addEventListener('click', () => this.closeDetailModal());
        document.getElementById('cancel-detail').addEventListener('click', () => this.closeDetailModal());

        // 保存药品详情
        document.getElementById('save-medicine-detail').addEventListener('click', () => this.saveMedicineDetail());

        // 数量价格变化计算小计
        document.getElementById('quantity').addEventListener('input', () => this.calculateSubtotal());
        document.getElementById('purchase-price').addEventListener('input', () => this.calculateSubtotal());
    }

    // 设置搜索功能
    setupSearch() {
        let searchTimeout;
        const searchInput = document.getElementById('medicine-search-input');

        searchInput.addEventListener('input', (e) => {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                this.searchMedicines(e.target.value);
            }, 300);
        });

        // 分类筛选
        document.getElementById('category-filter').addEventListener('change', () => {
            this.searchMedicines(searchInput.value);
        });
    }

    // 搜索药品
    async searchMedicines(keyword) {
        if (!keyword.trim()) {
            document.getElementById('search-results').classList.add('hidden');
            return;
        }

        try {
            const category = document.getElementById('category-filter').value;
            const response = await fetch(`/api/medicines/search?keyword=${encodeURIComponent(keyword)}&category=${category}&page=1&size=10`);
            const result = await response.json();

            if (result.code === 200) {
                this.renderSearchResults(result.data.content || result.data);
            } else {
                console.error('搜索失败:', result.message);
            }
        } catch (error) {
            console.error('搜索药品失败:', error);
            // 使用模拟数据演示
            this.renderSearchResults(this.getMockMedicines());
        }
    }

    // 渲染搜索结果
    renderSearchResults(medicines) {
        const container = document.getElementById('medicine-results');
        const resultsSection = document.getElementById('search-results');

        if (medicines.length === 0) {
            container.innerHTML = `
                <tr>
                    <td colspan="5" class="px-4 py-8 text-center text-gray-500">
                        <i class="fa fa-search text-2xl mb-2"></i>
                        <p>未找到相关药品</p>
                    </td>
                </tr>
            `;
        } else {
            container.innerHTML = medicines.map(medicine => `
                <tr class="hover:bg-gray-50 transition-colors">
                    <td class="px-4 py-3">
                        <div class="font-medium">${medicine.genericName}</div>
                        <div class="text-sm text-gray-500">${medicine.tradeName || ''}</div>
                    </td>
                    <td class="px-4 py-3 text-sm">${medicine.spec || '无'}</td>
                    <td class="px-4 py-3 text-sm">${medicine.manufacturer || '无'}</td>
                    <td class="px-4 py-3 text-sm">
                        <span class="badge ${medicine.stockQuantity > 10 ? 'bg-green-100 text-green-800' : medicine.stockQuantity > 0 ? 'bg-yellow-100 text-yellow-800' : 'bg-red-100 text-red-800'}">
                            ${medicine.stockQuantity || 0}
                        </span>
                    </td>
                    <td class="px-4 py-3">
                        <button class="btn btn-primary text-sm" onclick="stockInManager.addMedicine('${medicine.medicineId}')">
                            <i class="fa fa-plus"></i> 选择
                        </button>
                    </td>
                </tr>
            `).join('');
        }

        resultsSection.classList.remove('hidden');
    }

    // 添加药品到入库列表
    addMedicine(medicineId) {
        // 在实际应用中，这里应该通过API获取药品详情
        const medicine = this.getMedicineById(medicineId);
        if (medicine && !this.selectedMedicines.has(medicineId)) {
            this.openDetailModal(medicine);
        }
    }

    // 打开药品详情模态框
    openDetailModal(medicine) {
        document.getElementById('edit-medicine-id').value = medicine.medicineId;
        document.getElementById('medicine-basic-info').textContent = `${medicine.genericName} ${medicine.tradeName ? `(${medicine.tradeName})` : ''}`;
        document.getElementById('medicine-spec').textContent = `规格: ${medicine.spec || '无'} | 生产厂家: ${medicine.manufacturer || '无'}`;

        // 重置表单
        document.getElementById('quantity').value = '';
        document.getElementById('purchase-price').value = '';
        document.getElementById('production-batch').value = '';
        document.getElementById('production-date').value = '';
        document.getElementById('expiry-date').value = '';
        document.getElementById('detail-remark').value = '';

        document.getElementById('medicine-detail-modal').classList.remove('hidden');
    }

    // 关闭详情模态框
    closeDetailModal() {
        document.getElementById('medicine-detail-modal').classList.add('hidden');
    }

    // 计算小计金额
    calculateSubtotal() {
        const quantity = parseInt(document.getElementById('quantity').value) || 0;
        const price = parseFloat(document.getElementById('purchase-price').value) || 0;
        const subtotal = quantity * price;

        document.getElementById('subtotal-amount').textContent = `¥${subtotal.toFixed(2)}`;
    }

    // 保存药品详情
    saveMedicineDetail() {
        const medicineId = document.getElementById('edit-medicine-id').value;
        const quantity = parseInt(document.getElementById('quantity').value);
        const purchasePrice = parseFloat(document.getElementById('purchase-price').value);

        if (!quantity || quantity <= 0) {
            alert('请输入有效的入库数量');
            return;
        }

        if (!purchasePrice || purchasePrice < 0) {
            alert('请输入有效的采购价格');
            return;
        }

        const medicine = this.getMedicineById(medicineId);
        if (!medicine) return;

        const medicineDetail = {
            ...medicine,
            quantity: quantity,
            purchasePrice: purchasePrice,
            productionBatch: document.getElementById('production-batch').value,
            productionDate: document.getElementById('production-date').value,
            expiryDate: document.getElementById('expiry-date').value,
            supplierId: document.getElementById('detail-supplier').value,
            remark: document.getElementById('detail-remark').value,
            subtotal: quantity * purchasePrice
        };

        this.selectedMedicines.set(medicineId, medicineDetail);
        this.renderSelectedMedicines();
        this.closeDetailModal();
        this.updateSummary();
    }

    // 渲染已选药品列表
    renderSelectedMedicines() {
        const container = document.getElementById('selected-medicines-list');

        if (this.selectedMedicines.size === 0) {
            container.innerHTML = `
                <div class="text-center py-8 text-gray-500 border-2 border-dashed border-gray-300 rounded-lg">
                    <i class="fa fa-box text-4xl mb-2"></i>
                    <p>请从上方搜索并选择药品</p>
                </div>
            `;
        } else {
            container.innerHTML = Array.from(this.selectedMedicines.values()).map(medicine => `
                <div class="flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                    <div class="flex-1">
                        <div class="flex items-center gap-3 mb-2">
                            <h5 class="font-bold">${medicine.genericName}</h5>
                            ${medicine.tradeName ? `<span class="text-sm text-gray-500">${medicine.tradeName}</span>` : ''}
                        </div>
                        <div class="grid grid-cols-2 md:grid-cols-4 gap-2 text-sm text-gray-600">
                            <div><span class="font-medium">规格:</span> ${medicine.spec || '无'}</div>
                            <div><span class="font-medium">数量:</span> ${medicine.quantity}</div>
                            <div><span class="font-medium">单价:</span> ¥${medicine.purchasePrice.toFixed(2)}</div>
                            <div><span class="font-medium">小计:</span> <span class="text-primary font-bold">¥${medicine.subtotal.toFixed(2)}</span></div>
                        </div>
                        ${medicine.productionBatch ? `<div class="text-xs text-gray-500 mt-1">批号: ${medicine.productionBatch}</div>` : ''}
                    </div>
                    <button class="ml-4 text-red-500 hover:text-red-700" onclick="stockInManager.removeMedicine('${medicine.medicineId}')">
                        <i class="fa fa-trash"></i>
                    </button>
                </div>
            `).join('');
        }

        document.getElementById('selected-count').textContent = `已选择 ${this.selectedMedicines.size} 个药品`;
    }

    // 移除药品
    removeMedicine(medicineId) {
        this.selectedMedicines.delete(medicineId);
        this.renderSelectedMedicines();
        this.updateSummary();
    }

    // 更新汇总信息
    updateSummary() {
        const totalTypes = this.selectedMedicines.size;
        const totalQuantity = Array.from(this.selectedMedicines.values()).reduce((sum, med) => sum + med.quantity, 0);
        const totalAmount = Array.from(this.selectedMedicines.values()).reduce((sum, med) => sum + med.subtotal, 0);

        document.getElementById('total-types').textContent = totalTypes;
        document.getElementById('total-quantity').textContent = totalQuantity;
        document.getElementById('total-amount').textContent = `¥${totalAmount.toFixed(2)}`;

        // 启用/禁用提交按钮
        document.getElementById('submit-stock-in').disabled = totalTypes === 0;
    }

    // 提交入库
    async submitStockIn() {
        if (this.selectedMedicines.size === 0) {
            alert('请至少选择一个药品');
            return;
        }

        const stockInData = {
            batchNumber: document.getElementById('batch-number').value,
            stockDate: document.getElementById('stock-date').value,
            operator: document.getElementById('operator').value,
            remark: document.getElementById('remark').value,
            medicines: Array.from(this.selectedMedicines.values()).map(med => ({
                medicineId: med.medicineId,
                quantity: med.quantity,
                purchasePrice: med.purchasePrice,
                productionBatch: med.productionBatch,
                productionDate: med.productionDate,
                expiryDate: med.expiryDate,
                supplierId: med.supplierId,
                remark: med.remark
            }))
        };

        try {
            const response = await fetch('/api/stock/in', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(stockInData)
            });

            const result = await response.json();

            if (result.code === 200) {
                alert('入库成功！');
                this.selectedMedicines.clear();
                this.renderSelectedMedicines();
                this.updateSummary();
                this.loadRecentRecords();

                // 重置表单
                document.getElementById('remark').value = '';
            } else {
                alert('入库失败: ' + result.message);
            }
        } catch (error) {
            console.error('入库失败:', error);
            alert('网络错误，请重试！');
        }
    }

    // 加载最近入库记录
    async loadRecentRecords() {
        try {
            // 模拟数据加载
            const mockRecords = this.getMockRecords();
            this.renderRecentRecords(mockRecords);
        } catch (error) {
            console.error('加载记录失败:', error);
        }
    }

    // 渲染最近记录
    renderRecentRecords(records) {
        const container = document.getElementById('recent-records');
        const info = document.getElementById('records-info');

        if (records.length === 0) {
            container.innerHTML = `
                <tr>
                    <td colspan="8" class="px-6 py-8 text-center text-gray-500">
                        <i class="fa fa-inbox text-2xl mb-2"></i>
                        <p>暂无入库记录</p>
                    </td>
                </tr>
            `;
            info.textContent = '共 0 条记录';
        } else {
            container.innerHTML = records.map(record => `
                <tr class="hover:bg-gray-50 transition-colors">
                    <td class="px-6 py-4 whitespace-nowrap text-sm font-medium">${record.batchNumber}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm">${record.supplier}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm">${record.medicineCount}种</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm font-medium">¥${record.totalAmount.toFixed(2)}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm">${record.stockDate}</td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm">${record.operator}</td>
                    <td class="px-6 py-4 whitespace-nowrap">
                        <span class="badge ${record.status === 'completed' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'}">
                            ${record.status === 'completed' ? '已完成' : '处理中'}
                        </span>
                    </td>
                    <td class="px-6 py-4 whitespace-nowrap text-sm">
                        <button class="text-primary hover:underline mr-3" onclick="stockInManager.viewRecordDetail('${record.batchNumber}')">查看</button>
                        <button class="text-gray-500 hover:text-gray-700">打印</button>
                    </td>
                </tr>
            `).join('');

            info.textContent = `显示 1-${records.length} 条，共 ${records.length} 条`;
        }
    }

    // 查看记录详情
    viewRecordDetail(batchNumber) {
        alert(`查看入库单详情: ${batchNumber}`);
        // 在实际应用中，这里可以打开详情模态框显示完整信息
    }

    // 分页
    changePage(direction) {
        this.currentPage += direction;
        if (this.currentPage < 1) this.currentPage = 1;
        this.loadRecentRecords();
    }

    // 模拟数据方法
    getMockMedicines() {
        return [
            {
                medicineId: '1',
                genericName: '阿莫西林胶囊',
                tradeName: '阿莫仙',
                spec: '0.25g*24粒',
                manufacturer: '珠海联邦制药',
                stockQuantity: 45,
                retailPrice: 25.80
            },
            {
                medicineId: '2',
                genericName: '布洛芬缓释胶囊',
                tradeName: '芬必得',
                spec: '0.3g*20粒',
                manufacturer: '中美天津史克',
                stockQuantity: 32,
                retailPrice: 18.50
            },
            {
                medicineId: '3',
                genericName: '连花清瘟胶囊',
                spec: '0.35g*24粒',
                manufacturer: '以岭药业',
                stockQuantity: 12,
                retailPrice: 28.00
            }
        ];
    }

    getMedicineById(medicineId) {
        const medicines = this.getMockMedicines();
        return medicines.find(med => med.medicineId === medicineId);
    }

    getMockRecords() {
        const currentOperator = (localStorage.getItem('loginUsername') || localStorage.getItem('currentUser') || '系统用户');
        return [
            {
                batchNumber: 'IN-20231215-001',
                supplier: '北京医药股份有限公司',
                medicineCount: 8,
                totalAmount: 24560.00,
                stockDate: '2023-12-15',
                operator: currentOperator,
                status: 'completed'
            },
            {
                batchNumber: 'IN-20231214-002',
                supplier: '上海医药集团',
                medicineCount: 12,
                totalAmount: 38720.00,
                stockDate: '2023-12-14',
                operator: '李库管',
                status: 'completed'
            },
            {
                batchNumber: 'IN-20231213-003',
                supplier: '广州医药有限公司',
                medicineCount: 5,
                totalAmount: 15680.00,
                stockDate: '2023-12-13',
                operator: currentOperator,
                status: 'processing'
            }
        ];
    }
}

// 初始化药品入库管理器
let stockInManager;

function initStockIn() {
    stockInManager = new StockInManager();
}