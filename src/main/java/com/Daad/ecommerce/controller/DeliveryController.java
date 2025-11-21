package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.service.DeliveryService;
import com.Daad.ecommerce.repository.DeliveryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/delivery")
@CrossOrigin(origins = "*")
public class DeliveryController {

    @Autowired
    private DeliveryService deliveryService;
    
    @Autowired
    private DeliveryRepository deliveryRepository;

    /**
     * Get delivery tracking for customer
     */
    @GetMapping("/track/{fincartOrderId}")
    public ResponseEntity<Map<String, Object>> trackDelivery(@PathVariable String fincartOrderId) {
        try {
            Map<String, Object> result = deliveryService.getDeliveryTracking(fincartOrderId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error tracking delivery for order ID: {}", fincartOrderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get all deliveries for an order
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Map<String, Object>>> getOrderDeliveries(@PathVariable String orderId) {
        try {
            List<Map<String, Object>> deliveries = deliveryService.getOrderDeliveries(orderId);
            return ResponseEntity.ok(deliveries);
        } catch (Exception e) {
            log.error("Error getting deliveries for order ID: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    /**
     * Cancel delivery order
     */
    @PostMapping("/cancel/{fincartOrderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> cancelDelivery(@PathVariable String fincartOrderId) {
        try {
            Map<String, Object> result = deliveryService.cancelDelivery(fincartOrderId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error canceling delivery for order ID: {}", fincartOrderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Generate delivery labels
     */
    @PostMapping("/labels")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<byte[]> generateLabels(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> orderIds = (List<String>) request.get("order_ids");
            String type = (String) request.getOrDefault("type", "paper");
            
            byte[] labels = deliveryService.generateLabels(orderIds, type);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "delivery_labels.pdf");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(labels);
                
        } catch (Exception e) {
            log.error("Error generating labels for orders: {}", request.get("order_ids"), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Generate pickup manifest
     */
    @PostMapping("/manifest")
    @PreAuthorize("hasRole('ADMIN') or hasRole('VENDOR')")
    public ResponseEntity<byte[]> generateManifest(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> orderIds = (List<String>) request.get("order_ids");
            
            byte[] manifest = deliveryService.generateManifest(orderIds);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "pickup_manifest.pdf");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(manifest);
                
        } catch (Exception e) {
            log.error("Error generating manifest for orders: {}", request.get("order_ids"), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Admin endpoints

    /**
     * Get all deliveries with filters
     */
    @GetMapping("/admin/deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllDeliveries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendorId) {
        try {
            List<Map<String, Object>> deliveries;
            
            if (status != null) {
                deliveries = deliveryRepository.getDeliveriesByStatus(status);
            } else if (vendorId != null) {
                deliveries = deliveryRepository.getDeliveriesByVendorId(vendorId);
            } else {
                deliveries = deliveryRepository.getAllPickupLocations(); // This should be getAllDeliveries
            }
            
            return ResponseEntity.ok(deliveries);
        } catch (Exception e) {
            log.error("Error getting all deliveries with filters: status={}, vendorId={}", status, vendorId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    /**
     * Get delivery statistics
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDeliveryStats() {
        try {
            // Get counts by status
            List<Map<String, Object>> pendingDeliveries = deliveryRepository.getDeliveriesByStatus("pending");
            List<Map<String, Object>> processingDeliveries = deliveryRepository.getDeliveriesByStatus("processing");
            List<Map<String, Object>> completedDeliveries = deliveryRepository.getDeliveriesByStatus("successful");
            List<Map<String, Object>> failedDeliveries = deliveryRepository.getDeliveriesByStatus("unsuccessful");
            
            Map<String, Object> stats = Map.of(
                "pending", pendingDeliveries.size(),
                "processing", processingDeliveries.size(),
                "completed", completedDeliveries.size(),
                "failed", failedDeliveries.size(),
                "total", pendingDeliveries.size() + processingDeliveries.size() + 
                        completedDeliveries.size() + failedDeliveries.size()
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting delivery statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Sync pickup locations
     */
    @PostMapping("/admin/sync/locations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> syncPickupLocations() {
        try {
            deliveryService.syncPickupLocations();
            return ResponseEntity.ok(Map.of("success", true, "message", "Pickup locations synced"));
        } catch (Exception e) {
            log.error("Error syncing pickup locations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Sync delivery areas
     */
    @PostMapping("/admin/sync/areas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> syncDeliveryAreas() {
        try {
            deliveryService.syncDeliveryAreas();
            return ResponseEntity.ok(Map.of("success", true, "message", "Delivery areas synced"));
        } catch (Exception e) {
            log.error("Error syncing delivery areas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get available delivery areas
     */
    @GetMapping("/areas")
    public ResponseEntity<List<Map<String, Object>>> getDeliveryAreas(
            @RequestParam(required = false) String city) {
        try {
            List<Map<String, Object>> areas;
            if (city != null) {
                areas = deliveryRepository.getAreasByCity(city);
            } else {
                areas = deliveryRepository.getAvailableDeliveryAreas();
            }
            return ResponseEntity.ok(areas);
        } catch (Exception e) {
            log.error("Error getting delivery areas for city: {}", city, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    /**
     * Get pickup locations
     */
    @GetMapping("/pickup-locations")
    public ResponseEntity<List<Map<String, Object>>> getPickupLocations() {
        try {
            List<Map<String, Object>> locations = deliveryRepository.getAllPickupLocations();
            return ResponseEntity.ok(locations);
        } catch (Exception e) {
            log.error("Error getting pickup locations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }
}
