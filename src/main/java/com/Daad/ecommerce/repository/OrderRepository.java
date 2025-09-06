package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class OrderRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // Helper method to safely parse timestamp strings
    private Timestamp parseTimestamp(String dateTimeString) {
        try {
            if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
                return null;
            }
            return Timestamp.valueOf(java.time.LocalDateTime.parse(dateTimeString));
        } catch (Exception e) {
            // Log error and return null instead of crashing
            System.err.println("Failed to parse timestamp: " + dateTimeString + ", error: " + e.getMessage());
            return null;
        }
    }
    
    // Helper method to safely parse UUID strings
    private UUID parseUUID(String uuidString) {
        try {
            if (uuidString == null || uuidString.trim().isEmpty()) {
                return null;
            }
            return UUID.fromString(uuidString);
        } catch (Exception e) {
            // Log error and return null instead of crashing
            System.err.println("Failed to parse UUID: " + uuidString + ", error: " + e.getMessage());
            return null;
        }
    }

    private final RowMapper<Order> orderRowMapper = new RowMapper<Order>() {
        @Override
        public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
            Order order = new Order();
            order.setId(rs.getString("id"));
            order.setUserId(rs.getString("user_id"));
            order.setCustomerEmail(rs.getString("customer_email"));
            order.setSubtotal(rs.getBigDecimal("subtotal").doubleValue());
            order.setDiscount(rs.getBigDecimal("discount").doubleValue());
            order.setDiscountCode(rs.getString("discount_code"));
            order.setShippingCharges(rs.getBigDecimal("shipping_charges").doubleValue());
            order.setTotal(rs.getBigDecimal("total").doubleValue());
            order.setPointsUsed(rs.getInt("points_used"));
            order.setPointsEarned(rs.getInt("points_earned"));
            order.setPaymentMethod(rs.getString("payment_method"));
            order.setPaymentStatus(rs.getString("payment_status"));
            order.setOrderStatus(rs.getString("order_status"));
            order.setIsFirstOrder(rs.getBoolean("is_first_order"));
            order.setTrackingNumber(rs.getString("tracking_number"));
            order.setEstimatedDelivery(rs.getString("estimated_delivery"));
            order.setDeliveredAt(rs.getString("delivered_at"));
            order.setCancelledAt(rs.getString("cancelled_at"));
            order.setCancellationReason(rs.getString("cancellation_reason"));
            
            // Set timestamps
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                order.setCreatedAt(createdAtTs.toLocalDateTime());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                order.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
            
            return order;
        }
    };

    public Order save(Order order) {
        if (order.getId() == null) {
            return insert(order);
        } else {
            return update(order);
        }
    }
    
    private Order insert(Order order) {
        String sql = """
            INSERT INTO orders (
                user_id, customer_email, shipping_full_name, shipping_address_line1,
                shipping_address_line2, shipping_city, shipping_state, shipping_postal_code,
                shipping_country, shipping_phone_number, subtotal, discount, discount_code,
                shipping_charges, total, points_used, points_earned, payment_method,
                payment_status, payment_receipt_url, payment_receipt_uploaded,
                order_status, is_first_order, tracking_number, estimated_delivery,
                delivered_at, cancelled_at, cancellation_reason, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::payment_method, ?::payment_status, ?, ?, ?::order_status, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;

        jdbcTemplate.update(sql,
            parseUUID(order.getUserId()),
            order.getCustomerEmail(),
            order.getShippingAddress() != null ? order.getShippingAddress().getFullName() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getAddressLine1() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getAddressLine2() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getCity() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getState() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getPostalCode() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getCountry() : null,
            order.getShippingAddress() != null ? order.getShippingAddress().getPhoneNumber() : null,
            order.getSubtotal(),
            order.getDiscount() != null ? order.getDiscount() : 0.0,
            order.getDiscountCode(),
            order.getShippingCharges() != null ? order.getShippingCharges() : 0.0,
            order.getTotal(),
            order.getPointsUsed() != null ? order.getPointsUsed() : 0,
            order.getPointsEarned() != null ? order.getPointsEarned() : 0,
            order.getPaymentMethod() != null ? order.getPaymentMethod() : "cash-on-delivery",
            order.getPaymentStatus() != null ? order.getPaymentStatus() : "pending",
            order.getPaymentReceipt() != null ? order.getPaymentReceipt().getUrl() : null,
            order.getPaymentReceipt() != null ? order.getPaymentReceipt().getUploaded() : false,
            order.getOrderStatus() != null ? order.getOrderStatus() : "pending",
            order.getIsFirstOrder() != null ? order.getIsFirstOrder() : false,
            order.getTrackingNumber(),
            parseTimestamp(order.getEstimatedDelivery()),
            parseTimestamp(order.getDeliveredAt()),
            parseTimestamp(order.getCancelledAt()),
            order.getCancellationReason()
        );

        // Get the generated ID
        String idSql = "SELECT id FROM orders WHERE customer_email = ? ORDER BY created_at DESC LIMIT 1";
        String id = jdbcTemplate.queryForObject(idSql, String.class, order.getCustomerEmail());
        order.setId(id);
        
        return order;
    }
    
    private Order update(Order order) {
        String sql = """
            UPDATE orders SET 
                customer_email = ?, shipping_full_name = ?, shipping_address_line1 = ?,
                shipping_address_line2 = ?, shipping_city = ?, shipping_state = ?,
                shipping_postal_code = ?, shipping_country = ?, shipping_phone_number = ?,
                subtotal = ?, discount = ?, discount_code = ?, shipping_charges = ?,
                total = ?, points_used = ?, points_earned = ?, payment_method = ?::payment_method,
                payment_status = ?::payment_status, payment_receipt_url = ?,
                payment_receipt_uploaded = ?, order_status = ?::order_status,
                is_first_order = ?, tracking_number = ?, estimated_delivery = ?,
                delivered_at = ?, cancelled_at = ?, cancellation_reason = ?, updated_at = NOW()
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            order.getCustomerEmail(),
            order.getShippingAddress().getFullName(),
            order.getShippingAddress().getAddressLine1(),
            order.getShippingAddress().getAddressLine2(),
            order.getShippingAddress().getCity(),
            order.getShippingAddress().getState(),
            order.getShippingAddress().getPostalCode(),
            order.getShippingAddress().getCountry(),
            order.getShippingAddress().getPhoneNumber(),
            order.getSubtotal(),
            order.getDiscount(),
            order.getDiscountCode(),
            order.getShippingCharges(),
            order.getTotal(),
            order.getPointsUsed(),
            order.getPointsEarned(),
            order.getPaymentMethod(),
            order.getPaymentStatus(),
            order.getPaymentReceipt() != null ? order.getPaymentReceipt().getUrl() : null,
            order.getPaymentReceipt() != null ? order.getPaymentReceipt().getUploaded() : false,
            order.getOrderStatus(),
            order.getIsFirstOrder(),
            order.getTrackingNumber(),
            parseTimestamp(order.getEstimatedDelivery()),
            parseTimestamp(order.getDeliveredAt()),
            parseTimestamp(order.getCancelledAt()),
            order.getCancellationReason(),
            order.getId()
        );

        return order;
    }

    public Optional<Order> findById(String id) {
        String sql = "SELECT * FROM orders WHERE id = ? LIMIT 1";
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, id);
        return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM orders";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM orders WHERE order_status = ?::order_status";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status);
        return count != null ? count : 0;
    }

    public List<Order> findAllSortedByCreatedDesc() {
        String sql = "SELECT * FROM orders ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, orderRowMapper);
    }

    public List<Order> findByUserId(String userId) {
        String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, orderRowMapper, parseUUID(userId));
    }

    public List<Order> findAllByStatusOptional(String status) {
        String sql;
        if (status == null) {
            sql = "SELECT * FROM orders ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, orderRowMapper);
        } else {
            sql = "SELECT * FROM orders WHERE order_status = ?::order_status ORDER BY created_at DESC";
            return jdbcTemplate.query(sql, orderRowMapper, status);
        }
    }

    public double sumTotalExcludingCancelled() {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM orders WHERE order_status != 'cancelled'";
        Double total = jdbcTemplate.queryForObject(sql, Double.class);
        return total != null ? total : 0.0;
    }

    public double sumDiscountExcludingCancelled() {
        String sql = "SELECT COALESCE(SUM(discount), 0) FROM orders WHERE order_status != 'cancelled'";
        Double discount = jdbcTemplate.queryForObject(sql, Double.class);
        return discount != null ? discount : 0.0;
    }

    public OptionalDouble averageOrderValueExcludingCancelled() {
        String sql = "SELECT AVG(total) FROM orders WHERE order_status != 'cancelled'";
        Double average = jdbcTemplate.queryForObject(sql, Double.class);
        return average != null ? OptionalDouble.of(average) : OptionalDouble.empty();
    }

    public long countStudentUsers(Set<Long> studentUserIds) {
        if (studentUserIds.isEmpty()) {
            return 0;
        }
        String sql = "SELECT COUNT(DISTINCT user_id) FROM orders WHERE user_id = ANY(?)";
        // Convert Set<Long> to UUID array for PostgreSQL
        UUID[] uuidArray = studentUserIds.stream()
            .map(id -> parseUUID(id.toString()))
            .toArray(UUID[]::new);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, (Object) uuidArray);
        return count != null ? count : 0;
    }

    public Map<String, Long> pointsStats() {
        String sql = "SELECT COALESCE(SUM(points_earned), 0) as earned, COALESCE(SUM(points_used), 0) as used FROM orders";
        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalPointsEarned", ((Number) result.get("earned")).longValue());
        stats.put("totalPointsUsed", ((Number) result.get("used")).longValue());
        return stats;
    }

    public Map<String, Long> discountCountsByCode(String code) {
        String countSql = "SELECT COUNT(*) FROM orders WHERE discount_code = ? AND order_status != 'cancelled'";
        String amountSql = "SELECT COALESCE(SUM(discount), 0) FROM orders WHERE discount_code = ? AND order_status != 'cancelled'";
        
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, code);
        Double amount = jdbcTemplate.queryForObject(amountSql, Double.class, code);
        
        Map<String, Long> stats = new HashMap<>();
        stats.put("count", count != null ? count.longValue() : 0L);
        stats.put("totalAmount", amount != null ? amount.longValue() : 0L);
        return stats;
    }

    public void updateOrderStatus(String orderId, String status) {
        String sql = "UPDATE orders SET order_status = ?::order_status, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, status, orderId);
    }

    public void updatePaymentStatus(String orderId, String paymentStatus) {
        String sql = "UPDATE orders SET payment_status = ?::payment_status, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, paymentStatus, orderId);
    }

    public void updateTrackingNumber(String orderId, String trackingNumber) {
        String sql = "UPDATE orders SET tracking_number = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, trackingNumber, orderId);
    }

    public void updatePaymentReceipt(String orderId, String receiptUrl) {
        String sql = "UPDATE orders SET payment_receipt_url = ?, payment_receipt_uploaded = true, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, receiptUrl, orderId);
    }

    public List<Order> findRecentOrders(int limit) {
        String sql = "SELECT * FROM orders ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, orderRowMapper, limit);
    }

    public List<Order> findOrdersByDateRange(String startDate, String endDate) {
        String sql = "SELECT * FROM orders WHERE created_at >= ? AND created_at <= ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, orderRowMapper, 
            Timestamp.valueOf(java.time.LocalDateTime.parse(startDate)),
            Timestamp.valueOf(java.time.LocalDateTime.parse(endDate)));
    }
}
