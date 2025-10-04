package com.Daad.ecommerce.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
}
