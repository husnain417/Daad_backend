package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import com.Daad.ecommerce.service.DiscountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/discount")
@CrossOrigin(origins = "*")
public class DiscountController {

	@Autowired private UserRepository userRepository;
	@Autowired private DiscountService discountService;

	@PostMapping("/calculate-preview")
	public ResponseEntity<?> calculateDiscountPreview(@RequestBody Map<String, Object> body) {
		try {
			Object subtotalObj = body.get("subtotal");
			int pointsToUse = body.get("pointsToUse") != null ? Integer.parseInt(body.get("pointsToUse").toString()) : 0;
			String voucherCode = body.get("voucherCode") != null ? body.get("voucherCode").toString() : null;
			@SuppressWarnings("unchecked")
			java.util.List<Map<String, Object>> cartItems = body.get("cartItems") != null ? 
				(java.util.List<Map<String, Object>>) body.get("cartItems") : null;
			
			if (subtotalObj == null) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Subtotal is required"));
			}
			double subtotal = Double.parseDouble(subtotalObj.toString());

		// Get userId if authenticated - vouchers require login
		String userId = null;
		try {
			userId = SecurityUtils.currentUserId();
		} catch (Exception e) {
			// User is not authenticated (guest)
			// Vouchers require login, so return error if voucher code is provided
			if (voucherCode != null && !voucherCode.isBlank()) {
				Map<String, Object> response = new HashMap<>();
				response.put("success", false);
				response.put("message", "Please log in to use voucher codes");
				response.put("requiresLogin", true);
				response.put("subtotal", subtotal);
				response.put("discountAmount", 0.0);
				response.put("discountReason", "Login required");
				response.put("voucherCode", "");
				response.put("pointsDiscount", 0.0);
				response.put("pointsToUse", 0);
				response.put("pointsEarned", 0);
				response.put("total", subtotal);
				return ResponseEntity.status(401).body(response);
			}
		}

		// Validate user exists if authenticated
		if (userId != null) {
			var userOpt = userRepository.findById(userId);
			if (userOpt.isEmpty()) {
				return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
			}
			var user = userOpt.get();
			int rewardPoints = user.getRewardPoints() == null ? 0 : user.getRewardPoints();
			if (pointsToUse > rewardPoints) {
				return ResponseEntity.status(400).body(Map.of("success", false, "message", "Cannot use more points than available. You have " + rewardPoints + " points."));
			}
		} else if (pointsToUse > 0) {
			// Guest users cannot use points
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Please log in to use reward points"));
		}

		// Calculate discount (vouchers require authentication)
		var result = discountService.calculateDiscount(userId != null ? userId : "guest", subtotal, voucherCode, cartItems);
		double pointsDiscount = pointsToUse; // 1 point = 1 unit
		double total = subtotal - result.amount - pointsDiscount;
		int pointsEarned = userId != null ? (int) Math.floor(total / 100.0) : 0;

		// Check if voucher was successfully applied (discount amount > 0 and voucher code is present)
		boolean isSuccess = result.amount > 0 && result.voucherCode != null && !result.voucherCode.isBlank();
		
		// If voucher code was provided but discount is 0, it means validation failed
		if (voucherCode != null && !voucherCode.isBlank() && result.amount == 0) {
			return ResponseEntity.ok(Map.of(
				"success", false,
				"message", result.reason != null && !result.reason.equals("No discount") ? result.reason : "Invalid or inactive voucher code",
				"subtotal", subtotal,
				"discountAmount", 0.0,
				"discountReason", result.reason != null ? result.reason : "Invalid voucher",
				"voucherCode", "",
				"pointsDiscount", pointsDiscount,
				"pointsToUse", pointsToUse,
				"pointsEarned", pointsEarned,
				"total", Math.max(0, total)
			));
		}

		return ResponseEntity.ok(Map.of(
			"success", isSuccess,
			"subtotal", subtotal,
			"discountAmount", result.amount,
			"discountReason", result.reason,
			"voucherCode", result.voucherCode != null ? result.voucherCode : "",
			"pointsDiscount", pointsDiscount,
			"pointsToUse", pointsToUse,
			"pointsEarned", pointsEarned,
			"total", Math.max(0, total)
		));
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(500).body(Map.of(
				"success", false,
				"message", "An error occurred while processing the voucher: " + e.getMessage(),
				"subtotal", body.get("subtotal") != null ? Double.parseDouble(body.get("subtotal").toString()) : 0.0,
				"discountAmount", 0.0,
				"discountReason", "Error processing voucher",
				"voucherCode", "",
				"pointsDiscount", 0.0,
				"pointsToUse", 0,
				"pointsEarned", 0,
				"total", body.get("subtotal") != null ? Double.parseDouble(body.get("subtotal").toString()) : 0.0
			));
		}
	}
}
