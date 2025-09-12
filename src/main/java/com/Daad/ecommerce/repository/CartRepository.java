package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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
            
            // Check if it's a guest cart or user cart
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
            
            // Set shipping address - handle nulls properly
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
            
            // Set estimated delivery - handle nulls properly
            Cart.EstimatedDelivery delivery = new Cart.EstimatedDelivery();
            int minDays = rs.getInt("estimated_delivery_min_days");
            int maxDays = rs.getInt("estimated_delivery_max_days");
            // If both are 0, set default values
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
            
            // Load cart items (pass isUser = !isGuest)
            String identifier = isGuest ? cartId : userId;
            cart.setItems(loadCartItems(identifier, !isGuest));
            
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
            return carts.isEmpty() ? Optional.empty() : Optional.of(carts.get(0));
        } catch (Exception e) {
            // If cart doesn't exist or identifier is invalid, return empty
            return Optional.empty();
        }
    }

    // Legacy method for backward compatibility
    public Optional<Cart> findByUser(String userId) {
        return findByIdentifier(userId, true);
    }
    private List<Cart.CartItem> loadCartItems(String identifier, boolean isUser) {
        System.out.println("[CartRepository] loadCartItems called | identifier=" + identifier + ", isUser=" + isUser);
        UUID idAsUuid;
        try {
            idAsUuid = UUID.fromString(identifier);
        } catch (IllegalArgumentException iae) {
            System.out.println("[CartRepository] Invalid UUID for identifier: " + identifier);
            return new ArrayList<>();
        }

        try {
            // Resolve concrete cart PK
            String cartIdSql;
            Object queryParam;
            if (isUser) {
                cartIdSql = "SELECT id FROM carts WHERE user_id = ? AND is_guest = false";
                queryParam = idAsUuid;
            } else {
                cartIdSql = "SELECT id FROM carts WHERE cart_id = ? AND is_guest = true";
                queryParam = idAsUuid;
            }

            System.out.println("[CartRepository] cart lookup SQL=" + cartIdSql);
            System.out.println("[CartRepository] cart lookup param=" + queryParam);
            List<Map<String, Object>> cartRows = jdbcTemplate.queryForList(cartIdSql, queryParam);
            System.out.println("[CartRepository] cartRows size=" + cartRows.size());
            if (cartRows.isEmpty()) {
                System.out.println("[CartRepository] No cart found for identifier=" + identifier + ", isUser=" + isUser);
                return new ArrayList<>();
            }

            UUID cartId = (UUID) cartRows.get(0).get("id");
            System.out.println("[CartRepository] Resolved cartId=" + cartId + " for identifier=" + identifier);

            // Fetch items
            String itemsSql = "SELECT product_id, vendor_id, color, size, quantity, price, discounted_price, total_price FROM cart_items WHERE cart_id = ?";
            List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(itemsSql, cartId);
            System.out.println("[CartRepository] itemRows size=" + itemRows.size());

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
                System.out.println("[CartRepository] Loaded item | product=" + item.getProductId() + ", color=" + item.getColor() + ", size=" + item.getSize() + ", qty=" + item.getQuantity());
            }

            return items;
        } catch (Exception e) {
            System.out.println("[CartRepository] Error loading cart items: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Cart save(Cart cart) {
        String identifier = cart.getIdentifier();
        boolean isUser = !cart.isGuest();
        
        Optional<Cart> existingCart = findByIdentifier(identifier, isUser);
        if (existingCart.isEmpty()) {
            return insert(cart);
        } else {
            return update(cart);
        }
    }
    
    private Cart insert(Cart cart) {
        // Ensure EstimatedDelivery is not null
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

        // Save cart items
        saveCartItems(cart);
        
        return cart;
    }
    
    private Cart update(Cart cart) {
        // Ensure EstimatedDelivery is not null
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

        // Update cart items
        saveCartItems(cart);

        return cart;
    }

    private void saveCartItems(Cart cart) {
        System.out.println("Saving cart items for cart: " + cart.getIdentifier() + ", isGuest: " + cart.isGuest());
        System.out.println("Number of items to save: " + (cart.getItems() != null ? cart.getItems().size() : 0));
        
        try {
            // First, get the cart ID
            String cartIdSql;
            if (cart.isGuest()) {
                cartIdSql = "SELECT id FROM carts WHERE cart_id = ? AND is_guest = true";
            } else {
                cartIdSql = "SELECT id FROM carts WHERE user_id = ? AND is_guest = false";
            }
            
            List<Map<String, Object>> cartRows = jdbcTemplate.queryForList(cartIdSql, UUID.fromString(cart.getIdentifier()));
            if (cartRows.isEmpty()) {
                System.out.println("No cart found for identifier: " + cart.getIdentifier());
                return;
            }
            
            UUID cartId = (UUID) cartRows.get(0).get("id");
            System.out.println("Found cart ID for saving: " + cartId);
            
            // Delete existing items for this cart
            String deleteSql = "DELETE FROM cart_items WHERE cart_id = ?";
            int deletedRows = jdbcTemplate.update(deleteSql, cartId);
            System.out.println("Deleted " + deletedRows + " existing cart items");
            
            // Insert current items
            if (cart.getItems() != null && !cart.getItems().isEmpty()) {
                String insertSql = """
                    INSERT INTO cart_items (
                        cart_id, product_id, vendor_id, color, size, quantity, 
                        price, discounted_price, total_price
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                
                for (Cart.CartItem item : cart.getItems()) {
                    System.out.println("Inserting item: " + item.getProductId() + ", " + item.getColor() + ", " + item.getSize() + ", qty: " + item.getQuantity());
                    
                    int rowsInserted = jdbcTemplate.update(insertSql,
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
                    System.out.println("Rows inserted: " + rowsInserted);
                }
            }
        } catch (Exception e) {
            System.out.println("Error saving cart items: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Cart getOrCreate(String identifier, boolean isUser) {
        Optional<Cart> existingCart = findByIdentifier(identifier, isUser);
        if (existingCart.isPresent()) {
            Cart cart = existingCart.get();
            // Ensure EstimatedDelivery is not null
            if (cart.getEstimatedDelivery() == null) {
                cart.setEstimatedDelivery(new Cart.EstimatedDelivery(3, 7));
            }
            return cart;
        }
        
        Cart newCart = new Cart();
        if (isUser) {
            newCart.setUserId(identifier);
        } else {
            newCart.setCartId(identifier);
        }
        newCart.setEstimatedDelivery(new Cart.EstimatedDelivery(3, 7)); // Set default
        return save(newCart);
    }

    public void deleteByUserId(String userId) {
        // Delete cart items first (foreign key constraint)
        String deleteItemsSql = "DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE user_id = ? AND is_guest = false)";
        jdbcTemplate.update(deleteItemsSql, UUID.fromString(userId));
        
        // Then delete cart
        String sql = "DELETE FROM carts WHERE user_id = ? AND is_guest = false";
        jdbcTemplate.update(sql, UUID.fromString(userId));
    }

    public void deleteByCartId(String cartId) {
        // Delete cart items first (foreign key constraint)
        String deleteItemsSql = "DELETE FROM cart_items WHERE cart_id = (SELECT id FROM carts WHERE cart_id = ? AND is_guest = true)";
        jdbcTemplate.update(deleteItemsSql, UUID.fromString(cartId));
        
        // Then delete cart
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

    // Clean up old guest carts (run this periodically)
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