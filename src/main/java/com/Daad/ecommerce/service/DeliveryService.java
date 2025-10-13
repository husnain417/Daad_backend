package com.Daad.ecommerce.service;

import com.Daad.ecommerce.repository.DeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class DeliveryService {

    @Autowired
    private FincartApiService fincartApiService;
    
    @Autowired
    private DeliveryRepository deliveryRepository;
    
    @Autowired
    private OrderEmailService orderEmailService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create delivery order for a single vendor
     */
    public Map<String, Object> createDeliveryOrder(String orderId, String vendorId, Map<String, Object> orderData) {
        try {
            // Get access token
            String accessToken = getValidAccessToken();
            
            // Get vendor's pickup location
            Map<String, Object> pickupLocation = deliveryRepository.getPickupLocationByVendor(vendorId);
            if (pickupLocation == null) {
                // Use dummy pickup location if none found
                System.out.println("No pickup location found for vendor " + vendorId + ", using dummy location");
                pickupLocation = new HashMap<>();
                pickupLocation.put("id", "dummy_location_1");
                pickupLocation.put("fincart_location_id", "dummy_location_1");
                pickupLocation.put("name", "Dummy Warehouse");
            }
            
            // Determine payment type and amount
            String paymentMethod = (String) orderData.get("payment_method");
            String paymentType = "with_cash_collection".equals(paymentMethod) ? "with_cash_collection" : "without_cash_collection";
            int amountCents = "with_cash_collection".equals(paymentMethod) ? 
                (int) Math.round((Double) orderData.get("total_amount") * 100) : 0;
            
            // Build Fincart order request
            Map<String, Object> fincartOrder = buildFincartOrderRequest(orderData, pickupLocation, paymentType, amountCents);
            
            // Create order via Fincart API
            Map<String, Object> response = fincartApiService.createOrder(accessToken, fincartOrder);
            
            // Extract response data
            String fincartOrderId = (String) response.get("orderId");
            String fincartOrderCode = (String) response.get("orderCode");
            String trackingNumber = (String) response.get("trackingNumber");
            String trackURL = (String) response.get("trackURL");
            String courier = (String) response.get("courier");
            
            // Save delivery record
            deliveryRepository.insertDelivery(
                orderId, vendorId, fincartOrderId, fincartOrderCode,
                paymentType, amountCents, (String) orderData.get("service_type"),
                "parcel", (Integer) orderData.get("no_of_items"),
                (String) orderData.get("description"), orderId,
                (String) pickupLocation.get("id")
            );
            
            // Log initial status
            deliveryRepository.insertDeliveryLog(
                fincartOrderId, "pending", "created", null, null,
                trackingNumber, null, courier, null, false, null,
                "Delivery order created successfully"
            );
            
            // Send tracking info to customer
            sendTrackingNotification(orderId, fincartOrderId, trackingNumber, trackURL);
            
            return Map.of(
                "success", true,
                "fincart_order_id", fincartOrderId,
                "tracking_number", trackingNumber,
                "track_url", trackURL,
                "courier", courier
            );
            
        } catch (Exception e) {
            System.err.println("Error creating delivery order: " + e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Create delivery orders for multi-vendor order
     */
    public List<Map<String, Object>> createMultiVendorDeliveries(String orderId, List<Map<String, Object>> vendorOrders) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map<String, Object> vendorOrder : vendorOrders) {
            String vendorId = (String) vendorOrder.get("vendor_id");
            Map<String, Object> result = createDeliveryOrder(orderId, vendorId, vendorOrder);
            result.put("vendor_id", vendorId);
            results.add(result);
        }
        
        return results;
    }

    /**
     * Update delivery status from webhook
     */
    public void updateDeliveryStatus(String fincartOrderId, Map<String, Object> webhookData) {
        try {
            String status = (String) webhookData.get("status");
            String subStatus = (String) webhookData.get("subStatus");
            String rejectionReason = (String) webhookData.get("rejectionReason");
            String supportNote = (String) webhookData.get("supportNote");
            String trackingNumber = (String) webhookData.get("trackingNumber");
            String returnTrackingNumber = (String) webhookData.get("returnTrackingNumber");
            String courier = (String) webhookData.get("courier");
            String courierLogo = (String) webhookData.get("courierLogo");
            Boolean invoiced = (Boolean) webhookData.get("invoiced");
            Long invoicedAt = webhookData.get("invoicedAt") != null ? 
                ((Number) webhookData.get("invoicedAt")).longValue() : null;
            
            // Update delivery record
            deliveryRepository.updateDeliveryStatus(fincartOrderId, status, subStatus, rejectionReason,
                supportNote, trackingNumber, returnTrackingNumber, courier, courierLogo, invoiced, invoicedAt);
            
            // Get delivery details for logging
            Map<String, Object> delivery = deliveryRepository.getDeliveryByFincartOrderId(fincartOrderId);
            if (delivery != null) {
                // Log status change
                deliveryRepository.insertDeliveryLog(
                    (String) delivery.get("id"), status, subStatus, rejectionReason, supportNote,
                    trackingNumber, returnTrackingNumber, courier, courierLogo, invoiced, invoicedAt,
                    "Status updated via webhook"
                );
                
                // Send customer notification
                sendStatusUpdateNotification((String) delivery.get("order_id"), fincartOrderId, status, subStatus);
            }
            
        } catch (Exception e) {
            System.err.println("Error updating delivery status: " + e.getMessage());
        }
    }

    /**
     * Cancel delivery order
     */
    public Map<String, Object> cancelDelivery(String fincartOrderId) {
        try {
            String accessToken = getValidAccessToken();
            List<String> orderIds = Arrays.asList(fincartOrderId);
            
            Map<String, Object> response = fincartApiService.cancelOrder(accessToken, orderIds);
            
            // Update local status
            deliveryRepository.updateDeliveryStatus(fincartOrderId, "cancelled", "cancelled", 
                "Order cancelled by customer", null, null, null, null, null, false, null);
            
            return Map.of("success", true, "response", response);
            
        } catch (Exception e) {
            System.err.println("Error cancelling delivery: " + e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Get delivery tracking information
     */
    public Map<String, Object> getDeliveryTracking(String fincartOrderId) {
        try {
            Map<String, Object> delivery = deliveryRepository.getDeliveryByFincartOrderId(fincartOrderId);
            if (delivery == null) {
                return Map.of("success", false, "error", "Delivery not found");
            }
            
            // Get detailed logs
            List<Map<String, Object>> logs = deliveryRepository.getDeliveryLogs((String) delivery.get("id"));
            
            return Map.of(
                "success", true,
                "delivery", delivery,
                "logs", logs
            );
            
        } catch (Exception e) {
            System.err.println("Error getting delivery tracking: " + e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Get order deliveries
     */
    public List<Map<String, Object>> getOrderDeliveries(String orderId) {
        return deliveryRepository.getDeliveriesByOrderId(orderId);
    }

    /**
     * Sync pickup locations from Fincart
     */
    public void syncPickupLocations() {
        try {
            String accessToken = getValidAccessToken();
            List<Map<String, Object>> locations = fincartApiService.getPickupLocations(accessToken);
            
            for (Map<String, Object> location : locations) {
                String fincartId = (String) location.get("id");
                String name = (String) location.get("name");
                String city = (String) location.get("city");
                String area = (String) location.get("area");
                String address = (String) location.get("address");
                String contactPerson = (String) location.get("contact_person");
                String contactPhone = (String) location.get("contact_phone");
                
                // Check if location exists
                Map<String, Object> existing = deliveryRepository.getPickupLocationByFincartId(fincartId);
                if (existing == null) {
                    // Insert new location (you'll need to map vendor_id somehow)
                    deliveryRepository.insertPickupLocation("00000000-0000-0000-0000-000000000000", 
                        fincartId, name, city, area, address, contactPerson, contactPhone);
                } else {
                    // Update existing
                    deliveryRepository.updatePickupLocation((String) existing.get("id"), name, city, area, 
                        address, contactPerson, contactPhone, true);
                }
            }
            
            System.out.println("Synced " + locations.size() + " pickup locations");
            
        } catch (Exception e) {
            System.err.println("Error syncing pickup locations: " + e.getMessage());
        }
    }

    /**
     * Sync delivery areas from Fincart
     */
    public void syncDeliveryAreas() {
        try {
            String accessToken = getValidAccessToken();
            List<Map<String, Object>> areas = fincartApiService.getCitiesAndAreas(accessToken);
            
            for (Map<String, Object> area : areas) {
                String city = (String) area.get("city");
                String areaName = (String) area.get("area");
                Boolean isAvailable = (Boolean) area.get("is_available");
                
                deliveryRepository.insertDeliveryArea(city, areaName, isAvailable != null ? isAvailable : true);
            }
            
            System.out.println("Synced " + areas.size() + " delivery areas");
            
        } catch (Exception e) {
            System.err.println("Error syncing delivery areas: " + e.getMessage());
        }
    }

    /**
     * Generate delivery labels
     */
    public byte[] generateLabels(List<String> fincartOrderIds, String type) {
        try {
            String accessToken = getValidAccessToken();
            return fincartApiService.generateLabels(accessToken, fincartOrderIds, type);
        } catch (Exception e) {
            System.err.println("Error generating labels: " + e.getMessage());
            throw new RuntimeException("Failed to generate delivery labels", e);
        }
    }

    /**
     * Generate pickup manifest
     */
    public byte[] generateManifest(List<String> fincartOrderIds) {
        try {
            String accessToken = getValidAccessToken();
            return fincartApiService.generateManifest(accessToken, fincartOrderIds);
        } catch (Exception e) {
            System.err.println("Error generating manifest: " + e.getMessage());
            throw new RuntimeException("Failed to generate pickup manifest", e);
        }
    }

    // Helper Methods

    private String getValidAccessToken() {
        // Check if we have a valid token in database
        Map<String, Object> config = deliveryRepository.getFincartConfig();
        if (config != null) {
            LocalDateTime expiresAt = ((java.sql.Timestamp) config.get("token_expires_at")).toLocalDateTime();
            if (expiresAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
                return (String) config.get("access_token");
            }
        }
        
        // Get new token
        String newToken = fincartApiService.authenticate();
        long expiresAt = LocalDateTime.now().plusHours(24).toEpochSecond(ZoneOffset.UTC);
        deliveryRepository.updateFincartToken(newToken, expiresAt);
        
        return newToken;
    }

    private Map<String, Object> buildFincartOrderRequest(Map<String, Object> orderData, 
                                                       Map<String, Object> pickupLocation,
                                                       String paymentType, int amountCents) {
        Map<String, Object> request = new HashMap<>();
        
        // Basic order info
        request.put("instaShip", true);
        request.put("source", "s2s");
        request.put("orderType", "delivery");
        
        // Customer details
        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("name", orderData.get("customer_name"));
        customerDetails.put("phone", orderData.get("customer_phone"));
        customerDetails.put("backupPhone", orderData.get("backup_phone"));
        customerDetails.put("shippingNotes", orderData.get("shipping_notes"));
        
        // Address
        Map<String, Object> address = new HashMap<>();
        address.put("addressLine", orderData.get("address_line"));
        address.put("city", orderData.get("city"));
        address.put("area", orderData.get("area"));
        address.put("landmark", orderData.get("landmark"));
        customerDetails.put("address", address);
        
        request.put("customerDetails", customerDetails);
        
        // Order details
        Map<String, Object> orderDetails = new HashMap<>();
        orderDetails.put("paymentType", paymentType);
        orderDetails.put("pickupLocationId", pickupLocation.get("fincart_location_id"));
        orderDetails.put("packageType", "parcel");
        orderDetails.put("amount", amountCents);
        orderDetails.put("noOfItems", orderData.get("no_of_items"));
        orderDetails.put("description", orderData.get("description"));
        orderDetails.put("openPackageAllowed", true);
        orderDetails.put("referenceNumber", orderData.get("reference_number"));
        orderDetails.put("serviceType", orderData.get("service_type"));
        
        request.put("orderDetails", orderDetails);
        
        return request;
    }

    private void sendTrackingNotification(String orderId, String fincartOrderId, String trackingNumber, String trackURL) {
        try {
            // Send email notification with tracking info
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("order_id", orderId);
            emailData.put("tracking_number", trackingNumber);
            emailData.put("track_url", trackURL);
            emailData.put("fincart_order_id", fincartOrderId);
            
            orderEmailService.sendDeliveryTrackingEmail(emailData);
            
        } catch (Exception e) {
            System.err.println("Error sending tracking notification: " + e.getMessage());
        }
    }

    private void sendStatusUpdateNotification(String orderId, String fincartOrderId, String status, String subStatus) {
        try {
            // Send email notification for status update
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("order_id", orderId);
            emailData.put("fincart_order_id", fincartOrderId);
            emailData.put("status", status);
            emailData.put("sub_status", subStatus);
            
            orderEmailService.sendDeliveryStatusUpdateEmail(emailData);
            
        } catch (Exception e) {
            System.err.println("Error sending status update notification: " + e.getMessage());
        }
    }
}
