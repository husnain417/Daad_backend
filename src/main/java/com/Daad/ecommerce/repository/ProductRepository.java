package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class ProductRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Product> productRowMapper = new RowMapper<Product>() {
        @Override
        public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
            Product product = new Product();
            product.setId(rs.getString("id"));
            product.setName(rs.getString("name"));
            try { product.setNameAr(rs.getString("name_ar")); } catch (SQLException ignored) {}
            product.setDescription(rs.getString("description"));
            try { product.setDescriptionAr(rs.getString("description_ar")); } catch (SQLException ignored) {}
            product.setPrice(rs.getBigDecimal("price"));
            product.setGender(rs.getString("gender"));
            try { product.setAgeRange(rs.getString("age_range")); } catch (SQLException ignored) {}
            product.setTotalStock(rs.getInt("total_stock"));
            product.setAverageRating(rs.getBigDecimal("average_rating"));
            product.setStatus(rs.getString("status"));
            product.setIsActive(rs.getBoolean("is_active"));
            product.setIsCustomersAlsoBought(rs.getBoolean("is_customers_also_bought"));
            // reference id for external sync
            try { product.setReferenceId(rs.getString("reference_id")); } catch (SQLException ignored) {}
            
            // Set category with full details from JOIN (subcategory)
            Product.Category category = new Product.Category();
            category.setId(rs.getString("category_id"));
            category.setName(rs.getString("category_name"));
            category.setSlug(rs.getString("category_slug"));
            category.setDescription(rs.getString("category_description"));
            category.setImageUrl(rs.getString("category_image_url"));
            category.setImagePublicId(rs.getString("category_image_public_id"));
            category.setParentCategoryId(rs.getString("category_parent_id"));
            category.setLevel(rs.getInt("category_level"));
            category.setIsActive(rs.getBoolean("category_is_active"));
            product.setCategory(category);

            // Set parent category if available
            try {
                // Always attempt to map parent if join provided; guard each access
                String pcId = null;
                try { pcId = rs.getString("parent_category_id_full"); } catch (SQLException ignored) {}
                if (pcId != null) {
                    Product.Category parent = new Product.Category();
                    parent.setId(pcId);
                    try { parent.setName(rs.getString("parent_category_name")); } catch (SQLException ignored) {}
                    try { parent.setSlug(rs.getString("parent_category_slug")); } catch (SQLException ignored) {}
                    try { parent.setDescription(rs.getString("parent_category_description")); } catch (SQLException ignored) {}
                    try { parent.setImageUrl(rs.getString("parent_category_image_url")); } catch (SQLException ignored) {}
                    try { parent.setImagePublicId(rs.getString("parent_category_image_public_id")); } catch (SQLException ignored) {}
                    try { parent.setParentCategoryId(rs.getString("parent_parent_id")); } catch (SQLException ignored) {}
                    try { parent.setLevel(rs.getObject("parent_category_level") != null ? rs.getInt("parent_category_level") : null); } catch (SQLException ignored) {}
                    try { parent.setIsActive(rs.getObject("parent_category_is_active") != null ? rs.getBoolean("parent_category_is_active") : null); } catch (SQLException ignored) {}
                    product.setParentCategory(parent);
                }
            } catch (Exception ignored) {}
            
            // Set vendor with full details from JOIN
            Product.Vendor vendor = new Product.Vendor();
            vendor.setId(rs.getString("vendor_id"));
            vendor.setBusinessName(rs.getString("vendor_business_name"));
            vendor.setBusinessType(rs.getString("vendor_business_type"));
            vendor.setStatus(rs.getString("vendor_status"));
            vendor.setRating(rs.getBigDecimal("vendor_rating") != null ? rs.getBigDecimal("vendor_rating").doubleValue() : null);
            product.setVendor(vendor);
            
            // Set discount if exists
            if (rs.getBigDecimal("discount_percentage") != null && rs.getBigDecimal("discount_percentage").compareTo(BigDecimal.ZERO) > 0) {
                Product.Discount discount = new Product.Discount();
                discount.setDiscountValue(rs.getBigDecimal("discount_percentage"));
                discount.setDiscountType("percentage");
                Timestamp validUntil = rs.getTimestamp("discount_valid_until");
                if (validUntil != null) {
                    discount.setEndDate(validUntil.toLocalDateTime().toString());
                }
                discount.setIsActive(true);
                product.setDiscount(discount);
            }
            
            // Set timestamps
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                product.setCreatedAt(createdAtTs.toLocalDateTime().toString());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                product.setUpdatedAt(updatedAtTs.toLocalDateTime().toString());
            }
            
            return product;
        }
    };

    // (kept intentionally minimal; dynamic guards are used inline when mapping)
    
    // Method to load inventory for a product
    public void loadInventory(Product product) {
        if (product.getId() == null) {
            return;
        }
        
        try {
            // Make SQL query unique to avoid prepared statement conflicts
            String sql = """
                SELECT 
                    color, 
                    color_code, 
                    "size", 
                    stock, 
                    is_available, 
                    min_stock_threshold 
                FROM product_inventory 
                WHERE product_id = ?
                ORDER BY color, "size"
                """;
            List<Map<String, Object>> inventoryRows = jdbcTemplate.queryForList(sql, java.util.UUID.fromString(product.getId()));
        
        Map<String, Product.ColorInventory> colorMap = new HashMap<>();
        
        for (Map<String, Object> row : inventoryRows) {
            String color = (String) row.get("color");
            String colorCode = (String) row.get("color_code");
            String size = (String) row.get("size");
            Integer stock = (Integer) row.get("stock");
            Boolean isAvailable = (Boolean) row.get("is_available");
            Integer minStockThreshold = (Integer) row.get("min_stock_threshold");
            
            // Get or create color inventory
            Product.ColorInventory colorInventory = colorMap.computeIfAbsent(color, k -> {
                Product.ColorInventory ci = new Product.ColorInventory();
                ci.setColor(color);
                ci.setColorCode(colorCode);
                ci.setAvailable(isAvailable);
                return ci;
            });
            
            // Create size inventory
            Product.SizeInventory sizeInventory = new Product.SizeInventory();
            sizeInventory.setSize(size);
            sizeInventory.setStock(stock != null ? stock : 0);
            sizeInventory.setIsAvailable(isAvailable != null ? isAvailable : true);
            sizeInventory.setMinStockThreshold(minStockThreshold != null ? minStockThreshold : 5);
            
            colorInventory.getSizes().add(sizeInventory);
        }
        
        // Set the inventory and calculate total stock
        product.setColorInventories(new ArrayList<>(colorMap.values()));
        product.calculateTotalStock();
        } catch (Exception e) {
            System.err.println("Error loading inventory for product " + product.getId() + ": " + e.getMessage());
            e.printStackTrace();
            // Set empty inventory on error
            product.setColorInventories(new ArrayList<>());
            product.setTotalStock(0);
        }
    }
    
    // Method to load default images for a product
    private void loadDefaultImages(Product product) {
        if (product.getId() == null) {
            return;
        }
        
        try {
            String sql = """
                SELECT 
                    url, 
                    alt_text, 
                    file_id, 
                    is_primary
                FROM product_images
                WHERE product_id = ? AND color IS NULL
                ORDER BY is_primary DESC, created_at ASC
                """;

            List<Map<String, Object>> imageData = jdbcTemplate.queryForList(sql, java.util.UUID.fromString(product.getId()));
        
        List<Product.Image> defaultImages = new ArrayList<>();
        for (Map<String, Object> row : imageData) {
            Product.Image image = new Product.Image();
            image.setUrl((String) row.get("url"));
            image.setAlt((String) row.get("alt_text"));
            image.setFileId((String) row.get("file_id"));
            image.setIsPrimary((Boolean) row.get("is_primary"));
            defaultImages.add(image);
            }
            
            product.setDefaultImages(defaultImages);
        } catch (Exception e) {
            System.err.println("Error loading default images for product " + product.getId() + ": " + e.getMessage());
            e.printStackTrace();
            // Set empty images on error
            product.setDefaultImages(new ArrayList<>());
        }
    }
    
    // Method to save product images to database
    public void saveProductImages(String productId, List<String> imageUrls, List<String> altTexts, List<String> fileIds) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        
        String sql = """
            INSERT INTO product_images (product_id, color, url, alt_text, file_id, is_primary, created_at)
            VALUES (?, NULL, ?, ?, ?, ?, NOW())
            """;
        
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            String altText = (altTexts != null && i < altTexts.size()) ? altTexts.get(i) : null;
            String fileId = (fileIds != null && i < fileIds.size()) ? fileIds.get(i) : null;
            boolean isPrimary = (i == 0); // First image is primary
            
            jdbcTemplate.update(sql, 
                java.util.UUID.fromString(productId), 
                url, 
                altText, 
                fileId, 
                isPrimary
            );
        }
    }
    
    // Method to delete product images
    public void deleteProductImages(String productId) {
        String sql = "DELETE FROM product_images WHERE product_id = ?";
        jdbcTemplate.update(sql, java.util.UUID.fromString(productId));
    }
    
    // Basic CRUD operations
    public List<Product> findAll() {
        String sql = """
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   pc.id as parent_category_id_full, pc.name as parent_category_name, pc.slug as parent_category_slug,
                   pc.description as parent_category_description, pc.image_url as parent_category_image_url,
                   pc.image_public_id as parent_category_image_public_id, pc.parent_category_id as parent_parent_id,
                   pc.level as parent_category_level, pc.is_active as parent_category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN categories pc ON c.parent_category_id = pc.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.is_active = true
            ORDER BY p.created_at DESC
            """;
        List<Product> products = jdbcTemplate.query(sql, productRowMapper);
        
        // Load inventory and default images for each product
        for (Product product : products) {
            loadInventory(product);
            loadDefaultImages(product);
            ensureParentCategoryLoaded(product);
        }
        
        return products;
    }
    
    public Optional<Product> findById(String id) {
        String sql = """
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.id = ?::uuid AND p.is_active = true
            LIMIT 1
            """;
        List<Product> products = jdbcTemplate.query(sql, productRowMapper, id);
        
        if (products.isEmpty()) {
            return Optional.empty();
        }
        
        Product product = products.get(0);
        loadInventory(product);
        loadDefaultImages(product);
        ensureParentCategoryLoaded(product);
        return Optional.of(product);
    }

    public Optional<Product> findByReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) return Optional.empty();
        String sql = """
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.reference_id = ?
            LIMIT 1
            """;
        try {
            List<Product> products = jdbcTemplate.query(sql, productRowMapper, referenceId);
            if (products.isEmpty()) return Optional.empty();
            Product product = products.get(0);
            loadInventory(product);
            loadDefaultImages(product);
            ensureParentCategoryLoaded(product);
            return Optional.of(product);
        } catch (Exception e) {
            System.err.println("Error finding product by referenceId " + referenceId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<Product> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Use individual findById calls to avoid prepared statement conflicts
        List<Product> products = new ArrayList<>();
        for (String id : ids) {
            try {
                Optional<Product> productOpt = findById(id);
                if (productOpt.isPresent()) {
                    products.add(productOpt.get());
                }
            } catch (Exception e) {
                System.err.println("Error finding product by ID " + id + ": " + e.getMessage());
            }
        }
        
        return products;
    }
    
    public Product save(Product product) {
        if (product.getId() == null) {
            return insert(product);
        } else {
            return update(product);
        }
    }
    
    private Product insert(Product product) {
        String sql = """
            INSERT INTO products (
                name, name_ar, description, description_ar, price, category_id, vendor_id, gender, age_range,
                total_stock, discount_percentage, discount_valid_until,
                average_rating, status, is_active, is_customers_also_bought, reference_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?::uuid, ?::uuid, ?::product_gender, ?, ?, ?, ?, ?, ?::product_status, ?, ?, ?, NOW(), NOW())
            """;

        // If referenceId is provided we include it in the INSERT; build SQL accordingly
        boolean hasReference = product.getReferenceId() != null && !product.getReferenceId().isBlank();

        // Prepare UUID params for category/vendor (allow null category)
        java.util.UUID categoryUuid = null;
        java.util.UUID vendorUuid = null;
        try { categoryUuid = product.getCategory() != null && product.getCategory().getId() != null ? java.util.UUID.fromString(product.getCategory().getId()) : null; } catch (Exception ignored) {}
        try { vendorUuid = product.getVendor() != null && product.getVendor().getId() != null ? java.util.UUID.fromString(product.getVendor().getId()) : null; } catch (Exception ignored) {}

        // Normalize gender to match DB enum
        String genderValue = normalizeProductGender(product.getGender());

        // Execute insert using UUID objects (jdbcTemplate will map them)
        jdbcTemplate.update(sql,
            product.getName(),
            product.getNameAr(),
            product.getDescription(),
            product.getDescriptionAr(),
            product.getPrice(),
            categoryUuid,
            vendorUuid,
            genderValue,
            product.getAgeRange(),
            product.getTotalStock() != null ? product.getTotalStock() : 0,
            product.getDiscount() != null ? product.getDiscount().getDiscountValue() : BigDecimal.ZERO,
            product.getDiscount() != null && product.getDiscount().getEndDate() != null ? 
                Timestamp.valueOf(java.time.LocalDateTime.parse(product.getDiscount().getEndDate())) : null,
            product.getAverageRating() != null ? product.getAverageRating() : BigDecimal.ZERO,
            product.getStatus() != null ? product.getStatus() : "none",
            product.getIsActive() != null ? product.getIsActive() : true,
            product.getIsCustomersAlsoBought() != null ? product.getIsCustomersAlsoBought() : false,
            product.getReferenceId() == null || product.getReferenceId().isBlank() ? null : product.getReferenceId()
        );

        // Get the generated UUID. If we have a referenceId, fetch by it to avoid ambiguity.
        String generatedId = null;
        if (hasReference) {
            String idSql = "SELECT id FROM products WHERE reference_id = ? AND vendor_id = ?  LIMIT 1";
            try { generatedId = jdbcTemplate.queryForObject(idSql, String.class, product.getReferenceId(), product.getVendor().getId()); } catch (Exception ignored) {}
        }

        if (generatedId == null) {
            String idSql;
            Object[] idParams;
            if (categoryUuid != null) {
                idSql = "SELECT id FROM products WHERE name = ? AND category_id = ?::uuid AND vendor_id = ?::uuid ORDER BY created_at DESC LIMIT 1";
                idParams = new Object[]{product.getName(), categoryUuid, vendorUuid};
            } else {
                // No category provided, match by name and vendor only
                idSql = "SELECT id FROM products WHERE name = ? AND vendor_id = ?::uuid ORDER BY created_at DESC LIMIT 1";
                idParams = new Object[]{product.getName(), vendorUuid};
            }
            generatedId = jdbcTemplate.queryForObject(idSql, String.class, idParams);
        }
        
        // Set the generated ID
        product.setId(generatedId);
        
        return product;
    }
    
    private Product update(Product product) {
        String sql = """
            UPDATE products SET 
                name = ?, name_ar = ?, description = ?, description_ar = ?, price = ?, category_id = ?, vendor_id = ?,
                gender = ?::product_gender, age_range = ?, total_stock = ?, discount_percentage = ?,
                discount_valid_until = ?, average_rating = ?, status = ?::product_status,
                is_active = ?, is_customers_also_bought = ?, reference_id = ?, updated_at = NOW()
            WHERE id = ?
            """;

        java.util.UUID categoryUuid = null;
        java.util.UUID vendorUuid = null;
        java.util.UUID idUuid = null;
        try { categoryUuid = product.getCategory() != null && product.getCategory().getId() != null ? java.util.UUID.fromString(product.getCategory().getId()) : null; } catch (Exception ignored) {}
        try { vendorUuid = product.getVendor() != null && product.getVendor().getId() != null ? java.util.UUID.fromString(product.getVendor().getId()) : null; } catch (Exception ignored) {}
        try { idUuid = product.getId() != null ? java.util.UUID.fromString(product.getId()) : null; } catch (Exception ignored) {}

        Timestamp discountUntil = null;
        if (product.getDiscount() != null && product.getDiscount().getEndDate() != null) {
            try { discountUntil = Timestamp.valueOf(java.time.LocalDateTime.parse(product.getDiscount().getEndDate())); } catch (Exception ignored) {}
        }

        jdbcTemplate.update(sql,
            product.getName(),
            product.getNameAr(),
            product.getDescription(),
            product.getDescriptionAr(),
            product.getPrice(),
            categoryUuid,
            vendorUuid,
            normalizeProductGender(product.getGender()),
            product.getAgeRange(),
            product.getTotalStock(),
            product.getDiscount() != null ? product.getDiscount().getDiscountValue() : BigDecimal.ZERO,
            discountUntil,
            product.getAverageRating(),
            product.getStatus(),
            product.getIsActive(),
            product.getIsCustomersAlsoBought(),
            product.getReferenceId(),
            idUuid
        );

        return product;
    }
    
    public void deleteById(String id) {
        // Soft delete by setting is_active to false
        String sql = "UPDATE products SET is_active = false, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, id);
    }
    
    public long count() {
        String sql = "SELECT COUNT(*) FROM products WHERE is_active = true";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    // Custom finders
    public List<Product> findByStatus(String status) {
        String sql = "SELECT * FROM products WHERE status = ?::product_status AND is_active = true ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, status);
    }
    
    public List<Product> findByGender(String gender) {
        String sql = "SELECT * FROM products WHERE gender = ?::product_gender AND is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, productRowMapper, gender);
    }
    
    public List<Product> findByCategory(String categoryId) {
        String sql = "SELECT * FROM products WHERE category_id = ?::uuid AND is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, productRowMapper, categoryId);
    }
    
    public List<Product> findByVendor(String vendorId) {
        String sql = "SELECT * FROM products WHERE vendor_id = ?::uuid AND is_active = true ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, vendorId);
    }
    
    public List<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        String sql = "SELECT * FROM products WHERE price >= ? AND price <= ? AND is_active = true ORDER BY price ASC";
        return jdbcTemplate.query(sql, productRowMapper, minPrice, maxPrice);
    }
    
    public List<Product> findInStock() {
        String sql = "SELECT * FROM products WHERE total_stock > 0 AND is_active = true ORDER BY name ASC";
        return jdbcTemplate.query(sql, productRowMapper);
    }
    
    public List<Product> findByApprovalStatus(String status) {
        String sql = "SELECT * FROM products WHERE status = ?::product_status AND is_active = true ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, productRowMapper, status);
    }
    
    // Complex filtering
    public List<Product> findWithFilters(Map<String, Object> filters) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM products WHERE is_active = true");
        List<Object> params = new ArrayList<>();
        
        if (filters.containsKey("status")) {
            sqlBuilder.append(" AND status = ?::product_status");
            params.add(filters.get("status"));
        }
        
        if (filters.containsKey("gender")) {
            sqlBuilder.append(" AND gender = ?::product_gender");
            params.add(filters.get("gender"));
        }
        
        if (filters.containsKey("categoryId")) {
            sqlBuilder.append(" AND category_id = ?::uuid");
            params.add(filters.get("categoryId"));
        }
        
        if (filters.containsKey("vendorId")) {
            sqlBuilder.append(" AND vendor_id = ?::uuid");
            params.add(filters.get("vendorId"));
        }
        
        if (filters.containsKey("minPrice")) {
            sqlBuilder.append(" AND price >= ?");
            params.add(filters.get("minPrice"));
        }
        
        if (filters.containsKey("maxPrice")) {
            sqlBuilder.append(" AND price <= ?");
            params.add(filters.get("maxPrice"));
        }
        
        if (filters.containsKey("inStock") && (Boolean) filters.get("inStock")) {
            sqlBuilder.append(" AND total_stock > 0");
        }
        
        sqlBuilder.append(" ORDER BY created_at DESC");
        
        return jdbcTemplate.query(sqlBuilder.toString(), productRowMapper, params.toArray());
    }
    
    // Sorting
    public List<Product> findAllSorted(String sortBy) {
        String sql = "SELECT * FROM products WHERE is_active = true ORDER BY ";
        
        switch (sortBy) {
            case "name":
                sql += "name ASC";
                break;
            case "-name":
                sql += "name DESC";
                break;
            case "price":
                sql += "price ASC";
                break;
            case "-price":
                sql += "price DESC";
                break;
            case "createdAt":
                sql += "created_at ASC";
                break;
            case "-createdAt":
            default:
                sql += "created_at DESC";
                break;
        }
        
        return jdbcTemplate.query(sql, productRowMapper);
    }
    
    // Pagination
    public List<Product> findWithPagination(int page, int size, String sortBy) {
        String sql = "SELECT * FROM products WHERE is_active = true ORDER BY ";
        
        switch (sortBy) {
            case "name":
                sql += "name ASC";
                break;
            case "-name":
                sql += "name DESC";
                break;
            case "price":
                sql += "price ASC";
                break;
            case "-price":
                sql += "price DESC";
                break;
            case "createdAt":
                sql += "created_at ASC";
                break;
            case "-createdAt":
            default:
                sql += "created_at DESC";
                break;
        }
        
        sql += " LIMIT ? OFFSET ?";
        int offset = (page - 1) * size;
        
        return jdbcTemplate.query(sql, productRowMapper, size, offset);
    }
    
    // Search
    public List<Product> searchProducts(String searchTerm) {
        try {
            String sql = """
                SELECT p.*,
                       c.name as category_name, c.slug as category_slug, c.description as category_description,
                       c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                       c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                       v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                       v.status as vendor_status, v.rating_average as vendor_rating
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN vendors v ON p.vendor_id = v.id
                WHERE (p.name ILIKE ? OR p.description ILIKE ?) AND p.is_active = true AND p.status::text = 'approved'
                ORDER BY p.name ASC
                """;
            String searchPattern = "%" + searchTerm + "%";
            List<Product> products = jdbcTemplate.query(sql, productRowMapper, searchPattern, searchPattern);
            for (Product product : products) {
                loadInventory(product);
                loadDefaultImages(product);
            }
            return products;
        } catch (Exception e) {
            System.err.println("Error in searchProducts (legacy): " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    // Update stock
    public void updateStock(String productId, int newStock) {
        String sql = "UPDATE products SET total_stock = ?, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, newStock, productId);
    }
    
    // Update rating
    public void updateAverageRating(String productId, BigDecimal averageRating) {
        String sql = "UPDATE products SET average_rating = ?, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, averageRating, productId);
    }
    
    // Update status
    public void updateStatus(String productId, String status) {
        String sql = "UPDATE products SET status = ?::product_status, updated_at = NOW() WHERE id = ?::uuid";
        jdbcTemplate.update(sql, status, productId);
    }

    // Inventory management methods
    public void addColorInventory(String productId, String color, String colorCode) {
        // This method is no longer needed in the new structure
        // Colors are added directly with their sizes
    }
    
    public void addSizeToColor(String productId, String color, String colorCode, String size, int initialStock) {
        String updateSql = """
            UPDATE product_inventory 
            SET stock = ?, is_available = ?, color_code = ?, updated_at = NOW()
            WHERE product_id = ?::uuid AND color = ? AND "size" = ?
            """;

        String insertSql = """
            INSERT INTO product_inventory (product_id, color, color_code, "size", stock, is_available, min_stock_threshold)
            VALUES (?::uuid, ?, ?, ?, ?, ?, 5)
            """;
        
        boolean isAvailable = initialStock > 0;
        int rows = jdbcTemplate.update(updateSql,
            initialStock,
            isAvailable,
            colorCode,
            productId,
            color,
            size
        );

        if (rows == 0) {
            jdbcTemplate.update(insertSql,
                productId,
                color,
                colorCode,
                size,
                initialStock,
                isAvailable
            );
        }

        updateTotalStock(productId);
    }
    
    public void updateStock(String productId, String color, String size, int newStock) {
        String sql = """
            UPDATE product_inventory 
            SET stock = ?, is_available = ?, updated_at = NOW() 
            WHERE product_id = ? AND color = ? AND "size" = ?
            """;
        boolean isAvailable = newStock > 0;
        jdbcTemplate.update(sql, newStock, isAvailable, java.util.UUID.fromString(productId), color, size);
        
        // Update total stock in products table
        updateTotalStock(productId);
    }
    
    public void decrementStock(String productId, String color, String size, int quantity) {
        String sql = """
            UPDATE product_inventory 
            SET stock = GREATEST(0, stock - ?), 
                is_available = (GREATEST(0, stock - ?) > 0), 
                updated_at = NOW() 
            WHERE product_id = ? AND color = ? AND "size" = ?
            """;
        jdbcTemplate.update(sql, quantity, quantity, java.util.UUID.fromString(productId), color, size);
        
        // Update total stock in products table
        updateTotalStock(productId);
    }
    
    private void updateTotalStock(String productId) {
        String sql = """
            UPDATE products 
            SET total_stock = (
                SELECT COALESCE(SUM(stock), 0) 
                FROM product_inventory 
                WHERE product_id = ?
            ), updated_at = NOW() 
            WHERE id = ?
            """;
        java.util.UUID uuid = java.util.UUID.fromString(productId);
        jdbcTemplate.update(sql, uuid, uuid);
    }
    
    public List<Map<String, Object>> getLowStockItems(String productId) {
        String sql = "SELECT color, \"size\", stock, min_stock_threshold FROM product_inventory WHERE product_id = ? AND stock <= min_stock_threshold ORDER BY stock ASC";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, java.util.UUID.fromString(productId));
        
        // Convert Long values to Integer for consistency
        List<Map<String, Object>> convertedResults = new ArrayList<>();
        for (Map<String, Object> row : results) {
            Map<String, Object> convertedRow = new HashMap<>();
            convertedRow.put("color", row.get("color"));
            convertedRow.put("size", row.get("size"));
            
            // Handle stock conversion
            Object stockObj = row.get("stock");
            if (stockObj instanceof Long) {
                convertedRow.put("stock", ((Long) stockObj).intValue());
            } else {
                convertedRow.put("stock", stockObj);
            }
            
            // Handle min_stock_threshold conversion
            Object thresholdObj = row.get("min_stock_threshold");
            if (thresholdObj instanceof Long) {
                convertedRow.put("min_stock_threshold", ((Long) thresholdObj).intValue());
            } else {
                convertedRow.put("min_stock_threshold", thresholdObj);
            }
            
            convertedResults.add(convertedRow);
        }
        
        return convertedResults;
    }
    
    public Map<String, Integer> getStockSummary(String productId) {
        String sql = "SELECT color, SUM(stock) as total_stock FROM product_inventory WHERE product_id = ? GROUP BY color ORDER BY color";
        java.util.UUID uuid = java.util.UUID.fromString(productId);
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, uuid);
        
        Map<String, Integer> summary = new HashMap<>();
        for (Map<String, Object> row : results) {
            String color = (String) row.get("color");
            Object stockObj = row.get("total_stock");
            int stock;
            if (stockObj instanceof Number) {
                stock = ((Number) stockObj).intValue();
            } else if (stockObj != null) {
                try { stock = Integer.parseInt(stockObj.toString()); } catch (Exception e) { stock = 0; }
            } else {
                stock = 0;
            }
            summary.put(color, stock);
        }
        return summary;
    }
    
    // COB (Customers Also Bought) methods
    public List<Product> getCustomersAlsoBoughtProducts(String vendorId, int limit) {
        String sql = """
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.is_customers_also_bought = true AND p.is_active = true
            """ + (vendorId != null ? " AND p.vendor_id = ?::uuid" : "") + """
            ORDER BY p.created_at DESC
            LIMIT ?
            """;
        
        List<Product> products;
        if (vendorId != null) {
            products = jdbcTemplate.query(sql, productRowMapper, vendorId, limit);
        } else {
            products = jdbcTemplate.query(sql, productRowMapper, limit);
        }
        
        // Load inventory for each product
        for (Product product : products) {
            loadInventory(product);
        }
        
        return products;
    }
    
    public List<Product> getCOBForProduct(String productId, int limit) {
        // First get the product to find its vendor
        Optional<Product> productOpt = findById(productId);
        if (productOpt.isEmpty()) {
            return new ArrayList<>();
        }
        
        Product product = productOpt.get();
        if (product.getVendor() == null || product.getVendor().getId() == null) {
            return new ArrayList<>();
        }
        
        String sql = """
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.is_customers_also_bought = true 
            AND p.is_active = true 
            AND p.vendor_id = ?::uuid 
            AND p.id != ?::uuid
            ORDER BY p.created_at DESC
            LIMIT ?
            """;
        
        List<Product> products = jdbcTemplate.query(sql, productRowMapper, 
            product.getVendor().getId(), productId, limit);
        
        // Load inventory for each product
        for (Product p : products) {
            loadInventory(p);
        }
        
        return products;
    }
    
    public List<Product> getVendorCOBProducts(String vendorId, int page, int limit) {
        String sql = """
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.vendor_id = ?::uuid 
            AND p.is_customers_also_bought = true 
            AND p.is_active = true
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
            """;
        
        List<Product> products = jdbcTemplate.query(sql, productRowMapper, 
            vendorId, limit, (page - 1) * limit);
        
        // Load inventory for each product
        for (Product product : products) {
            loadInventory(product);
        }
        
        return products;
    }
    
    public int countVendorCOBProducts(String vendorId) {
        String sql = "SELECT COUNT(*) FROM products WHERE vendor_id = ?::uuid AND is_customers_also_bought = true AND is_active = true";
        return jdbcTemplate.queryForObject(sql, Integer.class, vendorId);
    }
    
    public boolean updateCustomersAlsoBought(String productId, boolean isCustomersAlsoBought) {
        String sql = "UPDATE products SET is_customers_also_bought = ?, updated_at = NOW() WHERE id = ?::uuid";
        int rowsAffected = jdbcTemplate.update(sql, isCustomersAlsoBought, productId);
        return rowsAffected > 0;
    }
    
    // Product update and delete methods
    public boolean updateProduct(Product product) {
        String sql = """
            UPDATE products SET 
                name = ?, name_ar = ?, description = ?, description_ar = ?, price = ?, category_id = ?, vendor_id = ?, 
                gender = ?::product_gender, age_range = ?, total_stock = ?, discount_percentage = ?, 
                discount_valid_until = ?, average_rating = ?, status = ?::product_status, 
                is_active = ?, is_customers_also_bought = ?, reference_id = ?, updated_at = NOW()
            WHERE id = ?
            """;

        java.util.UUID categoryUuid = null;
        java.util.UUID vendorUuid = null;
        java.util.UUID idUuid = null;
        try { categoryUuid = product.getCategory() != null && product.getCategory().getId() != null ? java.util.UUID.fromString(product.getCategory().getId()) : null; } catch (Exception ignored) {}
        try { vendorUuid = product.getVendor() != null && product.getVendor().getId() != null ? java.util.UUID.fromString(product.getVendor().getId()) : null; } catch (Exception ignored) {}
        try { idUuid = product.getId() != null ? java.util.UUID.fromString(product.getId()) : null; } catch (Exception ignored) {}

        Timestamp discountUntil = null;
        if (product.getDiscount() != null && product.getDiscount().getEndDate() != null) {
            try { discountUntil = Timestamp.valueOf(java.time.LocalDateTime.parse(product.getDiscount().getEndDate())); } catch (Exception ignored) {}
        }

        int rowsAffected = jdbcTemplate.update(sql,
            product.getName(),
            product.getNameAr(),
            product.getDescription(),
            product.getDescriptionAr(),
            product.getPrice(),
            categoryUuid,
            vendorUuid,
            normalizeProductGender(product.getGender()),
            product.getAgeRange(),
            product.getTotalStock(),
            product.getDiscount() != null ? product.getDiscount().getDiscountValue() : null,
            discountUntil,
            product.getAverageRating(),
            product.getStatus(),
            product.getIsActive(),
            product.getIsCustomersAlsoBought(),
            product.getReferenceId(),
            idUuid
        );
        
        return rowsAffected > 0;
    }

    public boolean updateDiscount(String productId, BigDecimal discountPercentage, Timestamp discountValidUntil, boolean isActive) {
        try {
            String sql = """
                UPDATE products
                SET discount_percentage = ?, 
                    discount_valid_until = ?, 
                    updated_at = NOW()
                WHERE id = ?::uuid
                """;

            BigDecimal pct = (isActive && discountPercentage != null) ? discountPercentage : BigDecimal.ZERO;
            Timestamp end = (isActive) ? discountValidUntil : null;

            log.debug("Updating discount for product {}: percentage={}, validUntil={}, isActive={}", 
                productId, pct, end, isActive);
            
            int rows = jdbcTemplate.update(sql, pct, end, productId);
            
            if (rows == 0) {
                log.warn("No rows updated for product discount. Product ID: {}", productId);
            }
            
            return rows > 0;
        } catch (Exception e) {
            log.error("Error updating discount for product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }

    public boolean clearDiscount(String productId) {
        return updateDiscount(productId, BigDecimal.ZERO, null, false);
    }

    // Normalize incoming gender values to match the database enum product_gender
    private String normalizeProductGender(String gender) {
        if (gender == null || gender.isBlank()) return "Unisex";
        String g = gender.trim().toLowerCase();
        switch (g) {
            case "boy":
            case "male":
            case "men":
                return "Men";
            case "girl":
            case "female":
            case "women":
                return "Women";
            case "unisex":
                return "Unisex";
            case "none":
                return "None";
            default:
                return "Unisex";
        }
    }
    
    public boolean deleteProduct(String productId) {
        String sql = "DELETE FROM products WHERE id = ?::uuid";
        int rowsAffected = jdbcTemplate.update(sql, productId);
        return rowsAffected > 0;
    }
    
    // Product stats methods
    public Map<String, Object> getProductStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Total products count
        String totalSql = "SELECT COUNT(*) FROM products";
        int totalProducts = jdbcTemplate.queryForObject(totalSql, Integer.class);
        stats.put("total", totalProducts);
        
        // Active products count
        String activeSql = "SELECT COUNT(*) FROM products WHERE is_active = true";
        int activeProducts = jdbcTemplate.queryForObject(activeSql, Integer.class);
        stats.put("active", activeProducts);
        
        // Out of stock products count
        String outOfStockSql = "SELECT COUNT(*) FROM products WHERE is_active = true AND total_stock = 0";
        int outOfStockProducts = jdbcTemplate.queryForObject(outOfStockSql, Integer.class);
        stats.put("outOfStock", outOfStockProducts);
        
        // Low stock products count
        String lowStockSql = "SELECT COUNT(*) FROM products WHERE is_active = true AND total_stock > 0 AND total_stock < 10";
        int lowStockProducts = jdbcTemplate.queryForObject(lowStockSql, Integer.class);
        stats.put("lowStock", lowStockProducts);
        
        return stats;
    }
    
    // Fix stock method
    public boolean fixStock(String productId) {
        // Recalculate total stock from inventory
        String sql = "UPDATE products SET total_stock = (SELECT COALESCE(SUM(stock), 0) FROM product_inventory WHERE product_id = ?), updated_at = NOW() WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, productId, productId);
        return rowsAffected > 0;
    }
    
    // Get products by vendor
    public List<Product> getProductsByVendor(String vendorId, int page, int limit, String status) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   pc.id as parent_category_id_full, pc.name as parent_category_name, pc.slug as parent_category_slug,
                   pc.description as parent_category_description, pc.image_url as parent_category_image_url,
                   pc.image_public_id as parent_category_image_public_id, pc.parent_category_id as parent_parent_id,
                   pc.level as parent_category_level, pc.is_active as parent_category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN categories pc ON c.parent_category_id = pc.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.vendor_id = ?::uuid
            """);
        
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        
        // Add status filtering
        if ("approved".equals(status)) {
            sqlBuilder.append(" AND p.status::text = 'approved'");
        } else if ("rejected".equals(status)) {
            sqlBuilder.append(" AND p.status::text = 'rejected'");
        } else if ("pending".equals(status)) {
            sqlBuilder.append(" AND p.status::text = 'awaiting_approval'");
        } else {
            // Default: show all statuses
            sqlBuilder.append(" AND p.status::text IN ('rejected', 'approved', 'awaiting_approval')");
        }
        
        sqlBuilder.append(" ORDER BY p.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);
        
        List<Product> products = jdbcTemplate.query(sqlBuilder.toString(), productRowMapper, params.toArray());
        
        // Load inventory and default images for each product
        for (Product product : products) {
            loadInventory(product);
            loadDefaultImages(product);
            ensureParentCategoryLoaded(product);
        }
        
        return products;
    }

    // Fallback: if join didn't populate parentCategory but category has parentCategoryId, fetch and attach it
    private void ensureParentCategoryLoaded(Product product) {
        try {
            if (product.getParentCategory() == null && product.getCategory() != null && product.getCategory().getParentCategoryId() != null) {
                String parentId = product.getCategory().getParentCategoryId();
                String sql = "SELECT * FROM categories WHERE id = ?::uuid LIMIT 1";
                List<Product.Category> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    Product.Category cat = new Product.Category();
                    cat.setId(rs.getString("id"));
                    cat.setName(rs.getString("name"));
                    cat.setSlug(rs.getString("slug"));
                    cat.setDescription(rs.getString("description"));
                    cat.setImageUrl(rs.getString("image_url"));
                    cat.setImagePublicId(rs.getString("image_public_id"));
                    cat.setParentCategoryId(rs.getString("parent_category_id"));
                    cat.setLevel(rs.getObject("level") != null ? rs.getInt("level") : null);
                    cat.setIsActive(rs.getObject("is_active") != null ? rs.getBoolean("is_active") : null);
                    return cat;
                }, parentId);
                if (!rows.isEmpty()) {
                    product.setParentCategory(rows.get(0));
                }
            }
        } catch (Exception ignored) {}
    }

    // Lightweight fetch without inventory/images loading on Java side
    public List<Map<String, Object>> findLightweight(
            String categoryId,
            boolean includeChildren,
            String gender,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            String status,
            String sort,
            int limit,
            int offset
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                p.id,
                p.name,
                p.name_ar,
                p.price,
                p.status,
                p.gender,
                p.age_range,
                p.discount_percentage,
                p.discount_valid_until,
                c.slug AS category_slug,
                CASE 
                    WHEN p.description IS NULL THEN NULL 
                    ELSE LEFT(REGEXP_REPLACE(p.description, '\\s+', ' ', 'g'), 120)
                END AS short_description,
                p.description_ar,
                COALESCE(imgs.image_urls, ARRAY[]::text[]) AS image_urls,
                COALESCE(inv.colors, ARRAY[]::text[]) AS colors,
                COALESCE(inv_sizes.sizes, ARRAY[]::text[]) AS available_sizes
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN LATERAL (
                SELECT ARRAY(
                    SELECT pi.url 
                    FROM product_images pi 
                    WHERE pi.product_id = p.id AND pi.color IS NULL
                    ORDER BY pi.is_primary DESC, pi.created_at ASC
                    LIMIT 2
                ) AS image_urls
            ) imgs ON TRUE
            LEFT JOIN LATERAL (
                SELECT ARRAY(
                    SELECT DISTINCT color
                    FROM product_inventory pi
                    WHERE pi.product_id = p.id
                    ORDER BY color
                ) AS colors
            ) inv ON TRUE
            LEFT JOIN LATERAL (
                SELECT ARRAY(
                    SELECT DISTINCT "size"
                    FROM product_inventory pi
                    WHERE pi.product_id = p.id AND pi.stock > 0
                    ORDER BY "size"
                ) AS sizes
            ) inv_sizes ON TRUE
            WHERE p.is_active = TRUE
        """);

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?::product_status");
            params.add(status);
        } else {
            // Default to approved only
            sql.append(" AND p.status = 'approved'");
        }

        if (gender != null && !gender.isBlank()) {
            sql.append(" AND p.gender = ?::product_gender");
            params.add(gender);
        }

        if (categoryId != null && !categoryId.isBlank()) {
            if (includeChildren) {
                sql.append(" AND (p.category_id = ?::uuid OR c.parent_category_id = ?::uuid)");
                params.add(categoryId);
                params.add(categoryId);
            } else {
                sql.append(" AND p.category_id = ?::uuid");
                params.add(categoryId);
            }
        }

        if (minPrice != null) {
            sql.append(" AND p.price >= ?");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND p.price <= ?");
            params.add(maxPrice);
        }
        if (inStock != null && inStock) {
            sql.append(" AND p.total_stock > 0");
        }

        // Sorting
        sql.append(" ORDER BY ");
        switch (sort) {
            case "price":
                sql.append(" p.price ASC ");
                break;
            case "-price":
                sql.append(" p.price DESC ");
                break;
            case "createdAt":
                sql.append(" p.created_at ASC ");
                break;
            case "-createdAt":
            default:
                sql.append(" p.created_at DESC ");
                break;
        }

        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getString("id"));
            m.put("name", rs.getString("name"));
            try { m.put("nameAr", rs.getString("name_ar")); } catch (Exception ignored) {}
            m.put("price", rs.getBigDecimal("price"));
            m.put("status", rs.getString("status"));
            try { m.put("gender", rs.getString("gender")); } catch (Exception ignored) {}
            try { m.put("ageRange", rs.getString("age_range")); } catch (Exception ignored) {}
            m.put("slug", rs.getString("category_slug"));
            m.put("shortDescription", rs.getString("short_description"));
            try { m.put("descriptionAr", rs.getString("description_ar")); } catch (Exception ignored) {}
            try {
                java.sql.Array colorsArr = rs.getArray("colors");
                if (colorsArr != null) {
                    Object[] arr = (Object[]) colorsArr.getArray();
                    List<String> colors = new ArrayList<>();
                    for (Object o : arr) {
                        if (o != null) colors.add(o.toString());
                    }
                    m.put("colors", colors);
                }
            } catch (Exception ignored) {}
            try {
                java.sql.Array sizesArr = rs.getArray("available_sizes");
                if (sizesArr != null) {
                    Object[] arr = (Object[]) sizesArr.getArray();
                    List<String> sizes = new ArrayList<>();
                    for (Object o : arr) {
                        if (o != null) sizes.add(o.toString());
                    }
                    m.put("availableSizes", sizes);
                }
            } catch (Exception ignored) {}
            // Convert image_urls (text[]) to List<String>
            java.sql.Array arr = rs.getArray("image_urls");
            List<String> urls = new ArrayList<>();
            if (arr != null) {
                Object[] arrObj = (Object[]) arr.getArray();
                for (Object o : arrObj) {
                    if (o != null) urls.add(o.toString());
                }
            }
            m.put("images", urls);
            
            // Add discount information if available
            try {
                BigDecimal discountPercentage = rs.getBigDecimal("discount_percentage");
                Timestamp discountValidUntil = rs.getTimestamp("discount_valid_until");
                
                if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                    // Check if discount is still valid
                    boolean isActive = true;
                    if (discountValidUntil != null) {
                        isActive = discountValidUntil.after(new java.util.Date());
                    }
                    
                    if (isActive) {
                        Map<String, Object> discount = new HashMap<>();
                        discount.put("discountValue", discountPercentage);
                        discount.put("discountType", "percentage"); // Default to percentage since DB only stores percentage
                        if (discountValidUntil != null) {
                            discount.put("endDate", discountValidUntil.toInstant().toString());
                        }
                        discount.put("isActive", true);
                        m.put("discount", discount);
                    }
                }
            } catch (Exception ignored) {}
            
            return m;
        }, params.toArray());
    }

    // Lightweight: other products from same vendor excluding a product
    public List<Map<String, Object>> findVendorOthersLightweight(String vendorId, String excludeProductId, int limit) {
        String sql = """
            SELECT 
                p.id,
                p.name,
                p.price,
                p.status,
                c.slug AS category_slug,
                CASE 
                    WHEN p.description IS NULL THEN NULL 
                    ELSE LEFT(REGEXP_REPLACE(p.description, '\\s+', ' ', 'g'), 120)
                END AS short_description,
                COALESCE(imgs.image_urls, ARRAY[]::text[]) AS image_urls
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN LATERAL (
                SELECT ARRAY(
                    SELECT pi.url 
                    FROM product_images pi 
                    WHERE pi.product_id = p.id AND pi.color IS NULL
                    ORDER BY pi.is_primary DESC, pi.created_at ASC
                    LIMIT 2
                ) AS image_urls
            ) imgs ON TRUE
            WHERE p.is_active = TRUE
              AND p.status = 'approved'
              AND p.vendor_id = ?::uuid
              AND p.id != ?::uuid
            ORDER BY p.created_at DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getString("id"));
            m.put("name", rs.getString("name"));
            m.put("price", rs.getBigDecimal("price"));
            // keep minimal fields
            java.sql.Array arr = rs.getArray("image_urls");
            List<String> urls = new ArrayList<>();
            if (arr != null) {
                Object[] arrObj = (Object[]) arr.getArray();
                for (Object o : arrObj) {
                    if (o != null) urls.add(o.toString());
                }
            }
            m.put("images", urls);
            return m;
        }, vendorId, excludeProductId, limit);
    }
    
    public int countProductsByVendor(String vendorId, String status) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT COUNT(*) FROM products WHERE vendor_id = ?::uuid");
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        
        // Add status filtering
        if ("approved".equals(status)) {
            sqlBuilder.append(" AND status::text = 'approved'");
        } else if ("rejected".equals(status)) {
            sqlBuilder.append(" AND status::text = 'rejected'");
        } else if ("pending".equals(status)) {
            sqlBuilder.append(" AND status::text = 'awaiting_approval'");
        } else {
            // Default: show all statuses
            sqlBuilder.append(" AND status::text IN ('rejected', 'approved', 'awaiting_approval')");
        }
        
        return jdbcTemplate.queryForObject(sqlBuilder.toString(), Integer.class, params.toArray());
    }
    
    // Get all products by vendor with specific statuses (no pagination)
    public List<Product> getAllProductsByVendor(String vendorId, String status) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT p.*,
                   c.name as category_name, c.slug as category_slug, c.description as category_description,
                   c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                   c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                   v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                   v.status as vendor_status, v.rating_average as vendor_rating
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE p.vendor_id = ?::uuid
            """);
        
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        
        // Add status filtering
        if ("approved".equals(status)) {
            sqlBuilder.append(" AND p.status::text = 'approved'");
        } else if ("rejected".equals(status)) {
            sqlBuilder.append(" AND p.status::text = 'rejected'");
        } else if ("pending".equals(status)) {
            sqlBuilder.append(" AND p.status::text = 'awaiting_approval'");
        } else if ("awaiting_approval_and_approved".equals(status)) {
            // Special case for both awaiting_approval and approved
            sqlBuilder.append(" AND p.status::text IN ('awaiting_approval', 'approved')");
        } else {
            // Default: show all statuses
            sqlBuilder.append(" AND p.status::text IN ('rejected', 'approved', 'awaiting_approval')");
        }
        
        sqlBuilder.append(" ORDER BY p.created_at DESC");
        
        List<Product> products = jdbcTemplate.query(sqlBuilder.toString(), productRowMapper, params.toArray());
        
        // Load inventory and default images for each product
        for (Product product : products) {
            loadInventory(product);
            loadDefaultImages(product);
        }
        
        return products;
    }
    
    // Update approval details
    public boolean updateApprovalDetails(String productId, String action, String approvedBy, String reason) {
        String sql = """
            UPDATE products SET 
                approval_action = ?, approval_approved_by = ?::uuid, 
                approval_approved_at = NOW(), approval_reason = ?, updated_at = NOW()
            WHERE id = ?::uuid
            """;
        
        int rowsAffected = jdbcTemplate.update(sql, action, approvedBy, reason, productId);
        return rowsAffected > 0;
    }
    
    // Efficient vendor-specific queries
    public List<Product> findRecentProductsByVendorId(String vendorId, int limit) {
        try {
            String sql = """
                SELECT p.*,
                       c.name as category_name, c.slug as category_slug, c.description as category_description,
                       c.image_url as category_image_url, c.image_public_id as category_image_public_id,
                       c.parent_category_id as category_parent_id, c.level as category_level, c.is_active as category_is_active,
                       v.business_name as vendor_business_name, v.business_type as vendor_business_type,
                       v.status as vendor_status, v.rating_average as vendor_rating
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN vendors v ON p.vendor_id = v.id
                WHERE p.vendor_id = ?::uuid
                AND p.status IN ('awaiting_approval', 'approved')
                ORDER BY p.created_at DESC
                LIMIT ?
                """;
            List<Product> products = jdbcTemplate.query(sql, productRowMapper, parseUUID(vendorId), limit);
            for (Product product : products) {
                loadInventory(product);
                loadDefaultImages(product);
            }
            return products;
        } catch (Exception e) {
            System.err.println("Error finding recent products by vendor ID: " + vendorId + ", Error: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public int countActiveProductsByVendorId(String vendorId) {
        try {
            String sql = "SELECT COUNT(*) FROM products WHERE vendor_id = ?::uuid AND is_active = true";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parseUUID(vendorId));
            return count != null ? count : 0;
        } catch (Exception e) {
            System.err.println("Error counting active products by vendor ID: " + vendorId + ", Error: " + e.getMessage());
            return 0;
        }
    }
    
    private UUID parseUUID(String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format: " + uuidString, e);
        }
    }

    // Lightweight fetch by vendor
    public List<Map<String, Object>> findLightweightByVendor(
            String vendorId,
            String status,
            int limit,
            int offset
    ) {
        String sql = """
            SELECT 
                p.id,
                p.name,
                p.price,
                p.status,
                c.slug AS category_slug,
                CASE 
                    WHEN p.description IS NULL THEN NULL 
                    ELSE LEFT(REGEXP_REPLACE(p.description, '\\s+', ' ', 'g'), 120)
                END AS short_description,
                COALESCE(imgs.image_urls, ARRAY[]::text[]) AS image_urls
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN LATERAL (
                SELECT ARRAY(
                    SELECT pi.url 
                    FROM product_images pi 
                    WHERE pi.product_id = p.id AND pi.color IS NULL
                    ORDER BY pi.is_primary DESC, pi.created_at ASC
                    LIMIT 2
                ) AS image_urls
            ) imgs ON TRUE
            WHERE p.is_active = TRUE 
            AND p.vendor_id = ?::uuid
            AND p.status = ?::product_status
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
        """;

        return jdbcTemplate.query(sql, new Object[]{vendorId, status, limit, offset}, (rs, rowNum) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", rs.getString("id"));
            item.put("name", rs.getString("name"));
            item.put("price", rs.getBigDecimal("price"));
            item.put("status", rs.getString("status"));
            item.put("category_slug", rs.getString("category_slug"));
            item.put("short_description", rs.getString("short_description"));
            
            // Handle PostgreSQL array - more robust handling
            java.sql.Array array = rs.getArray("image_urls");
            List<String> imageUrls = new ArrayList<>();
            if (array != null) {
                Object[] arrayObj = (Object[]) array.getArray();
                for (Object obj : arrayObj) {
                    if (obj != null) {
                        imageUrls.add(obj.toString());
                    }
                }
            }
            item.put("image_urls", imageUrls);
            
            return item;
        });
    }

    // Get brands/vendors with stats for home page (only vendors with at least one approved active product)
    public List<Map<String, Object>> findBrandsWithStats(int page, int limit) {
        String sql = """
            SELECT 
                v.id as vendor_id,
                v.business_name,
                v.business_type,
                v.logo_url,
                v.rating_average,
                v.rating_count,
                COUNT(p.id) as product_count
            FROM vendors v
            LEFT JOIN products p ON p.vendor_id = v.id AND p.is_active = TRUE AND p.status = 'approved'
            WHERE v.status = 'approved'
            GROUP BY v.id, v.business_name, v.business_type, v.logo_url, v.rating_average, v.rating_count
            HAVING COUNT(p.id) > 0
            ORDER BY v.business_name ASC
            LIMIT ? OFFSET ?
        """;
        
        int offset = (page - 1) * limit;
        
        return jdbcTemplate.query(sql, new Object[]{limit, offset}, (rs, rowNum) -> {
            Map<String, Object> brand = new HashMap<>();
            brand.put("vendorId", rs.getString("vendor_id"));
            brand.put("businessName", rs.getString("business_name"));
            brand.put("businessType", rs.getString("business_type"));
            brand.put("logoUrl", rs.getString("logo_url"));
            brand.put("ratingAverage", rs.getBigDecimal("rating_average"));
            brand.put("ratingCount", rs.getInt("rating_count"));
            brand.put("productCount", rs.getInt("product_count"));
            return brand;
        });
    }

    // Count active approved vendors that have at least one approved active product
    public int countActiveVendors() {
        String sql = """
            SELECT COUNT(DISTINCT v.id)
            FROM vendors v
            INNER JOIN products p ON p.vendor_id = v.id AND p.is_active = TRUE AND p.status = 'approved'
            WHERE v.status = 'approved'
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    // Filtered Products for Vendor Dashboard
    public List<Map<String, Object>> findVendorProductsWithFilters(
            String vendorId, String productName, String startDate, 
            String endDate, String productStatus, int page, int limit) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                p.id,
                p.name,
                p.price,
                p.status,
                p.created_at,
                c.name as category_name
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.vendor_id = ?::uuid
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        
        if (productName != null && !productName.trim().isEmpty()) {
            sql.append(" AND LOWER(p.name) LIKE LOWER(?)");
            params.add("%" + productName.trim() + "%");
        }
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND p.created_at >= ?::timestamp");
            params.add(startDate.trim());
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND p.created_at <= ?::timestamp");
            params.add(endDate.trim() + " 23:59:59");
        }
        
        if (productStatus != null && !productStatus.trim().isEmpty()) {
            sql.append(" AND p.status = ?::product_status");
            params.add(productStatus.trim());
        }
        
        sql.append(" ORDER BY p.created_at DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);
        
        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            Map<String, Object> product = new HashMap<>();
            product.put("id", rs.getString("id"));
            product.put("name", rs.getString("name"));
            product.put("price", rs.getBigDecimal("price"));
            product.put("status", rs.getString("status"));
            product.put("categoryName", rs.getString("category_name"));
            product.put("createdAt", rs.getTimestamp("created_at"));
            return product;
        });
    }

    public int countVendorProductsWithFilters(
            String vendorId, String productName, String startDate, 
            String endDate, String productStatus) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM products p
            WHERE p.vendor_id = ?::uuid
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        
        if (productName != null && !productName.trim().isEmpty()) {
            sql.append(" AND LOWER(p.name) LIKE LOWER(?)");
            params.add("%" + productName.trim() + "%");
        }
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND p.created_at >= ?::timestamp");
            params.add(startDate.trim());
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND p.created_at <= ?::timestamp");
            params.add(endDate.trim() + " 23:59:59");
        }
        
        if (productStatus != null && !productStatus.trim().isEmpty()) {
            sql.append(" AND p.status = ?::product_status");
            params.add(productStatus.trim());
        }
        
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count != null ? count : 0;
    }

    // Search Methods
    public List<Map<String, Object>> searchProducts(String query, int limit) {
        try {
            String sql = """
                SELECT 
                    p.id,
                    p.name,
                    p.price,
                    p.status,
                    c.name as category_name,
                    c.slug as category_slug,
                    v.business_name as vendor_name,
                    v.id as vendor_id,
                    CASE 
                        WHEN p.description IS NULL THEN NULL 
                        ELSE LEFT(REGEXP_REPLACE(p.description, '\\s+', ' ', 'g'), 120)
                    END AS short_description,
                    COALESCE(imgs.image_urls, ARRAY[]::text[]) AS image_urls
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN vendors v ON p.vendor_id = v.id
                LEFT JOIN LATERAL (
                    SELECT ARRAY(
                        SELECT pi.url 
                        FROM product_images pi 
                        WHERE pi.product_id = p.id AND pi.color IS NULL
                        ORDER BY pi.is_primary DESC, pi.created_at ASC
                        LIMIT 2
                    ) AS image_urls
                ) imgs ON TRUE
                WHERE p.is_active = TRUE 
                    AND p.status::text = 'approved'
                    AND (
                        p.name ILIKE ? 
                        OR p.description ILIKE ?
                        OR c.name ILIKE ?
                        OR v.business_name ILIKE ?
                    )
                ORDER BY 
                    CASE 
                        WHEN p.name ILIKE ? THEN 1
                        WHEN c.name ILIKE ? THEN 2
                        WHEN v.business_name ILIKE ? THEN 3
                        ELSE 4
                    END,
                    p.created_at DESC
                LIMIT ?
            """;
            
            String searchPattern = "%" + query + "%";
            Object[] params = {
                searchPattern, searchPattern, searchPattern, searchPattern,
                searchPattern, searchPattern, searchPattern, limit
            };
            
            return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
                Map<String, Object> product = new HashMap<>();
                product.put("id", rs.getString("id"));
                product.put("name", rs.getString("name"));
                product.put("price", rs.getBigDecimal("price"));
                product.put("status", rs.getString("status"));
                product.put("categoryName", rs.getString("category_name"));
                product.put("categorySlug", rs.getString("category_slug"));
                product.put("vendorName", rs.getString("vendor_name"));
                product.put("vendorId", rs.getString("vendor_id"));
                product.put("shortDescription", rs.getString("short_description"));
                product.put("type", "product");
                
                // Handle PostgreSQL array for image_urls
                java.sql.Array array = rs.getArray("image_urls");
                List<String> imageUrls = new ArrayList<>();
                if (array != null) {
                    Object[] arrayObj = (Object[]) array.getArray();
                    for (Object obj : arrayObj) {
                        if (obj != null) {
                            imageUrls.add(obj.toString());
                        }
                    }
                }
                product.put("imageUrls", imageUrls);
                
                return product;
            });
        } catch (Exception e) {
            System.err.println("Error in searchProducts: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> searchCategories(String query, int limit) {
        try {
            String sql = """
                SELECT 
                    c.id,
                    c.name,
                    c.slug,
                    c.description,
                    c.image_url,
                    COUNT(p.id) as product_count
                FROM categories c
                LEFT JOIN products p ON p.category_id = c.id AND p.is_active = TRUE AND p.status::text = 'approved'
                WHERE c.name ILIKE ?
                    OR c.description ILIKE ?
                GROUP BY c.id, c.name, c.slug, c.description, c.image_url
                ORDER BY 
                    CASE 
                        WHEN c.name ILIKE ? THEN 1
                        ELSE 2
                    END,
                    product_count DESC
                LIMIT ?
            """;
            
            String searchPattern = "%" + query + "%";
            Object[] params = {searchPattern, searchPattern, searchPattern, limit};
            
            return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
                Map<String, Object> category = new HashMap<>();
                category.put("id", rs.getString("id"));
                category.put("name", rs.getString("name"));
                category.put("slug", rs.getString("slug"));
                category.put("description", rs.getString("description"));
                category.put("imageUrl", rs.getString("image_url"));
                category.put("productCount", rs.getInt("product_count"));
                category.put("type", "category");
                return category;
            });
        } catch (Exception e) {
            System.err.println("Error in searchCategories: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> searchVendors(String query, int limit) {
        try {
            String sql = """
                SELECT 
                    v.id as vendor_id,
                    v.business_name,
                    v.business_type,
                    v.logo_url,
                    v.rating_average,
                    v.rating_count,
                    COUNT(p.id) as product_count
                FROM vendors v
                LEFT JOIN products p ON p.vendor_id = v.id AND p.is_active = TRUE AND p.status::text = 'approved'
                WHERE v.status::text = 'approved'
                    AND (
                        v.business_name ILIKE ?
                        OR v.business_type::text ILIKE ?
                    )
                GROUP BY v.id, v.business_name, v.business_type, v.logo_url, v.rating_average, v.rating_count
                ORDER BY 
                    CASE 
                        WHEN v.business_name ILIKE ? THEN 1
                        ELSE 2
                    END,
                    product_count DESC
                LIMIT ?
            """;
            
            String searchPattern = "%" + query + "%";
            Object[] params = {searchPattern, searchPattern, searchPattern, limit};
            
            return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
                Map<String, Object> vendor = new HashMap<>();
                vendor.put("vendorId", rs.getString("vendor_id"));
                vendor.put("businessName", rs.getString("business_name"));
                vendor.put("businessType", rs.getString("business_type"));
                vendor.put("logoUrl", rs.getString("logo_url"));
                vendor.put("ratingAverage", rs.getBigDecimal("rating_average"));
                vendor.put("ratingCount", rs.getInt("rating_count"));
                vendor.put("productCount", rs.getInt("product_count"));
                vendor.put("type", "vendor");
                return vendor;
            });
        } catch (Exception e) {
            System.err.println("Error in searchVendors: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Search Suggestions Methods
    public List<String> getProductNameSuggestions(String query, int limit) {
        try {
            String sql = """
                SELECT DISTINCT p.name
                FROM products p
                WHERE p.is_active = TRUE 
                    AND p.status::text = 'approved'
                    AND p.name ILIKE ?
                ORDER BY p.name
                LIMIT ?
            """;
            
            String searchPattern = "%" + query + "%";
            Object[] params = {searchPattern, limit};
            
            return jdbcTemplate.queryForList(sql, params, String.class);
        } catch (Exception e) {
            System.err.println("Error in getProductNameSuggestions: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<String> getCategoryNameSuggestions(String query, int limit) {
        try {
            String sql = """
                SELECT DISTINCT c.name
                FROM categories c
                WHERE c.name ILIKE ?
                ORDER BY c.name
                LIMIT ?
            """;
            
            String searchPattern = "%" + query + "%";
            Object[] params = {searchPattern, limit};
            
            return jdbcTemplate.queryForList(sql, params, String.class);
        } catch (Exception e) {
            System.err.println("Error in getCategoryNameSuggestions: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<String> getVendorNameSuggestions(String query, int limit) {
        try {
            String sql = """
                SELECT DISTINCT v.business_name
                FROM vendors v
                WHERE v.status::text = 'approved'
                    AND v.business_name ILIKE ?
                ORDER BY v.business_name
                LIMIT ?
            """;
            
            String searchPattern = "%" + query + "%";
            Object[] params = {searchPattern, limit};
            
            return jdbcTemplate.queryForList(sql, params, String.class);
        } catch (Exception e) {
            System.err.println("Error in getVendorNameSuggestions: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}