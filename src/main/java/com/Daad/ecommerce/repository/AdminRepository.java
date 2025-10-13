package com.Daad.ecommerce.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Total Vendors
        String totalVendorsSql = "SELECT COUNT(*) FROM vendors WHERE status = 'approved'";
        int totalVendors = jdbcTemplate.queryForObject(totalVendorsSql, Integer.class);
        stats.put("totalVendors", totalVendors);
        
        // Total Customers (users with role 'customer')
        String totalCustomersSql = "SELECT COUNT(*) FROM users WHERE role = 'customer'";
        int totalCustomers = jdbcTemplate.queryForObject(totalCustomersSql, Integer.class);
        stats.put("totalCustomers", totalCustomers);
        
        // Total Revenue (sum of all delivered orders)
        String totalRevenueSql = "SELECT COALESCE(SUM(total), 0) FROM orders WHERE order_status = 'delivered'";
        BigDecimal totalRevenue = jdbcTemplate.queryForObject(totalRevenueSql, BigDecimal.class);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue.doubleValue() : 0.0);
        
        // Pending Approvals (vendors + products pending approval)
        String pendingVendorsSql = "SELECT COUNT(*) FROM vendors WHERE status = 'pending'";
        int pendingVendors = jdbcTemplate.queryForObject(pendingVendorsSql, Integer.class);
        
        String pendingProductsSql = "SELECT COUNT(*) FROM products WHERE status = 'awaiting_approval'";
        int pendingProducts = jdbcTemplate.queryForObject(pendingProductsSql, Integer.class);
        
        int pendingApprovals = pendingVendors + pendingProducts;
        stats.put("pendingApprovals", pendingApprovals);
        
        // Additional useful stats
        stats.put("pendingVendors", pendingVendors);
        stats.put("pendingProducts", pendingProducts);
        
        return stats;
    }

    public List<Map<String, Object>> getVendorSalesRanking() {
        String sql = """
            SELECT 
                v.id as vendor_id,
                v.business_name,
                v.business_type,
                v.status,
                v.rating_average,
                v.rating_count,
                COALESCE(SUM(oi.price * oi.quantity), 0) as total_sales,
                COUNT(DISTINCT o.id) as total_orders,
                COUNT(DISTINCT oi.product_id) as products_sold
            FROM vendors v
            LEFT JOIN products p ON p.vendor_id = v.id
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON o.id = oi.order_id AND o.order_status = 'delivered'
            WHERE v.status = 'approved'
            GROUP BY v.id, v.business_name, v.business_type, v.status, v.rating_average, v.rating_count
            ORDER BY total_sales DESC, total_orders DESC
        """;
        
        return jdbcTemplate.queryForList(sql);
    }

    // Commission rate helpers
    public double getGlobalCommissionRate() {
        try {
            String sql = "SELECT commission FROM vendors ORDER BY updated_at DESC LIMIT 1";
            Double rate = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getDouble(1) : null);
            return rate != null ? rate : 10.0; // default 10%
        } catch (Exception e) {
            return 10.0;
        }
    }

    public int setGlobalCommissionRate(double ratePercent) {
        String sql = "UPDATE vendors SET commission = ?, updated_at = NOW()";
        return jdbcTemplate.update(sql, ratePercent);
    }

    public List<Map<String, Object>> getVendorsWithCommissionInfo() {
        // First, get vendor and user info (fast query)
        String vendorSql = """
            SELECT 
                v.id as vendor_id,
                v.business_name,
                v.business_type,
                v.status,
                v.commission,
                v.rating_average,
                v.rating_count,
                v.created_at as vendor_created_at,
                u.id as user_id,
                u.username,
                u.email,
                u.profile_pic_url,
                u.is_verified,
                u.created_at as user_created_at
            FROM vendors v
            LEFT JOIN users u ON v.user_id = u.id
            WHERE v.status IN ('approved', 'pending')
            ORDER BY v.created_at DESC
        """;
        
        List<Map<String, Object>> vendors = jdbcTemplate.query(vendorSql, (rs, rowNum) -> {
            Map<String, Object> vendor = new HashMap<>();
            vendor.put("vendorId", rs.getString("vendor_id"));
            vendor.put("businessName", rs.getString("business_name"));
            vendor.put("businessType", rs.getString("business_type"));
            vendor.put("status", rs.getString("status"));
            vendor.put("commission", rs.getBigDecimal("commission"));
            vendor.put("ratingAverage", rs.getBigDecimal("rating_average"));
            vendor.put("ratingCount", rs.getInt("rating_count"));
            vendor.put("vendorCreatedAt", rs.getTimestamp("vendor_created_at"));
            
            // User info
            Map<String, Object> user = new HashMap<>();
            user.put("userId", rs.getString("user_id"));
            user.put("username", rs.getString("username"));
            user.put("email", rs.getString("email"));
            user.put("profilePicUrl", rs.getString("profile_pic_url"));
            user.put("isVerified", rs.getBoolean("is_verified"));
            user.put("userCreatedAt", rs.getTimestamp("user_created_at"));
            vendor.put("user", user);
            
            return vendor;
        });
        
        // Then, get sales data separately (optimized query)
        if (!vendors.isEmpty()) {
            try {
                String salesSql = """
                    SELECT 
                        p.vendor_id,
                        COALESCE(SUM(CASE WHEN o.order_status = 'delivered' THEN oi.price * oi.quantity ELSE 0 END), 0) as total_sales,
                        COUNT(DISTINCT CASE WHEN o.order_status = 'delivered' THEN o.id END) as total_orders
                    FROM products p
                    LEFT JOIN order_items oi ON oi.product_id = p.id
                    LEFT JOIN orders o ON o.id = oi.order_id
                    WHERE p.vendor_id = ANY(?)
                    GROUP BY p.vendor_id
                """;
                
                // Extract vendor IDs
                String[] vendorIds = vendors.stream()
                    .map(v -> (String) v.get("vendorId"))
                    .toArray(String[]::new);
                
                System.out.println("Querying sales data for " + vendorIds.length + " vendors");
                
                // Get sales data
                List<Map<String, Object>> salesData = jdbcTemplate.query(salesSql, 
                    vendorIds,
                    (rs, rowNum) -> {
                        Map<String, Object> sales = new HashMap<>();
                        sales.put("vendorId", rs.getString("vendor_id"));
                        sales.put("totalSales", rs.getBigDecimal("total_sales"));
                        sales.put("totalOrders", rs.getInt("total_orders"));
                        return sales;
                    });
                
                System.out.println("Found sales data for " + salesData.size() + " vendors");
                
                // Merge sales data with vendor data
                Map<String, Map<String, Object>> salesMap = salesData.stream()
                    .collect(java.util.stream.Collectors.toMap(
                        s -> (String) s.get("vendorId"),
                        s -> s
                    ));
                
                // Add sales data to vendors
                vendors.forEach(vendor -> {
                    String vendorId = (String) vendor.get("vendorId");
                    Map<String, Object> sales = salesMap.getOrDefault(vendorId, new HashMap<>());
                    vendor.put("totalSales", sales.getOrDefault("totalSales", java.math.BigDecimal.ZERO));
                    vendor.put("totalOrders", sales.getOrDefault("totalOrders", 0));
                });
                
            } catch (Exception e) {
                System.err.println("Error getting sales data: " + e.getMessage());
                e.printStackTrace();
                // Set default values for all vendors
                vendors.forEach(vendor -> {
                    vendor.put("totalSales", java.math.BigDecimal.ZERO);
                    vendor.put("totalOrders", 0);
                });
            }
        }
        
        return vendors;
    }

    public int updateVendorCommission(String vendorId, double commissionRate) {
        String sql = "UPDATE vendors SET commission = ?, updated_at = NOW() WHERE id = ?::uuid";
        return jdbcTemplate.update(sql, commissionRate, vendorId);
    }

    // Filtered Orders for Admin Dashboard
    public List<Map<String, Object>> findOrdersWithFilters(
            String productName, String startDate, String endDate, 
            String orderStatus, String vendorName, int page, int limit) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT
                o.id,
                o.order_status,
                o.total,
                o.customer_email,
                o.created_at,
                COALESCE(STRING_AGG(DISTINCT v.business_name, ', '), '') as vendor_name,
                COALESCE(STRING_AGG(DISTINCT p.name, ', '), '') as product_names
            FROM orders o
            LEFT JOIN order_items oi ON o.id = oi.order_id
            LEFT JOIN products p ON oi.product_id = p.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE o.id IS NOT NULL
        """);
        
        List<Object> params = new ArrayList<>();
        
        if (productName != null && !productName.trim().isEmpty()) {
            sql.append(" AND LOWER(p.name) LIKE LOWER(?)");
            params.add("%" + productName.trim() + "%");
        }
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND o.created_at >= ?::timestamp");
            params.add(startDate.trim());
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND o.created_at <= ?::timestamp");
            params.add(endDate.trim() + " 23:59:59");
        }
        
        if (orderStatus != null && !orderStatus.trim().isEmpty()) {
            sql.append(" AND o.order_status = ?::order_status");
            params.add(orderStatus.trim());
        }
        
        if (vendorName != null && !vendorName.trim().isEmpty()) {
            sql.append(" AND LOWER(v.business_name) LIKE LOWER(?)");
            params.add("%" + vendorName.trim() + "%");
        }
        
        sql.append(" GROUP BY o.id, o.order_status, o.total, o.customer_email, o.created_at");
        sql.append(" ORDER BY o.created_at DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add((page - 1) * limit);
        
        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            Map<String, Object> order = new HashMap<>();
            order.put("id", rs.getString("id"));
            order.put("orderStatus", rs.getString("order_status"));
            order.put("total", rs.getBigDecimal("total"));
            order.put("customerEmail", rs.getString("customer_email"));
            order.put("vendorName", rs.getString("vendor_name"));
            order.put("productNames", rs.getString("product_names"));
            order.put("createdAt", rs.getTimestamp("created_at"));
            return order;
        });
    }

    public int countOrdersWithFilters(
            String productName, String startDate, String endDate, 
            String orderStatus, String vendorName) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(DISTINCT o.id)
            FROM orders o
            LEFT JOIN order_items oi ON o.id = oi.order_id
            LEFT JOIN products p ON oi.product_id = p.id
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE o.id IS NOT NULL
        """);
        
        List<Object> params = new ArrayList<>();
        
        if (productName != null && !productName.trim().isEmpty()) {
            sql.append(" AND LOWER(p.name) LIKE LOWER(?)");
            params.add("%" + productName.trim() + "%");
        }
        
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND o.created_at >= ?::timestamp");
            params.add(startDate.trim());
        }
        
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND o.created_at <= ?::timestamp");
            params.add(endDate.trim() + " 23:59:59");
        }
        
        if (orderStatus != null && !orderStatus.trim().isEmpty()) {
            sql.append(" AND o.order_status = ?::order_status");
            params.add(orderStatus.trim());
        }
        
        if (vendorName != null && !vendorName.trim().isEmpty()) {
            sql.append(" AND LOWER(v.business_name) LIKE LOWER(?)");
            params.add("%" + vendorName.trim() + "%");
        }
        
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count != null ? count : 0;
    }

    // Filtered Products for Admin Dashboard
    public List<Map<String, Object>> findProductsWithFilters(
            String productName, String startDate, String endDate, 
            String productStatus, String vendorName, int page, int limit) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT 
                p.id,
                p.name,
                p.price,
                p.status,
                p.created_at,
                v.business_name as vendor_name
            FROM products p
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE 1=1
        """);
        
        List<Object> params = new ArrayList<>();
        
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
        
        if (vendorName != null && !vendorName.trim().isEmpty()) {
            sql.append(" AND LOWER(v.business_name) LIKE LOWER(?)");
            params.add("%" + vendorName.trim() + "%");
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
            product.put("vendorName", rs.getString("vendor_name"));
            product.put("createdAt", rs.getTimestamp("created_at"));
            return product;
        });
    }

    public int countProductsWithFilters(
            String productName, String startDate, String endDate, 
            String productStatus, String vendorName) {
        
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM products p
            LEFT JOIN vendors v ON p.vendor_id = v.id
            WHERE 1=1
        """);
        
        List<Object> params = new ArrayList<>();
        
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
        
        if (vendorName != null && !vendorName.trim().isEmpty()) {
            sql.append(" AND LOWER(v.business_name) LIKE LOWER(?)");
            params.add("%" + vendorName.trim() + "%");
        }
        
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count != null ? count : 0;
    }
}
