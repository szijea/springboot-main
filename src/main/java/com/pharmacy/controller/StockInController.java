package com.pharmacy.controller;

import com.pharmacy.entity.StockIn;
import com.pharmacy.entity.StockInItem;
import com.pharmacy.repository.InventoryRepository;
import com.pharmacy.repository.MedicineRepository;
import com.pharmacy.repository.StockInItemRepository;
import com.pharmacy.repository.StockInRepository;
import com.pharmacy.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/stock-ins")
public class StockInController {

    @Autowired
    private StockInRepository stockInRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private StockInItemRepository stockInItemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @GetMapping
    public ResponseEntity<Page<StockIn>> getStockIns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<StockIn> stockIns = stockInRepository.findAll(pageable);
            return ResponseEntity.ok(stockIns);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockIn> getStockInById(@PathVariable Long id) {
        Optional<StockIn> stockIn = stockInRepository.findById(id);
        return stockIn.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createStockIn(@RequestBody StockIn stockIn) {
        try {
            System.out.println("[StockInController] 接收到入库单数据: stockInNo=" + stockIn.getStockInNo() + ", items=" + (stockIn.getItems()==null?0:stockIn.getItems().size()));
            if (stockIn.getItems()==null || stockIn.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body("入库单必须包含至少一个药品");
            }
            // 供应商处理
            if (stockIn.getSupplier() != null && stockIn.getSupplier().getSupplierId() != null) {
                if (!supplierRepository.existsById(stockIn.getSupplier().getSupplierId())) {
                    System.out.println("供应商不存在, 改用默认供应商 ID=1");
                    Optional<com.pharmacy.entity.Supplier> defaultSupplier = supplierRepository.findById(1);
                    if (defaultSupplier.isPresent()) {
                        stockIn.setSupplier(defaultSupplier.get());
                    } else {
                        return ResponseEntity.badRequest().body("没有可用的供应商, 请先创建供应商");
                    }
                }
            } else {
                Optional<com.pharmacy.entity.Supplier> defaultSupplier = supplierRepository.findById(1);
                if (defaultSupplier.isPresent()) {
                    stockIn.setSupplier(defaultSupplier.get());
                } else {
                    return ResponseEntity.badRequest().body("请先创建供应商");
                }
            }
            // Items 校验与默认值
            for (StockInItem item : stockIn.getItems()) {
                if (item.getMedicine()==null || item.getMedicine().getMedicineId()==null) {
                    return ResponseEntity.badRequest().body("药品信息不完整");
                }
                String medId = item.getMedicine().getMedicineId();
                if (!medicineRepository.existsById(medId)) {
                    return ResponseEntity.badRequest().body("药品不存在: " + medId);
                }
                if (item.getBatchNumber()==null || item.getBatchNumber().isBlank()) {
                    item.setBatchNumber("DEFAULT_BATCH");
                }
                if (item.getQuantity()==null) {
                    item.setQuantity(0);
                }
                if (item.getUnitPrice()==null) { // 前端可能传 cost 字段映射为 unitPrice
                    // 尝试从已存在库存或零售价格推断
                    try {
                        var medOpt = medicineRepository.findById(medId);
                        if (medOpt.isPresent() && medOpt.get().getRetailPrice()!=null) {
                            item.setUnitPrice(medOpt.get().getRetailPrice().doubleValue());
                        } else {
                            item.setUnitPrice(0.0);
                        }
                    } catch (Exception ignore) { item.setUnitPrice(0.0); }
                }
                // 关联反向引用
                item.setStockIn(stockIn);
            }
            // 设置日期与编号
            if (stockIn.getStockInDate()==null) stockIn.setStockInDate(LocalDateTime.now());
            if (stockIn.getStockInNo()==null) stockIn.setStockInNo(generateStockInNo());
            if (stockIn.getStatus()==null) stockIn.setStatus(1); // 已入库状态
            stockIn.calculateTotalAmount();
            StockIn saved = stockInRepository.save(stockIn);
            System.out.println("[StockInController] 入库单保存成功 ID="+saved.getStockInId()+" 总金额="+saved.getTotalAmount());
            // 更新库存
            for (StockInItem item : stockIn.getItems()) {
                try {
                    String medId = item.getMedicine().getMedicineId();
                    String batch = item.getBatchNumber();
                    Integer qty = item.getQuantity();
                    java.time.LocalDate expiry = item.getExpiryDate();
                    var invs = inventoryRepository.findByMedicineId(medId);
                    com.pharmacy.entity.Inventory matched = null;
                    for (com.pharmacy.entity.Inventory inv : invs) {
                        if (batch.equals(inv.getBatchNo())) { matched = inv; break; }
                    }
                    if (matched != null) {
                        matched.setStockQuantity(matched.getStockQuantity() + qty);
                        inventoryRepository.save(matched);
                    } else {
                        com.pharmacy.entity.Inventory newInv = new com.pharmacy.entity.Inventory(medId, batch, qty, expiry);
                        newInv.setPurchasePrice(item.getUnitPrice()!=null? java.math.BigDecimal.valueOf(item.getUnitPrice()) : null);
                        inventoryRepository.save(newInv);
                    }
                } catch (Exception updEx) {
                    System.err.println("[StockInController] 更新库存失败: "+updEx.getMessage());
                }
            }
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("[StockInController] 创建入库单失败: "+e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("创建入库单失败: "+e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStockIn(@PathVariable Long id, @RequestBody StockIn stockInDetails) {
        try {
            Optional<StockIn> optionalStockIn = stockInRepository.findById(id);
            if (optionalStockIn.isPresent()) {
                StockIn stockIn = optionalStockIn.get();

                // 更新基本信息
                if (stockInDetails.getSupplier() != null) {
                    stockIn.setSupplier(stockInDetails.getSupplier());
                }
                if (stockInDetails.getStockInDate() != null) {
                    stockIn.setStockInDate(stockInDetails.getStockInDate());
                }
                if (stockInDetails.getRemark() != null) {
                    stockIn.setRemark(stockInDetails.getRemark());
                }
                if (stockInDetails.getStatus() != null) {
                    stockIn.setStatus(stockInDetails.getStatus());
                }

                // 更新明细项
                if (stockInDetails.getItems() != null) {
                    // 先清除原有明细
                    stockIn.getItems().clear();

                    // 添加新的明细
                    for (StockInItem item : stockInDetails.getItems()) {
                        item.setStockIn(stockIn);
                        stockIn.getItems().add(item);
                    }
                }

                // 重新计算总金额
                stockIn.calculateTotalAmount();

                StockIn updatedStockIn = stockInRepository.save(stockIn);
                return ResponseEntity.ok(updatedStockIn);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            System.err.println("更新入库单失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("更新入库单失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStockIn(@PathVariable Long id) {
        if (stockInRepository.existsById(id)) {
            stockInRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveStockIn(@PathVariable Long id) {
        Optional<StockIn> optionalStockIn = stockInRepository.findById(id);
        if (optionalStockIn.isPresent()) {
            StockIn stockIn = optionalStockIn.get();
            stockIn.setStatus(1); // 已入库
            stockInRepository.save(stockIn);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<StockIn>> searchStockIns(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<StockIn> stockIns = stockInRepository.findByKeyword(keyword, pageable);
            return ResponseEntity.ok(stockIns);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 生成入库单号
    private String generateStockInNo() {
        return "SI" + System.currentTimeMillis();
    }
}