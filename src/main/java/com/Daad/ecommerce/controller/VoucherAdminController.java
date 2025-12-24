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
            v.setValidFrom(LocalDateTime.parse(validFromStr).toInstant(ZoneOffset.UTC));
            v.setValidUntil(LocalDateTime.parse(validUntilStr).toInstant(ZoneOffset.UTC));
            v.setActive(true);

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


