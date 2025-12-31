package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.CategoryRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    // Get all categories
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getAllCategories(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "false") Boolean includeInactive,
            @RequestParam(defaultValue = "false") Boolean parentOnly
    ) {
        // Filter
        List<Product.Category> all = categoryRepository.findAll();
        List<Product.Category> filtered = all.stream()
                .filter(c -> includeInactive || Boolean.TRUE.equals(c.getIsActive() == null ? Boolean.TRUE : c.getIsActive()))
                .collect(Collectors.toList());

        if (parentOnly) {
            filtered = filtered.stream()
                    .filter(c -> c.getParentCategoryId() == null)
                    .collect(Collectors.toList());
        }

        // Sort
        if ("name".equalsIgnoreCase(sort)) {
            filtered.sort(Comparator.comparing(Product.Category::getName, String.CASE_INSENSITIVE_ORDER));
        } else if ("-name".equalsIgnoreCase(sort)) {
            filtered.sort(Comparator.comparing(Product.Category::getName, String.CASE_INSENSITIVE_ORDER).reversed());
        }

        // Pagination
        int total = filtered.size();
        int start = Math.max(0, (page - 1) * limit);
        int end = Math.min(total, start + limit);
        List<Product.Category> paginated = start < end ? filtered.subList(start, end) : new ArrayList<>();

        // Stats (product counts by category) - avoid loading products (which triggers image queries)
        Map<String, Long> countsByCategory = new HashMap<>();
        for (Product.Category cat : filtered) {
            long count = categoryRepository.countProductsInCategory(cat.getId());
            countsByCategory.put(cat.getId(), count);
        }

        List<Map<String, Object>> categoryStats = filtered.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", c.getName());
            m.put("slug", c.getSlug());
            m.put("productCount", countsByCategory.getOrDefault(c.getId(), 0L));
            m.put("isActive", c.getIsActive());
            m.put("level", c.getLevel());
            m.put("parentCategoryId", c.getParentCategoryId());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("pages", (int) Math.ceil((double) total / limit));

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("count", paginated.size());
        resp.put("total", total);
        resp.put("pagination", pagination);
        resp.put("data", paginated);
        resp.put("stats", categoryStats);
        return ResponseEntity.ok(resp);
    }

    // Add new category (admin only)
    @PostMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> addCategory(@RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? body.get("name").toString().trim() : null;
        String nameAr = body.get("nameAr") != null ? body.get("nameAr").toString().trim() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        String descriptionAr = body.get("descriptionAr") != null ? body.get("descriptionAr").toString() : null;
        String parentCategory = body.get("parentCategory") != null ? body.get("parentCategory").toString() : null;
        Integer level = body.get("level") != null ? Integer.valueOf(body.get("level").toString()) : null;
        Boolean isActive = body.get("isActive") != null ? Boolean.valueOf(body.get("isActive").toString()) : Boolean.TRUE;

        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Category name is required"
            ));
        }

        // Uniqueness rules:
        // - No two parent categories with the same name
        // - No two categories with the same name under the same parent
        boolean existsUnderParent = categoryRepository.existsByNameUnderParent(name, parentCategory);
        if (existsUnderParent) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Category with this name already exists at this level"
            ));
        }

        String parentId = null;
        int calculatedLevel = level != null ? level : 0;
        Optional<Product.Category> parentOpt = Optional.empty();
        if (parentCategory != null && !parentCategory.isEmpty()) {
            try {
                String parentIdStr = parentCategory;
                parentOpt = categoryRepository.findById(parentIdStr);
                if (parentOpt.isEmpty()) {
                    return ResponseEntity.status(400).body(Map.of("success", false, "message", "Parent category not found"));
                }
                parentId = parentOpt.get().getId();
                calculatedLevel = (parentOpt.get().getLevel() == null ? 0 : parentOpt.get().getLevel()) + 1;
            } catch (Exception e) {
                log.error("Error adding category: Invalid parent category id", e);
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "Invalid parent category id"));
            }
        }

        String baseSlug = name.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
        String slug = parentOpt.isPresent() ? parentOpt.get().getSlug() + "-" + baseSlug : baseSlug;
        if (categoryRepository.existsBySlug(slug)) {
            int i = 2;
            String candidate;
            do {
                candidate = (parentOpt.isPresent() ? parentOpt.get().getSlug() + "-" : "") + baseSlug + "-" + i;
                i++;
            } while (categoryRepository.existsBySlug(candidate));
            slug = candidate;
        }

        Product.Category category = new Product.Category();
        category.setName(name);
        category.setNameAr(nameAr);
        category.setSlug(slug);
        category.setDescription(description);
        category.setDescriptionAr(descriptionAr);
        category.setParentCategoryId(parentId);
        category.setLevel(calculatedLevel);
        category.setIsActive(isActive);

        Product.Category saved = categoryRepository.save(category);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Category created successfully");
        resp.put("data", saved);
        return ResponseEntity.status(201).body(resp);
    }

    // Remove category (admin only)
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> removeCategory(@PathVariable String categoryId) {
        if (categoryId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Category ID is required"));
        }

        Optional<Product.Category> categoryOpt = categoryRepository.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Category not found"));
        }

        // has subcategories?
        boolean hasSub = categoryRepository.findAll().stream()
                .anyMatch(c -> Objects.equals(c.getParentCategoryId(), categoryId));
        if (hasSub) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Cannot delete category that has subcategories. Please delete subcategories first."
            ));
        }

        // has products?
        boolean hasProducts = productRepository.findAll().stream()
                .anyMatch(p -> p.getCategory() != null && Objects.equals(p.getCategory().getId(), categoryId));
        if (hasProducts) {
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Cannot delete category that has products. Please reassign or delete products first."
            ));
        }

        // delete
        categoryRepository.deleteById(categoryId);
        Product.Category category = categoryOpt.get();

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Category deleted successfully");
        resp.put("data", Map.of(
                "deletedCategory", category.getName(),
                "categoryId", category.getId()
        ));
        return ResponseEntity.ok(resp);
    }

    // Update category (admin only)
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable String categoryId,
            @RequestBody Map<String, Object> updates
    ) {
        if (categoryId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Category ID is required"));
        }

        Optional<Product.Category> categoryOpt = categoryRepository.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Category not found"));
        }

        Product.Category category = categoryOpt.get();

        // Handle name and slug updates
        if (updates.containsKey("name") && updates.get("name") != null) {
            String newName = updates.get("name").toString();
            if (!newName.equals(category.getName())) {
                Optional<Product.Category> existing = categoryRepository.findByName(newName);
                if (existing.isPresent() && !Objects.equals(existing.get().getId(), categoryId)) {
                    return ResponseEntity.status(400).body(Map.of("success", false, "message", "Category with this name already exists"));
                }
                String newSlug = newName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
                category.setName(newName);
                category.setSlug(newSlug);
            }
        }

        // Handle parent category updates
        if (updates.containsKey("parentCategory")) {
            Object parentVal = updates.get("parentCategory");
            if (parentVal == null) {
                category.setParentCategoryId(null);
                category.setLevel(0);
            } else {
                try {
                    String newParentId = parentVal.toString();
                    if (Objects.equals(newParentId, categoryId)) {
                        return ResponseEntity.status(400).body(Map.of("success", false, "message", "Category cannot be its own parent"));
                    }
                    Optional<Product.Category> parent = categoryRepository.findById(newParentId);
                    if (parent.isEmpty()) {
                        return ResponseEntity.status(400).body(Map.of("success", false, "message", "Parent category not found"));
                    }

                    // Prevent circular reference
                    String current = newParentId;
                    while (current != null) {
                        Optional<Product.Category> cur = categoryRepository.findById(current);
                        if (cur.isEmpty()) break;
                        if (Objects.equals(cur.get().getParentCategoryId(), categoryId)) {
                            return ResponseEntity.status(400).body(Map.of("success", false, "message", "Cannot set parent to a descendant category"));
                        }
                        current = cur.get().getParentCategoryId();
                    }

                    category.setParentCategoryId(newParentId);
                    category.setLevel((parent.get().getLevel() == null ? 0 : parent.get().getLevel()) + 1);
                } catch (Exception e) {
                    log.error("Error updating category: Invalid parent category id", e);
                    return ResponseEntity.status(400).body(Map.of("success", false, "message", "Invalid parent category id"));
                }
            }
        }

        // Handle Arabic name
        if (updates.containsKey("nameAr")) {
            category.setNameAr(updates.get("nameAr") != null ? updates.get("nameAr").toString().trim() : null);
        }

        // Other simple fields
        if (updates.containsKey("description")) {
            category.setDescription(updates.get("description") != null ? updates.get("description").toString() : null);
        }
        if (updates.containsKey("descriptionAr")) {
            category.setDescriptionAr(updates.get("descriptionAr") != null ? updates.get("descriptionAr").toString() : null);
        }
        if (updates.containsKey("isActive")) {
            category.setIsActive(updates.get("isActive") != null ? Boolean.valueOf(updates.get("isActive").toString()) : null);
        }

        Product.Category saved = categoryRepository.save(category);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Category updated successfully");
        resp.put("data", saved);
        return ResponseEntity.ok(resp);
    }

    // Get category by ID
    @GetMapping("/{categoryId}")
    public ResponseEntity<Map<String, Object>> getCategoryById(@PathVariable String categoryId) {
        Optional<Product.Category> categoryOpt = categoryRepository.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Category not found"));
        }

        Product.Category category = categoryOpt.get();
        List<Product.Category> subcategories = categoryRepository.findAll().stream()
                .filter(c -> Objects.equals(c.getParentCategoryId(), categoryId))
                .map(c -> {
                    Product.Category sc = new Product.Category();
                    sc.setId(c.getId());
                    sc.setName(c.getName());
                    sc.setSlug(c.getSlug());
                    sc.setDescription(c.getDescription());
                    sc.setIsActive(c.getIsActive());
                    sc.setLevel(c.getLevel());
                    return sc;
                })
                .collect(Collectors.toList());

        long productCount = productRepository.findAll().stream()
                .filter(p -> p.getCategory() != null && Objects.equals(p.getCategory().getId(), categoryId))
                .count();

        Map<String, Object> data = new HashMap<>();
        data.put("id", category.getId());
        data.put("name", category.getName());
        data.put("slug", category.getSlug());
        data.put("description", category.getDescription());
        data.put("isActive", category.getIsActive());
        data.put("level", category.getLevel());
        data.put("parentCategoryId", category.getParentCategoryId());
        data.put("subcategories", subcategories);
        data.put("productCount", productCount);

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}
