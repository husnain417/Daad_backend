package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Repository
public class VendorRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Helper method to safely build JSON strings
    private String buildJsonString(String... keyValuePairs) {
        try {
            if (keyValuePairs.length % 2 != 0) {
                return "{}";
            }
            ObjectNode jsonNode = objectMapper.createObjectNode();
            for (int i = 0; i < keyValuePairs.length; i += 2) {
                String key = keyValuePairs[i];
                String value = keyValuePairs[i + 1];
                if (value != null && !value.trim().isEmpty()) {
                    jsonNode.put(key, value);
                }
            }
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            System.err.println("Failed to build JSON string: " + e.getMessage());
            return "{}";
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
            System.err.println("Failed to parse UUID: " + uuidString + ", error: " + e.getMessage());
            return null;
        }
    }

    private final RowMapper<Vendor> vendorRowMapper = new RowMapper<Vendor>() {
        @Override
        public Vendor mapRow(ResultSet rs, int rowNum) throws SQLException {
            Vendor vendor = new Vendor();
            vendor.setId(rs.getString("id"));
            
            // Set user reference
            User user = new User();
            user.setId(rs.getString("user_id"));
            vendor.setUser(user);
            
            vendor.setBusinessName(rs.getString("business_name"));
            vendor.setBusinessType(rs.getString("business_type"));
            vendor.setPhoneNumber(rs.getString("phone_number"));
            
            // Set business address
            Vendor.BusinessAddress address = new Vendor.BusinessAddress();
            address.setAddressLine1(rs.getString("business_address_line1"));
            address.setAddressLine2(rs.getString("business_address_line2"));
            address.setCity(rs.getString("business_city"));
            address.setState(rs.getString("business_state"));
            address.setPostalCode(rs.getString("business_postal_code"));
            address.setCountry(rs.getString("business_country"));
            vendor.setBusinessAddress(address);
            
            vendor.setDescription(rs.getString("description"));
            vendor.setLogo(rs.getString("logo_url"));
            vendor.setStatus(rs.getString("status"));
            
            // Set approval info
            if (rs.getString("approved_by") != null) {
                User approvedBy = new User();
                approvedBy.setId(rs.getString("approved_by"));
                vendor.setApprovedBy(approvedBy);
            }
            
            Timestamp approvedAtTs = rs.getTimestamp("approved_at");
            if (approvedAtTs != null) {
                vendor.setApprovedAt(approvedAtTs.toInstant());
            }
            
            // Set bank details as JSON string (since model uses single field)
            String bankDetails = buildJsonString(
                "accountNumber", rs.getString("bank_account_number"),
                "routingNumber", rs.getString("bank_routing_number"),
                "holderName", rs.getString("bank_account_holder_name"),
                "bankName", rs.getString("bank_name")
            );
            vendor.setBankDetails(bankDetails);
            
            vendor.setTaxId(rs.getString("tax_id"));
            
            // Set policies as JSON string
            String policies = buildJsonString(
                "returnWindow", String.valueOf(rs.getInt("return_window")),
                "shippingPolicy", rs.getString("shipping_policy"),
                "returnPolicy", rs.getString("return_policy")
            );
            vendor.setPolicies(policies);
            
            vendor.setCommission(rs.getDouble("commission"));
            vendor.setRating(rs.getDouble("rating_average"));
            vendor.setProfileCompleted(rs.getBoolean("profile_completed"));
            
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                vendor.setCreatedAt(createdAtTs.toInstant());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                vendor.setUpdatedAt(updatedAtTs.toInstant());
            }
            
            return vendor;
        }
    };

    public Optional<Vendor> findByUser(User user) {
        String sql = "SELECT * FROM vendors WHERE user_id = ? LIMIT 1";
        List<Vendor> vendors = jdbcTemplate.query(sql, vendorRowMapper, parseUUID(user.getId()));
        return vendors.isEmpty() ? Optional.empty() : Optional.of(vendors.get(0));
    }
    
    public List<Vendor> findAllByOrderByCreatedAtDesc() {
        String sql = "SELECT * FROM vendors ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, vendorRowMapper);
    }
    
    public Vendor save(Vendor vendor) {
        if (vendor.getId() == null) {
            return insert(vendor);
        } else {
            return update(vendor);
        }
    }
    
    private Vendor insert(Vendor vendor) {
        vendor.setCreatedAt(Instant.now());
        vendor.setUpdatedAt(Instant.now());
    
        String sql = """
            INSERT INTO vendors (
                user_id, business_name, business_type, phone_number,
                business_address_line1, business_address_line2, business_city,
                business_state, business_postal_code, business_country,
                description, logo_url, status, approved_by, approved_at,
                bank_account_number, bank_routing_number, bank_account_holder_name,
                bank_name, tax_id, return_window, shipping_policy, return_policy,
                commission, rating_average, profile_completed
            ) VALUES (?, ?, ?::business_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vendor_status, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    
        jdbcTemplate.update(sql,
            parseUUID(vendor.getUser().getId()),                    // 1. user_id
            vendor.getBusinessName(),                               // 2. business_name
            vendor.getBusinessType(),                               // 3. business_type
            vendor.getPhoneNumber(),                                // 4. phone_number
            vendor.getBusinessAddress().getAddressLine1(),          // 5. business_address_line1
            vendor.getBusinessAddress().getAddressLine2(),          // 6. business_address_line2
            vendor.getBusinessAddress().getCity(),                  // 7. business_city
            vendor.getBusinessAddress().getState(),                 // 8. business_state
            vendor.getBusinessAddress().getPostalCode(),            // 9. business_postal_code
            vendor.getBusinessAddress().getCountry(),               // 10. business_country
            vendor.getDescription(),                                // 11. description
            vendor.getLogo(),                                       // 12. logo_url
            vendor.getStatus() != null ? vendor.getStatus() : "pending", // 13. status
            vendor.getApprovedBy() != null ? parseUUID(vendor.getApprovedBy().getId()) : null, // 14. approved_by
            vendor.getApprovedAt() != null ? Timestamp.from(vendor.getApprovedAt()) : null,   // 15. approved_at
            null, // 16. bank_account_number
            null, // 17. bank_routing_number  
            null, // 18. bank_account_holder_name
            null, // 19. bank_name
            vendor.getTaxId(),                                      // 20. tax_id
            30,   // 21. return_window (default)
            null, // 22. shipping_policy
            null, // 23. return_policy  
            vendor.getCommission() != null ? vendor.getCommission() : 10.0, // 24. commission
            vendor.getRating() != null ? vendor.getRating() : 0.0, // 25. rating_average
            vendor.getProfileCompleted() != null ? vendor.getProfileCompleted() : false // 26. profile_completed
        );
    
        return vendor;
    }
    
    private Vendor update(Vendor vendor) {
        vendor.setUpdatedAt(Instant.now());

        String sql = """
            UPDATE vendors SET 
                business_name = ?, business_type = ?::business_type, phone_number = ?,
                business_address_line1 = ?, business_address_line2 = ?, business_city = ?,
                business_state = ?, business_postal_code = ?, business_country = ?,
                description = ?, logo_url = ?, status = ?::vendor_status, approved_by = ?,
                approved_at = ?, tax_id = ?, commission = ?, rating_average = ?,
                profile_completed = ?, updated_at = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            vendor.getBusinessName(),
            vendor.getBusinessType(),
            vendor.getPhoneNumber(),
            vendor.getBusinessAddress().getAddressLine1(),
            vendor.getBusinessAddress().getAddressLine2(),
            vendor.getBusinessAddress().getCity(),
            vendor.getBusinessAddress().getState(),
            vendor.getBusinessAddress().getPostalCode(),
            vendor.getBusinessAddress().getCountry(),
            vendor.getDescription(),
            vendor.getLogo(),
            vendor.getStatus(),
            vendor.getApprovedBy() != null ? parseUUID(vendor.getApprovedBy().getId()) : null,
            vendor.getApprovedAt() != null ? Timestamp.from(vendor.getApprovedAt()) : null,
            vendor.getTaxId(),
            vendor.getCommission(),
            vendor.getRating(),
            vendor.getProfileCompleted(),
            Timestamp.from(vendor.getUpdatedAt()),
            parseUUID(vendor.getId())
        );

        return vendor;
    }
    
    public Optional<Vendor> findById(String id) {
        String sql = "SELECT * FROM vendors WHERE id = ? LIMIT 1";
        List<Vendor> vendors = jdbcTemplate.query(sql, vendorRowMapper, parseUUID(id));
        return vendors.isEmpty() ? Optional.empty() : Optional.of(vendors.get(0));
    }

    public Optional<Vendor> findByUserId(String userId) {
        String sql = "SELECT * FROM vendors WHERE user_id = ? LIMIT 1";
        List<Vendor> vendors = jdbcTemplate.query(sql, vendorRowMapper, parseUUID(userId));
        return vendors.isEmpty() ? Optional.empty() : Optional.of(vendors.get(0));
    }

    public List<Vendor> findByStatus(String status) {
        String sql = "SELECT * FROM vendors WHERE status = ?::vendor_status ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, vendorRowMapper, status);
    }

    public void updateStatus(String vendorId, String status, String approvedById) {
        String sql = "UPDATE vendors SET status = ?::vendor_status, approved_by = ?, approved_at = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, status, 
            parseUUID(approvedById),
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            parseUUID(vendorId));
    }

    public void updateProfileCompletion(String vendorId, boolean completed) {
        String sql = "UPDATE vendors SET profile_completed = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, completed, Timestamp.from(Instant.now()), parseUUID(vendorId));
    }

    public void updateRating(String vendorId, Double rating) {
        String sql = "UPDATE vendors SET rating_average = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, rating, Timestamp.from(Instant.now()), parseUUID(vendorId));
    }
}


