package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.dto.Review;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.repository.ReviewRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ratings")
@CrossOrigin(origins = "*")
public class RatingController {

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private ProductRepository productRepository;

    // Create a new rating/review (customers only)
    @PostMapping("/")
    @PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> createRating(@RequestBody Map<String, Object> body) {
		String customerId = SecurityUtils.currentUserId();
		Object productIdObj = body.get("productId");
		Object ratingObj = body.get("rating");
		String title = body.get("title") != null ? body.get("title").toString() : null;
		String comment = body.get("comment") != null ? body.get("comment").toString() : null;

		if (productIdObj == null || ratingObj == null) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Product ID and rating are required"));
		}

		String productId;
		Integer rating;
		try {
			productId = productIdObj.toString();
			rating = Integer.valueOf(ratingObj.toString());
		} catch (NumberFormatException e) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid productId or rating"));
		}

		if (rating < 1 || rating > 5) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Rating must be between 1 and 5"));
		}

		Optional<Product> productOpt = productRepository.findById(productId);
		if (productOpt.isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found"));
		}

		// one-per-user-per-product
		if (reviewRepository.findOneByProductIdAndCustomerId(productId, customerId).isPresent()) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "You have already reviewed this product"));
		}

		Review review = new Review();
		review.setProductId(productId);
		review.setCustomerId(customerId);
		review.setRating(rating);
		review.setTitle(title);
		review.setComment(comment);
		review.setStatus("approved");

		Review saved = reviewRepository.save(review);

		// Update product average rating
		updateProductRating(productId);

		return ResponseEntity.status(201).body(Map.of("success", true, "message", "Rating submitted successfully", "data", saved));
	}

	// Get all ratings for a specific product
	@GetMapping("/product/{productId}")
	public ResponseEntity<Map<String, Object>> getProductRatings(
			@PathVariable String productId,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "10") Integer limit,
			@RequestParam(defaultValue = "-createdAt") String sort
	) {
		Optional<Product> productOpt = productRepository.findById(productId);
		if (productOpt.isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found"));
		}

		List<Review> allApproved = reviewRepository.findByProductIdAndStatus(productId, "approved");
		// sort handling: we only support -createdAt or createdAt
		if ("createdAt".equals(sort)) {
			allApproved.sort(Comparator.comparing(Review::getCreatedAt));
		} else {
			allApproved.sort(Comparator.comparing(Review::getCreatedAt).reversed());
		}

		int total = allApproved.size();
		int start = Math.max(0, (page - 1) * limit);
		int end = Math.min(total, start + limit);
		List<Review> paginated = start < end ? allApproved.subList(start, end) : new ArrayList<>();

		// rating stats
		Map<Integer, Long> dist = reviewRepository.ratingDistributionForProduct(productId);
		OptionalDouble avg = reviewRepository.averageRatingForProduct(productId);
		Map<String, Object> ratingStats = new HashMap<>();
		ratingStats.put("averageRating", avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : 0.0);
		ratingStats.put("totalReviews", total);
		ratingStats.put("ratingDistribution", dist);

		Map<String, Object> pagination = new HashMap<>();
		pagination.put("page", page);
		pagination.put("pages", (int) Math.ceil((double) total / limit));

		Map<String, Object> productInfo = new HashMap<>();
		productInfo.put("id", productOpt.get().getId());
		productInfo.put("name", productOpt.get().getName());
		productInfo.put("category", productOpt.get().getCategory());

		Map<String, Object> resp = new HashMap<>();
		resp.put("success", true);
		resp.put("count", paginated.size());
		resp.put("total", total);
		resp.put("pagination", pagination);
		resp.put("product", productInfo);
		resp.put("ratingStats", ratingStats);
		resp.put("data", paginated);
		return ResponseEntity.ok(resp);
	}

	// Get user's ratings
	@GetMapping("/user")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> getUserRatings(
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "10") Integer limit,
			@RequestParam(defaultValue = "-createdAt") String sort
	) {
		String customerId = SecurityUtils.currentUserId();
		List<Review> allApproved = reviewRepository.findByCustomerIdAndStatus(customerId, "approved");
		if ("createdAt".equals(sort)) {
			allApproved.sort(Comparator.comparing(Review::getCreatedAt));
		} else {
			allApproved.sort(Comparator.comparing(Review::getCreatedAt).reversed());
		}

		int total = allApproved.size();
		int start = Math.max(0, (page - 1) * limit);
		int end = Math.min(total, start + limit);
		List<Review> paginated = start < end ? allApproved.subList(start, end) : new ArrayList<>();

		Map<String, Object> pagination = new HashMap<>();
		pagination.put("page", page);
		pagination.put("pages", (int) Math.ceil((double) total / limit));

		Map<String, Object> resp = new HashMap<>();
		resp.put("success", true);
		resp.put("count", paginated.size());
		resp.put("total", total);
		resp.put("pagination", pagination);
		resp.put("data", paginated);
		return ResponseEntity.ok(resp);
	}

	// Update user's rating
	@PutMapping("/{reviewId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> updateRating(@PathVariable String reviewId,
																 @RequestBody Map<String, Object> body) {
		String customerId = SecurityUtils.currentUserId();
		Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
		if (reviewOpt.isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Review not found"));
		}
		Review review = reviewOpt.get();
		if (!Objects.equals(review.getCustomerId(), customerId)) {
			return ResponseEntity.status(403).body(Map.of("success", false, "message", "You can only update your own reviews"));
		}
		if (!"approved".equals(review.getStatus())) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Only approved reviews can be updated"));
		}

		if (body.containsKey("rating") && body.get("rating") != null) {
			int r;
			try { r = Integer.parseInt(body.get("rating").toString()); } catch (NumberFormatException e) { r = -1; }
			if (r < 1 || r > 5) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Rating must be between 1 and 5"));
			}
			review.setRating(r);
		}
		if (body.containsKey("title")) {
			review.setTitle(body.get("title") != null ? body.get("title").toString() : null);
		}
		if (body.containsKey("comment")) {
			review.setComment(body.get("comment") != null ? body.get("comment").toString() : null);
		}

		reviewRepository.save(review);
		updateProductRating(review.getProductId());
		return ResponseEntity.ok(Map.of("success", true, "message", "Rating updated successfully", "data", review));
	}

	// Delete user's rating
	@DeleteMapping("/{reviewId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> deleteRating(@PathVariable String reviewId) {
		String customerId = SecurityUtils.currentUserId();
		Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
		if (reviewOpt.isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Review not found"));
		}
		Review review = reviewOpt.get();
		if (!Objects.equals(review.getCustomerId(), customerId)) {
			return ResponseEntity.status(403).body(Map.of("success", false, "message", "You can only delete your own reviews"));
		}

		String productId = review.getProductId();
		reviewRepository.deleteById(reviewId);
		updateProductRating(productId);
		return ResponseEntity.ok(Map.of("success", true, "message", "Rating deleted successfully"));
	}

	// Helper to update product average rating in in-memory product store
	private void updateProductRating(String productId) {
		OptionalDouble avg = reviewRepository.averageRatingForProduct(productId);
		productRepository.findById(productId).ifPresent(p -> {
			double val = avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : 0.0;
			p.setAverageRating(java.math.BigDecimal.valueOf(val));
			productRepository.save(p);
		});
	}
}
