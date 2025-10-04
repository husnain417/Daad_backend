package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.PaymentDtos.CreatePaymentSessionRequest;
import com.Daad.ecommerce.dto.PaymentDtos.PaymobWebhookEvent;
import com.Daad.ecommerce.repository.OrderRepository;
import com.Daad.ecommerce.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
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
            System.err.println("Webhook processing error: " + e.getMessage());
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
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
        }
        var order = orderOpt.get();
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "paymentStatus", order.getPaymentStatus(),
                "paymentMethod", order.getPaymentMethod(),
                "transactionId", order.getTransactionId(),
                "paymentReference", order.getPaymentReference()
        )));
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}


