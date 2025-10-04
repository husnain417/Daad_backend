package com.Daad.ecommerce.service;

import org.springframework.stereotype.Service;

@Service
public class DiscountService {
	public static class DiscountResult {
		public final double amount;
		public final String reason;
		public DiscountResult(double amount, String reason) { this.amount = amount; this.reason = reason; }
	}

	public DiscountResult calculateDiscount(String userId, double subtotal) {
		// Stub: no complex rules yet. You can plug real logic later (student, first-order, vendor, etc.)
		double amount = 0.0;
		String reason = "No discount";
		return new DiscountResult(amount, reason);
	}
}
