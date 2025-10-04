package com.Daad.ecommerce.repository;

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

@Repository
public class UserRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private UUID parseUUID(String uuidString) {
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid UUID format: " + uuidString, e);
        }
    }

    private final RowMapper<User> userRowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getString("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPassword(rs.getString("password"));
            user.setAuthProvider(rs.getString("auth_provider"));
            user.setGoogleId(rs.getString("google_id"));
            user.setOtp(rs.getString("otp"));
            
            Timestamp otpExpiresTs = rs.getTimestamp("otp_expires");
            if (otpExpiresTs != null) {
                user.setOtpExpires(otpExpiresTs.toInstant());
            }
            
            user.setIsVerified(rs.getBoolean("is_verified"));
            user.setProfilePicUrl(rs.getString("profile_pic_url"));
            user.setRole(rs.getString("role"));
            
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                user.setCreatedAt(createdAtTs.toInstant());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                user.setUpdatedAt(updatedAtTs.toInstant());
            }
            
            return user;
        }
    };

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ? LIMIT 1";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, email);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ? LIMIT 1";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, username);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<User> findById(String id) {
        try {
            String sql = "SELECT * FROM users WHERE id = ? LIMIT 1";
            List<User> users = jdbcTemplate.query(sql, userRowMapper, parseUUID(id));
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (Exception e) {
            System.err.println("Error finding user by ID: " + id + ", Error: " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<User> findByGoogleId(String googleId) {
        String sql = "SELECT * FROM users WHERE google_id = ? LIMIT 1";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, googleId);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public User save(User user) {
        if (user.getId() == null) {
            return insert(user);
        } else {
            return update(user);
        }
    }

    private User insert(User user) {
        String id = UUID.randomUUID().toString();
        user.setId(id);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        String sql = """
            INSERT INTO users (
                id, username, email, password, auth_provider, google_id, 
                otp, otp_expires, is_verified, profile_pic_url, role, 
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?::auth_provider, ?, ?, ?, ?, ?, ?::user_role, ?, ?)
            """;

        jdbcTemplate.update(sql,
            UUID.fromString(user.getId()),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            user.getAuthProvider() != null ? user.getAuthProvider() : "local",
            user.getGoogleId(),
            user.getOtp(),
            user.getOtpExpires() != null ? Timestamp.from(user.getOtpExpires()) : null,
            user.getIsVerified() != null ? user.getIsVerified() : false,
            user.getProfilePicUrl(),
            user.getRole() != null ? user.getRole() : "customer",
            Timestamp.from(user.getCreatedAt()),
            Timestamp.from(user.getUpdatedAt())
        );

        return user;
    }

    private User update(User user) {
        user.setUpdatedAt(Instant.now());

        String sql = """
            UPDATE users SET 
                username = ?, 
                email = ?, 
                password = ?, 
                auth_provider = ?::auth_provider, 
                google_id = ?, 
                otp = ?, 
                otp_expires = ?, 
                is_verified = ?, 
                profile_pic_url = ?, 
                role = ?::user_role,
                updated_at = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            user.getAuthProvider() != null ? user.getAuthProvider() : "local",
            user.getGoogleId(),
            user.getOtp(),
            user.getOtpExpires() != null ? Timestamp.from(user.getOtpExpires()) : null,
            user.getIsVerified() != null ? user.getIsVerified() : false,
            user.getProfilePicUrl(),
            user.getRole() != null ? user.getRole() : "customer",
            Timestamp.from(user.getUpdatedAt()),
            UUID.fromString(user.getId())
        );

        return user;
    }

    public void deleteById(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        jdbcTemplate.update(sql, UUID.fromString(id));
    }

    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    public List<User> findByRole(String role) {
        String sql = "SELECT * FROM users WHERE role = ?::user_role ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, userRowMapper, role);
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public void updateLastLogin(String userId) {
        String sql = "UPDATE users SET updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void updatePassword(String userId, String newPassword) {
        String sql = "UPDATE users SET password = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, newPassword, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void updateRole(String userId, String role) {
        String sql = "UPDATE users SET role = ?::user_role, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, role, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void updateOtp(String userId, String otp, Instant expiresAt) {
        String sql = "UPDATE users SET otp = ?, otp_expires = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, otp, 
            expiresAt != null ? Timestamp.from(expiresAt) : null,
            Timestamp.from(Instant.now()),
            UUID.fromString(userId));
    }

    public void clearOtp(String userId) {
        String sql = "UPDATE users SET otp = NULL, otp_expires = NULL, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void updateProfilePicture(String userId, String profilePicUrl) {
        String sql = "UPDATE users SET profile_pic_url = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, profilePicUrl, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void updateVerificationStatus(String userId, boolean isVerified) {
        String sql = "UPDATE users SET is_verified = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, isVerified, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void updateRewardPoints(String userId, int points) {
        String sql = "UPDATE users SET reward_points = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, points, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }

    public void markFirstOrderPlaced(String userId) {
        String sql = "UPDATE users SET first_order_placed = true, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), UUID.fromString(userId));
    }
}