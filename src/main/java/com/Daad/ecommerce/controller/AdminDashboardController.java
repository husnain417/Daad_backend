package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
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
        if (val == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate is required"));
        double rate;
        try { rate = Double.parseDouble(val.toString()); } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate must be a number")); }
        if (rate < 0 || rate > 100) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "commissionRate must be between 0 and 100"));
        int rows = adminRepository.setGlobalCommissionRate(rate);
        return ResponseEntity.ok(Map.of("success", true, "updatedRows", rows, "commissionRate", rate));
    }
}
