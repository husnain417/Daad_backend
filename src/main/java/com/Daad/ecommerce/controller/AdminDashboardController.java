package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.repository.AdminRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@Slf4j
public class AdminDashboardController {

    @Autowired
    private AdminRepository adminRepository;

    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboardOverview() {
        try {
            Map<String, Object> stats = adminRepository.getDashboardStats();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", stats
            ));
        } catch (Exception e) {
            log.error("Error fetching dashboard stats", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching dashboard stats: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/vendors/sales-ranking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getVendorSalesRanking() {
        try {
            List<Map<String, Object>> vendorRankings = adminRepository.getVendorSalesRanking();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", vendorRankings
            ));
        } catch (Exception e) {
            log.error("Error fetching vendor sales ranking", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching vendor sales ranking: " + e.getMessage()
            ));
        }
    }

    // Commission rate APIs
    @GetMapping("/commission-rate")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<Map<String, Object>> getCommissionRate() {
        double rate = adminRepository.getGlobalCommissionRate();
        return ResponseEntity.ok(Map.of("success", true, "commissionRate", rate));
    }

    @PostMapping("/commission-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> setCommissionRate(@RequestBody Map<String, Object> body) {
        Object val = body.get("commissionRate");
        if (val == null) {
            log.error("Commission rate validation failed: commissionRate is required");
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate is required"));
        }
        double rate;
        try { 
            rate = Double.parseDouble(val.toString()); 
        } catch (Exception e) { 
            log.error("Commission rate validation failed: commissionRate must be a number", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate must be a number")); 
        }
        if (rate < 0 || rate > 100) {
            log.error("Commission rate validation failed: rate {} is out of range", rate);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate must be between 0 and 100"));
        }
        int rows = adminRepository.setGlobalCommissionRate(rate);
        return ResponseEntity.ok(Map.of("success", true, "updatedRows", rows, "commissionRate", rate));
    }

    // Get all vendors with commission info for admin commission management
    @GetMapping("/vendors/commission")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getVendorsWithCommission() {
        try {
            System.out.println("Getting vendors with commission info...");
            List<Map<String, Object>> vendors = adminRepository.getVendorsWithCommissionInfo();
            System.out.println("Found " + vendors.size() + " vendors");
            return ResponseEntity.ok(Map.of("success", true, "count", vendors.size(), "data", vendors));
        } catch (Exception e) {
            log.error("Error in getVendorsWithCommission", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to fetch vendors", "error", e.getMessage()));
        }
    }

    // Update individual vendor commission rate
    @PutMapping("/vendors/{vendorId}/commission")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateVendorCommission(
            @PathVariable String vendorId, 
            @RequestBody Map<String, Object> body) {
        try {
            Object val = body.get("commissionRate");
            if (val == null) {
                log.error("Vendor commission update failed for vendorId {}: commissionRate is required", vendorId);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate is required"));
            }

            double rate;
            try { 
                rate = Double.parseDouble(val.toString()); 
            } catch (Exception e) { 
                log.error("Vendor commission update failed for vendorId {}: commissionRate must be a number", vendorId, e);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate must be a number")); 
            }

            if (rate < 0 || rate > 100) {
                log.error("Vendor commission update failed for vendorId {}: rate {} is out of range", vendorId, rate);
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate must be between 0 and 100"));
            }

            int rows = adminRepository.updateVendorCommission(vendorId, rate);
            if (rows > 0) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Commission rate updated successfully", "vendorId", vendorId, "commissionRate", rate));
            } else {
                log.error("Vendor commission update failed: Vendor {} not found", vendorId);
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vendor not found"));
            }
        } catch (Exception e) {
            log.error("Failed to update commission rate for vendorId {}", vendorId, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to update commission rate", "error", e.getMessage()));
        }
    }

    // Filtered Orders API for Admin Dashboard
    @GetMapping("/orders/filtered")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFilteredOrders(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String vendorName,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            List<Map<String, Object>> orders = adminRepository.findOrdersWithFilters(
                productName, startDate, endDate, orderStatus, vendorName, page, limit);
            
            int total = adminRepository.countOrdersWithFilters(
                productName, startDate, endDate, orderStatus, vendorName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", orders.size(),
                "total", total,
                "pagination", Map.of(
                    "page", page,
                    "pages", (int) Math.ceil((double) total / limit)
                ),
                "data", orders
            ));
            
        } catch (Exception e) {
            log.error("Error fetching filtered orders", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching filtered orders: " + e.getMessage()
            ));
        }
    }

    // Filtered Products API for Admin Dashboard
    @GetMapping("/products/filtered")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFilteredProducts(
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String productStatus,
            @RequestParam(required = false) String vendorName,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            List<Map<String, Object>> products = adminRepository.findProductsWithFilters(
                productName, startDate, endDate, productStatus, vendorName, page, limit);
            
            int total = adminRepository.countProductsWithFilters(
                productName, startDate, endDate, productStatus, vendorName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", products.size(),
                "total", total,
                "pagination", Map.of(
                    "page", page,
                    "pages", (int) Math.ceil((double) total / limit)
                ),
                "data", products
            ));
            
        } catch (Exception e) {
            log.error("Error fetching filtered products", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching filtered products: " + e.getMessage()
            ));
        }
    }
}
