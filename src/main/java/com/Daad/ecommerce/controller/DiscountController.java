package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import com.Daad.ecommerce.service.DiscountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/discount")
@CrossOrigin(origins = "*")
@PreAuthorize("isAuthenticated()")
public class DiscountController {

	@Autowired private UserRepository userRepository;
	@Autowired private DiscountService discountService;

	@PostMapping("/calculate-preview")
	public ResponseEntity<?> calculateDiscountPreview(@RequestBody Map<String, Object> body) {
		Object subtotalObj = body.get("subtotal");
		int pointsToUse = body.get("pointsToUse") != null ? Integer.parseInt(body.get("pointsToUse").toString()) : 0;
		String voucherCode = body.get("voucherCode") != null ? body.get("voucherCode").toString() : null;
		if (subtotalObj == null) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Subtotal is required"));
		}
		double subtotal = Double.parseDouble(subtotalObj.toString());

		String userId = SecurityUtils.currentUserId();
		var userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
		}
		var user = userOpt.get();
		int rewardPoints = user.getRewardPoints() == null ? 0 : user.getRewardPoints();
		if (pointsToUse > rewardPoints) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Cannot use more points than available. You have " + rewardPoints + " points."));
		}

		var result = discountService.calculateDiscount(userId, subtotal, voucherCode);
		double pointsDiscount = pointsToUse; // 1 point = 1 unit
		double total = subtotal - result.amount - pointsDiscount;
		int pointsEarned = (int) Math.floor(total / 100.0);

		return ResponseEntity.ok(Map.of(
			"success", true,
			"subtotal", subtotal,
			"discountAmount", result.amount,
			"discountReason", result.reason,
			"voucherCode", result.voucherCode,
			"pointsDiscount", pointsDiscount,
			"pointsToUse", pointsToUse,
			"pointsEarned", pointsEarned,
			"total", Math.max(0, total)
		));
	}
}
