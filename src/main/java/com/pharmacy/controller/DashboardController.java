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
        resp.put("ordersChange", percentChange((double) safeLong(todayOrders), (double) safeLong(yesterdayOrders)));
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
    public ResponseEntity<Map<String,Object>> hotProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "day") String period){
        try {
            // 参数校验
            if (limit <= 0) limit = 10;
            if (limit > 100) limit = 100;

            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start;
            String p = (period == null ? "day" : period.trim().toLowerCase(Locale.ROOT));
            switch (p) {
                case "week":
                    start = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
                    break;
                case "month":
                    start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                    break;
                case "year":
                    start = LocalDate.now().withDayOfYear(1).atStartOfDay();
                    break;
                case "day":
                default:
                    start = LocalDate.now().atStartOfDay();
                    break;
            }

            // 执行查询
            List<Object[]> rows = orderItemRepository.findTopProductsByDateRange(start, end);

            List<Map<String,Object>> list = new ArrayList<>();
            // 判空处理，防止 NPE
            if (rows != null && !rows.isEmpty()) {
                int c=0;
                for(Object[] r: rows){
                    if(c >= limit) break;
                    // 数据完整性检查
                    if(r == null || r.length < 3){ continue; }

                    Map<String,Object> m = new HashMap<>();

                    // 安全获取数据
                    Object col0 = r[0]; // medicineId
                    Object col1 = r[1]; // quantity
                    Object col2 = r[2]; // subtotal

                    String medicineId = col0 == null ? null : String.valueOf(col0);
                    m.put("medicineId", medicineId);

                    // 补充药品信息
                    Medicine med = null;
                    if (medicineId != null) {
                        try {
                            med = medicineRepository.findById(medicineId).orElse(null);
                        } catch(Exception ignored) {}
                    }

                    // 填充显示字段
                    m.put("name", med != null ? med.getGenericName() : (medicineId == null ? "未知药品" : medicineId));
                    m.put("genericName", med != null ? med.getGenericName() : "");
                    m.put("tradeName", med != null ? med.getTradeName() : "");
                    m.put("spec", med != null ? med.getSpec() : "");
                    m.put("retailPrice", med != null ? med.getRetailPrice() : 0.0);

                    // 数量类型安全转换
                    long qty = 0;
                    if (col1 instanceof Number) {
                        qty = ((Number) col1).longValue();
                    } else if (col1 != null) {
                        try { qty = Long.parseLong(String.valueOf(col1)); } catch(Exception e){}
                    }
                    m.put("totalQuantity", qty);

                    // 金额类型安全转换
                    BigDecimal revenue = BigDecimal.ZERO;
                    if (col2 instanceof BigDecimal) {
                        revenue = (BigDecimal) col2;
                    } else if (col2 instanceof Number) {
                        revenue = BigDecimal.valueOf(((Number) col2).doubleValue());
                    } else if (col2 != null) {
                        try { revenue = new BigDecimal(String.valueOf(col2)); } catch(Exception e){}
                    }
                    m.put("totalRevenue", revenue);

                    // 当前库存
                    try {
                        m.put("currentStock", inventoryService.getCurrentStock(medicineId));
                    } catch(Exception ignored){
                        m.put("currentStock", 0);
                    }

                    list.add(m);
                    c++;
                }
            }
            return ResponseEntity.ok(Map.of("data", list));
        } catch (Exception ex) {
            ex.printStackTrace();
            // 返回详细错误以便调试
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "获取热销药品失败: " + ex.getMessage(),
                "error", ex.getClass().getSimpleName()
            ));
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

    private long safeLong(Long v){ return v == null ? 0L : v; }

    private Double safe(Double d){ return d == null? 0.0: d; }
    private BigDecimal round(Double d){ return BigDecimal.valueOf(safe(d)).setScale(2, RoundingMode.HALF_UP); }
    private String percentChange(Double today, Double yesterday){
        if(yesterday == null || yesterday == 0) return "--";
        double diff = safe(today) - safe(yesterday);
        double pct = (diff / yesterday) * 100.0;
        return String.format(Locale.CHINA, "%.1f%%", pct);
    }
}
