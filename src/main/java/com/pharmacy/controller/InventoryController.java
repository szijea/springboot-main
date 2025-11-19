package com.pharmacy.controller;

import com.pharmacy.entity.Inventory;
import com.pharmacy.dto.InventoryDTO;
import com.pharmacy.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/expiring-soon")
    public ResponseEntity<?> getExpiringSoon() {
        try {
            List<Inventory> inventories = inventoryService.getExpiringSoon();

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", inventories);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "获取即将过期药品失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllInventory() {
        try {
            // 使用 DTO 返回，避免懒加载代理序列化失败
            List<InventoryDTO> inventories = inventoryService.findAllWithMedicineDTO();

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", inventories);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "获取库存列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/low-stock")
    public ResponseEntity<?> getLowStock() {
        try {
            List<Inventory> inventories = inventoryService.getLowStock();

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", inventories);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "获取低库存药品失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getInventoryDetail(@PathVariable("id") Long id) {
        try {
            InventoryDTO dto = inventoryService.findDTOById(id);
            if (dto == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 404);
                response.put("message", "库存记录不存在: " + id);
                return ResponseEntity.status(404).body(response);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", dto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "获取库存详情失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/by-medicine/{medicineId}")
    public ResponseEntity<?> getInventoryByMedicine(@PathVariable String medicineId) {
        try {
            List<InventoryDTO> list = inventoryService.findDTOByMedicineId(medicineId);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", list);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "获取药品批次库存失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/replenish")
    public ResponseEntity<?> replenish(@RequestBody Map<String,Object> body) {
        try {
            String medicineId = String.valueOf(body.get("medicineId"));
            Integer addQuantity = body.get("quantity") != null ? Integer.valueOf(body.get("quantity").toString()) : null;
            String batchNo = body.get("batchNo") != null ? String.valueOf(body.get("batchNo")) : null;
            Integer minStock = body.get("minStock") != null ? Integer.valueOf(body.get("minStock").toString()) : null;
            java.time.LocalDate expiryDate = null;
            if (body.get("expiryDate") != null && !String.valueOf(body.get("expiryDate")).isBlank()) {
                try { expiryDate = java.time.LocalDate.parse(String.valueOf(body.get("expiryDate"))); } catch (Exception ignored) {}
            }
            if (medicineId == null || medicineId.isBlank() || addQuantity == null || addQuantity < 0) {
                return ResponseEntity.badRequest().body(Map.of("code",400,"message","参数不合法"));
            }
            Inventory inv = inventoryService.replenish(medicineId, addQuantity, batchNo, minStock);
            if (inv != null && expiryDate != null) {
                inv.setExpiryDate(expiryDate);
                inventoryService.save(inv);
            }
            // 返回当前该药品所有批次 DTO（含统一状态）
            List<InventoryDTO> dtoList = inventoryService.findDTOByMedicineId(medicineId);
            return ResponseEntity.ok(Map.of("code",200,"message","补货成功","data", dtoList));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","补货失败: "+e.getMessage()));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<?> createBatch(@RequestBody Map<String,Object> body) {
        try {
            String medicineId = String.valueOf(body.get("medicineId"));
            String batchNo = String.valueOf(body.getOrDefault("batchNo","AUTO"+System.currentTimeMillis()));
            Integer stockQuantity = body.get("stockQuantity")!=null?Integer.valueOf(body.get("stockQuantity").toString()):0;
            Integer minStock = body.get("minStock")!=null?Integer.valueOf(body.get("minStock").toString()):null;
            Integer maxStock = body.get("maxStock")!=null?Integer.valueOf(body.get("maxStock").toString()):null;
            java.math.BigDecimal purchasePrice = body.get("purchasePrice")!=null?new java.math.BigDecimal(body.get("purchasePrice").toString()):null;
            java.time.LocalDate expiryDate = null;
            if (body.get("expiryDate")!=null) {
                expiryDate = java.time.LocalDate.parse(body.get("expiryDate").toString());
            }
            String supplier = body.get("supplier")!=null?String.valueOf(body.get("supplier")) : null;
            if (medicineId == null || medicineId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("code",400,"message","缺少 medicineId"));
            }
            Inventory inv = inventoryService.createBatch(medicineId,batchNo,stockQuantity,minStock,maxStock,purchasePrice,expiryDate,supplier);
            return ResponseEntity.ok(Map.of("code",200,"message","新建批次成功","data",inv.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","新建批次失败: "+e.getMessage()));
        }
    }

    @GetMapping("/search-batch")
    public ResponseEntity<?> searchByBatch(@RequestParam String batchNo){
        try {
            java.util.List<com.pharmacy.dto.InventoryDTO> list = inventoryService.findDTOByBatchNo(batchNo);
            Map<String,Object> resp = new HashMap<>();
            resp.put("code",200);
            resp.put("message","success");
            resp.put("data", list);
            resp.put("total", list.size());
            return ResponseEntity.ok(resp);
        } catch (Exception e){
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","按批号查询失败: "+e.getMessage(),"data", java.util.List.of()));
        }
    }
}