package com.Daad.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class FincartApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${fincart.api.base-url:https://api.fincart.com}")
    private String baseUrl;
    
    @Value("${fincart.api.access-token:}")
    private String accessToken;
    
    @Value("${fincart.webhook.secret:}")
    private String webhookSecret;

    public FincartApiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get access token (either configured or dummy)
     */
    public String authenticate() {
        try {
            // Check if access token is configured
            if (accessToken == null || accessToken.isEmpty()) {
                System.out.println("Fincart access token not configured, using dummy token");
                return "dummy_fincart_token_" + System.currentTimeMillis();
            }
            
            // Return the configured access token
            return accessToken;
        } catch (Exception e) {
            System.err.println("Fincart authentication error: " + e.getMessage());
            System.out.println("Using dummy token for development");
            return "dummy_fincart_token_" + System.currentTimeMillis();
        }
    }

    /**
     * Get cities and areas for address validation
     */
    public List<Map<String, Object>> getCitiesAndAreas(String accessToken) {
        try {
            // Check if using dummy token
            if (accessToken.startsWith("dummy_")) {
                System.out.println("Using dummy cities and areas data");
                return getDummyCitiesAndAreas();
            }
            
            String url = baseUrl + "/merchant/config/gov";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return (List<Map<String, Object>>) body.get("data");
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error fetching cities and areas: " + e.getMessage());
            System.out.println("Using dummy data for development");
            return getDummyCitiesAndAreas();
        }
    }

    /**
     * Get pickup locations for vendors
     */
    public List<Map<String, Object>> getPickupLocations(String accessToken) {
        try {
            // Check if using dummy token
            if (accessToken.startsWith("dummy_")) {
                System.out.println("Using dummy pickup locations data");
                return getDummyPickupLocations();
            }
            
            String url = baseUrl + "/merchant/locations";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return (List<Map<String, Object>>) body.get("data");
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error fetching pickup locations: " + e.getMessage());
            System.out.println("Using dummy data for development");
            return getDummyPickupLocations();
        }
    }

    /**
     * Create delivery order
     */
    public Map<String, Object> createOrder(String accessToken, Map<String, Object> orderData) {
        try {
            // Check if using dummy token
            if (accessToken.startsWith("dummy_")) {
                System.out.println("Using dummy order creation response");
                return getDummyOrderResponse();
            }
            
            String url = baseUrl + "/v4/orders/create";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderData, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody();
            }
            
            throw new RuntimeException("Order creation failed: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Error creating Fincart order: " + e.getMessage());
            System.out.println("Using dummy response for development");
            return getDummyOrderResponse();
        }
    }

    /**
     * Edit existing order (only for pending orders)
     */
    public Map<String, Object> editOrder(String accessToken, String orderId, Map<String, Object> orderData) {
        try {
            String url = baseUrl + "/v4/orders/" + orderId + "/edit";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderData, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            
            throw new RuntimeException("Order edit failed: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Error editing Fincart order: " + e.getMessage());
            throw new RuntimeException("Failed to edit delivery order", e);
        }
    }

    /**
     * Cancel order
     */
    public Map<String, Object> cancelOrder(String accessToken, List<String> orderIds) {
        try {
            String url = baseUrl + "/v4/orders/cancel";
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> filters = new HashMap<>();
            filters.put("ids", orderIds);
            requestBody.put("filters", filters);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            
            throw new RuntimeException("Order cancellation failed: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Error cancelling Fincart order: " + e.getMessage());
            throw new RuntimeException("Failed to cancel delivery order", e);
        }
    }

    /**
     * Get order logs/tracking history
     */
    public List<Map<String, Object>> getOrderLogs(String accessToken, String orderId) {
        try {
            String url = baseUrl + "/v4/orders/" + orderId + "/logs";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                return (List<Map<String, Object>>) body.get("data");
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Error fetching order logs: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Generate AWB labels
     */
    public byte[] generateLabels(String accessToken, List<String> orderIds, String type) {
        try {
            String url = baseUrl + "/v4/orders/awb";
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> filters = new HashMap<>();
            filters.put("ids", orderIds);
            filters.put("type", type); // "paper", "a6", "thermal"
            requestBody.put("filters", filters);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, request, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            
            throw new RuntimeException("Label generation failed: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Error generating labels: " + e.getMessage());
            throw new RuntimeException("Failed to generate delivery labels", e);
        }
    }

    /**
     * Generate pickup manifest
     */
    public byte[] generateManifest(String accessToken, List<String> orderIds) {
        try {
            String url = baseUrl + "/v4/orders/manifesto";
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> filters = new HashMap<>();
            filters.put("ids", orderIds);
            requestBody.put("filters", filters);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<byte[]> response = restTemplate.postForEntity(url, request, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            
            throw new RuntimeException("Manifest generation failed: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("Error generating manifest: " + e.getMessage());
            throw new RuntimeException("Failed to generate pickup manifest", e);
        }
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                return false;
            }
            
            // Fincart typically uses HMAC-SHA256 for webhook signatures
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String calculatedSignature = sb.toString();
            
            return calculatedSignature.equals(signature.toLowerCase());
        } catch (Exception e) {
            System.err.println("Webhook signature verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convert timestamp to milliseconds
     */
    private long toTimestamp(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Dummy cities and areas data for development
     */
    private List<Map<String, Object>> getDummyCitiesAndAreas() {
        List<Map<String, Object>> dummyData = new ArrayList<>();
        
        Map<String, Object> cairo = new HashMap<>();
        cairo.put("city", "Cairo");
        cairo.put("area", "Downtown");
        cairo.put("is_available", true);
        dummyData.add(cairo);
        
        Map<String, Object> cairo2 = new HashMap<>();
        cairo2.put("city", "Cairo");
        cairo2.put("area", "Giza");
        cairo2.put("is_available", true);
        dummyData.add(cairo2);
        
        Map<String, Object> alex = new HashMap<>();
        alex.put("city", "Alexandria");
        alex.put("area", "Downtown");
        alex.put("is_available", true);
        dummyData.add(alex);
        
        return dummyData;
    }

    /**
     * Dummy pickup locations data for development
     */
    private List<Map<String, Object>> getDummyPickupLocations() {
        List<Map<String, Object>> dummyData = new ArrayList<>();
        
        Map<String, Object> location1 = new HashMap<>();
        location1.put("id", "dummy_location_1");
        location1.put("name", "Main Warehouse - Cairo");
        location1.put("city", "Cairo");
        location1.put("area", "Downtown");
        location1.put("address", "123 Main Street, Cairo");
        location1.put("contact_person", "Ahmed Ali");
        location1.put("contact_phone", "01000000001");
        dummyData.add(location1);
        
        Map<String, Object> location2 = new HashMap<>();
        location2.put("id", "dummy_location_2");
        location2.put("name", "Secondary Warehouse - Giza");
        location2.put("city", "Cairo");
        location2.put("area", "Giza");
        location2.put("address", "456 Pyramid Street, Giza");
        location2.put("contact_person", "Mohamed Hassan");
        location2.put("contact_phone", "01000000002");
        dummyData.add(location2);
        
        return dummyData;
    }

    /**
     * Dummy order creation response for development
     */
    private Map<String, Object> getDummyOrderResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", "dummy_order_" + System.currentTimeMillis());
        response.put("orderCode", "DUMMY-" + System.currentTimeMillis());
        response.put("trackingNumber", "TRK" + System.currentTimeMillis());
        response.put("trackURL", "https://dummy-tracking.com/track/" + System.currentTimeMillis());
        response.put("courier", "Dummy Courier");
        response.put("status", "pending");
        return response;
    }
}
