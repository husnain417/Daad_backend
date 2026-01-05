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
@RequestMapping("/api/admin/vouchers")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class VoucherAdminController {

    private final VoucherRepository voucherRepository;

    public VoucherAdminController(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    @GetMapping
    public ResponseEntity<?> listVouchers() {
        List<Voucher> vouchers = voucherRepository.findAll();
        return ResponseEntity.ok(Map.of("success", true, "count", vouchers.size(), "data", vouchers));
    }

    @PostMapping
    public ResponseEntity<?> createVoucher(@RequestBody Map<String, Object> body) {
        try {
            String code = (String) body.get("code");
            String type = (String) body.get("type"); // percentage | fixed
            Object valueObj = body.get("value");
            Object minOrderObj = body.get("minimumOrder");
            String applicableFor = body.get("applicableFor") != null ? body.get("applicableFor").toString() : "all";
            String validFromStr = (String) body.get("validFrom");
            String validUntilStr = (String) body.get("validUntil");
            @SuppressWarnings("unchecked")
            List<String> applicableItemIds = body.get("applicableItemIds") != null ? 
                (List<String>) body.get("applicableItemIds") : null;

            if (code == null || code.isBlank() || type == null || valueObj == null || validFromStr == null || validUntilStr == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "code, type, value, validFrom and validUntil are required"));
            }

            double value = Double.parseDouble(valueObj.toString());
            double minOrder = minOrderObj != null ? Double.parseDouble(minOrderObj.toString()) : 0.0;

            Voucher v = new Voucher();
            v.setCode(code.trim().toUpperCase());
            v.setType(type.toLowerCase());
            v.setValue(value);
            v.setMinimumOrder(minOrder);

            if (body.get("maximumDiscount") != null) {
                v.setMaximumDiscount(Double.parseDouble(body.get("maximumDiscount").toString()));
            }
            if (body.get("usageLimit") != null) {
                v.setUsageLimit(Integer.parseInt(body.get("usageLimit").toString()));
            }
            v.setApplicableFor(applicableFor);
            
            // Parse dates - handle both with and without timezone
            Instant now = Instant.now();
            try {
                // Try parsing as LocalDateTime first (datetime-local format from frontend)
                // datetime-local inputs are in the user's local timezone, so we need to interpret them
                // as local time and convert to UTC
                LocalDateTime validFromLocal = LocalDateTime.parse(validFromStr);
                LocalDateTime validUntilLocal = LocalDateTime.parse(validUntilStr);
                
                // Get the server's default timezone (or use UTC if not available)
                java.time.ZoneId serverZone = java.time.ZoneId.systemDefault();
                
                // Convert LocalDateTime to Instant using server timezone, then to UTC
                Instant validFromInstant = validFromLocal.atZone(serverZone).toInstant();
                Instant validUntilInstant = validUntilLocal.atZone(serverZone).toInstant();
                
                // If validFrom is in the past, set it to now (voucher should be valid immediately)
                if (validFromInstant.isBefore(now)) {
                    System.out.println("Warning: validFrom is in the past, setting to now");
                    validFromInstant = now;
                }
                
                v.setValidFrom(validFromInstant);
                v.setValidUntil(validUntilInstant);
                
                System.out.println("Parsed dates (server timezone: " + serverZone + "):");
                System.out.println("  validFrom (local): " + validFromLocal + " -> UTC: " + validFromInstant);
                System.out.println("  validUntil (local): " + validUntilLocal + " -> UTC: " + validUntilInstant);
            } catch (Exception e) {
                // If parsing fails, try with ISO format (includes timezone)
                try {
                    Instant validFromInstant = java.time.Instant.parse(validFromStr);
                    Instant validUntilInstant = java.time.Instant.parse(validUntilStr);
                    
                    // If validFrom is in the past, set it to now
                    if (validFromInstant.isBefore(now)) {
                        System.out.println("Warning: validFrom is in the past, setting to now");
                        validFromInstant = now;
                    }
                    
                    v.setValidFrom(validFromInstant);
                    v.setValidUntil(validUntilInstant);
                } catch (Exception e2) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid date format. Expected format: YYYY-MM-DDTHH:mm (e.g., 2026-01-05T13:25) or ISO-8601 with timezone"
                    ));
                }
            }
            
            // Ensure voucher is set as active
            v.setActive(true);
            
            // Log for debugging
            System.out.println("Creating voucher with isActive: " + v.isActive());
            System.out.println("Valid from: " + v.getValidFrom());
            System.out.println("Valid until: " + v.getValidUntil());

            String adminId = SecurityUtils.currentUserId();
            v.setCreatedBy(adminId);

            voucherRepository.insert(v);

            // Add applicable items if provided
            if (applicableItemIds != null && !applicableItemIds.isEmpty() && !"all".equalsIgnoreCase(applicableFor)) {
                voucherRepository.addApplicableItems(v.getId(), applicableItemIds);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Voucher created");
            resp.put("data", v);
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Failed to create voucher: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{voucherId}/deactivate")
    public ResponseEntity<?> deactivateVoucher(@PathVariable String voucherId) {
        voucherRepository.deactivate(voucherId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Voucher deactivated"));
    }

    @DeleteMapping("/{voucherId}")
    public ResponseEntity<?> deleteVoucher(@PathVariable String voucherId) {
        voucherRepository.delete(voucherId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Voucher deleted"));
    }
}


