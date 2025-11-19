// DashboardController.java
package com.pharmacy.controller;

import com.pharmacy.dto.ApiResponse;
import com.pharmacy.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats() {
        try {
            Map<String, Object> stats = dashboardService.getDashboardStats();
            return ResponseEntity.ok(ApiResponse.success("获取统计数据成功", stats));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取统计数据失败"));
        }
    }

    @GetMapping("/sales-trend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesTrend(
            @RequestParam(defaultValue = "week") String period) {
        try {
            Map<String, Object> trendData = dashboardService.getSalesTrend(period);
            return ResponseEntity.ok(ApiResponse.success("获取销售趋势成功", trendData));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取销售趋势失败"));
        }
    }

    @GetMapping("/category-distribution")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCategoryDistribution() {
        try {
            Map<String, Object> distribution = dashboardService.getCategoryDistribution();
            return ResponseEntity.ok(ApiResponse.success("获取分类占比成功", distribution));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取分类占比失败"));
        }
    }

    @GetMapping("/expiring-medicines")
    public ResponseEntity<ApiResponse<?>> getExpiringMedicines() {
        try {
            return ResponseEntity.ok(ApiResponse.success("获取近效期药品成功", dashboardService.getExpiringMedicines()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取近效期药品失败"));
        }
    }

    @GetMapping("/hot-products")
    public ResponseEntity<ApiResponse<?>> getHotProducts() {
        try {
            return ResponseEntity.ok(ApiResponse.success("获取热销药品成功", dashboardService.getTodayHotProducts()));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取热销药品失败"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refreshData() {
        // 这里可以触发数据刷新，例如重新计算统计指标等
        return ResponseEntity.ok(ApiResponse.success("数据刷新成功", null));
    }

    // 在 DashboardController.java 中添加缺失的 /stock-alerts 接口
    @GetMapping("/stock-alerts")
    public ResponseEntity<ApiResponse<?>> getStockAlerts() {
        try {
            Map<String, Object> stockAlerts = dashboardService.getStockAlerts();
            return ResponseEntity.ok(ApiResponse.success("获取库存预警成功", stockAlerts));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("获取库存预警失败"));
        }
    }
}