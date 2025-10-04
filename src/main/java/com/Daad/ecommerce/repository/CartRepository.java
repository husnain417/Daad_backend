package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CartRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Cart> cartRowMapper = new RowMapper<Cart>() {
        @Override
        public Cart mapRow(ResultSet rs, int rowNum) throws SQLException {
            Cart cart = new Cart();
            
            String userId = rs.getString("user_id");
            String cartId = rs.getString("cart_id");
            boolean isGuest = rs.getBoolean("is_guest");
            
            if (isGuest) {
                cart.setCartId(cartId);
                cart.setGuest(true);
            } else {
                cart.setUserId(userId);
                cart.setGuest(false);
            }
            
            cart.setSubtotal(rs.getDouble("subtotal"));
            cart.setTax(rs.getDouble("tax"));
            cart.setShipping(rs.getDouble("shipping"));
            cart.setDiscount(rs.getDouble("discount"));
            cart.setTotal(rs.getDouble("total"));
            
            // Set shipping address
            Cart.ShippingAddress address = new Cart.ShippingAddress();
            String addressType = rs.getString("shipping_address_type");
            address.setType(addressType != null ? addressType : "home");
            address.setFullName(rs.getString("shipping_full_name"));
            address.setAddressLine1(rs.getString("shipping_address_line1"));
            address.setAddressLine2(rs.getString("shipping_address_line2"));
            address.setCity(rs.getString("shipping_city"));
            address.setState(rs.getString("shipping_state"));
            address.setPostalCode(rs.getString("shipping_postal_code"));
            address.setCountry(rs.getString("shipping_country"));
            address.setPhoneNumber(rs.getString("shipping_phone_number"));
            address.setEmail(rs.getString("shipping_email"));
            cart.setShippingAddress(address);
            
            // Set estimated delivery
            Cart.EstimatedDelivery delivery = new Cart.EstimatedDelivery();
            int minDays = rs.getInt("estimated_delivery_min_days");
            int maxDays = rs.getInt("estimated_delivery_max_days");
            if (minDays == 0 && maxDays == 0) {
                delivery.setMinDays(3);
                delivery.setMaxDays(7);
            } else {
                delivery.setMinDays(minDays);
                delivery.setMaxDays(maxDays);
            }
            cart.setEstimatedDelivery(delivery);
            
            // Set timestamps
            Timestamp lastUpdatedTs = rs.getTimestamp("last_updated");
            if (lastUpdatedTs != null) {
                cart.setLastUpdated(lastUpdatedTs.toLocalDateTime());
            }
            
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                cart.setCreatedAt(createdAtTs.toLocalDateTime());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                cart.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
            
            // Don't load cart items here to avoid prepared statement conflicts
            // Items will be loaded separately
            cart.setItems(new ArrayList<>());
            
            return cart;
        }
    };

    public Optional<Cart> findByIdentifier(String identifier, boolean isUser) {
        String sql;
        if (isUser) {
            sql = "SELECT * FROM carts WHERE user_id = ? AND is_guest = false LIMIT 1";
        } else {
            sql = "SELECT * FROM carts WHERE cart_id = ? AND is_guest = true LIMIT 1";
        }
        
        try {
            List<Cart> carts = jdbcTemplate.query(sql, cartRowMapper, UUID.fromString(identifier));
            if (carts.isEmpty()) {
                return Optional.empty();
            }
            
            Cart cart = carts.get(0);
            // Load cart items separately to avoid prepared statement conflicts
            cart.setItems(loadCartItems(identifier, isUser));
            return Optional.of(cart);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Cart> findByUser(String userId) {
        return findByIdentifier(userId, true);
    }

    private List<Cart.CartItem> loadCartItems(String identifier, boolean isUser) {
        try {
            UUID idAsUuid = UUID.fromString(identifier);
            
            // Use a single query with JOIN to avoid multiple prepared statements
            String sql;
            if (isUser) {
                sql = """
                    SELECT ci.product_id, ci.vendor_id, ci.color, ci.size, ci.quantity, 
                           ci.price, ci.discounted_price, ci.total_price
                    FROM cart_items ci
                    JOIN carts c ON ci.cart_id = c.id
                    WHERE c.user_id = ? AND c.is_guest = false
                    ORDER BY ci.created_at
                    """;
            } else {
                sql = """
                    SELECT ci.product_id, ci.vendor_id, ci.color, ci.size, ci.quantity, 
                           ci.price, ci.discounted_price, ci.total_price
                    FROM cart_items ci
                    JOIN carts c ON ci.cart_id = c.id
                    WHERE c.cart_id = ? AND c.is_guest = true
                    ORDER BY ci.created_at
                    """;
            }

            List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(sql, idAsUuid);
            List<Cart.CartItem> items = new ArrayList<>();
            
            for (Map<String, Object> row : itemRows) {
                Cart.CartItem item = new Cart.CartItem();
                item.setProductId(row.get("product_id").toString());
                item.setVendorId(row.get("vendor_id") != null ? row.get("vendor_id").toString() : null);
                item.setColor(row.get("color") != null ? row.get("color").toString() : null);
                item.setSize(row.get("size") != null ? row.get("size").toString() : null);
                
                Object qObj = row.get("quantity");
                Object pObj = row.get("price");
                Object dpObj = row.get("discounted_price");
                Object tpObj = row.get("total_price");
                
                item.setQuantity(qObj instanceof Number ? ((Number) qObj).intValue() : Integer.parseInt(qObj.toString()));
                item.setPrice(pObj instanceof Number ? ((Number) pObj).doubleValue() : Double.parseDouble(pObj.toString()));
                item.setDiscountedPrice(dpObj != null ? (dpObj instanceof Number ? ((Number) dpObj).doubleValue() : Double.parseDouble(dpObj.toString())) : null);
                item.setTotalPrice(tpObj instanceof Number ? ((Number) tpObj).doubleValue() : Double.parseDouble(tpObj.toString()));
                items.add(item);
            }

            return items;
        } catch (Exception e) {
            System.out.println("[CartRepository] Error loading cart items: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public Cart save(Cart cart) {
        String identifier = cart.getIdentifier();
        boolean isUser = !cart.isGuest();
        
        // Check if cart exists without loading items to avoid prepared statement conflicts
        String checkSql;
        if (isUser) {
            checkSql = "SELECT id FROM carts WHERE user_id = ? AND is_guest = false LIMIT 1";
        } else {
            checkSql = "SELECT id FROM carts WHERE cart_id = ? AND is_guest = true LIMIT 1";
        }
        
        try {
            UUID idAsUuid = UUID.fromString(identifier);
            List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(checkSql, idAsUuid);
            
            if (existingRows.isEmpty()) {
                // No existing cart, insert new one
                return insert(cart);
            } else {
                // Cart exists, update it
                return update(cart);
            }
        } catch (Exception e) {
            // If there's any error, try to insert
            return insert(cart);
        }
    }
    
    private Cart insert(Cart cart) {
        if (cart.getEstimatedDelivery() == null) {
            cart.setEstimatedDelivery(new Cart.EstimatedDelivery(3, 7));
        }
        
        String sql = """
            INSERT INTO carts (
                user_id, cart_id, is_guest, subtotal, tax, shipping, discount, total,
                shipping_address_type, shipping_full_name, shipping_address_line1,
                shipping_address_line2, shipping_city, shipping_state,
                shipping_postal_code, shipping_country, shipping_phone_number, shipping_email,
                estimated_delivery_min_days, estimated_delivery_max_days,
                last_updated, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::address_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())
            """;

        try {
            jdbcTemplate.update(sql,
                cart.isGuest() ? null : UUID.fromString(cart.getUserId()),
                cart.isGuest() ? UUID.fromString(cart.getCartId()) : null,
                cart.isGuest(),
                cart.getSubtotal(),
                cart.getTax(),
                cart.getShipping(),
                cart.getDiscount(),
                cart.getTotal(),
                cart.getShippingAddress() != null ? cart.getShippingAddress().getType() : "home",
                cart.getShippingAddress() != null ? cart.getShippingAddress().getFullName() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getAddressLine1() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getAddressLine2() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getCity() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getState() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getPostalCode() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getCountry() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getPhoneNumber() : null,
                cart.getShippingAddress() != null ? cart.getShippingAddress().getEmail() : null,
                cart.getEstimatedDelivery().getMinDays(),
                cart.getEstimatedDelivery().getMaxDays()
            );
        } catch (Exception e) {
            // If insert fails due to duplicate key, this is handled by the save method
            throw e;
        }

        saveCartItems(cart);
        return cart;
    }
    
    private Cart update(Cart cart) {
        if (cart.getEstimatedDelivery() == null) {
            cart.setEstimatedDelivery(new Cart.EstimatedDelivery(3, 7));
        }
        
        String sql;
        if (cart.isGuest()) {
            sql = """
                UPDATE carts SET 
                    subtotal = ?, tax = ?, shipping = ?, discount = ?, total = ?,
                    shipping_address_type = ?::address_type, shipping_full_name = ?,
                    shipping_address_line1 = ?, shipping_address_line2 = ?,
                    shipping_city = ?, shipping_state = ?, shipping_postal_code = ?,
                    shipping_country = ?, shipping_phone_number = ?, shipping_email = ?,
                    estimated_delivery_min_days = ?, estimated_delivery_max_days = ?,
                    last_updated = NOW(), updated_at = NOW()
                WHERE cart_id = ? AND is_guest = true
                """;
        } else {
            sql = """
                UPDATE carts SET 
                    subtotal = ?, tax = ?, shipping = ?, discount = ?, total = ?,
                    shipping_address_type = ?::address_type, shipping_full_name = ?,
                    shipping_address_line1 = ?, shipping_address_line2 = ?,
                    shipping_city = ?, shipping_state = ?, shipping_postal_code = ?,
                    shipping_country = ?, shipping_phone_number = ?, shipping_email = ?,
                    estimated_delivery_min_days = ?, estimated_delivery_max_days = ?,
                    last_updated = NOW(), updated_at = NOW()
                WHERE user_id = ? AND is_guest = false
                """;
        }

        jdbcTemplate.update(sql,
            cart.getSubtotal(),
            cart.getTax(),
            cart.getShipping(),
            cart.getDiscount(),
            cart.getTotal(),
            cart.getShippingAddress() != null ? cart.getShippingAddress().getType() : "home",
            cart.getShippingAddress() != null ? cart.getShippingAddress().getFullName() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getAddressLine1() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getAddressLine2() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getCity() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getState() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getPostalCode() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getCountry() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getPhoneNumber() : null,
            cart.getShippingAddress() != null ? cart.getShippingAddress().getEmail() : null,
            cart.getEstimatedDelivery().getMinDays(),
            cart.getEstimatedDelivery().getMaxDays(),
            UUID.fromString(cart.getIdentifier())
        );

        saveCartItems(cart);
        return cart;
    }

    /**
     * CRITICAL FIX: Use UPSERT instead of DELETE+INSERT to prevent race conditions
     * This ensures atomic updates and prevents data loss
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    private void saveCartItems(Cart cart) {
        System.out.println("[CartRepository] saveCartItems | identifier=" + cart.getIdentifier() + ", isGuest=" + cart.isGuest());
        
        try {
            // Get the cart ID
            String cartIdSql;
            if (cart.isGuest()) {
                cartIdSql = "SELECT id FROM carts WHERE cart_id = ? AND is_guest = true";
            } else {
                cartIdSql = "SELECT id FROM carts WHERE user_id = ? AND is_guest = false";
            }
            
            List<Map<String, Object>> cartRows = jdbcTemplate.queryForList(cartIdSql, UUID.fromString(cart.getIdentifier()));
            if (cartRows.isEmpty()) {
                System.out.println("[CartRepository] No cart found for identifier: " + cart.getIdentifier());
                return;
            }
            
            UUID cartId = (UUID) cartRows.get(0).get("id");
            System.out.println("[CartRepository] Cart ID: " + cartId);
            
            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                // If no items, delete all
            String deleteSql = "DELETE FROM cart_items WHERE cart_id = ?";
                int deleted = jdbcTemplate.update(deleteSql, cartId);
                System.out.println("[CartRepository] Deleted " + deleted + " items (cart cleared)");
                return;
            }
            
            // Use UPSERT (INSERT ... ON CONFLICT) for atomic updates
            String upsertSql = """
                    INSERT INTO cart_items (
                        cart_id, product_id, vendor_id, color, size, quantity, 
                    price, discounted_price, total_price, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (cart_id, product_id, vendor_id, color, size) 
                DO UPDATE SET 
                    quantity = EXCLUDED.quantity,
                    price = EXCLUDED.price,
                    discounted_price = EXCLUDED.discounted_price,
                    total_price = EXCLUDED.total_price,
                    updated_at = NOW()
                """;
            
            // Track which items should exist
            List<String> currentItemKeys = new ArrayList<>();
            
            // Upsert each item
            for (Cart.CartItem item : cart.getItems()) {
                String itemKey = String.format("%s-%s-%s-%s", 
                    item.getProductId(), 
                    item.getVendorId() != null ? item.getVendorId() : "null",
                    item.getColor(), 
                    item.getSize());
                currentItemKeys.add(itemKey);
                
                System.out.println("[CartRepository] Upserting item: " + itemKey + ", qty=" + item.getQuantity());
                
                int rows = jdbcTemplate.update(upsertSql,
                        cartId,
                        UUID.fromString(item.getProductId()),
                        item.getVendorId() != null ? UUID.fromString(item.getVendorId()) : null,
                        item.getColor(),
                        item.getSize(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getDiscountedPrice(),
                        item.getTotalPrice()
                    );
                System.out.println("[CartRepository] Upsert affected " + rows + " row(s)");
            }
            
            // Delete items that are no longer in the cart
            String selectAllSql = "SELECT product_id, vendor_id, color, size FROM cart_items WHERE cart_id = ?";
            List<Map<String, Object>> existingItems = jdbcTemplate.queryForList(selectAllSql, cartId);
            
            for (Map<String, Object> existingItem : existingItems) {
                String existingKey = String.format("%s-%s-%s-%s",
                    existingItem.get("product_id").toString(),
                    existingItem.get("vendor_id") != null ? existingItem.get("vendor_id").toString() : "null",
                    existingItem.get("color").toString(),
                    existingItem.get("size").toString());
                
                if (!currentItemKeys.contains(existingKey)) {
                    // Item should be deleted
                    String deleteSql = """
                        DELETE FROM cart_items 
                        WHERE cart_id = ? AND product_id = ? AND vendor_id IS NOT DISTINCT FROM ? 
                        AND color = ? AND size = ?
                        """;
                    
                    jdbcTemplate.update(deleteSql,
                        cartId,
                        UUID.fromString(existingItem.get("product_id").toString()),
                        existingItem.get("vendor_id") != null ? UUID.fromString(existingItem.get("vendor_id").toString()) : null,
                        existingItem.get("color").toString(),
                        existingItem.get("size").toString()
                    );
                    System.out.println("[CartRepository] Deleted obsolete item: " + existingKey);
                }
            }
            
        } catch (Exception e) {
            System.out.println("[CartRepository] Error saving cart items: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save cart items", e);
        }
    }

    public Cart getOrCreate(String identifier, boolean isUser) {
        System.out.println("[CartRepository] getOrCreate | identifier=" + identifier + ", isUser=" + isUser);
        
        // Try to find existing cart first
        Optional<Cart> existingCart = findByIdentifier(identifier, isUser);
        if (existingCart.isPresent()) {
            Cart cart = existingCart.get();
            System.out.println("[CartRepository] Found existing cart with " + cart.getItems().size() + " items");
            if (cart.getEstimatedDelivery() == null) {
                cart.setEstimatedDelivery(new Cart.EstimatedDelivery(3, 7));
            }
            return cart;
        }
        
        System.out.println("[CartRepository] No existing cart found, creating new one");
        // If no cart exists, create one using save method which handles duplicates
        Cart newCart = new Cart();
        if (isUser) {
            newCart.setUserId(identifier);
        } else {
            newCart.setCartId(identifier);
        }
        newCart.setEstimatedDelivery(new Cart.EstimatedDelivery(3, 7));
        
        // Use save method which properly handles existing cart logic
        return save(newCart);
    }

    public void deleteByUserId(String userId) {
        String deleteItemsSql = "DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE user_id = ? AND is_guest = false)";
        jdbcTemplate.update(deleteItemsSql, UUID.fromString(userId));
        
        String sql = "DELETE FROM carts WHERE user_id = ? AND is_guest = false";
        jdbcTemplate.update(sql, UUID.fromString(userId));
    }

    public void deleteByCartId(String cartId) {
        String deleteItemsSql = "DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE cart_id = ? AND is_guest = true)";
        jdbcTemplate.update(deleteItemsSql, UUID.fromString(cartId));
        
        String sql = "DELETE FROM carts WHERE cart_id = ? AND is_guest = true";
        jdbcTemplate.update(sql, UUID.fromString(cartId));
    }

    public void updateCartTotals(String identifier, boolean isUser, double subtotal, double tax, 
                                double shipping, double discount, double total) {
        String sql;
        if (isUser) {
            sql = "UPDATE carts SET subtotal = ?, tax = ?, shipping = ?, discount = ?, total = ?, last_updated = NOW(), updated_at = NOW() WHERE user_id = ? AND is_guest = false";
        } else {
            sql = "UPDATE carts SET subtotal = ?, tax = ?, shipping = ?, discount = ?, total = ?, last_updated = NOW(), updated_at = NOW() WHERE cart_id = ? AND is_guest = true";
        }
        jdbcTemplate.update(sql, subtotal, tax, shipping, discount, total, UUID.fromString(identifier));
    }

    public void updateShippingAddress(String identifier, boolean isUser, Cart.ShippingAddress address) {
        String sql;
        if (isUser) {
            sql = """
                UPDATE carts SET 
                    shipping_address_type = ?::address_type, shipping_full_name = ?,
                    shipping_address_line1 = ?, shipping_address_line2 = ?,
                    shipping_city = ?, shipping_state = ?, shipping_postal_code = ?,
                    shipping_country = ?, shipping_phone_number = ?, shipping_email = ?, updated_at = NOW()
                WHERE user_id = ? AND is_guest = false
                """;
        } else {
            sql = """
                UPDATE carts SET 
                    shipping_address_type = ?::address_type, shipping_full_name = ?,
                    shipping_address_line1 = ?, shipping_address_line2 = ?,
                    shipping_city = ?, shipping_state = ?, shipping_postal_code = ?,
                    shipping_country = ?, shipping_phone_number = ?, shipping_email = ?, updated_at = NOW()
                WHERE cart_id = ? AND is_guest = true
                """;
        }
        
        jdbcTemplate.update(sql,
            address.getType(),
            address.getFullName(),
            address.getAddressLine1(),
            address.getAddressLine2(),
            address.getCity(),
            address.getState(),
            address.getPostalCode(),
            address.getCountry(),
            address.getPhoneNumber(),
            address.getEmail(),
            UUID.fromString(identifier)
        );
    }

    public void clearCartAfterOrder(String userId) {
        try {
            String deleteItemsSql = "DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE user_id = ? AND is_guest = false)";
            jdbcTemplate.update(deleteItemsSql, UUID.fromString(userId));
            
            String deleteCartSql = "DELETE FROM carts WHERE user_id = ? AND is_guest = false";
            jdbcTemplate.update(deleteCartSql, UUID.fromString(userId));
            
            System.out.println("[CartRepository] Cart cleared for user: " + userId);
        } catch (Exception e) {
            System.out.println("[CartRepository] Error clearing cart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void cleanupOldGuestCarts(int daysOld) {
        String sql = """
            DELETE FROM cart_items WHERE cart_id IN (
                SELECT id FROM carts WHERE is_guest = true AND created_at < NOW() - INTERVAL ? DAY
            )
            """;
        jdbcTemplate.update(sql, daysOld);
        
        sql = "DELETE FROM carts WHERE is_guest = true AND created_at < NOW() - INTERVAL ? DAY";
        jdbcTemplate.update(sql, daysOld);
    }
}