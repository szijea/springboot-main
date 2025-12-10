package com.pharmacy.controller;

import com.pharmacy.entity.Medicine;
import com.pharmacy.repository.MedicineRepository;
import com.pharmacy.repository.OrderItemRepository;
import com.pharmacy.repository.OrderRepository;
import com.pharmacy.service.InventoryService;
import com.pharmacy.service.MedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private MedicineService medicineService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private MedicineRepository medicineRepository;

    @GetMapping("/metrics")
    public ResponseEntity<Map<String,Object>> metrics(){
        Map<String,Object> resp = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startYesterday = today.minusDays(1).atStartOfDay();
        LocalDateTime endYesterday = startToday.minusSeconds(1);
        // 已支付销售额
        Double paidToday = safe(orderRepository.getPaidSalesBetween(startToday, now));
        Double paidYesterday = safe(orderRepository.getPaidSalesBetween(startYesterday, endYesterday));
        // 当日退款金额（按 refundTime 计入）
        Double refundedToday = safe(orderRepository.getRefundedAmountBetween(startToday, now));
        // 今日净销售额 = 已支付 - 退款
        Double netTodaySales = Math.max(0.0, paidToday - refundedToday);
        Long todayOrders = orderRepository.countPaidOrdersBetween(startToday, now);
        Long yesterdayOrders = orderRepository.countPaidOrdersBetween(startYesterday, endYesterday);
        Long pendingToday = orderRepository.countByPaymentStatusAndOrderTimeBetween(0, startToday, now);
        Long refundedCountToday = orderRepository.countByPaymentStatusAndOrderTimeBetween(2, startToday, now);
        BigDecimal avgOrderValue = (todayOrders!=null && todayOrders>0) ? BigDecimal.valueOf(netTodaySales/todayOrders).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        List<Map<String,Object>> alerts = computeStockAlerts();
        long lowStockCount = alerts.stream().filter(a -> "LOW_STOCK".equals(a.get("type"))).count();
        long nearExpiryCount = alerts.stream().filter(a -> "NEAR_EXPIRY".equals(a.get("type"))).count();
        resp.put("todaySales", round(netTodaySales));
        resp.put("salesChange", percentChange(netTodaySales, Math.max(0.0, paidYesterday - safe(orderRepository.getRefundedAmountBetween(startYesterday, endYesterday)))));
        resp.put("todayOrders", todayOrders);
        resp.put("ordersChange", percentChange(todayOrders.doubleValue(), yesterdayOrders.doubleValue()));
        resp.put("pendingOrders", pendingToday);
        resp.put("refundedOrders", refundedCountToday);
        resp.put("avgOrderValue", avgOrderValue);
        resp.put("memberConsumption", round(netTodaySales));
        resp.put("memberChange", percentChange(netTodaySales, Math.max(0.0, paidYesterday - safe(orderRepository.getRefundedAmountBetween(startYesterday, endYesterday)))));
        resp.put("lowStockCount", lowStockCount);
        resp.put("stockChange", "--");
        resp.put("nearExpiryCount", nearExpiryCount);
        resp.put("nearExpiryChange", "--");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/stock-alerts")
    public ResponseEntity<Map<String,Object>> stockAlerts(){
        List<Map<String,Object>> alerts = computeStockAlerts();
        return ResponseEntity.ok(Map.of("data", alerts));
    }

    @GetMapping("/hot-products")
    public ResponseEntity<Map<String,Object>> hotProducts(@RequestParam(defaultValue = "10") int limit){
        try {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusDays(7); // 最近7天
            List<Object[]> rows;
            try {
                rows = orderItemRepository.findTopProductsByDateRange(start, end);
            } catch(Exception e){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            if (limit <= 0) limit = 10;
            List<Map<String,Object>> list = new ArrayList<>();
            int c=0;
            for(Object[] r: rows){
                if(c>=limit) break;
                if(r == null || r.length == 0){ c++; continue; }
                Map<String,Object> m = new HashMap<>();
                // 兼容不同查询列：常见返回为 [medicineId, SUM(quantity), SUM(subtotal)]
                Object col0 = (r.length>0? r[0]: null);
                Object col1 = (r.length>1? r[1]: null);
                Object col2 = (r.length>2? r[2]: null);

                String medicineId = col0==null? null: String.valueOf(col0);
                // 名称优先取查询列（若存在并为 String），否则从 medicineRepository 查找
                String name = null;
                if (r.length>3) {
                    Object maybeName = r[1];
                    if (maybeName instanceof String) name = (String) maybeName;
                }
                if (name == null && medicineId != null) {
                    try {
                        name = medicineRepository.findById(medicineId).map(com.pharmacy.entity.Medicine::getGenericName).orElse(medicineId);
                    } catch(Exception ignored) { name = medicineId; }
                }
                m.put("medicineId", medicineId);
                m.put("name", name == null ? medicineId : name);

                // 数量转换（col1 通常是 SUM(quantity)）
                Number qtyNum = null;
                if(col1 instanceof Number){ qtyNum = (Number) col1; }
                else { try { qtyNum = Double.valueOf(String.valueOf(col1)); } catch(Exception ignored){} }
                m.put("totalQuantity", qtyNum==null? 0: (qtyNum instanceof Double || qtyNum instanceof Float? Math.round(qtyNum.doubleValue()) : qtyNum.longValue()));

                // 收入转换为 BigDecimal（col2 通常是 SUM(subtotal)）
                java.math.BigDecimal revenue;
                if(col2 instanceof java.math.BigDecimal){ revenue = (java.math.BigDecimal) col2; }
                else if(col2 instanceof Number){ revenue = java.math.BigDecimal.valueOf(((Number) col2).doubleValue()); }
                else { try { revenue = new java.math.BigDecimal(String.valueOf(col2)); } catch(Exception ex){ revenue = java.math.BigDecimal.ZERO; } }
                m.put("totalRevenue", revenue);

                // 当前库存（使用 String medicineId）
                try { m.put("currentStock", inventoryService.getCurrentStock(medicineId)); } catch(Exception ignored){ m.put("currentStock", 0); }
                list.add(m); c++;
            }
            return ResponseEntity.ok(Map.of("data", list));
        } catch (Exception ex) {
            // 临时输出堆栈以便定位生产/开发环境的 500 错误
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("code",500,"message","hot-products 处理失败","error", ex.toString()));
        }
    }

    private List<Map<String,Object>> computeStockAlerts(){
        List<Map<String,Object>> alerts = new ArrayList<>();
        // 取前 300 条药品分页（避免全量过大）
        var page = medicineService.getAllMedicines(org.springframework.data.domain.PageRequest.of(0,300));
        LocalDate today = LocalDate.now();
        for(Medicine m: page.getContent()){
            Integer stock = null;
            try { stock = inventoryService.getCurrentStock(m.getMedicineId()); } catch(Exception ignored){}
            LocalDate expiry = m.getExpiryDate();
            String type = null;
            if(stock == null || stock == 0){ type = "OUT_OF_STOCK"; }
            else if(stock <= 10){ type = "LOW_STOCK"; }
            if(expiry != null){
                if(expiry.isBefore(today)){ type = "EXPIRED"; }
                else if(expiry.isBefore(today.plusDays(60))){ type = "NEAR_EXPIRY"; }
            }
            if(type != null){
                Map<String,Object> row = new HashMap<>();
                row.put("medicineId", m.getMedicineId());
                row.put("genericName", m.getGenericName());
                row.put("tradeName", m.getTradeName());
                row.put("currentStock", stock == null? 0: stock);
                row.put("type", type);
                alerts.add(row);
            }
        }
        return alerts;
    }

    private Double safe(Double d){ return d == null? 0.0: d; }
    private BigDecimal round(Double d){ return BigDecimal.valueOf(safe(d)).setScale(2, RoundingMode.HALF_UP); }
    private String percentChange(Double today, Double yesterday){
        if(yesterday == null || yesterday == 0) return "--";
        double diff = safe(today) - safe(yesterday);
        double pct = (diff / yesterday) * 100.0;
        return String.format(Locale.CHINA, "%.1f%%", pct);
    }
}
