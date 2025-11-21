package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/vendor/inventory")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('VENDOR')")
public class VendorInventoryController {

    @Autowired private ProductRepository productRepository;
    @Autowired private VendorRepository vendorRepository;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getVendorInventory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        String userId = SecurityUtils.currentUserId();
        var vendorOpt = vendorRepository.findByUserId(userId);
        if (vendorOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vendor profile not found"));
        String vendorId = vendorOpt.get().getId();

        List<Product> products = productRepository.findByVendor(vendorId);
        // pagination
        int total = products.size();
        int start = Math.max(0, (page - 1) * limit);
        int end = Math.min(total, start + limit);
        List<Product> paginated = start < end ? products.subList(start, end) : new ArrayList<>();

        // compute stock alerts per product
        List<Map<String, Object>> data = new ArrayList<>();
        for (Product p : paginated) {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", p.getId());
            m.put("name", p.getName());
            m.put("totalStock", p.getTotalStock());
            m.put("inStock", p.getTotalStock() != null && p.getTotalStock() > 0);
            m.put("lowStockItems", productRepository.getLowStockItems(p.getId()));
            data.add(m);
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("pages", (int) Math.ceil((double) total / limit));
        return ResponseEntity.ok(Map.of(
            "success", true,
            "count", data.size(),
            "total", total,
            "pagination", pagination,
            "data", data
        ));
    }
}


