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
            product.setDescription(rs.getString("description"));
            product.setPrice(rs.getBigDecimal("price"));
            product.setGender(rs.getString("gender"));
            product.setTotalStock(rs.getInt("total_stock"));
            product.setAverageRating(rs.getBigDecimal("average_rating"));
            product.setStatus(rs.getString("status"));
            product.setIsActive(rs.getBoolean("is_active"));
            product.setIsCustomersAlsoBought(rs.getBoolean("is_customers_also_bought"));
            
            // Set category with full details from JOIN
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
    
    // Method to load inventory for a product
    public void loadInventory(Product product) {
        if (product.getId() == null) {
            return;
        }
        
        String sql = "SELECT color, color_code, size, stock, is_available, min_stock_threshold FROM product_inventory WHERE product_id = ? ORDER BY color, size";
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
    }
    
    // Basic CRUD operations
    public List<Product> findAll() {
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
            WHERE p.is_active = true
            ORDER BY p.created_at DESC
            """;
        List<Product> products = jdbcTemplate.query(sql, productRowMapper);
        
        // Load inventory for each product
        for (Product product : products) {
            loadInventory(product);
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
        return Optional.of(product);
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
                name, description, price, category_id, vendor_id, gender,
                total_stock, discount_percentage, discount_valid_until,
                average_rating, status, is_active, is_customers_also_bought, created_at, updated_at
            ) VALUES (?, ?, ?, ?::uuid, ?::uuid, ?::product_gender, ?, ?, ?, ?, ?::product_status, ?, ?, NOW(), NOW())
            """;

        jdbcTemplate.update(sql,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategory().getId(),
            product.getVendor().getId(),
            product.getGender(),
            product.getTotalStock() != null ? product.getTotalStock() : 0,
            product.getDiscount() != null ? product.getDiscount().getDiscountValue() : BigDecimal.ZERO,
            product.getDiscount() != null && product.getDiscount().getEndDate() != null ? 
                Timestamp.valueOf(java.time.LocalDateTime.parse(product.getDiscount().getEndDate())) : null,
            product.getAverageRating() != null ? product.getAverageRating() : BigDecimal.ZERO,
            product.getStatus() != null ? product.getStatus() : "none",
            product.getIsActive() != null ? product.getIsActive() : true,
            product.getIsCustomersAlsoBought() != null ? product.getIsCustomersAlsoBought() : false
        );

        // Get the generated UUID
        String idSql = "SELECT id FROM products WHERE name = ? AND category_id = ?::uuid AND vendor_id = ?::uuid ORDER BY created_at DESC LIMIT 1";
        String generatedId = jdbcTemplate.queryForObject(idSql, String.class, 
            product.getName(), 
            product.getCategory().getId(),
            product.getVendor().getId()
        );
        
        // Set the generated ID
        product.setId(generatedId);
        
        return product;
    }
    
    private Product update(Product product) {
        String sql = """
            UPDATE products SET 
                name = ?, description = ?, price = ?, category_id = ?::uuid, vendor_id = ?::uuid,
                gender = ?::product_gender, total_stock = ?, discount_percentage = ?,
                discount_valid_until = ?, average_rating = ?, status = ?::product_status,
                is_active = ?, is_customers_also_bought = ?, updated_at = NOW()
            WHERE id = ?::uuid
            """;

        jdbcTemplate.update(sql,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategory().getId(),
            product.getVendor().getId(),
            product.getGender(),
            product.getTotalStock(),
            product.getDiscount() != null ? product.getDiscount().getDiscountValue() : BigDecimal.ZERO,
            product.getDiscount() != null && product.getDiscount().getEndDate() != null ? 
                Timestamp.valueOf(java.time.LocalDateTime.parse(product.getDiscount().getEndDate())) : null,
            product.getAverageRating(),
            product.getStatus(),
            product.getIsActive(),
            product.getIsCustomersAlsoBought(),
            product.getId()
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
        String sql = "SELECT * FROM products WHERE (name ILIKE ? OR description ILIKE ?) AND is_active = true ORDER BY name ASC";
        String searchPattern = "%" + searchTerm + "%";
        return jdbcTemplate.query(sql, productRowMapper, searchPattern, searchPattern);
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
        String sql = """
            INSERT INTO product_inventory (product_id, color, color_code, size, stock, is_available, min_stock_threshold) 
            VALUES (?, ?, ?, ?, ?, ?, 5) 
            ON CONFLICT (product_id, color, size) 
            DO UPDATE SET 
                stock = EXCLUDED.stock, 
                is_available = EXCLUDED.is_available,
                color_code = EXCLUDED.color_code,
                updated_at = NOW()
            """;
        
        boolean isAvailable = initialStock > 0;
        
        jdbcTemplate.update(sql, 
            java.util.UUID.fromString(productId), // product_id as UUID
            color,               // color
            colorCode,           // color_code
            size,                // size
            initialStock,        // stock
            isAvailable          // is_available
            // min_stock_threshold is set to 5 directly in SQL
        );
        
        // Update total stock in products table
        updateTotalStock(productId);
    }
    
    public void updateStock(String productId, String color, String size, int newStock) {
        String sql = """
            UPDATE product_inventory 
            SET stock = ?, is_available = ?, updated_at = NOW() 
            WHERE product_id = ? AND color = ? AND size = ?
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
            WHERE product_id = ? AND color = ? AND size = ?
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
        String sql = "SELECT color, size, stock, min_stock_threshold FROM product_inventory WHERE product_id = ? AND stock <= min_stock_threshold ORDER BY stock ASC";
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
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, productId);
        
        Map<String, Integer> summary = new HashMap<>();
        for (Map<String, Object> row : results) {
            String color = (String) row.get("color");
            Integer stock = (Integer) row.get("total_stock");
            summary.put(color, stock != null ? stock : 0);
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
                name = ?, description = ?, price = ?, category_id = ?, vendor_id = ?, 
                gender = ?::product_gender, total_stock = ?, discount_percentage = ?, 
                discount_valid_until = ?, average_rating = ?, status = ?::product_status, 
                is_active = ?, is_customers_also_bought = ?, updated_at = NOW()
            WHERE id = ?::uuid
            """;
        
        int rowsAffected = jdbcTemplate.update(sql,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategory() != null ? product.getCategory().getId() : null,
            product.getVendor() != null ? product.getVendor().getId() : null,
            product.getGender(),
            product.getTotalStock(),
            product.getDiscount() != null ? product.getDiscount().getDiscountValue() : null,
            product.getDiscount() != null ? product.getDiscount().getEndDate() : null,
            product.getAverageRating(),
            product.getStatus(),
            product.getIsActive(),
            product.getIsCustomersAlsoBought(),
            product.getId()
        );
        
        return rowsAffected > 0;
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
            sqlBuilder.append(" AND p.status = 'approved'");
        } else if ("rejected".equals(status)) {
            sqlBuilder.append(" AND p.status = 'rejected'");
        } else if ("pending".equals(status)) {
            sqlBuilder.append(" AND p.status = 'awaiting_approval'");
        } else {
            // Default: show all statuses
            sqlBuilder.append(" AND p.status IN ('rejected', 'approved', 'awaiting_approval')");
        }
        
        sqlBuilder.append(" ORDER BY p.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);
        
        List<Product> products = jdbcTemplate.query(sqlBuilder.toString(), productRowMapper, params.toArray());
        
        // Load inventory for each product
        for (Product product : products) {
            loadInventory(product);
        }
        
        return products;
    }
    
    public int countProductsByVendor(String vendorId, String status) {
        StringBuilder sqlBuilder = new StringBuilder("SELECT COUNT(*) FROM products WHERE vendor_id = ?::uuid");
        List<Object> params = new ArrayList<>();
        params.add(vendorId);
        
        // Add status filtering
        if ("approved".equals(status)) {
            sqlBuilder.append(" AND status = 'approved'");
        } else if ("rejected".equals(status)) {
            sqlBuilder.append(" AND status = 'rejected'");
        } else if ("pending".equals(status)) {
            sqlBuilder.append(" AND status = 'awaiting_approval'");
        } else {
            // Default: show all statuses
            sqlBuilder.append(" AND status IN ('rejected', 'approved', 'awaiting_approval')");
        }
        
        return jdbcTemplate.queryForObject(sqlBuilder.toString(), Integer.class, params.toArray());
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
}