package com.Daad.ecommerce.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/brands")
@CrossOrigin(origins = "*")
public class BrandController {

    // The current schema does not have a dedicated brands table.
    // Return a static list or derive from products' names if needed.
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getBrands() {
        List<Map<String, Object>> brands = java.util.List.of();
        return ResponseEntity.ok(Map.of("success", true, "count", brands.size(), "data", brands));
    }
}


