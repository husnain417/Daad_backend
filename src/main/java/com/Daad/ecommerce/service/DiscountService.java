package com.Daad.ecommerce.service;

import com.Daad.ecommerce.model.Voucher;
import com.Daad.ecommerce.repository.VoucherRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class DiscountService {
	public static class DiscountResult {
		public final double amount;
		public final String reason;
		public final String voucherCode;
		public DiscountResult(double amount, String reason, String voucherCode) {
			this.amount = amount;
			this.reason = reason;
			this.voucherCode = voucherCode;
		}
	}

	private final VoucherRepository voucherRepository;

	public DiscountService(VoucherRepository voucherRepository) {
		this.voucherRepository = voucherRepository;
	}

	public DiscountResult calculateDiscount(String userId, double subtotal, String voucherCode) {
		// Currently we only apply voucher-based admin discounts.
		if (voucherCode == null || voucherCode.isBlank()) {
			return new DiscountResult(0.0, "No discount", null);
		}

		Instant now = Instant.now();
		Optional<Voucher> opt = voucherRepository.findActiveByCode(voucherCode.trim(), now);
		if (opt.isEmpty()) {
			return new DiscountResult(0.0, "Invalid or inactive voucher", null);
		}
		Voucher v = opt.get();

		// Basic eligibility: minimum order
		if (subtotal < v.getMinimumOrder()) {
			return new DiscountResult(0.0, "Order does not meet minimum amount for this voucher", null);
		}

		// Usage limit
		if (v.getUsageLimit() != null && v.getUsedCount() != null && v.getUsedCount() >= v.getUsageLimit()) {
			return new DiscountResult(0.0, "Voucher usage limit reached", null);
		}

		// Note: applicableFor (all/category/vendor/...) is not enforced yet; treat as 'all'

		double discountAmount = 0.0;
		if ("percentage".equalsIgnoreCase(v.getType())) {
			discountAmount = subtotal * (v.getValue() / 100.0);
			if (v.getMaximumDiscount() != null && discountAmount > v.getMaximumDiscount()) {
				discountAmount = v.getMaximumDiscount();
			}
		} else if ("fixed".equalsIgnoreCase(v.getType())) {
			discountAmount = v.getValue();
		}

		if (discountAmount < 0) discountAmount = 0;
		if (discountAmount > subtotal) discountAmount = subtotal;

		return new DiscountResult(discountAmount, "Voucher " + v.getCode(), v.getCode());
	}

	// Backwards-compatible overload without voucher
	public DiscountResult calculateDiscount(String userId, double subtotal) {
		return calculateDiscount(userId, subtotal, null);
	}
}

