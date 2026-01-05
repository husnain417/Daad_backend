package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.model.Voucher;
import com.Daad.ecommerce.repository.VoucherRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendor/vouchers")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('VENDOR')")
public class VendorVoucherController {

    private final VoucherRepository voucherRepository;

    public VendorVoucherController(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    @GetMapping
    public ResponseEntity<?> listVouchers() {
        try {
            String vendorUserId = SecurityUtils.currentUserId();
            if (vendorUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Get vouchers created by this vendor
            List<Voucher> vouchers = voucherRepository.findByCreatedBy(vendorUserId);
            return ResponseEntity.ok(Map.of("success", true, "count", vouchers.size(), "data", vouchers));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error fetching vouchers: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createVoucher(@RequestBody Map<String, Object> body) {
        // Vendors are not allowed to create vouchers - only admins can
        // Vendors should use product discounts instead (via /api/products/{productId}/discount)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("success", false, "message", "Vendors cannot create vouchers. Please use product discounts instead."));
    }

    @PostMapping("/{voucherId}/deactivate")
    public ResponseEntity<?> deactivateVoucher(@PathVariable String voucherId) {
        try {
            String vendorUserId = SecurityUtils.currentUserId();
            if (vendorUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Verify the voucher belongs to this vendor
            List<Voucher> vendorVouchers = voucherRepository.findByCreatedBy(vendorUserId);
            boolean ownsVoucher = vendorVouchers.stream().anyMatch(v -> v.getId().equals(voucherId));
            
            if (!ownsVoucher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You don't have permission to modify this voucher"));
            }

            voucherRepository.deactivate(voucherId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Voucher deactivated"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error deactivating voucher: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{voucherId}")
    public ResponseEntity<?> deleteVoucher(@PathVariable String voucherId) {
        try {
            String vendorUserId = SecurityUtils.currentUserId();
            if (vendorUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Verify the voucher belongs to this vendor
            List<Voucher> vendorVouchers = voucherRepository.findByCreatedBy(vendorUserId);
            boolean ownsVoucher = vendorVouchers.stream().anyMatch(v -> v.getId().equals(voucherId));
            
            if (!ownsVoucher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "You don't have permission to delete this voucher"));
            }

            voucherRepository.delete(voucherId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Voucher deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Error deleting voucher: " + e.getMessage()));
        }
    }
}

