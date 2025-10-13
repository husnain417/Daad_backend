package com.Daad.ecommerce.service;

import com.Daad.ecommerce.repository.VendorPayoutRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayoutService {

    private final VendorPayoutRepository vendorPayoutRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${payouts.paymob.api-base-url:https://accept.paymob.com/api}")
    private String payoutBaseUrl;
    @Value("${payouts.paymob.api-key:}")
    private String payoutApiKey;
    @Value("${payouts.paymob.secret-key:}")
    private String payoutSecretKey;

    public PayoutService(VendorPayoutRepository vendorPayoutRepository) {
        this.vendorPayoutRepository = vendorPayoutRepository;
    }

    public boolean verifyPayoutWebhookSignature(Map<String, Object> payload, String signatureHeader) {
        try {
            if (signatureHeader == null || signatureHeader.isBlank() || payoutSecretKey == null || payoutSecretKey.isBlank()) {
                return false;
            }
            String json = toJson(payload);
            String expected = calculateHMAC(json, payoutSecretKey);
            return expected.equals(signatureHeader);
        } catch (Exception e) {
            System.err.println("Payout webhook signature verification failed: " + e.getMessage());
            return false;
        }
    }

    private String calculateHMAC(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            javax.crypto.spec.SecretKeySpec sk = new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(sk);
            byte[] bytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getAuthToken() {
        Map<String, Object> body = new HashMap<>();
        body.put("api_key", payoutApiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(payoutBaseUrl + "/auth/tokens", entity, Map.class);
        if (response == null || response.get("token") == null) {
            throw new RuntimeException("Failed to authenticate with Paymob Payout API");
        }
        return response.get("token").toString();
    }

    public void processDuePayoutsBatch(int limit) {
        if (payoutApiKey == null || payoutApiKey.isBlank()) {
            System.out.println("PayoutService: payouts disabled (missing payouts.paymob.api-key). Skipping batch.");
            return;
        }
        List<Map<String, Object>> due = vendorPayoutRepository.findDuePayouts(limit);
        if (due.isEmpty()) return;
        String token = getAuthToken();
        for (Map<String, Object> p : due) {
            try {
                String payoutId = p.get("id").toString();
                vendorPayoutRepository.markProcessing(payoutId);
                executePayout(p, token);
            } catch (Exception e) {
                try {
                    vendorPayoutRepository.markFailed(p.get("id").toString(), e.getMessage());
                    vendorPayoutRepository.rescheduleForRetry(p.get("id").toString(), 60);
                } catch (Exception ignored) {}
            }
        }
    }

    private void executePayout(Map<String, Object> payout, String token) {
        double netAmount = ((java.math.BigDecimal) payout.get("net_amount")).doubleValue();
        if (netAmount <= 0) throw new IllegalArgumentException("Net amount must be > 0");

        Map<String, Object> body = new HashMap<>();
        body.put("auth_token", token);
        body.put("amount_cents", (int) Math.round(netAmount * 100));
        body.put("currency", "EGP");
        body.put("merchant_reference", payout.get("id").toString());

        Map<String, Object> recipient = new HashMap<>();
        recipient.put("type", "bank");
        recipient.put("bank_account_number", payout.get("bank_account_number"));
        recipient.put("bank_routing_number", payout.get("bank_routing_number"));
        recipient.put("account_holder_name", payout.get("bank_account_holder_name"));
        recipient.put("bank_name", payout.get("bank_name"));
        body.put("recipient", recipient);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(payoutBaseUrl + "/acceptance/payouts", entity, Map.class);
        String responseJson = toJson(response != null ? response : Map.of("message", "null response"));
        String payoutId = payout.get("id").toString();

        if (response != null) {
            String status = String.valueOf(response.getOrDefault("status", "processing"));
            String paymobId = response.get("id") != null ? response.get("id").toString() : null;
            if ("completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) {
                vendorPayoutRepository.markCompleted(payoutId, paymobId, responseJson);
            } else if ("failed".equalsIgnoreCase(status) || "declined".equalsIgnoreCase(status)) {
                vendorPayoutRepository.markFailed(payoutId, "Paymob failed: " + status);
                vendorPayoutRepository.rescheduleForRetry(payoutId, 60);
            } else {
                vendorPayoutRepository.markProcessingWithResponse(payoutId, paymobId, responseJson);
            }
        } else {
            throw new RuntimeException("Paymob payout returned null response");
        }
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledHourlyPayouts() {
        try {
            if (payoutApiKey == null || payoutApiKey.isBlank()) {
                // No credentials yet, silently skip to keep app running
                return;
            }
            processDuePayoutsBatch(50);
        } catch (Exception e) {
            System.err.println("Scheduled payouts failed: " + e.getMessage());
        }
    }
}


