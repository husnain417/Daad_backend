package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.service.DeliveryService;
import com.Daad.ecommerce.service.FincartApiService;
import com.Daad.ecommerce.repository.DeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/delivery")
@CrossOrigin(origins = "*")
public class DeliveryWebhookController {

    @Autowired
    private DeliveryService deliveryService;
    
    @Autowired
    private FincartApiService fincartApiService;
    
    @Autowired
    private DeliveryRepository deliveryRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fincart webhook endpoint for delivery status updates
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String payload) {
        
        try {
            // Verify webhook signature
            if (!fincartApiService.verifyWebhookSignature(payload, signature)) {
                System.err.println("Invalid webhook signature");
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid signature"));
            }
            
            // Parse payload
            Map<String, Object> webhookData = objectMapper.readValue(payload, Map.class);
            
            // Extract order ID
            String fincartOrderId = (String) webhookData.get("orderId");
            if (fincartOrderId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing orderId"));
            }
            
            // Check for duplicate processing
            String eventType = (String) webhookData.get("action");
            if (deliveryRepository.isWebhookProcessed(fincartOrderId, eventType)) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Already processed"));
            }
            
            // Log webhook
            deliveryRepository.insertWebhookLog(fincartOrderId, eventType, payload);
            
            // Process webhook data
            deliveryService.updateDeliveryStatus(fincartOrderId, webhookData);
            
            // Mark as processed
            deliveryRepository.markWebhookProcessed(fincartOrderId);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Webhook processed"));
            
        } catch (Exception e) {
            System.err.println("Error processing delivery webhook: " + e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Test webhook endpoint for development
     */
    @PostMapping("/webhook/test")
    public ResponseEntity<Map<String, Object>> testWebhook(@RequestBody Map<String, Object> testData) {
        try {
            String fincartOrderId = (String) testData.get("orderId");
            if (fincartOrderId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing orderId"));
            }
            
            // Process test data
            deliveryService.updateDeliveryStatus(fincartOrderId, testData);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Test webhook processed"));
            
        } catch (Exception e) {
            System.err.println("Error processing test webhook: " + e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
