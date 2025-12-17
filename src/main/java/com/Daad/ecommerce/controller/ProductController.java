package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.CreateProductRequest;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.*;
import com.Daad.ecommerce.service.NotificationService;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.service.BackblazeService;
import com.Daad.ecommerce.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import com.Daad.ecommerce.model.Vendor;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private VendorRepository vendorRepository;
    
    @Autowired
    private BackblazeService backblazeService;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;
    
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Autowired
    private ProductService productService;

    // Get all products with filtering
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "true") Boolean inStock,
            @RequestParam(defaultValue = "-createdAt") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(defaultValue = "false") Boolean showAllStatuses,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") Boolean light) {
        
        try {
            // Fast path: lightweight response should not trigger full product load
            if (Boolean.TRUE.equals(light)) {
                // Resolve categoryId/includeChildren up front
                String resolvedCategoryId = null;
                boolean includeChildren = false;
                if ((category != null && !category.isEmpty()) || (subcategory != null && !subcategory.isEmpty())) {
                    if (subcategory == null && category != null && category.contains("-")) {
                        String[] parts = category.split("-", 2);
                        if (parts.length == 2) {
                            var subOpt = categoryRepository.findSubcategoryByParentSlugAndChildSlug(parts[0], parts[1]);
                            if (subOpt.isEmpty()) {
                                subOpt = categoryRepository.findSubcategoryByParentAndName(parts[0], parts[1]);
                            }
                            if (subOpt.isPresent()) {
                                resolvedCategoryId = subOpt.get().getId();
                                includeChildren = false;
                            }
                        }
                    }
                    if (resolvedCategoryId == null && category != null && !category.isEmpty()) {
                        var bySlug = categoryRepository.findParentCategoryBySlug(category);
                        if (bySlug.isPresent()) {
                            resolvedCategoryId = bySlug.get().getId();
                            includeChildren = true;
                        } else {
                            var byName = categoryRepository.findParentCategoryByName(category);
                            if (byName.isPresent()) {
                                resolvedCategoryId = byName.get().getId();
                                includeChildren = true;
                            } else if (category.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                                resolvedCategoryId = category;
                                includeChildren = true;
                            } else {
                                var leaf = categoryRepository.findBySlug(category);
                                if (leaf.isPresent()) {
                                    resolvedCategoryId = leaf.get().getId();
                                    includeChildren = false;
                                }
                            }
                        }
                    }
                    if (subcategory != null && !subcategory.isEmpty()) {
                        var sub = categoryRepository.findSubcategoryByParentAndChildFlexible(category != null ? category : "", subcategory);
                        if (sub.isPresent()) {
                            resolvedCategoryId = sub.get().getId();
                            includeChildren = false;
                        }
                    }
                }

                // Normalize gender
                String normalizedGender = null;
                if (gender != null && !gender.isEmpty()) {
                    if (gender.equalsIgnoreCase("men") || gender.equalsIgnoreCase("male")) normalizedGender = "Men";
                    else if (gender.equalsIgnoreCase("women") || gender.equalsIgnoreCase("female")) normalizedGender = "Women";
                    else if (gender.equalsIgnoreCase("unisex")) normalizedGender = "Unisex";
                }

                int effOffset = (offset != null) ? Math.max(0, offset) : (page - 1) * limit;
                String effStatus = (status != null && !status.isBlank()) ? status : null;

                List<Map<String, Object>> items = productRepository.findLightweight(
                        resolvedCategoryId,
                        includeChildren,
                        normalizedGender,
                        minPrice,
                        maxPrice,
                        inStock,
                        effStatus,
                        sort,
                        limit,
                        effOffset
                );

                int total = items.size(); // lightweight fast path returns only page items; omit expensive count
                Map<String, Object> pagination = new HashMap<>();
                pagination.put("page", page);
                pagination.put("pages", 1);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("count", items.size());
                response.put("total", total);
                response.put("pagination", pagination);
                response.put("data", items);
                return ResponseEntity.ok(response);
            }
            // Build filter - only show active products by default
            List<Product> allProducts = productRepository.findAll();
            List<Product> filteredProducts = allProducts.stream()
                    .filter(product -> product.getIsActive())
                    .collect(Collectors.toList());
            
            // If explicit status filter provided, use it (case-insensitive)
            if (status != null && !status.isBlank()) {
                String desired = status.toLowerCase();
                filteredProducts = filteredProducts.stream()
                        .filter(p -> p.getStatus() != null && p.getStatus().toLowerCase().equals(desired))
                        .collect(Collectors.toList());
            } else if (!showAllStatuses) {
                // Default: show only approved when showAllStatuses is false
                filteredProducts = filteredProducts.stream()
                        .filter(p -> "approved".equalsIgnoreCase(p.getStatus()))
                        .collect(Collectors.toList());
            }
            
            // Apply category & subcategory filters
            // Supports:
            // - category as UUID
            // - category as slug or name
            // - compound "parent-sub" like "men-tshirt" meaning parent category "men" and subcategory "tshirt"
            if ((category != null && !category.isEmpty()) || (subcategory != null && !subcategory.isEmpty())) {
                String resolvedCategoryId = null;

            if (category != null && !category.isEmpty()) {
                    String normalized = category.trim();

                    // Handle compound pattern parent-sub (e.g., men-tshirts)
                    if (subcategory == null && normalized.contains("-")) {
                        String[] parts = normalized.split("-", 2);
                        if (parts.length == 2) {
                            String parentPart = parts[0];
                            String subPart = parts[1];
                            // Prefer slug-based resolution first; fall back to name-based
                            Optional<Product.Category> subOpt = categoryRepository.findSubcategoryByParentSlugAndChildSlug(parentPart, subPart);
                            if (subOpt.isEmpty()) {
                                subOpt = categoryRepository.findSubcategoryByParentAndName(parentPart, subPart);
                            }
                            if (subOpt.isPresent()) {
                                resolvedCategoryId = subOpt.get().getId();
                            }
                        }
                    }

                    // If not resolved by compound, try UUID directly
                    if (resolvedCategoryId == null && normalized.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                        resolvedCategoryId = normalized;
                    }

                    // If still not resolved, try slug, then name (for parent or leaf categories)
                    if (resolvedCategoryId == null) {
                        Optional<Product.Category> bySlug = categoryRepository.findBySlug(normalized);
                        if (bySlug.isPresent()) {
                            resolvedCategoryId = bySlug.get().getId();
                } else {
                            Optional<Product.Category> byName = categoryRepository.findByName(normalized);
                            if (byName.isPresent()) {
                                resolvedCategoryId = byName.get().getId();
                            }
                        }
                    }
                }

                // Explicit subcategory param takes precedence if provided
                if (subcategory != null && !subcategory.isEmpty()) {
                    String parentForSub = category != null ? category : "";
                    Optional<Product.Category> subOpt = categoryRepository.findSubcategoryByParentAndChildFlexible(parentForSub, subcategory);
                    if (subOpt.isPresent()) {
                        resolvedCategoryId = subOpt.get().getId();
                    }
                }

                if (resolvedCategoryId != null) {
                    String finalResolvedCategoryId = resolvedCategoryId;
                        filteredProducts = filteredProducts.stream()
                            .filter(product -> {
                                if (product.getCategory() == null) return false;
                                String catId = product.getCategory().getId();
                                String parentId = product.getCategory().getParentCategoryId();
                                // Match exact category OR any subcategory whose parent is the resolved category
                                return (finalResolvedCategoryId.equals(catId)) ||
                                       (parentId != null && finalResolvedCategoryId.equals(parentId));
                            })
                                .collect(Collectors.toList());
                }
            }
            
            // Apply gender filter (also accept common shorthands)
            if (gender != null && !gender.isEmpty()) {
                String desiredGender = gender.trim();
                if (desiredGender.equalsIgnoreCase("men") || desiredGender.equalsIgnoreCase("male")) {
                    desiredGender = "Men";
                } else if (desiredGender.equalsIgnoreCase("women") || desiredGender.equalsIgnoreCase("female")) {
                    desiredGender = "Women";
                } else if (desiredGender.equalsIgnoreCase("unisex")) {
                    desiredGender = "Unisex";
                }
                String finalDesiredGender = desiredGender;
                filteredProducts = filteredProducts.stream()
                        .filter(product -> product.getGender() != null && product.getGender().equalsIgnoreCase(finalDesiredGender))
                        .collect(Collectors.toList());
            }
            
            // Apply color filter (check if any color in inventory matches)
            if (color != null && !color.isEmpty()) {
                filteredProducts = filteredProducts.stream()
                        .filter(product -> product.getColorInventories().stream()
                                .anyMatch(colorInv -> colorInv.getColor().equalsIgnoreCase(color)))
                        .collect(Collectors.toList());
            }
            
            // Apply price filter
            if (minPrice != null || maxPrice != null) {
                filteredProducts = filteredProducts.stream()
                        .filter(product -> {
                            if (minPrice != null && product.getPrice().compareTo(minPrice) < 0) {
                                return false;
                            }
                            if (maxPrice != null && product.getPrice().compareTo(maxPrice) > 0) {
                                return false;
                            }
                            return true;
                        })
                        .collect(Collectors.toList());
            }
            
            // Check for in-stock items
            if (inStock) {
                filteredProducts = filteredProducts.stream()
                        .filter(product -> product.getTotalStock() > 0)
                        .collect(Collectors.toList());
            }
            
            // Apply sorting
            if (sort.equals("-createdAt")) {
                filteredProducts.sort((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()));
            } else if (sort.equals("createdAt")) {
                filteredProducts.sort((p1, p2) -> p1.getCreatedAt().compareTo(p2.getCreatedAt()));
            } else if (sort.equals("price")) {
                filteredProducts.sort((p1, p2) -> p1.getPrice().compareTo(p2.getPrice()));
            } else if (sort.equals("-price")) {
                filteredProducts.sort((p1, p2) -> p2.getPrice().compareTo(p1.getPrice()));
            }
            
            // Apply pagination (support offset or page)
            int total = filteredProducts.size();
            int startIndex = (offset != null) ? Math.max(0, offset) : (page - 1) * limit;
            int endIndex = Math.min(startIndex + limit, total);
            
            List<Product> paginatedProducts = startIndex < endIndex ? filteredProducts.subList(startIndex, endIndex) : new ArrayList<>();
            
            // Create pagination response
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("pages", (int) Math.ceil((double) total / limit));
            
            // Create response (support lightweight payload)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", paginatedProducts.size());
            response.put("total", total);
            response.put("pagination", pagination);
            if (light) {
                // Resolve categoryId and includeChildren for parent slug handling
                String resolvedCategoryId = null;
                boolean includeChildren = false;
                if ((category != null && !category.isEmpty()) || (subcategory != null && !subcategory.isEmpty())) {
                    // Compound handling
                    if (subcategory == null && category != null && category.contains("-")) {
                        String[] parts = category.split("-", 2);
                        if (parts.length == 2) {
                            var subOpt = categoryRepository.findSubcategoryByParentSlugAndChildSlug(parts[0], parts[1]);
                            if (subOpt.isEmpty()) {
                                subOpt = categoryRepository.findSubcategoryByParentAndName(parts[0], parts[1]);
                            }
                            if (subOpt.isPresent()) {
                                resolvedCategoryId = subOpt.get().getId();
                                includeChildren = false;
                            }
                        }
                    }
                    if (resolvedCategoryId == null && category != null && !category.isEmpty()) {
                        // Parent category (men) should include children
                        var bySlug = categoryRepository.findParentCategoryBySlug(category);
                        if (bySlug.isPresent()) {
                            resolvedCategoryId = bySlug.get().getId();
                            includeChildren = true;
                        } else {
                            var byName = categoryRepository.findParentCategoryByName(category);
                            if (byName.isPresent()) {
                                resolvedCategoryId = byName.get().getId();
                                includeChildren = true;
                            } else if (category.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                                resolvedCategoryId = category;
                                includeChildren = true;
                            } else {
                                // leaf/slug fallback
                                var leaf = categoryRepository.findBySlug(category);
                                if (leaf.isPresent()) {
                                    resolvedCategoryId = leaf.get().getId();
                                    includeChildren = false;
                                }
                            }
                        }
                    }
                    if (subcategory != null && !subcategory.isEmpty()) {
                        var sub = categoryRepository.findSubcategoryByParentAndChildFlexible(category != null ? category : "", subcategory);
                        if (sub.isPresent()) {
                            resolvedCategoryId = sub.get().getId();
                            includeChildren = false;
                        }
                    }
                }

                // Normalize gender
                String normalizedGender = null;
                if (gender != null && !gender.isEmpty()) {
                    if (gender.equalsIgnoreCase("men") || gender.equalsIgnoreCase("male")) normalizedGender = "Men";
                    else if (gender.equalsIgnoreCase("women") || gender.equalsIgnoreCase("female")) normalizedGender = "Women";
                    else if (gender.equalsIgnoreCase("unisex")) normalizedGender = "Unisex";
                }

                int effOffset = (offset != null) ? Math.max(0, offset) : (page - 1) * limit;
                String effStatus = (status != null && !status.isBlank()) ? status : null;

                List<Map<String, Object>> items = productRepository.findLightweight(
                        resolvedCategoryId,
                        includeChildren,
                        normalizedGender,
                        minPrice,
                        maxPrice,
                        inStock,
                        effStatus,
                        sort,
                        limit,
                        effOffset
                );

                response.put("data", items);
                // Adjust count to returned items for faster response, keep total as before for compatibility
                response.put("count", items.size());
            } else {
            response.put("data", paginatedProducts);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in getAllProducts: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("data", new ArrayList<>());
            return ResponseEntity.ok(response);
        }
    }
    
    // Get single product
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable String id) {
        try {
            Optional<Product> productOpt = productRepository.findById(id);
            
            if (productOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Product not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            Product product = productOpt.get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", product);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in getProduct: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error retrieving product");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    //sync products
    @PostMapping("/sync")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<List<Product>> syncProducts(Authentication authentication) {
        try{
            String userId = authentication.getName();
            var products = productService.syncProductsFromVendorWebsite(userId);
            return ResponseEntity.ok(products);
        } catch (Exception e){
            log.error("Error in syncProducts: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(new ArrayList<>());
    }
    
    // Create product
    @PostMapping("/")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody CreateProductRequest request, Authentication authentication) {
        try {
            // Extract vendor user ID from JWT
            String vendorUserId = authentication.getName();
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(vendorUserId);
            
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vendor not found"
                ));
            }
            
            Vendor vendor = vendorOpt.get();
            
            // Create product
            Product product = new Product();
            product.setName(request.getName());
            product.setDescription(request.getDescription());
            product.setPrice(request.getPrice());
            
            // Convert gender to DB enum values (Men, Women, Unisex, Male, Female, None)
            String gender = request.getGender();
            if (gender != null) {
                switch (gender.toLowerCase()) {
                    case "boy":
                    case "men":
                    case "male":
                        product.setGender("Male"); // matches enum product_gender
                        break;
                    case "girl":
                    case "women":
                    case "female":
                        product.setGender("Female"); // matches enum product_gender
                        break;
                    case "unisex":
                        product.setGender("Unisex");
                        break;
                    default:
                        product.setGender("Unisex"); // Default fallback
                        break;
                }
            } else {
                product.setGender("Unisex"); // Default fallback
            }
            
            // Set age range if provided
            if (request.getAgeRange() != null && !request.getAgeRange().trim().isEmpty()) {
                product.setAgeRange(request.getAgeRange());
            }
            
            product.setStatus("draft");
            product.setIsActive(true);
            
            // Set category - handle both parent category and subcategory
            if (request.getCategory() != null) {
                Product.Category productCategory = null;
                
                // If subcategory is provided, try to find the subcategory first
                if (request.getSubcategory() != null && !request.getSubcategory().trim().isEmpty()) {
                    Optional<Product.Category> subcategoryOpt = categoryRepository.findSubcategoryByParentAndName(
                        request.getCategory(), 
                        request.getSubcategory()
                    );
                    
                    if (subcategoryOpt.isPresent()) {
                        // Use the subcategory
                        Product.Category subcategory = subcategoryOpt.get();
                        productCategory = new Product.Category();
                        productCategory.setId(subcategory.getId());
                        productCategory.setName(subcategory.getName());
                        productCategory.setSlug(subcategory.getSlug());
                        productCategory.setDescription(subcategory.getDescription());
                        productCategory.setImageUrl(subcategory.getImageUrl());
                        productCategory.setImagePublicId(subcategory.getImagePublicId());
                        productCategory.setParentCategoryId(subcategory.getParentCategoryId());
                        productCategory.setLevel(subcategory.getLevel());
                        productCategory.setIsActive(subcategory.getIsActive());
                        
                        System.out.println("Using subcategory: " + subcategory.getName() + " (ID: " + subcategory.getId() + ")");
                    } else {
                        System.out.println("Subcategory not found: " + request.getSubcategory() + " under parent: " + request.getCategory());
                    }
                }
                
                // If no subcategory found or no subcategory provided, try to find parent category
                if (productCategory == null) {
                    Optional<Product.Category> parentCategoryOpt = categoryRepository.findParentCategoryByName(request.getCategory());
                    
                    if (parentCategoryOpt.isPresent()) {
                        // Use the parent category
                        Product.Category parentCategory = parentCategoryOpt.get();
                        productCategory = new Product.Category();
                        productCategory.setId(parentCategory.getId());
                        productCategory.setName(parentCategory.getName());
                        productCategory.setSlug(parentCategory.getSlug());
                        productCategory.setDescription(parentCategory.getDescription());
                        productCategory.setImageUrl(parentCategory.getImageUrl());
                        productCategory.setImagePublicId(parentCategory.getImagePublicId());
                        productCategory.setParentCategoryId(parentCategory.getParentCategoryId());
                        productCategory.setLevel(parentCategory.getLevel());
                        productCategory.setIsActive(parentCategory.getIsActive());
                        
                        System.out.println("Using parent category: " + parentCategory.getName() + " (ID: " + parentCategory.getId() + ")");
                    } else {
                        System.out.println("Parent category not found: " + request.getCategory());
                    }
                }
                
                if (productCategory != null) {
                    product.setCategory(productCategory);
                } else {
                    log.error("Category not found: {}{}", request.getCategory(), (request.getSubcategory() != null ? " with subcategory: " + request.getSubcategory() : ""));
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Category not found: " + request.getCategory() + 
                                  (request.getSubcategory() != null ? " with subcategory: " + request.getSubcategory() : "")
                    ));
                }
            }
            
            // Set vendor
            Product.Vendor productVendor = new Product.Vendor();
            productVendor.setId(vendor.getId());
            productVendor.setBusinessName(vendor.getBusinessName());
            productVendor.setBusinessType(vendor.getBusinessType());
            productVendor.setStatus(vendor.getStatus());
            productVendor.setRating(vendor.getRating());
            product.setVendor(productVendor);
            
            // Save product first to get ID
            Product savedProduct = productRepository.save(product);
            
            // Process inventory if provided
            if (request.getColorInventories() != null && !request.getColorInventories().isEmpty()) {
                for (CreateProductRequest.ColorInventoryRequest colorInv : request.getColorInventories()) {
                    // Add sizes for this color directly
                    if (colorInv.getSizes() != null) {
                        for (CreateProductRequest.SizeInventoryRequest sizeInv : colorInv.getSizes()) {
                            productRepository.addSizeToColor(
                                savedProduct.getId(), 
                                colorInv.getColor(), 
                                colorInv.getColorCode() != null && !colorInv.getColorCode().trim().isEmpty() ? colorInv.getColorCode() : "#000000",
                                sizeInv.getSize(), 
                                sizeInv.getStock()
                            );
                        }
                    }
                }
                
                // Reload product with inventory
                savedProduct = productRepository.findById(savedProduct.getId()).orElse(savedProduct);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Product created successfully",
                "data", savedProduct
            ));
            
        } catch (Exception e) {
            log.error("Error in createProduct: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error creating product: " + e.getMessage()
            ));
        }
    }

    // --- Discount Management (Vendor only) ---

    @PutMapping("/{productId}/discount")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> setDiscount(
            @PathVariable String productId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(userId);
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Vendor not found"));
            }

            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Product not found"));
            }

            Product product = productOpt.get();
            if (product.getVendor() == null || product.getVendor().getId() == null ||
                    !product.getVendor().getId().equals(vendorOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "You can only manage your own products"));
            }

            Object val = body.get("discountValue");
            Object typeObj = body.get("discountType");
            Object endDateObj = body.get("endDate");
            Object activeObj = body.get("isActive");

            if (val == null || typeObj == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "discountValue and discountType are required"));
            }

            double discountValue;
            try {
                discountValue = Double.parseDouble(val.toString());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "discountValue must be a number"));
            }

            String discountType = typeObj.toString().toLowerCase();
            if (!discountType.equals("percentage") && !discountType.equals("fixed")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "discountType must be 'percentage' or 'fixed'"));
            }

            boolean isActive = activeObj == null || Boolean.parseBoolean(activeObj.toString());

            BigDecimal pct;
            if (discountType.equals("percentage")) {
                if (discountValue < 0 || discountValue > 100) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Percentage discount must be between 0 and 100"));
                }
                pct = BigDecimal.valueOf(discountValue);
            } else { // fixed
                if (discountValue < 0) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Fixed discount cannot be negative"));
                }
                BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Product price must be greater than zero for fixed discount"));
                }
                pct = BigDecimal.valueOf(discountValue).divide(price, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                    pct = BigDecimal.valueOf(100);
                }
            }

            Timestamp endTs = null;
            if (endDateObj != null && !endDateObj.toString().isBlank()) {
                try {
                    endTs = Timestamp.valueOf(java.time.LocalDateTime.parse(endDateObj.toString()));
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid endDate format; expected ISO-8601 LocalDateTime"));
                }
            }

            boolean updated = productRepository.updateDiscount(productId, pct, endTs, isActive);
            if (!updated) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Failed to update discount"));
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Discount updated successfully",
                "discountPercentage", pct,
                "discountType", discountType,
                "discountValidUntil", endTs,
                "isActive", isActive
            ));
        } catch (Exception e) {
            log.error("Error setting discount: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Error setting discount", "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{productId}/discount")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> clearDiscount(
            @PathVariable String productId,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(userId);
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Vendor not found"));
            }

            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Product not found"));
            }

            Product product = productOpt.get();
            if (product.getVendor() == null || product.getVendor().getId() == null ||
                    !product.getVendor().getId().equals(vendorOpt.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "You can only manage your own products"));
            }

            boolean cleared = productRepository.clearDiscount(productId);
            if (!cleared) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Failed to remove discount"));
            }

            return ResponseEntity.ok(Map.of("success", true, "message", "Discount removed successfully"));
        } catch (Exception e) {
            log.error("Error clearing discount: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", "Error clearing discount", "error", e.getMessage()));
        }
    }

    // Public: Get up to 4 other products from same vendor (lightweight)
    @GetMapping("/vendor/{vendorId}/others/{productId}")
    public ResponseEntity<Map<String, Object>> getOtherProductsFromVendor(
            @PathVariable String vendorId,
            @PathVariable String productId,
            @RequestParam(defaultValue = "4") Integer limit
    ) {
        try {
            int capped = Math.max(1, Math.min(limit, 4));
            List<Map<String, Object>> items = productRepository.findVendorOthersLightweight(vendorId, productId, capped);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", items.size(),
                    "data", items
            ));
        } catch (Exception e) {
            log.error("Error in getOtherProductsFromVendor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error fetching products by vendor: " + e.getMessage()
            ));
        }
    }

    // Search products by name/description
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchProducts(@RequestParam("q") String query,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Query 'q' is required"));
        }
        List<Product> results = productRepository.searchProducts(query);
        int total = results.size();
        int start = Math.max(0, (page - 1) * limit);
        int end = Math.min(total, start + limit);
        List<Product> paginated = start < end ? results.subList(start, end) : new ArrayList<>();
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("pages", (int) Math.ceil((double) total / limit));
        return ResponseEntity.ok(Map.of(
            "success", true,
            "count", paginated.size(),
            "total", total,
            "pagination", pagination,
            "data", paginated
        ));
    }

    // Upload default image for a product (stores in Backblaze B2 and returns array of URLs)
    @PostMapping("/{productId}/images/default")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> uploadDefaultImage(
            @PathVariable String productId,
            @RequestParam("images") MultipartFile[] images,
            HttpServletRequest request
    ) {
        try {
            if (images == null || images.length == 0) {
                log.error("No files uploaded");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No files uploaded"
                ));
            }

            List<String> uploadedUrls = new ArrayList<>();
            List<String> fileIds = new ArrayList<>(); // This will store the B2 keys for deletion
            
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    try {
                        // Upload to Backblaze B2 instead of local storage
                        BackblazeService.UploadResult uploadResult = backblazeService.uploadMultipart(
                            image, 
                            "products/" + productId + "/default"
                        );
                        
                        uploadedUrls.add(uploadResult.url);
                        fileIds.add(uploadResult.key); // Store B2 key for future deletion
                        
                    } catch (IOException e) {
                        // Log the error but continue with other images
                        System.err.println("Failed to upload image: " + e.getMessage());
                    }
                }
            }

            if (uploadedUrls.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No valid images were uploaded"
                ));
            }

            // Save images to database with B2 keys as file_ids
            productRepository.saveProductImages(productId, uploadedUrls, null, fileIds);

            // Update product status from draft to awaiting_approval
            productRepository.updateStatus(productId, "awaiting_approval");

            Map<String, Object> data = new HashMap<>();
            data.put("uploadedUrls", uploadedUrls);
            data.put("totalImages", uploadedUrls.size());
            data.put("storageProvider", "Backblaze B2");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Images uploaded successfully to Backblaze B2. Product status changed to awaiting approval.",
                    "data", data
            ));
            
        } catch (Exception e) {
            log.error("Error in uploadDefaultImage: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to upload images",
                    "error", Objects.toString(e.getMessage(), "Error")
            ));
        }
    }

    // Admin: Get all products pending approval
    @GetMapping("/admin/pending-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getPendingApprovalProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        
        try {
            List<Product> allProducts = productRepository.findAll();
            List<Product> pendingProducts = allProducts.stream()
                    .filter(product -> "awaiting_approval".equalsIgnoreCase(product.getStatus()))
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .collect(Collectors.toList());
            
            // Apply pagination
            int total = pendingProducts.size();
            int startIndex = (page - 1) * limit;
            int endIndex = Math.min(startIndex + limit, total);
            
            List<Product> paginatedProducts = pendingProducts.subList(startIndex, endIndex);
            
            // Create pagination response
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("pages", (int) Math.ceil((double) total / limit));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", paginatedProducts.size());
            response.put("total", total);
            response.put("pagination", pagination);
            response.put("data", paginatedProducts);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in getPendingApprovalProducts: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error retrieving pending approval products: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // Admin: Approve or reject a product
    @PutMapping("/admin/{productId}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateProductApproval(
            @PathVariable String productId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String action = (String) request.get("action"); // "approve" or "reject"
            String reason = (String) request.get("reason"); // Optional reason for rejection
            
            if (action == null || (!action.equals("approve") && !action.equals("reject"))) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid action. Must be 'approve' or 'reject'");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Log the reason if provided
            if (reason != null && !reason.trim().isEmpty()) {
                System.out.println("Product " + action + " reason: " + reason);
            }
            
            // Find the product first to check if it exists
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Product not found");
                return ResponseEntity.status(404).body(errorResponse);
            }
            
            Product product = productOpt.get();
            
            // Update status using repository method
            String newStatus = action.equals("approve") ? "approved" : "rejected";
            productRepository.updateStatus(productId, newStatus);
            
            // Update the product object for response
            product.setStatus(newStatus);
            product.setUpdatedAt(LocalDateTime.now().format(formatter));

            // Send notification to vendor (ensure vendor user email is loaded)
            try {
                Vendor vendor = null;
                if (product.getVendor() != null && product.getVendor().getId() != null) {
                    var vendOpt = vendorRepository.findById(product.getVendor().getId());
                    if (vendOpt.isPresent()) {
                        vendor = vendOpt.get();
                        if (vendor.getUser() != null && vendor.getUser().getId() != null) {
                            var userOpt = userRepository.findById(vendor.getUser().getId());
                            userOpt.ifPresent(vendor::setUser);
                        }
                    }
                }
                if (vendor != null && vendor.getUser() != null && vendor.getUser().getEmail() != null) {
                    boolean isApproved = "approved".equalsIgnoreCase(newStatus);
                    notificationService.notifyProductApproval(product, vendor, isApproved, reason);
                } else {
                    System.err.println("Product approval email not sent: vendor or vendor user email missing for product " + productId);
                }
            } catch (Exception emailEx) {
                System.err.println("Failed to send product approval notification: " + emailEx.getMessage());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Product " + action + "d successfully");
            response.put("data", product);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in updateProductApproval: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating product approval: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // Inventory management endpoints
    
    @PostMapping("/{productId}/inventory/colors")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> addColorToProduct(
            @PathVariable String productId,
            @RequestBody Map<String, Object> request) {
        try {
            String color = (String) request.get("color");
            String colorCode = (String) request.get("colorCode");
            
            if (color == null || color.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Color is required"
                ));
            }
            
            // If sizes are provided, add all sizes for this color into inventory
            Object sizesObj = request.get("sizes");
            if (sizesObj instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sizes = (List<Map<String, Object>>) sizesObj;
                for (Map<String, Object> s : sizes) {
                    if (s == null) continue;
                    Object sizeVal = s.get("size");
                    Object stockVal = s.get("stock");
                    if (sizeVal == null || stockVal == null) continue;
                    String size = sizeVal.toString();
                    int stock;
                    try {
                        stock = Integer.parseInt(stockVal.toString());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    productRepository.addSizeToColor(
                        productId,
                        color,
                        (colorCode != null && !colorCode.isBlank()) ? colorCode : "#000000",
                        size,
                        stock
                    );
                }
            } else {
                // No sizes array provided; initialize the color with zero-stock default size if needed
                // Skip creating a placeholder row to avoid confusing inventory; rely on sizes endpoint instead
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Color added to product successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error in addColorToProduct: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error adding color: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/{productId}/inventory/sizes")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> addSizeToColor(
            @PathVariable String productId,
            @RequestBody Map<String, Object> request) {
        try {
            String color = (String) request.get("color");
            String size = (String) request.get("size");
            Integer stock = (Integer) request.get("stock");
            
            if (color == null || size == null || stock == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Color, size, and stock are required"
                ));
            }
            
            productRepository.addSizeToColor(productId, color, "#000000", size, stock);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Size added to color successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error in addSizeToColor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error adding size: " + e.getMessage()
            ));
        }
    }
    
    @PutMapping("/{productId}/inventory/stock")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> updateStock(
            @PathVariable String productId,
            @RequestBody Map<String, Object> request) {
        try {
            String color = (String) request.get("color");
            String size = (String) request.get("size");
            Integer newStock = (Integer) request.get("stock");
            
            if (color == null || size == null || newStock == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Color, size, and stock are required"
                ));
            }
            
            if (newStock < 0) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Stock cannot be negative"
                ));
            }
            
            productRepository.updateStock(productId, color, size, newStock);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Stock updated successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error in updateStock: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error updating stock: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/{productId}/inventory/summary")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> getInventorySummary(@PathVariable String productId) {
        try {
            Map<String, Integer> stockSummary = productRepository.getStockSummary(productId);
            List<Map<String, Object>> lowStockItems = productRepository.getLowStockItems(productId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "stockSummary", stockSummary,
                    "lowStockItems", lowStockItems
                )
            ));
            
        } catch (Exception e) {
            log.error("Error in getInventorySummary: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error getting inventory summary: " + e.getMessage()
            ));
        }
    }
    
    // COB (Customers Also Bought) endpoints
    
    @GetMapping("/cob")
    public ResponseEntity<Map<String, Object>> getCustomersAlsoBoughtProducts(
            @RequestParam(required = false) String vendorId,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            List<Product> products = productRepository.getCustomersAlsoBoughtProducts(vendorId, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", products.size(),
                "vendorId", vendorId != null ? vendorId : "all",
                "data", products
            ));
        } catch (Exception e) {
            log.error("Error in getCustomersAlsoBoughtProducts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching COB products: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/vendor/cob")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> getVendorCOBProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            Authentication authentication) {
        try {
            String vendorUserId = authentication.getName();
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(vendorUserId);
            
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vendor not found"
                ));
            }
            
            Vendor vendor = vendorOpt.get();
            List<Product> products = productRepository.getVendorCOBProducts(vendor.getId(), page, limit);
            int total = productRepository.countVendorCOBProducts(vendor.getId());
            
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
            log.error("Error in getVendorCOBProducts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching vendor COB products: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/{productId}/cob")
    public ResponseEntity<Map<String, Object>> getCOBForProduct(
            @PathVariable String productId,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            List<Product> products = productRepository.getCOBForProduct(productId, limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", products.size(),
                "productId", productId,
                "data", products
            ));
        } catch (Exception e) {
            log.error("Error in getCOBForProduct: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching COB for product: " + e.getMessage()
            ));
        }
    }
    
    @PutMapping("/customers-also-bought/add")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
    public ResponseEntity<Map<String, Object>> addToCustomersAlsoBought(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            String productId = (String) request.get("productId");
            
            if (productId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Product ID is required"
                ));
            }
            
            // Check if user is vendor and owns this product
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"))) {
                String vendorUserId = authentication.getName();
                Optional<Vendor> vendorOpt = vendorRepository.findByUserId(vendorUserId);
                
                if (vendorOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Vendor not found"
                    ));
                }
                
                Optional<Product> productOpt = productRepository.findById(productId);
                if (productOpt.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                
                Product product = productOpt.get();
                if (!vendorOpt.get().getId().equals(product.getVendor().getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "You can only manage COB for your own products"
                    ));
                }
            }
            
            boolean success = productRepository.updateCustomersAlsoBought(productId, true);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Product added to 'Customers Also Bought' section"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update product"
                ));
            }
        } catch (Exception e) {
            log.error("Error in addToCustomersAlsoBought: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error updating product status: " + e.getMessage()
            ));
        }
    }
    
    @PutMapping("/customers-also-bought/remove")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
    public ResponseEntity<Map<String, Object>> removeFromCustomersAlsoBought(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            String productId = (String) request.get("productId");
            
            if (productId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Product ID is required"
                ));
            }
            
            // Check if user is vendor and owns this product
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VENDOR"))) {
                String vendorUserId = authentication.getName();
                Optional<Vendor> vendorOpt = vendorRepository.findByUserId(vendorUserId);
                
                if (vendorOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Vendor not found"
                    ));
                }
                
                Optional<Product> productOpt = productRepository.findById(productId);
                if (productOpt.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                
                Product product = productOpt.get();
                if (!vendorOpt.get().getId().equals(product.getVendor().getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "You can only manage COB for your own products"
                    ));
                }
            }
            
            boolean success = productRepository.updateCustomersAlsoBought(productId, false);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Product removed from 'Customers Also Bought' section"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update product"
                ));
            }
        } catch (Exception e) {
            log.error("Error in removeFromCustomersAlsoBought: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error updating product status: " + e.getMessage()
            ));
        }
    }
    
    // Product management endpoints
    
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable String productId,
            @RequestBody Map<String, Object> updates,
            Authentication authentication) {
        try {
            // Verify vendor owns this product
            String vendorUserId = authentication.getName();
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(vendorUserId);
            
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vendor not found"
                ));
            }
            
            Optional<Product> existingProductOpt = productRepository.findById(productId);
            if (existingProductOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Product existingProduct = existingProductOpt.get();
            if (!vendorOpt.get().getId().equals(existingProduct.getVendor().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "You can only update your own products"
                ));
            }
            
            // Update fields
            if (updates.containsKey("name")) {
                existingProduct.setName((String) updates.get("name"));
            }
            if (updates.containsKey("description")) {
                existingProduct.setDescription((String) updates.get("description"));
            }
            if (updates.containsKey("price")) {
                existingProduct.setPrice(new BigDecimal(updates.get("price").toString()));
            }
            if (updates.containsKey("gender")) {
                existingProduct.setGender((String) updates.get("gender"));
            }
            if (updates.containsKey("isActive")) {
                existingProduct.setIsActive((Boolean) updates.get("isActive"));
            }
            
            // Handle category updates
            if (updates.containsKey("category")) {
                String categorySlug = (String) updates.get("category");
                Optional<Product.Category> categoryOpt = categoryRepository.findBySlug(categorySlug);
                if (categoryOpt.isPresent()) {
                    Product.Category category = categoryOpt.get();
                    Product.Category productCategory = new Product.Category();
                    productCategory.setId(category.getId());
                    productCategory.setName(category.getName());
                    productCategory.setSlug(category.getSlug());
                    productCategory.setDescription(category.getDescription());
                    productCategory.setImageUrl(category.getImageUrl());
                    productCategory.setImagePublicId(category.getImagePublicId());
                    productCategory.setParentCategoryId(category.getParentCategoryId());
                    productCategory.setLevel(category.getLevel());
                    productCategory.setIsActive(category.getIsActive());
                    existingProduct.setCategory(productCategory);
                }
            }
            
            // Handle inventory updates
            if (updates.containsKey("colorInventories")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> colorInventories = (List<Map<String, Object>>) updates.get("colorInventories");
                
                // Clear existing inventory
                existingProduct.setColorInventories(new ArrayList<>());
                
                // Add new inventory
                for (Map<String, Object> colorInv : colorInventories) {
                    String color = (String) colorInv.get("color");
                    String colorCode = (String) colorInv.get("colorCode");
                    
                    Product.ColorInventory colorInventory = new Product.ColorInventory();
                    colorInventory.setColor(color);
                    colorInventory.setColorCode(colorCode);
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sizes = (List<Map<String, Object>>) colorInv.get("sizes");
                    if (sizes != null) {
                        for (Map<String, Object> sizeInv : sizes) {
                            String size = (String) sizeInv.get("size");
                            Integer stock = (Integer) sizeInv.get("stock");
                            
                            Product.SizeInventory sizeInventory = new Product.SizeInventory();
                            sizeInventory.setSize(size);
                            sizeInventory.setStock(stock != null ? stock : 0);
                            
                            colorInventory.getSizes().add(sizeInventory);
                        }
                    }
                    
                    existingProduct.getColorInventories().add(colorInventory);
                }
                
                // Calculate total stock
                existingProduct.calculateTotalStock();
            }
            
            boolean success = productRepository.updateProduct(existingProduct);
            
            if (success) {
                // Reload product with inventory
                Product updatedProduct = productRepository.findById(productId).orElse(existingProduct);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Product updated successfully",
                    "data", updatedProduct
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update product"
                ));
            }
        } catch (Exception e) {
            log.error("Error in updateProduct: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error updating product: " + e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> deleteProduct(
            @PathVariable String productId,
            Authentication authentication) {
        try {
            // Verify vendor owns this product
            String vendorUserId = authentication.getName();
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(vendorUserId);
            
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vendor not found"
                ));
            }
            
            Optional<Product> existingProductOpt = productRepository.findById(productId);
            if (existingProductOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Product existingProduct = existingProductOpt.get();
            if (!vendorOpt.get().getId().equals(existingProduct.getVendor().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "You can only delete your own products"
                ));
            }
            
            boolean success = productRepository.deleteProduct(productId);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Product deleted permanently"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to delete product"
                ));
            }
        } catch (Exception e) {
            log.error("Error in deleteProduct: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error deleting product: " + e.getMessage()
            ));
        }
    }
    
    // Product stats endpoint
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getProductStats() {
        try {
            Map<String, Object> stats = productRepository.getProductStats();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "stats", stats
            ));
        } catch (Exception e) {
            log.error("Error in getProductStats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching product stats: " + e.getMessage()
            ));
        }
    }
    
    // Fix stock endpoint
    @GetMapping("/fix-product/{productId}")
    public ResponseEntity<Map<String, Object>> fixStock(@PathVariable String productId) {
        try {
            boolean success = productRepository.fixStock(productId);
            
            if (success) {
                Optional<Product> productOpt = productRepository.findById(productId);
                if (productOpt.isPresent()) {
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Total stock updated to " + productOpt.get().getTotalStock(),
                        "data", productOpt.get()
                    ));
                } else {
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Stock fixed successfully"
                    ));
                }
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to fix stock"
                ));
            }
        } catch (Exception e) {
            log.error("Error in fixStock: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fixing stock: " + e.getMessage()
            ));
        }
    }
    
    // Get products by vendor (lightweight for brand page)
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<Map<String, Object>> getProductsByVendor(
            @PathVariable String vendorId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "approved") String status,
            @RequestParam(defaultValue = "false") Boolean light) {
        try {
            if (light) {
                // Lightweight response for brand pages
                List<Map<String, Object>> items = productRepository.findLightweightByVendor(
                    vendorId, status, limit, (page - 1) * limit);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", items.size(),
                    "page", page,
                    "limit", limit,
                    "data", items
                ));
            } else {
                // Full product objects for detailed views
                List<Product> products = productRepository.getProductsByVendor(vendorId, page, limit, status);
                int total = productRepository.countProductsByVendor(vendorId, status);
                
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
            }
        } catch (Exception e) {
            log.error("Error in getProductsByVendor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching products by vendor: " + e.getMessage()
            ));
        }
    }
    
    // Get all brands/vendors for home page
    @GetMapping("/brands")
    public ResponseEntity<Map<String, Object>> getAllBrands(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            List<Map<String, Object>> brands = productRepository.findBrandsWithStats(page, limit);
            int total = productRepository.countActiveVendors();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", brands.size(),
                "total", total,
                "pagination", Map.of(
                    "page", page,
                    "pages", (int) Math.ceil((double) total / limit)
                ),
                "data", brands
            ));
        } catch (Exception e) {
            log.error("Error in getAllBrands: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching brands: " + e.getMessage()
            ));
        }
    }

    // Get all products by vendor (no pagination) - for vendor dashboard
    @GetMapping("/vendor/{vendorId}/all")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> getAllProductsByVendor(
            @PathVariable String vendorId,
            @RequestParam(defaultValue = "awaiting_approval_and_approved") String status) {
        try {
            // Verify the vendor is accessing their own products
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUserId = authentication.getName();
            
            // Get vendor details to verify ownership
            Optional<Vendor> vendorOpt = vendorRepository.findByUserId(currentUserId);
            if (vendorOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Vendor not found"
                ));
            }
            
            Vendor vendor = vendorOpt.get();
            if (!vendor.getId().equals(vendorId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Access denied: You can only view your own products"
                ));
            }
            
            List<Product> products = productRepository.getAllProductsByVendor(vendorId, status);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", products.size(),
                "status", status,
                "data", products
            ));
        } catch (Exception e) {
            log.error("Error in getAllProductsByVendor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error fetching all products by vendor: " + e.getMessage()
            ));
        }
    }
    
    // Update approval details
    @PutMapping("/{productId}/approval-details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateApprovalDetails(
            @PathVariable String productId,
            @RequestBody Map<String, Object> request) {
        try {
            String action = (String) request.get("action");
            String approvedBy = (String) request.get("approvedBy");
            String reason = (String) request.get("reason");
            
            if (action == null || approvedBy == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Action and approvedBy are required"
                ));
            }
            
            boolean success = productRepository.updateApprovalDetails(productId, action, approvedBy, reason);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Approval details updated successfully"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update approval details"
                ));
            }
        } catch (Exception e) {
            log.error("Error in updateApprovalDetails: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Error updating approval details: " + e.getMessage()
            ));
        }
    }

    // Unified Search API - searches products, categories, and vendors
    @GetMapping("/search-all")
    public ResponseEntity<Map<String, Object>> searchAll(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "results", Map.of(
                        "products", List.of(),
                        "categories", List.of(),
                        "vendors", List.of()
                    ),
                    "total", 0
                ));
            }

            // Search products
            List<Map<String, Object>> products = productRepository.searchProducts(query.trim(), limit);
            
            // Search categories
            List<Map<String, Object>> categories = productRepository.searchCategories(query.trim(), limit);
            
            // Search vendors
            List<Map<String, Object>> vendors = productRepository.searchVendors(query.trim(), limit);
            
            int totalResults = products.size() + categories.size() + vendors.size();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", query,
                "results", Map.of(
                    "products", products,
                    "categories", categories,
                    "vendors", vendors
                ),
                "total", totalResults,
                "counts", Map.of(
                    "products", products.size(),
                    "categories", categories.size(),
                    "vendors", vendors.size()
                )
            ));
            
        } catch (Exception e) {
            log.error("Error in searchAll: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Search failed: " + e.getMessage()
            ));
        }
    }

    // Search suggestions for autocomplete
    @GetMapping("/search/suggestions")
    public ResponseEntity<Map<String, Object>> getSearchSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer limit) {
        try {
            if (query == null || query.trim().isEmpty() || query.trim().length() < 1) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "suggestions", List.of()
                ));
            }

            List<String> suggestions = new ArrayList<>();
            
            // Get product name suggestions
            List<String> productSuggestions = productRepository.getProductNameSuggestions(query.trim(), limit);
            suggestions.addAll(productSuggestions);
            
            // Get category name suggestions
            List<String> categorySuggestions = productRepository.getCategoryNameSuggestions(query.trim(), limit);
            suggestions.addAll(categorySuggestions);
            
            // Get vendor name suggestions
            List<String> vendorSuggestions = productRepository.getVendorNameSuggestions(query.trim(), limit);
            suggestions.addAll(vendorSuggestions);
            
            // Remove duplicates and limit results
            suggestions = suggestions.stream()
                .distinct()
                .limit(limit * 3) // Allow more suggestions for autocomplete
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "query", query,
                "suggestions", suggestions
            ));
            
        } catch (Exception e) {
            log.error("Error in getSearchSuggestions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Search suggestions failed: " + e.getMessage()
            ));
        }
    }
}
