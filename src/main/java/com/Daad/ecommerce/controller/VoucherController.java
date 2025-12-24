package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.model.Voucher;
import com.Daad.ecommerce.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vouchers")
@CrossOrigin(origins = "*")
public class VoucherController {

    private final VoucherRepository voucherRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public VoucherController(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    /**
     * Get active vouchers applicable to a specific product
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getVouchersForProduct(@PathVariable String productId) {
        try {
            Instant now = Instant.now();
            List<Voucher> allVouchers = voucherRepository.findAll();
            
            // Filter active vouchers that apply to this product
            List<Voucher> applicableVouchers = allVouchers.stream()
                .filter(v -> v.isActive() 
                    && v.getValidFrom().isBefore(now) 
                    && v.getValidUntil().isAfter(now))
                .filter(v -> {
                    // Check if voucher applies to all products
                    if ("all".equalsIgnoreCase(v.getApplicableFor())) {
                        return true;
                    }
                    // Check if voucher applies to this specific product
                    if ("product".equalsIgnoreCase(v.getApplicableFor())) {
                        return isVoucherApplicableToItem(v.getId(), productId);
                    }
                    return false;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", applicableVouchers.size(),
                "data", applicableVouchers
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching vouchers: " + e.getMessage()
            ));
        }
    }

    /**
     * Get active vouchers applicable to a specific vendor
     */
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<?> getVouchersForVendor(@PathVariable String vendorId) {
        try {
            Instant now = Instant.now();
            List<Voucher> allVouchers = voucherRepository.findAll();
            
            // Filter active vouchers that apply to this vendor
            List<Voucher> applicableVouchers = allVouchers.stream()
                .filter(v -> v.isActive() 
                    && v.getValidFrom().isBefore(now) 
                    && v.getValidUntil().isAfter(now))
                .filter(v -> {
                    // Check if voucher applies to all
                    if ("all".equalsIgnoreCase(v.getApplicableFor())) {
                        return true;
                    }
                    // Check if voucher applies to this specific vendor
                    if ("vendor".equalsIgnoreCase(v.getApplicableFor())) {
                        return isVoucherApplicableToItem(v.getId(), vendorId);
                    }
                    return false;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", applicableVouchers.size(),
                "data", applicableVouchers
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error fetching vouchers: " + e.getMessage()
            ));
        }
    }

    /**
     * Check if a voucher is applicable to a specific item (product or vendor)
     */
    private boolean isVoucherApplicableToItem(String voucherId, String itemId) {
        try {
            String sql = "SELECT COUNT(*) FROM voucher_applicable_items WHERE voucher_id = ?::uuid AND applicable_id = ?::uuid";
            Integer count = jdbcTemplate.queryForObject(
                sql, Integer.class, UUID.fromString(voucherId), UUID.fromString(itemId)
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

