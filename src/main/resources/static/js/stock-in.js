// stock-in.js - 药品入库功能
document.addEventListener('DOMContentLoaded', function() {
    // 初始化模态框
    if (typeof initAllModals === 'function') {
        initAllModals();
    }

    const medicineList = document.getElementById('medicine-list');
    const stockInForm = document.getElementById('stock-in-form');
    const selectedMedicines = [];
    let suppliers = [];

    // 设置入库日期为今天
    const today = new Date().toISOString().split('T')[0];
    document.querySelector('#stock-in-form input[type="date"]').value = today;

    // 加载供应商列表
    async function loadSuppliers() {
        try {
            if (window.api && window.api.supplierAPI) {
                suppliers = await window.api.supplierAPI.getAll();
                const supplierSelect = document.getElementById('supplier');
                supplierSelect.innerHTML = '<option value="">请选择供应商</option>';
                suppliers.forEach(supplier => {
                    const option = document.createElement('option');
                    option.value = supplier.id;
                    option.textContent = supplier.name;
                    supplierSelect.appendChild(option);
                });
            }
        } catch (error) {
            console.error('加载供应商列表失败:', error);
            if (window.api && window.api.showMessage) {
                window.api.showMessage('加载供应商列表失败', 'error');
            }
        }
    }

    // 加载药品列表（供选择）
    async function loadMedicinesForSelection() {
        try {
            if (window.api && window.api.medicineAPI) {
                const medicines = await window.api.medicineAPI.getAll();
                medicineList.innerHTML = '';
                medicines.forEach(med => {
                    const item = document.createElement('div');
                    item.className = 'flex items-center justify-between p-3 border-b border-gray-100 hover:bg-gray-50';
                    item.innerHTML = `
                        <div class="flex-1">
                            <h4 class="font-medium">${med.name}</h4>
                            <p class="text-sm text-gray-500">${med.specification} | ${med.manufacturer}</p>
                            <p class="text-xs text-gray-400">库存: ${med.stock || 0} | 采购价: ¥${med.purchasePrice?.toFixed(2) || '0.00'}</p>
                        </div>
                        <button class="btn btn-outline text-sm" onclick="selectMedicine(${med.id})">
                            选择
                        </button>
                    `;
                    medicineList.appendChild(item);
                });
            }
        } catch (error) {
            if (window.api && window.api.showMessage) {
                window.api.showMessage('加载药品列表失败', 'error');
            }
        }
    }

    // 搜索药品
    window.searchMedicines = async function() {
        const keyword = document.getElementById('medicine-search').value;
        try {
            if (window.api && window.api.medicineAPI) {
                const medicines = await window.api.medicineAPI.search(keyword);
                medicineList.innerHTML = '';
                medicines.forEach(med => {
                    const item = document.createElement('div');
                    item.className = 'flex items-center justify-between p-3 border-b border-gray-100 hover:bg-gray-50';
                    item.innerHTML = `
                        <div class="flex-1">
                            <h4 class="font-medium">${med.name}</h4>
                            <p class="text-sm text-gray-500">${med.specification} | ${med.manufacturer}</p>
                            <p class="text-xs text-gray-400">库存: ${med.stock || 0} | 采购价: ¥${med.purchasePrice?.toFixed(2) || '0.00'}</p>
                        </div>
                        <button class="btn btn-outline text-sm" onclick="selectMedicine(${med.id})">
                            选择
                        </button>
                    `;
                    medicineList.appendChild(item);
                });
            }
        } catch (error) {
            if (window.api && window.api.showMessage) {
                window.api.showMessage('搜索药品失败', 'error');
            }
        }
    };

    // 选择药品
    window.selectMedicine = async function(medicineId) {
        try {
            if (window.api && window.api.medicineAPI) {
                const medicine = await window.api.medicineAPI.getById(medicineId);

                // 检查是否已选择
                const exists = selectedMedicines.find(m => m.id === medicine.id);
                if (!exists) {
                    selectedMedicines.push({
                        ...medicine,
                        quantity: 1,
                        batchNumber: window.generateBatchNumber(),
                        expiryDate: window.getDefaultExpiryDate(),
                        purchasePrice: medicine.purchasePrice || 0
                    });
                    window.renderSelectedMedicines();
                    // 关闭选择模态框
                    document.getElementById('medicine-select-modal').classList.add('hidden');
                } else {
                    if (window.api && window.api.showMessage) {
                        window.api.showMessage('该药品已选择', 'warning');
                    }
                }
            }
        } catch (error) {
            if (window.api && window.api.showMessage) {
                window.api.showMessage('获取药品详情失败', 'error');
            }
        }
    };

    // 生成默认批号
    window.generateBatchNumber = function() {
        const now = new Date();
        return `BN${now.getFullYear()}${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
    };

    // 获取默认有效期（2年后）
    window.getDefaultExpiryDate = function() {
        const now = new Date();
        now.setFullYear(now.getFullYear() + 2);
        return now.toISOString().split('T')[0];
    };

    // 渲染已选择的药品
    window.renderSelectedMedicines = function() {
        const container = document.getElementById('selected-medicines');
        container.innerHTML = '';

        if (selectedMedicines.length === 0) {
            container.innerHTML = `
                <div class="text-center py-8 text-gray-500">
                    <i class="fa fa-box text-4xl mb-2"></i>
                    <p>暂无选择的药品</p>
                </div>
            `;
            return;
        }

        selectedMedicines.forEach((med, index) => {
            const totalPrice = (med.purchasePrice || 0) * (med.quantity || 0);
            const item = document.createElement('div');
            item.className = 'flex flex-col p-4 border rounded-lg mb-3 bg-white';
            item.innerHTML = `
                <div class="flex justify-between items-start mb-3">
                    <div class="flex items-center gap-3">
                        <img src="https://picsum.photos/40/40?random=${med.id}" alt="${med.name}" class="w-10 h-10 rounded">
                        <div>
                            <h4 class="font-medium">${med.name}</h4>
                            <p class="text-sm text-gray-500">${med.specification}</p>
                        </div>
                    </div>
                    <button class="text-red-500 hover:text-red-700" onclick="removeMedicine(${index})">
                        <i class="fa fa-times"></i>
                    </button>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-4 gap-4 text-sm">
                    <div>
                        <label class="block text-gray-500 mb-1">入库数量</label>
                        <input type="number" min="1" value="${med.quantity}"
                               class="input text-sm"
                               onchange="updateQuantity(${index}, this.value)">
                    </div>
                    <div>
                        <label class="block text-gray-500 mb-1">批号</label>
                        <input type="text" value="${med.batchNumber}"
                               class="input text-sm"
                               onchange="updateBatch(${index}, this.value)">
                    </div>
                    <div>
                        <label class="block text-gray-500 mb-1">有效期至</label>
                        <input type="date" value="${med.expiryDate}"
                               class="input text-sm"
                               onchange="updateExpiry(${index}, this.value)">
                    </div>
                    <div>
                        <label class="block text-gray-500 mb-1">采购单价</label>
                        <input type="number" step="0.01" min="0" value="${med.purchasePrice || 0}"
                               class="input text-sm"
                               onchange="updatePurchasePrice(${index}, this.value)">
                    </div>
                </div>
                <div class="mt-3 pt-3 border-t border-gray-200 flex justify-between items-center">
                    <span class="text-gray-500">小计:</span>
                    <span class="font-medium text-primary">¥${totalPrice.toFixed(2)}</span>
                </div>
            `;
            container.appendChild(item);
        });

        window.updateTotalAmount();
    };

    // 更新总金额
    window.updateTotalAmount = function() {
        const totalAmount = selectedMedicines.reduce((sum, med) => {
            return sum + (med.purchasePrice || 0) * (med.quantity || 0);
        }, 0);

        const totalElement = document.getElementById('total-amount');
        if (totalElement) {
            totalElement.textContent = `¥${totalAmount.toFixed(2)}`;
        }
    };

    // 入库表单提交
    stockInForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        if (selectedMedicines.length === 0) {
            if (window.api && window.api.showMessage) {
                return window.api.showMessage('请选择药品', 'warning');
            }
        }

        const operator = document.getElementById('operator').value;
        const supplierId = document.getElementById('supplier').value;
        const batchCode = document.getElementById('batch-code').value;

        if (!operator) {
            if (window.api && window.api.showMessage) {
                return window.api.showMessage('请输入操作人', 'warning');
            }
        }
        if (!supplierId) {
            if (window.api && window.api.showMessage) {
                return window.api.showMessage('请选择供应商', 'warning');
            }
        }

        const stockInData = {
            operator: operator,
            supplierId: parseInt(supplierId),
            batchCode: batchCode,
            medicines: selectedMedicines.map(med => ({
                medicineId: med.id,
                quantity: parseInt(med.quantity),
                batchNumber: med.batchNumber,
                expiryDate: med.expiryDate,
                purchasePrice: parseFloat(med.purchasePrice || 0)
            })),
            operationTime: new Date().toISOString()
        };

        try {
            if (window.api && window.api.stockInAPI) {
                await window.api.stockInAPI.create(stockInData);
                if (window.api && window.api.showMessage) {
                    window.api.showMessage('入库成功');
                }
                // 重置表单
                selectedMedicines.length = 0;
                stockInForm.reset();
                window.renderSelectedMedicines();
                // 关闭模态框
                document.getElementById('stock-in-modal').classList.add('hidden');
                // 刷新列表
                loadStockInList();
            }
        } catch (error) {
            if (window.api && window.api.showMessage) {
                window.api.showMessage('入库失败，请重试', 'error');
            }
        }
    });

    // 加载入库单列表
    async function loadStockInList() {
        try {
            if (window.api && window.api.stockInAPI) {
                const stockIns = await window.api.stockInAPI.getAll();
                // 这里需要更新表格显示
                updateStockInTable(stockIns);
            }
        } catch (error) {
            console.error('加载入库单列表失败:', error);
        }
    }

    // 更新入库单表格
    function updateStockInTable(stockIns) {
        // 实现表格更新逻辑
        console.log('更新入库单表格:', stockIns);
    }

    // 辅助函数
    window.removeMedicine = function(index) {
        selectedMedicines.splice(index, 1);
        window.renderSelectedMedicines();
    };

    window.updateQuantity = function(index, value) {
        selectedMedicines[index].quantity = parseInt(value) || 1;
        window.renderSelectedMedicines();
    };

    window.updateBatch = function(index, value) {
        selectedMedicines[index].batchNumber = value;
    };

    window.updateExpiry = function(index, value) {
        selectedMedicines[index].expiryDate = value;
    };

    window.updatePurchasePrice = function(index, value) {
        selectedMedicines[index].purchasePrice = parseFloat(value) || 0;
        window.renderSelectedMedicines();
    };

    // 初始化
    loadSuppliers();
    loadMedicinesForSelection();
    loadStockInList();
});

console.log('stock-in.js 加载完成');