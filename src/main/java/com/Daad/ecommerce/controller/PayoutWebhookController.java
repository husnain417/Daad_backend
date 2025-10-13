package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.repository.VendorPayoutRepository;
import com.Daad.ecommerce.service.PayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payouts")
@CrossOrigin(origins = "*")
public class PayoutWebhookController {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final PayoutService payoutService;

    public PayoutWebhookController(VendorPayoutRepository vendorPayoutRepository, PayoutService payoutService) {
        this.vendorPayoutRepository = vendorPayoutRepository;
        this.payoutService = payoutService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestHeader(value = "x-signature", required = false) String signature,
                                                       @RequestBody Map<String, Object> payload) {
        try {
            if (!payoutService.verifyPayoutWebhookSignature(payload, signature)) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "invalid signature"));
            }

            String paymobPayoutId = payload.get("id") != null ? payload.get("id").toString() : null;
            String status = payload.get("status") != null ? payload.get("status").toString() : "processing";
            String merchantReference = payload.get("merchant_reference") != null ? payload.get("merchant_reference").toString() : null;
            String payloadJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

            // Idempotency check: has a processed log for this paymob id?
            Boolean alreadyProcessed = vendorPayoutRepository.isWebhookProcessed(paymobPayoutId);
            if (Boolean.TRUE.equals(alreadyProcessed)) {
                return ResponseEntity.ok(Map.of("success", true, "message", "already processed"));
            }

            vendorPayoutRepository.insertWebhookLog(paymobPayoutId, status, payloadJson);

            if (merchantReference != null) {
                if ("completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
                    vendorPayoutRepository.markCompleted(merchantReference, paymobPayoutId, payloadJson);
                    vendorPayoutRepository.markWebhookProcessed(paymobPayoutId);
                } else if ("failed".equalsIgnoreCase(status) || "declined".equalsIgnoreCase(status)) {
                    vendorPayoutRepository.markFailed(merchantReference, "Webhook failed: " + status);
                    vendorPayoutRepository.markWebhookProcessed(paymobPayoutId);
                } else {
                    vendorPayoutRepository.markProcessingWithResponse(merchantReference, paymobPayoutId, payloadJson);
                }
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}


