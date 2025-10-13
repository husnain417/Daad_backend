package com.Daad.ecommerce.repository;

import com.Daad.ecommerce.dto.Review;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class ReviewRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
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

    private final RowMapper<Review> reviewRowMapper = new RowMapper<Review>() {
        @Override
        public Review mapRow(ResultSet rs, int rowNum) throws SQLException {
            Review review = new Review();
            review.setId(rs.getString("id"));
            review.setProductId(rs.getString("product_id"));
            review.setCustomerId(rs.getString("customer_id"));
            review.setRating(rs.getInt("rating"));
            review.setTitle(rs.getString("title"));
            review.setComment(rs.getString("comment"));
            review.setIsVerified(rs.getBoolean("is_verified"));
            review.setStatus(rs.getString("status"));
            
            // Set helpful count
            Review.Helpful helpful = new Review.Helpful();
            helpful.setCount(rs.getInt("helpful_count"));
            review.setHelpful(helpful);
            
            // Set timestamps
            Timestamp createdAtTs = rs.getTimestamp("created_at");
            if (createdAtTs != null) {
                review.setCreatedAt(createdAtTs.toLocalDateTime());
            }
            
            Timestamp updatedAtTs = rs.getTimestamp("updated_at");
            if (updatedAtTs != null) {
                review.setUpdatedAt(updatedAtTs.toLocalDateTime());
            }
            
            return review;
        }
    };

    public Review save(Review review) {
        if (review.getId() == null) {
            return insert(review);
        } else {
            return update(review);
        }
    }

    private Review insert(Review review) {
        String sql = """
            INSERT INTO reviews (
                product_id, customer_id, rating, title, comment,
                is_verified, status, helpful_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?::review_status, ?)
            """;

        jdbcTemplate.update(sql,
            parseUUID(review.getProductId()),
            parseUUID(review.getCustomerId()),
            review.getRating(),
            review.getTitle(),
            review.getComment(),
            review.getIsVerified() != null ? review.getIsVerified() : false,
            review.getStatus() != null ? review.getStatus() : "pending",
            review.getHelpful() != null ? review.getHelpful().getCount() : 0
        );

        // Get the generated ID
        String idSql = "SELECT id FROM reviews WHERE product_id = ? AND customer_id = ? ORDER BY created_at DESC LIMIT 1";
        String id = jdbcTemplate.queryForObject(idSql, String.class, parseUUID(review.getProductId()), parseUUID(review.getCustomerId()));
        review.setId(id);
        
        return review;
    }

    private Review update(Review review) {
        String sql = """
            UPDATE reviews SET 
                rating = ?, title = ?, comment = ?, is_verified = ?,
                status = ?::review_status, helpful_count = ?, updated_at = NOW()
            WHERE id = ?
            """;

        jdbcTemplate.update(sql,
            review.getRating(),
            review.getTitle(),
            review.getComment(),
            review.getIsVerified(),
            review.getStatus(),
            review.getHelpful() != null ? review.getHelpful().getCount() : 0,
            parseUUID(review.getId())
        );

        return review;
    }

    public Optional<Review> findById(String id) {
        String sql = "SELECT * FROM reviews WHERE id = ? LIMIT 1";
        List<Review> reviews = jdbcTemplate.query(sql, reviewRowMapper, parseUUID(id));
        return reviews.isEmpty() ? Optional.empty() : Optional.of(reviews.get(0));
    }

    public void deleteById(String id) {
        String sql = "DELETE FROM reviews WHERE id = ?";
        jdbcTemplate.update(sql, parseUUID(id));
    }

    public List<Review> findByProductIdAndStatus(String productId, String status) {
        String sql = "SELECT * FROM reviews WHERE product_id = ? AND status = ?::review_status ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, parseUUID(productId), status);
    }

    public long countByProductIdAndStatus(String productId, String status) {
        String sql = "SELECT COUNT(*) FROM reviews WHERE product_id = ? AND status = ?::review_status";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parseUUID(productId), status);
        return count != null ? count : 0;
    }

    public Optional<Review> findOneByProductIdAndCustomerId(String productId, String customerId) {
        String sql = "SELECT * FROM reviews WHERE product_id = ? AND customer_id = ? LIMIT 1";
        List<Review> reviews = jdbcTemplate.query(sql, reviewRowMapper, parseUUID(productId), parseUUID(customerId));
        return reviews.isEmpty() ? Optional.empty() : Optional.of(reviews.get(0));
    }

    public List<Review> findByCustomerIdAndStatus(String customerId, String status) {
        String sql = "SELECT * FROM reviews WHERE customer_id = ? AND status = ?::review_status ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, parseUUID(customerId), status);
    }

    public Map<Integer, Long> ratingDistributionForProduct(String productId) {
        String sql = "SELECT rating, COUNT(*) as count FROM reviews WHERE product_id = ? AND status = 'approved' GROUP BY rating ORDER BY rating";
        Map<Integer, Long> distribution = new HashMap<>();
        
        // Initialize with 0 counts
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        
        // Get actual counts from database
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, parseUUID(productId));
        for (Map<String, Object> row : results) {
            Integer rating = (Integer) row.get("rating");
            Long count = (Long) row.get("count");
            if (rating != null && count != null) {
                distribution.put(rating, count);
            }
        }
        
        return distribution;
    }

    public OptionalDouble averageRatingForProduct(String productId) {
        String sql = "SELECT AVG(rating) as avg_rating FROM reviews WHERE product_id = ? AND status = 'approved'";
        Double avgRating = jdbcTemplate.queryForObject(sql, Double.class, parseUUID(productId));
        return avgRating != null ? OptionalDouble.of(avgRating) : OptionalDouble.empty();
    }

    public List<Review> findByProductId(String productId) {
        String sql = "SELECT * FROM reviews WHERE product_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, parseUUID(productId));
    }

    public List<Review> findByCustomerId(String customerId) {
        String sql = "SELECT * FROM reviews WHERE customer_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, parseUUID(customerId));
    }

    public List<Review> findByStatus(String status) {
        String sql = "SELECT * FROM reviews WHERE status = ?::review_status ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, status);
    }

    public void updateStatus(String reviewId, String status) {
        String sql = "UPDATE reviews SET status = ?::review_status, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, status, parseUUID(reviewId));
    }

    public void updateHelpfulCount(String reviewId, int helpfulCount) {
        String sql = "UPDATE reviews SET helpful_count = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, helpfulCount, parseUUID(reviewId));
    }

    public void updateVerificationStatus(String reviewId, boolean isVerified) {
        String sql = "UPDATE reviews SET is_verified = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, isVerified, parseUUID(reviewId));
    }

    public List<Review> findPendingReviews() {
        String sql = "SELECT * FROM reviews WHERE status::text = 'pending' ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, reviewRowMapper);
    }

    public List<Review> findApprovedReviews() {
        String sql = "SELECT * FROM reviews WHERE status::text = 'approved' ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper);
    }

    public List<Review> findRejectedReviews() {
        String sql = "SELECT * FROM reviews WHERE status::text = 'rejected' ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, reviewRowMapper);
    }
}
