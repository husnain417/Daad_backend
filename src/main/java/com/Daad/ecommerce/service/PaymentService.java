package com.Daad.ecommerce.service;

import com.Daad.ecommerce.dto.PaymentDtos.CreatePaymentSessionRequest;
import com.Daad.ecommerce.dto.PaymentDtos.CreatePaymentSessionResponse;
import com.Daad.ecommerce.dto.PaymentDtos.PaymobWebhookEvent;
import com.Daad.ecommerce.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class PaymentService {

    @Value("${payments.paymob.api-base-url}")
    private String paymobBaseUrl;
    @Value("${payments.paymob.api-key}")
    private String paymobApiKey;
    @Value("${payments.paymob.secret-key}")
    private String paymobSecretKey;
    @Value("${payments.paymob.public-key}")
    private String paymobPublicKey;
    @Value("${payments.paymob.webhook-url}")
    private String paymobWebhookUrl;
    @Value("${payments.paymob.success-url}")
    private String paymobSuccessUrl;
    @Value("${payments.paymob.failure-url}")
    private String paymobFailureUrl;
    @Value("${payments.paymob.integration-id}")
    private String paymobIntegrationId;
    @Value("${payments.paymob.iframe-id}")
    private String paymobIframeId;

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public CreatePaymentSessionResponse createPaymentSession(CreatePaymentSessionRequest req, double amountCents, String orderId) {
        try {
            // Step 1: Authenticate with Paymob
            String authToken = authenticateWithPaymob();
            if (authToken == null) {
                throw new RuntimeException("Failed to authenticate with Paymob");
            }

            // Step 2: Create order in Paymob
            Map<String, Object> paymobOrder = createPaymobOrder(authToken, amountCents, orderId);
            Integer paymobOrderId = (Integer) paymobOrder.get("id");
            String paymentReference = paymobOrder.get("merchant_order_id").toString();

            // Step 3: Generate payment key
            String paymentKey = generatePaymentKey(authToken, paymobOrderId, amountCents, req);

            // Step 4: Build checkout URL
            String checkoutUrl = String.format("https://accept.paymob.com/api/acceptance/iframes/%s?payment_token=%s", 
                paymobIframeId, paymentKey);
            
            System.out.println("Paymob Payment Flow Debug:");
            System.out.println("Integration ID: " + paymobIntegrationId);
            System.out.println("Iframe ID: " + paymobIframeId);
            System.out.println("Payment Key: " + paymentKey);
            System.out.println("Checkout URL: " + checkoutUrl);

            // Log transaction
            paymentRepository.insertPaymentTransaction(orderId, "paymob", null, paymentReference, 
                amountCents / 100.0, "EGP", "initiated", toJson(paymobOrder));

            CreatePaymentSessionResponse resp = new CreatePaymentSessionResponse();
            resp.setPaymentKey(paymentKey);
            resp.setCheckoutUrl(checkoutUrl);
            resp.setPaymentReference(paymentReference);
            resp.setExpiresAt(Instant.now().plusSeconds(3600).getEpochSecond());
            return resp;

        } catch (Exception e) {
            System.err.println("Error creating payment session: " + e.getMessage());
            throw new RuntimeException("Failed to create payment session: " + e.getMessage());
        }
    }

    private String authenticateWithPaymob() {
        try {
            Map<String, String> authRequest = new HashMap<>();
            authRequest.put("api_key", paymobApiKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(authRequest, headers);

            String url = paymobBaseUrl + "/auth/tokens";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            return response != null ? (String) response.get("token") : null;
        } catch (Exception e) {
            System.err.println("Paymob authentication failed: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> createPaymobOrder(String authToken, double amountCents, String orderId) {
        try {
            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("auth_token", authToken);
            orderRequest.put("delivery_needed", false);
            orderRequest.put("amount_cents", (int) amountCents);
            orderRequest.put("currency", "EGP");
            orderRequest.put("merchant_order_id", orderId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderRequest, headers);

            String url = paymobBaseUrl + "/ecommerce/orders";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            return response;
        } catch (Exception e) {
            System.err.println("Paymob order creation failed: " + e.getMessage());
            throw new RuntimeException("Failed to create Paymob order");
        }
    }

    private String generatePaymentKey(String authToken, Integer paymobOrderId, double amountCents, CreatePaymentSessionRequest req) {
        try {
            Map<String, Object> paymentKeyRequest = new HashMap<>();
            paymentKeyRequest.put("auth_token", authToken);
            paymentKeyRequest.put("amount_cents", (int) amountCents);
            paymentKeyRequest.put("expiration", 3600);
            paymentKeyRequest.put("order_id", paymobOrderId);
            Map<String, Object> billingData = new HashMap<>();
            billingData.put("apartment", "NA");
            billingData.put("email", req.getCustomerEmail() != null ? req.getCustomerEmail() : "customer@example.com");
            billingData.put("floor", "NA");
            billingData.put("first_name", "Customer");
            billingData.put("street", "NA");
            billingData.put("building", "NA");
            billingData.put("phone_number", req.getCustomerPhone() != null ? req.getCustomerPhone() : "+201234567890");
            billingData.put("shipping_method", "PKG");
            billingData.put("postal_code", "NA");
            billingData.put("city", "Cairo");
            billingData.put("country", "EG");
            billingData.put("last_name", "User");
            billingData.put("state", "Cairo");
            paymentKeyRequest.put("billing_data", billingData);
            paymentKeyRequest.put("currency", "EGP");
            paymentKeyRequest.put("integration_id", Integer.parseInt(paymobIntegrationId));

            System.out.println("Paymob Payment Key Request:");
            System.out.println("Integration ID: " + paymobIntegrationId);
            System.out.println("Order ID: " + paymobOrderId);
            System.out.println("Amount Cents: " + amountCents);
            System.out.println("Auth Token: " + (authToken != null ? authToken.substring(0, 20) + "..." : "null"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentKeyRequest, headers);

            String url = paymobBaseUrl + "/acceptance/payment_keys";
            System.out.println("Requesting URL: " + url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            
            if (response != null) {
                System.out.println("Paymob Response: " + response);
                return (String) response.get("token");
            } else {
                System.err.println("Paymob returned null response");
                return null;
            }
        } catch (Exception e) {
            System.err.println("Paymob payment key generation failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate payment key: " + e.getMessage());
        }
    }

    public boolean verifyWebhookSignature(PaymobWebhookEvent event, String signatureHeader) {
        try {
            if (signatureHeader == null || signatureHeader.isBlank()) {
                return false;
            }

            // Paymob webhook signature verification
            String payload = toJson(event);
            String expectedSignature = calculateHMAC(payload, paymobSecretKey);
            
            return expectedSignature.equals(signatureHeader);
        } catch (Exception e) {
            System.err.println("Webhook signature verification failed: " + e.getMessage());
            return false;
        }
    }

    private String calculateHMAC(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        mac.init(secretKeySpec);
        byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacData);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public void processWebhookEvent(String transactionId, String status, String orderId, String webhookJson) {
        try {
            paymentRepository.updatePaymentTransactionStatus(transactionId, status, webhookJson);
            
            Map<String, Object> fields = new HashMap<>();
            fields.put("payment_provider", "paymob");
            fields.put("payment_reference", null);
            fields.put("transaction_id", transactionId);
            fields.put("payment_status", mapPaymobStatus(status));
            fields.put("paid_at", "paid".equalsIgnoreCase(mapPaymobStatus(status)) ? java.time.LocalDateTime.now() : null);
            fields.put("failure_reason", "failed".equalsIgnoreCase(mapPaymobStatus(status)) ? status : null);
            fields.put("payment_metadata", webhookJson);
            
            paymentRepository.updateOrderPaymentFields(orderId, fields);
        } catch (Exception e) {
            System.err.println("Error processing webhook event: " + e.getMessage());
        }
    }

    private String mapPaymobStatus(String paymobStatus) {
        if (paymobStatus == null) return "pending";
        switch (paymobStatus.toLowerCase()) {
            case "success":
            case "paid":
            case "captured":
                return "paid";
            case "failed":
            case "declined":
            case "voided":
            case "refunded":
                return "failed";
            default:
                return "pending";
        }
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}


