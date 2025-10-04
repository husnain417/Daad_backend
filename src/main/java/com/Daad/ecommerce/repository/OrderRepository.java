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
import java.util.ArrayList;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private VendorRepository vendorRepository;
    
    @Autowired
    private UserRepository userRepository;
    
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
            
            // Build shipping address from individual columns
            Order.ShippingAddress shippingAddress = new Order.ShippingAddress();
            shippingAddress.setFullName(rs.getString("shipping_full_name"));
            shippingAddress.setAddressLine1(rs.getString("shipping_address_line1"));
            shippingAddress.setAddressLine2(rs.getString("shipping_address_line2"));
            shippingAddress.setCity(rs.getString("shipping_city"));
            shippingAddress.setState(rs.getString("shipping_state"));
            shippingAddress.setPostalCode(rs.getString("shipping_postal_code"));
            shippingAddress.setCountry(rs.getString("shipping_country"));
            shippingAddress.setPhoneNumber(rs.getString("shipping_phone_number"));
            shippingAddress.setEmail(rs.getString("customer_email")); // Use customer email for shipping
            order.setShippingAddress(shippingAddress);
            
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
    
    // Row mapper for orders with user information
    private final RowMapper<Order> orderWithUserRowMapper = new RowMapper<Order>() {
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
            
            // Build shipping address from individual columns
            Order.ShippingAddress shippingAddress = new Order.ShippingAddress();
            shippingAddress.setFullName(rs.getString("shipping_full_name"));
            shippingAddress.setAddressLine1(rs.getString("shipping_address_line1"));
            shippingAddress.setAddressLine2(rs.getString("shipping_address_line2"));
            shippingAddress.setCity(rs.getString("shipping_city"));
            shippingAddress.setState(rs.getString("shipping_state"));
            shippingAddress.setPostalCode(rs.getString("shipping_postal_code"));
            shippingAddress.setCountry(rs.getString("shipping_country"));
            shippingAddress.setPhoneNumber(rs.getString("shipping_phone_number"));
            shippingAddress.setEmail(rs.getString("customer_email")); // Use customer email for shipping
            order.setShippingAddress(shippingAddress);
            
            // Set timestamps
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                order.setCreatedAt(createdAtTs.toLocalDateTime());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                order.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
            
            // Set payment-related fields (set to null since these columns don't exist in the current schema)
            order.setPaymentProvider(null);
            order.setPaymentReference(null);
            order.setTransactionId(null);
            
            // Create user information map
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", rs.getString("username"));
            userInfo.put("email", rs.getString("user_email"));
            userInfo.put("firstName", null); // Not available in users table
            userInfo.put("lastName", null); // Not available in users table
            userInfo.put("phoneNumber", null); // Not available in users table
            
            // Add user info to order (we'll extend Order DTO to include this)
            order.setUserInfo(userInfo);
            
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
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, parseUUID(id));
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        
        Order order = orders.get(0);
        // Load order items
        loadOrderItems(order);
        return Optional.of(order);
    }
    
    private void loadOrderItems(Order order) {
        try {
            // Use a completely different SQL structure to avoid prepared statement conflicts
            String sql = """
                SELECT 
                    oi.product_id, 
                    p.vendor_id, 
                    oi.product_name, 
                    oi.color, 
                    oi.size, 
                    oi.quantity, 
                    oi.price
                FROM order_items oi
                LEFT JOIN products p ON p.id = oi.product_id
                WHERE oi.order_id = ?::uuid
                ORDER BY oi.product_name
                """;
            
            System.out.println("Loading order items for order ID: " + order.getId());
            UUID orderIdUuid = parseUUID(order.getId());
            System.out.println("Parsed UUID: " + orderIdUuid);
            
            List<Order.Item> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Order.Item item = new Order.Item();
                item.setProduct(rs.getString("product_id"));
                item.setVendorId(rs.getString("vendor_id"));
                item.setProductName(rs.getString("product_name"));
                item.setColor(rs.getString("color"));
                item.setSize(rs.getString("size"));
                item.setQuantity(rs.getInt("quantity"));
                item.setPrice(rs.getDouble("price"));
                return item;
            }, orderIdUuid);
            
            System.out.println("Loaded " + items.size() + " order items");
            order.setItems(items);
        } catch (Exception e) {
            System.err.println("Error loading order items for order " + order.getId() + ": " + e.getMessage());
            e.printStackTrace();
            order.setItems(new ArrayList<>());
        }
    }

    public void insertOrderItems(String orderId, List<Order.Item> items) {
        if (items == null || items.isEmpty()) return;
        String sql = """
            INSERT INTO order_items (
                order_id, product_id, product_name, color, size, quantity, price
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        for (Order.Item it : items) {
            jdbcTemplate.update(sql,
                parseUUID(orderId),
                parseUUID(it.getProduct()),
                it.getProductName(),
                it.getColor(),
                it.getSize(),
                it.getQuantity(),
                it.getPrice()
            );
        }
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
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper);
        // Load order items for each order
        for (Order order : orders) {
            loadOrderItems(order);
        }
        return orders;
    }

    public List<Order> findByUserId(String userId) {
        String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC";
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, parseUUID(userId));
        // Load order items for each order
        for (Order order : orders) {
            loadOrderItems(order);
        }
        return orders;
    }

    // Optimized method to get user orders with vendor details
    public List<Map<String, Object>> findUserOrdersWithVendorDetails(String userId) {
        // First get all orders for the user
        List<Order> orders = findByUserId(userId);
        
        // Process each order to include vendor details
        List<Map<String, Object>> processedOrders = new ArrayList<>();
        
        for (Order order : orders) {
            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("id", order.getId());
            orderMap.put("userId", order.getUserId());
            
            // Process order items to include default image URLs
            List<Map<String, Object>> processedItems = new ArrayList<>();
            for (Order.Item item : order.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("product", item.getProduct());
                itemMap.put("vendorId", item.getVendorId());
                itemMap.put("productName", item.getProductName());
                itemMap.put("color", item.getColor());
                itemMap.put("size", item.getSize());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("price", item.getPrice());
                
                // Get default image URL for the product
                String defaultImageUrl = getProductDefaultImageUrl(item.getProduct());
                itemMap.put("defaultImageUrl", defaultImageUrl);
                
                processedItems.add(itemMap);
            }
            orderMap.put("items", processedItems);
            
            orderMap.put("shippingAddress", order.getShippingAddress());
            orderMap.put("subtotal", order.getSubtotal());
            orderMap.put("discount", order.getDiscount());
            orderMap.put("discountCode", order.getDiscountCode());
            orderMap.put("shippingCharges", order.getShippingCharges());
            orderMap.put("total", order.getTotal());
            orderMap.put("pointsUsed", order.getPointsUsed());
            orderMap.put("pointsEarned", order.getPointsEarned());
            orderMap.put("paymentMethod", order.getPaymentMethod());
            orderMap.put("paymentStatus", order.getPaymentStatus());
            orderMap.put("paymentProvider", order.getPaymentProvider());
            orderMap.put("transactionId", order.getTransactionId());
            orderMap.put("paymentReference", order.getPaymentReference());
            orderMap.put("paymentReceipt", order.getPaymentReceipt());
            orderMap.put("orderStatus", order.getOrderStatus());
            orderMap.put("isFirstOrder", order.getIsFirstOrder());
            orderMap.put("trackingNumber", order.getTrackingNumber());
            orderMap.put("estimatedDelivery", order.getEstimatedDelivery());
            orderMap.put("deliveredAt", order.getDeliveredAt());
            orderMap.put("cancelledAt", order.getCancelledAt());
            orderMap.put("cancellationReason", order.getCancellationReason());
            orderMap.put("customerEmail", order.getCustomerEmail());
            orderMap.put("createdAt", order.getCreatedAt());
            orderMap.put("updatedAt", order.getUpdatedAt());
            orderMap.put("userInfo", order.getUserInfo());
            
            // Get vendor information from order items
            Set<String> vendorIds = order.getItems().stream()
                .map(item -> item.getVendorId())
                .filter(vendorId -> vendorId != null)
                .collect(Collectors.toSet());
            
            List<Map<String, Object>> vendors = new ArrayList<>();
            for (String vendorId : vendorIds) {
                try {
                    vendorRepository.findById(vendorId).ifPresent(vendor -> {
                        Map<String, Object> vendorInfo = new HashMap<>();
                        vendorInfo.put("id", vendor.getId());
                        vendorInfo.put("businessName", vendor.getBusinessName());
                        vendorInfo.put("businessType", vendor.getBusinessType());
                        vendorInfo.put("status", vendor.getStatus());
                        vendorInfo.put("rating", vendor.getRating());
                        vendorInfo.put("phone", vendor.getPhoneNumber());
                        
                        // Get vendor email from users table
                        if (vendor.getUser() != null && vendor.getUser().getId() != null) {
                            userRepository.findById(vendor.getUser().getId()).ifPresent(user -> {
                                vendorInfo.put("email", user.getEmail());
                                vendorInfo.put("username", user.getUsername());
                            });
                        }
                        
                        vendors.add(vendorInfo);
                    });
                } catch (Exception e) {
                    System.err.println("Error loading vendor " + vendorId + ": " + e.getMessage());
                }
            }
            orderMap.put("vendors", vendors);
            
            processedOrders.add(orderMap);
        }
        
        return processedOrders;
    }
    
    // Helper method to get product default image URL
    private String getProductDefaultImageUrl(String productId) {
        try {
            String sql = """
                SELECT url 
                FROM product_images 
                WHERE product_id = ? AND color IS NULL 
                ORDER BY is_primary DESC, created_at ASC 
                LIMIT 1
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, parseUUID(productId));
            if (!results.isEmpty()) {
                return (String) results.get(0).get("url");
            }
        } catch (Exception e) {
            System.err.println("Error loading default image for product " + productId + ": " + e.getMessage());
        }
        return null;
    }

    public List<Order> findAllByStatusOptional(String status) {
        String sql;
        List<Order> orders;
        if (status == null) {
            sql = "SELECT * FROM orders ORDER BY created_at DESC";
            orders = jdbcTemplate.query(sql, orderRowMapper);
        } else {
            sql = "SELECT * FROM orders WHERE order_status = ?::order_status ORDER BY created_at DESC";
            orders = jdbcTemplate.query(sql, orderRowMapper, status);
        }
        // Load order items for each order
        for (Order order : orders) {
            loadOrderItems(order);
        }
        return orders;
    }

    public List<Order> findByVendorId(String vendorId) {
        String sql = """
            SELECT DISTINCT 
                o.id, o.user_id, o.shipping_full_name, o.shipping_address_line1, o.shipping_address_line2,
                o.shipping_city, o.shipping_state, o.shipping_postal_code, o.shipping_country, o.shipping_phone_number,
                o.subtotal, o.discount, o.discount_code, o.shipping_charges, o.total, o.points_used, o.points_earned, 
                o.payment_method, o.payment_status, o.payment_receipt_url, o.order_status, o.is_first_order,
                o.tracking_number, o.estimated_delivery, o.delivered_at, o.cancelled_at,
                o.cancellation_reason, o.customer_email, o.created_at, o.updated_at,
                u.username, u.email as user_email
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            JOIN products p ON p.id = oi.product_id
            LEFT JOIN users u ON o.user_id = u.id
            WHERE p.vendor_id = ?::uuid
            ORDER BY o.created_at DESC
            """;
        List<Order> orders = jdbcTemplate.query(sql, orderWithUserRowMapper, parseUUID(vendorId));
        // Load order items for each order
        for (Order order : orders) {
            loadOrderItems(order);
        }
        return orders;
    }

    public boolean vendorOwnsOrder(String orderId, String vendorId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM order_items oi
                JOIN products p ON p.id = oi.product_id
                WHERE oi.order_id = ? AND p.vendor_id = ?
            )
            """;
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, parseUUID(orderId), parseUUID(vendorId));
        return exists != null && exists;
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
        jdbcTemplate.update(sql, status, parseUUID(orderId));
    }

    public void updatePaymentStatus(String orderId, String paymentStatus) {
        String sql = "UPDATE orders SET payment_status = ?::payment_status, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, paymentStatus, parseUUID(orderId));
    }

    public void updateTrackingNumber(String orderId, String trackingNumber) {
        String sql = "UPDATE orders SET tracking_number = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, trackingNumber, parseUUID(orderId));
    }

    public void cancelOrder(String orderId, String reason) {
        String sql = "UPDATE orders SET order_status = 'cancelled', cancelled_at = NOW(), cancellation_reason = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, reason, parseUUID(orderId));
    }

    public void updatePaymentReceipt(String orderId, String receiptUrl) {
        String sql = "UPDATE orders SET payment_receipt_url = ?, payment_receipt_uploaded = true, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, receiptUrl, parseUUID(orderId));
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

    // Vendor analytics aggregates based on order_items to attribute revenue correctly
    public Map<String, Object> vendorSalesReport(String vendorId) {
        String sql = """
            SELECT 
                COUNT(DISTINCT o.id) AS total_orders,
                COALESCE(SUM(oi.quantity), 0) AS total_items_sold,
                COALESCE(SUM(oi.quantity * oi.price), 0) AS total_revenue
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            JOIN products p ON p.id = oi.product_id
            WHERE p.vendor_id = ? AND o.order_status != 'cancelled'
            """;
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, parseUUID(vendorId));
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("totalOrders", ((Number) result.get("total_orders")).longValue());
        out.put("totalItemsSold", ((Number) result.get("total_items_sold")).longValue());
        out.put("totalRevenue", ((Number) result.get("total_revenue")).doubleValue());
        return out;
    }

    public Map<String, Object> vendorCustomerInsights(String vendorId) {
        String distinctSql = """
            SELECT COUNT(DISTINCT o.customer_email) AS distinct_customers
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            JOIN products p ON p.id = oi.product_id
            WHERE p.vendor_id = ? AND o.order_status != 'cancelled'
            """;
        Long distinctCustomers = jdbcTemplate.queryForObject(distinctSql, Long.class, parseUUID(vendorId));

        String repeatSql = """
            SELECT COUNT(*) FROM (
                SELECT o.customer_email, COUNT(DISTINCT o.id) cnt
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                JOIN products p ON p.id = oi.product_id
                WHERE p.vendor_id = ? AND o.order_status != 'cancelled'
                GROUP BY o.customer_email
                HAVING COUNT(DISTINCT o.id) > 1
            ) t
            """;
        Long repeatCustomers = jdbcTemplate.queryForObject(repeatSql, Long.class, parseUUID(vendorId));

        String aovSql = """
            SELECT AVG(order_total) FROM (
                SELECT o.id, SUM(oi.quantity * oi.price) AS order_total
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                JOIN products p ON p.id = oi.product_id
                WHERE p.vendor_id = ? AND o.order_status != 'cancelled'
                GROUP BY o.id
            ) s
            """;
        Double avgOrderValue = jdbcTemplate.queryForObject(aovSql, Double.class, parseUUID(vendorId));

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("distinctCustomers", distinctCustomers != null ? distinctCustomers : 0L);
        out.put("repeatCustomers", repeatCustomers != null ? repeatCustomers : 0L);
        out.put("averageOrderValue", avgOrderValue != null ? avgOrderValue : 0.0);
        return out;
    }
    
    // Efficient vendor-specific queries
    public List<Order> findOrdersByVendorId(String vendorId) {
        try {
            String sql = """
                SELECT DISTINCT 
                    o.id, o.user_id, o.shipping_address, o.subtotal, o.discount, o.discount_code,
                    o.shipping_charges, o.total, o.points_used, o.points_earned, o.payment_method,
                    o.payment_status, o.payment_receipt_url, o.order_status, o.is_first_order,
                    o.tracking_number, o.estimated_delivery, o.delivered_at, o.cancelled_at,
                    o.cancellation_reason, o.customer_email, o.created_at, o.updated_at,
                    o.payment_provider, o.payment_reference, o.transaction_id, o.paid_at,
                    o.failure_reason, o.payment_metadata
                FROM orders o
                INNER JOIN order_items oi ON o.id = oi.order_id
                INNER JOIN products p ON oi.product_id = p.id
                WHERE p.vendor_id = ?::uuid
                ORDER BY o.created_at DESC
                """;
            List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, parseUUID(vendorId));
            for (Order order : orders) {
                loadOrderItems(order);
            }
            return orders;
        } catch (Exception e) {
            System.err.println("Error finding orders by vendor ID: " + vendorId + ", Error: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Order> findRecentOrdersByVendorId(String vendorId, int limit) {
        try {
            System.out.println("Finding recent orders for vendor ID: " + vendorId + " with limit: " + limit);
            
            // First try the efficient query
            String sql = """
                SELECT DISTINCT 
                    o.id, o.user_id, o.shipping_address, o.subtotal, o.discount, o.discount_code,
                    o.shipping_charges, o.total, o.points_used, o.points_earned, o.payment_method,
                    o.payment_status, o.payment_receipt_url, o.order_status, o.is_first_order,
                    o.tracking_number, o.estimated_delivery, o.delivered_at, o.cancelled_at,
                    o.cancellation_reason, o.customer_email, o.created_at, o.updated_at,
                    o.payment_provider, o.payment_reference, o.transaction_id, o.paid_at,
                    o.failure_reason, o.payment_metadata
                FROM orders o
                INNER JOIN order_items oi ON o.id = oi.order_id
                INNER JOIN products p ON oi.product_id = p.id
                WHERE p.vendor_id = ?::uuid
                ORDER BY o.created_at DESC
                LIMIT ?
                """;
            
            List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, parseUUID(vendorId), limit);
            System.out.println("Efficient query found " + orders.size() + " orders");
            
            // If no orders found, try fallback method
            if (orders.isEmpty()) {
                System.out.println("No orders found with efficient query, trying fallback method");
                return findRecentOrdersByVendorIdFallback(vendorId, limit);
            }
            
            for (Order order : orders) {
                loadOrderItems(order);
            }
            return orders;
        } catch (Exception e) {
            System.err.println("Error finding recent orders by vendor ID: " + vendorId + ", Error: " + e.getMessage());
            e.printStackTrace();
            // Try fallback method
            return findRecentOrdersByVendorIdFallback(vendorId, limit);
        }
    }
    
    private List<Order> findRecentOrdersByVendorIdFallback(String vendorId, int limit) {
        try {
            System.out.println("Using fallback method for vendor orders");
            // Fallback: get all orders and filter in memory (less efficient but more reliable)
            List<Order> allOrders = findAllByStatusOptional(null);
            List<Order> vendorOrders = allOrders.stream()
                    .filter(order -> order.getItems().stream()
                            .anyMatch(item -> Objects.equals(item.getVendorId(), vendorId)))
                    .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            System.out.println("Fallback method found " + vendorOrders.size() + " orders");
            return vendorOrders;
        } catch (Exception e) {
            System.err.println("Error in fallback method: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    public double calculateVendorRevenue(String vendorId) {
        try {
            String sql = """
                SELECT COALESCE(SUM(oi.price * oi.quantity), 0)
                FROM orders o
                INNER JOIN order_items oi ON o.id = oi.order_id
                INNER JOIN products p ON oi.product_id = p.id
                WHERE p.vendor_id = ?::uuid
                AND o.order_status = 'delivered'
                """;
            Double revenue = jdbcTemplate.queryForObject(sql, Double.class, parseUUID(vendorId));
            return revenue != null ? revenue : 0.0;
        } catch (Exception e) {
            System.err.println("Error calculating vendor revenue: " + vendorId + ", Error: " + e.getMessage());
            return 0.0;
        }
    }
    
    public int countVendorOrders(String vendorId) {
        try {
            String sql = """
                SELECT COUNT(DISTINCT o.id)
                FROM orders o
                INNER JOIN order_items oi ON o.id = oi.order_id
                INNER JOIN products p ON oi.product_id = p.id
                WHERE p.vendor_id = ?::uuid
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parseUUID(vendorId));
            return count != null ? count : 0;
        } catch (Exception e) {
            System.err.println("Error counting vendor orders: " + vendorId + ", Error: " + e.getMessage());
            return 0;
        }
    }
    
    public int countVendorPendingOrders(String vendorId) {
        try {
            String sql = """
                SELECT COUNT(DISTINCT o.id)
                FROM orders o
                INNER JOIN order_items oi ON o.id = oi.order_id
                INNER JOIN products p ON oi.product_id = p.id
                WHERE p.vendor_id = ?::uuid
                AND o.order_status = 'pending'
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parseUUID(vendorId));
            return count != null ? count : 0;
        } catch (Exception e) {
            System.err.println("Error counting vendor pending orders: " + vendorId + ", Error: " + e.getMessage());
            return 0;
        }
    }
    
    // Get order counts by status for a vendor
    public Map<String, Integer> getVendorOrderCountsByStatus(String vendorId) {
        try {
            String sql = """
                SELECT 
                    o.order_status,
                    COUNT(DISTINCT o.id) as count
                FROM orders o
                INNER JOIN order_items oi ON o.id = oi.order_id
                INNER JOIN products p ON oi.product_id = p.id
                WHERE p.vendor_id = ?::uuid
                GROUP BY o.order_status
                """;
            
            Map<String, Integer> statusCounts = new HashMap<>();
            
            jdbcTemplate.query(sql, (rs) -> {
                String status = rs.getString("order_status");
                Integer count = rs.getInt("count");
                statusCounts.put(status, count);
            }, parseUUID(vendorId));
            
            // Ensure all statuses are present with 0 count if not found
            String[] allStatuses = {"pending", "confirmed", "processing", "shipped", "delivered", "cancelled"};
            for (String status : allStatuses) {
                statusCounts.putIfAbsent(status, 0);
            }
            
            return statusCounts;
        } catch (Exception e) {
            System.err.println("Error getting vendor order counts by status: " + e.getMessage());
            // Return empty counts
            Map<String, Integer> emptyCounts = new HashMap<>();
            String[] allStatuses = {"pending", "confirmed", "processing", "shipped", "delivered", "cancelled"};
            for (String status : allStatuses) {
                emptyCounts.put(status, 0);
            }
            return emptyCounts;
        }
    }
}
