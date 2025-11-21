package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.PaymentDtos.CreatePaymentSessionRequest;
import com.Daad.ecommerce.dto.PaymentDtos.PaymobWebhookEvent;
import com.Daad.ecommerce.dto.PaymentDtos.RefundRequest;
import com.Daad.ecommerce.dto.PaymentDtos.RefundResponse;
import com.Daad.ecommerce.dto.PaymentDtos.VoidResponse;
import com.Daad.ecommerce.repository.OrderRepository;
import com.Daad.ecommerce.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    public PaymentController(PaymentService paymentService, OrderRepository orderRepository) {
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/paymob/create-session")
    public ResponseEntity<Map<String,Object>> createSession(@RequestBody CreatePaymentSessionRequest req) {
        var orderOpt = orderRepository.findById(req.getOrderId());
        if (orderOpt.isEmpty()) {
            log.error("Payment session creation failed: Order {} not found", req.getOrderId());
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }
        var order = orderOpt.get();
        var resp = paymentService.createPaymentSession(req, Math.round(order.getTotal() * 100), order.getId());
        return ResponseEntity.ok(Map.of("success", true, "data", resp));
    }

    @PostMapping("/paymob/webhook")
    public ResponseEntity<String> webhook(@RequestHeader(value = "x-signature", required = false) String signature,
                                          @RequestBody PaymobWebhookEvent event) {
        try {
            if (!paymentService.verifyWebhookSignature(event, signature)) {
                log.error("Webhook verification failed: invalid signature");
                return ResponseEntity.status(401).body("invalid signature");
            }

            // Extract transaction details from Paymob webhook
            String transactionId = extractTransactionId(event);
            String status = extractStatus(event);
            String orderId = extractOrderId(event);

            if (orderId != null && !orderId.isBlank()) {
                paymentService.processWebhookEvent(transactionId, status, orderId, toJson(event));
            }

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Webhook processing error", e);
            return ResponseEntity.status(500).body("webhook processing failed");
        }
    }

    private String extractTransactionId(PaymobWebhookEvent event) {
        try {
            if (event.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) event.getData();
                return data.get("id") != null ? data.get("id").toString() : "";
            }
        } catch (Exception e) {
            System.err.println("Error extracting transaction ID: " + e.getMessage());
        }
        return "";
    }

    private String extractStatus(PaymobWebhookEvent event) {
        try {
            if (event.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) event.getData();
                return data.get("success") != null ? 
                    (Boolean.TRUE.equals(data.get("success")) ? "success" : "failed") : "pending";
            }
        } catch (Exception e) {
            System.err.println("Error extracting status: " + e.getMessage());
        }
        return "pending";
    }

    private String extractOrderId(PaymobWebhookEvent event) {
        try {
            if (event.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) event.getData();
                return data.get("merchant_order_id") != null ? data.get("merchant_order_id").toString() : "";
            }
        } catch (Exception e) {
            System.err.println("Error extracting order ID: " + e.getMessage());
        }
        return "";
    }

    @GetMapping("/order/{orderId}/status")
    public ResponseEntity<Map<String,Object>> getOrderPaymentStatus(@PathVariable String orderId) {
        var orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            log.error("Payment status check failed: Order {} not found", orderId);
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }
        var order = orderOpt.get();
        Map<String, Object> data = new HashMap<>();
        data.put("paymentStatus", order.getPaymentStatus());
        data.put("paymentMethod", order.getPaymentMethod());
        data.put("transactionId", order.getTransactionId());
        data.put("paymentReference", order.getPaymentReference());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    @PostMapping("/refund/{orderId}")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable String orderId, @RequestBody(required = false) RefundRequest req) {
        try {
            var orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.error("Refund failed: Order {} not found", orderId);
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
            }
            var order = orderOpt.get();
            if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
                log.error("Refund failed for order {}: No transaction found", orderId);
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "No transaction found for order"));
            }

            // Check if order is already refunded
            if ("refunded".equalsIgnoreCase(order.getPaymentStatus()) || "voided".equalsIgnoreCase(order.getPaymentStatus())) {
                log.error("Refund failed for order {}: Order already refunded/voided", orderId);
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "Order already refunded/voided"));
            }

            String transactionId = order.getTransactionId();
            Double amountCents = req != null && req.getAmountCents() != null ? req.getAmountCents() : null;
            String reason = req != null ? req.getReason() : null;

            RefundResponse result = paymentService.refundTransaction(transactionId, orderId, amountCents, reason);
            return ResponseEntity.ok(Map.of("success", result.isSuccess(), "data", result));
        } catch (Exception e) {
            log.error("Refund endpoint error for order {}", orderId, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/void/{orderId}")
    public ResponseEntity<Map<String, Object>> voidPayment(@PathVariable String orderId, @RequestBody(required = false) Map<String, Object> body) {
        try {
            var orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.error("Void payment failed: Order {} not found", orderId);
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
            }
            var order = orderOpt.get();
            if (order.getTransactionId() == null || order.getTransactionId().isBlank()) {
                log.error("Void payment failed for order {}: No transaction found", orderId);
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "No transaction found for order"));
            }

            // Check if order is already refunded/voided
            if ("refunded".equalsIgnoreCase(order.getPaymentStatus()) || "voided".equalsIgnoreCase(order.getPaymentStatus())) {
                log.error("Void payment failed for order {}: Order already refunded/voided", orderId);
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "Order already refunded/voided"));
            }

            String transactionId = order.getTransactionId();
            String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;

            VoidResponse result = paymentService.voidTransaction(transactionId, orderId, reason);
            return ResponseEntity.ok(Map.of("success", result.isSuccess(), "data", result));
        } catch (Exception e) {
            log.error("Void endpoint error for order {}", orderId, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/refund-status/{orderId}")
    public ResponseEntity<Map<String, Object>> refundStatus(@PathVariable String orderId) {
        try {
            var orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                log.error("Refund status check failed: Order {} not found", orderId);
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
            }
            var latest = paymentService.paymentsGetLatestRefundForOrder(orderId);
            return ResponseEntity.ok(Map.of("success", true, "data", latest != null ? latest : Map.of()));
        } catch (Exception e) {
            log.error("Refund status endpoint error for order {}", orderId, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
        }
    }
}


