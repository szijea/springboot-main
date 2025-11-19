package com.pharmacy.controller;

import com.pharmacy.dto.OrderRequest;
import com.pharmacy.dto.OrderResponse;
import com.pharmacy.entity.Order;
import com.pharmacy.service.OrderService;
import com.pharmacy.repository.OrderItemRepository;
import com.pharmacy.repository.MedicineRepository;
import com.pharmacy.entity.OrderItem;
import com.pharmacy.entity.Medicine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    // 修复: 直接使用 Jackson 反序列化 OrderRequest，避免 415 Unsupported Media Type
    @PostMapping(consumes = {"application/json","application/json;charset=UTF-8"}, produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest orderRequest) {
        try {
            if (orderRequest == null) {
                return ResponseEntity.badRequest().body(Map.of("code",400,"message","请求体为空"));
            }
            if (orderRequest.getItems() == null) {
                orderRequest.setItems(new java.util.ArrayList<>());
            }
            if (orderRequest.getCustomerName() == null || orderRequest.getCustomerName().trim().isEmpty()) {
                orderRequest.setCustomerName("匿名客户" + System.currentTimeMillis());
            }
            // 空购物明细校验
            if (orderRequest.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code",400,"message","订单项为空，无法创建订单"));
            }
            OrderResponse resp = orderService.createOrder(orderRequest);
            String orderId = resp.getOrderNumber();
            return ResponseEntity.ok(Map.of(
                    "code",200,
                    "message","订单创建成功",
                    "orderId", orderId,
                    "data",resp
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","订单创建失败: "+ e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public Map<String,Object> ping(){
        return Map.of("code",200,"message","orders controller alive");
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {
        try {
            Optional<Order> orderOpt = orderService.getOrderByOrderId(orderId);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                // 构建订单项列表并直接嵌入 data，前端 displayOrderDetail 读取 order.items
                List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
                java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
                for (OrderItem oi : orderItems) {
                    java.util.Map<String, Object> itemMap = new java.util.HashMap<>();
                    itemMap.put("medicineId", oi.getMedicineId());
                    itemMap.put("quantity", oi.getQuantity());
                    itemMap.put("unitPrice", oi.getUnitPrice());
                    itemMap.put("subtotal", oi.getSubtotal());
                    Medicine med = medicineRepository.findById(oi.getMedicineId()).orElse(null);
                    if (med != null) {
                        itemMap.put("genericName", med.getGenericName());
                        itemMap.put("tradeName", med.getTradeName());
                        itemMap.put("spec", med.getSpec());
                        itemMap.put("manufacturer", med.getManufacturer());
                        itemMap.put("barcode", med.getBarcode());
                    } else {
                        itemMap.put("genericName", "未知药品");
                    }
                    items.add(itemMap);
                }
                Map<String,Object> orderMap = new HashMap<>();
                orderMap.put("orderId", order.getOrderId());
                orderMap.put("customerName", order.getCustomerName());
                orderMap.put("memberId", order.getMemberId());
                orderMap.put("cashierId", order.getCashierId());
                orderMap.put("totalAmount", order.getTotalAmount());
                orderMap.put("discountAmount", order.getDiscountAmount());
                orderMap.put("actualPayment", order.getActualPayment());
                orderMap.put("paymentType", order.getPaymentType());
                orderMap.put("paymentStatus", order.getPaymentStatus());
                orderMap.put("orderTime", order.getOrderTime());
                orderMap.put("payTime", order.getPayTime());
                orderMap.put("refundTime", order.getRefundTime());
                orderMap.put("refundReason", order.getRefundReason());
                orderMap.put("usedPoints", order.getUsedPoints());
                orderMap.put("createdPoints", order.getCreatedPoints());
                orderMap.put("items", items);
                return ResponseEntity.ok(Map.of("code",200,"message","success","data",orderMap));
            } else {
                return ResponseEntity.status(404).body(Map.of("code",404,"message","订单不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","获取订单失败: "+e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Order> orders = orderService.getAllOrders(pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", orders.getContent());
            response.put("total", orders.getTotalElements());
            response.put("totalPages", orders.getTotalPages());
            response.put("currentPage", page);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","获取订单列表失败: "+e.getMessage()));
        }
    }

    @GetMapping("/today")
    public ResponseEntity<?> getTodayOrders() {
        try {
            List<Order> orders = orderService.getTodayOrders();

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", orders);
            response.put("total", orders.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","获取今日订单失败: "+e.getMessage()));
        }
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderId) {
        try {
            boolean deleted = orderService.deleteOrder(orderId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("code",200,"message","订单删除成功"));
            } else {
                return ResponseEntity.status(404).body(Map.of("code",404,"message","订单不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","删除订单失败: "+e.getMessage()));
        }
    }

    // 新增：退单接口
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<?> refund(@PathVariable String orderId, @RequestBody(required = false) Map<String,Object> body){
        try {
            String reason = body!=null && body.get("reason")!=null? String.valueOf(body.get("reason")) : "无";
            OrderResponse resp = orderService.refundOrder(orderId, reason);
            return ResponseEntity.ok(Map.of("code",200,"message","退单成功","data", resp));
        } catch (Exception e){
            return ResponseEntity.internalServerError().body(Map.of("code",500,"message","退单失败: "+e.getMessage()));
        }
    }
}
