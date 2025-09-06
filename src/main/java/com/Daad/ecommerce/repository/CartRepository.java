package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
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
            cart.setUserId(rs.getString("user_id"));
            cart.setSubtotal(rs.getDouble("subtotal"));
            cart.setTax(rs.getDouble("tax"));
            cart.setShipping(rs.getDouble("shipping"));
            cart.setDiscount(rs.getDouble("discount"));
            cart.setTotal(rs.getDouble("total"));
            
            // Set shipping address
            Cart.ShippingAddress address = new Cart.ShippingAddress();
            address.setType(rs.getString("shipping_address_type"));
            address.setFullName(rs.getString("shipping_full_name"));
            address.setAddressLine1(rs.getString("shipping_address_line1"));
            address.setAddressLine2(rs.getString("shipping_address_line2"));
            address.setCity(rs.getString("shipping_city"));
            address.setState(rs.getString("shipping_state"));
            address.setPostalCode(rs.getString("shipping_postal_code"));
            address.setCountry(rs.getString("shipping_country"));
            address.setPhoneNumber(rs.getString("shipping_phone_number"));
            cart.setShippingAddress(address);
            
            // Set estimated delivery
            Cart.EstimatedDelivery delivery = new Cart.EstimatedDelivery();
            delivery.setMinDays(rs.getInt("estimated_delivery_min_days"));
            delivery.setMaxDays(rs.getInt("estimated_delivery_max_days"));
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
            
            return cart;
        }
    };

    public Optional<Cart> findByUser(String userId) {
        String sql = "SELECT * FROM carts WHERE user_id = ? LIMIT 1";
        List<Cart> carts = jdbcTemplate.query(sql, cartRowMapper, UUID.fromString(userId));
        return carts.isEmpty() ? Optional.empty() : Optional.of(carts.get(0));
    }

    public Cart save(Cart cart) {
        Optional<Cart> existingCart = findByUser(cart.getUserId());
        if (existingCart.isEmpty()) {
            return insert(cart);
        } else {
            return update(cart);
        }
    }
    
    private Cart insert(Cart cart) {
        String sql = """
            INSERT INTO carts (
                user_id, subtotal, tax, shipping, discount, total,
                shipping_address_type, shipping_full_name, shipping_address_line1,
                shipping_address_line2, shipping_city, shipping_state,
                shipping_postal_code, shipping_country, shipping_phone_number,
                estimated_delivery_min_days, estimated_delivery_max_days,
                last_updated, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?::address_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())
            """;

        jdbcTemplate.update(sql,
            UUID.fromString(cart.getUserId()),
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
            cart.getEstimatedDelivery().getMinDays(),
            cart.getEstimatedDelivery().getMaxDays()
        );

        // Get the generated ID
        String idSql = "SELECT id FROM carts WHERE user_id = ? ORDER BY created_at DESC LIMIT 1";
        Long id = jdbcTemplate.queryForObject(idSql, Long.class, UUID.fromString(cart.getUserId()));
        // Note: Cart DTO doesn't have setId method, so we can't set the ID
        
        return cart;
    }
    
    private Cart update(Cart cart) {
        String sql = """
            UPDATE carts SET 
                subtotal = ?, tax = ?, shipping = ?, discount = ?, total = ?,
                shipping_address_type = ?::address_type, shipping_full_name = ?,
                shipping_address_line1 = ?, shipping_address_line2 = ?,
                shipping_city = ?, shipping_state = ?, shipping_postal_code = ?,
                shipping_country = ?, shipping_phone_number = ?,
                estimated_delivery_min_days = ?, estimated_delivery_max_days = ?,
                last_updated = NOW(), updated_at = NOW()
            WHERE user_id = ?
            """;

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
            cart.getEstimatedDelivery().getMinDays(),
            cart.getEstimatedDelivery().getMaxDays(),
            cart.getUserId()
        );

        return cart;
    }

    public Cart getOrCreate(String userId) {
        Optional<Cart> existingCart = findByUser(userId);
        if (existingCart.isPresent()) {
            return existingCart.get();
        }
        
        Cart newCart = new Cart();
        newCart.setUserId(userId);
        return save(newCart);
    }

    public void deleteByUserId(String userId) {
        String sql = "DELETE FROM carts WHERE user_id = ?";
        jdbcTemplate.update(sql, UUID.fromString(userId));
    }

    public void updateCartTotals(String userId, double subtotal, double tax, 
                                double shipping, double discount, double total) {
        String sql = "UPDATE carts SET subtotal = ?, tax = ?, shipping = ?, discount = ?, total = ?, last_updated = NOW(), updated_at = NOW() WHERE user_id = ?";
        jdbcTemplate.update(sql, subtotal, tax, shipping, discount, total, UUID.fromString(userId));
    }

    public void updateShippingAddress(String userId, Cart.ShippingAddress address) {
        String sql = """
            UPDATE carts SET 
                shipping_address_type = ?::address_type, shipping_full_name = ?,
                shipping_address_line1 = ?, shipping_address_line2 = ?,
                shipping_city = ?, shipping_state = ?, shipping_postal_code = ?,
                shipping_country = ?, shipping_phone_number = ?, updated_at = NOW()
            WHERE user_id = ?
            """;
        
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
            UUID.fromString(userId)
        );
    }
}
