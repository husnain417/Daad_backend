package com.Daad.ecommerce.service;

import com.Daad.ecommerce.model.Voucher;
import com.Daad.ecommerce.repository.VoucherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

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
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	public DiscountService(VoucherRepository voucherRepository) {
		this.voucherRepository = voucherRepository;
	}

	public DiscountResult calculateDiscount(String userId, double subtotal, String voucherCode, List<Map<String, Object>> cartItems) {
		// Currently we only apply voucher-based admin discounts.
		if (voucherCode == null || voucherCode.isBlank()) {
			return new DiscountResult(0.0, "No discount", null);
		}

		Instant now = Instant.now();
		// Normalize the voucher code: trim, uppercase, and normalize spaces (replace multiple spaces with single space)
		String normalizedCode = voucherCode.trim().replaceAll("\\s+", " ").toUpperCase();
		
		// Log for debugging
		System.out.println("=== VOUCHER VALIDATION ===");
		System.out.println("Original voucher code: " + voucherCode);
		System.out.println("Normalized voucher code: " + normalizedCode);
		System.out.println("Current time: " + now);
		System.out.println("Subtotal: " + subtotal);
		
		Optional<Voucher> opt = voucherRepository.findActiveByCode(normalizedCode, now);
		if (opt.isEmpty()) {
			// Try to find the voucher without date/active checks to see why it failed
			Optional<Voucher> anyVoucher = voucherRepository.findByCode(normalizedCode);
			if (anyVoucher.isPresent()) {
				Voucher v = anyVoucher.get();
				System.out.println("Voucher found but not active/valid:");
				System.out.println("  isActive: " + v.isActive());
				System.out.println("  validFrom: " + v.getValidFrom());
				System.out.println("  validUntil: " + v.getValidUntil());
				System.out.println("  Now: " + now);
				
				if (!v.isActive()) {
					return new DiscountResult(0.0, "Voucher is inactive", null);
				}
				
				// WORKAROUND: If validFrom is in the future but within 5 hours (timezone offset issue),
				// treat it as if it was meant to be valid now
				if (v.getValidFrom() != null && v.getValidFrom().isAfter(now)) {
					java.time.Duration timeDiff = java.time.Duration.between(now, v.getValidFrom());
					long hoursDiff = timeDiff.toHours();
					
					// If the voucher is less than 6 hours in the future, it's likely a timezone issue
					// from the old code. In this case, we'll treat it as valid now.
					if (hoursDiff > 0 && hoursDiff < 6) {
						System.out.println("⚠️ Voucher validFrom is " + hoursDiff + " hours in the future (likely timezone issue). Treating as valid now.");
						// Continue with validation as if the voucher is valid
					} else {
						return new DiscountResult(0.0, "Voucher is not yet valid. Valid from: " + v.getValidFrom(), null);
					}
				}
				
				if (v.getValidUntil() != null && v.getValidUntil().isBefore(now)) {
					return new DiscountResult(0.0, "Voucher has expired. Valid until: " + v.getValidUntil(), null);
				}
				
				// If we get here, the voucher exists and should be valid (timezone workaround applied)
				// Use this voucher for discount calculation
				opt = Optional.of(v);
			} else {
				System.out.println("Voucher not found in database");
				return new DiscountResult(0.0, "Invalid or inactive voucher", null);
			}
		}
		
		if (opt.isEmpty()) {
			return new DiscountResult(0.0, "Invalid or inactive voucher", null);
		}
		Voucher v = opt.get();
		
		System.out.println("Voucher found and active:");
		System.out.println("  Code: " + v.getCode());
		System.out.println("  Type: " + v.getType());
		System.out.println("  Value: " + v.getValue());
		System.out.println("  Minimum Order: " + v.getMinimumOrder());
		System.out.println("  Applicable For: " + v.getApplicableFor());

		// Basic eligibility: minimum order
		if (subtotal < v.getMinimumOrder()) {
			return new DiscountResult(0.0, "Order does not meet minimum amount for this voucher", null);
		}

		// Usage limit
		if (v.getUsageLimit() != null && v.getUsedCount() != null && v.getUsedCount() >= v.getUsageLimit()) {
			return new DiscountResult(0.0, "Voucher usage limit reached", null);
		}

		// Check if voucher applies to cart items
		if (!isVoucherApplicableToCart(v, cartItems)) {
			return new DiscountResult(0.0, "Voucher does not apply to items in your cart", null);
		}

		// Calculate discount based on applicable items only
		double applicableSubtotal = calculateApplicableSubtotal(v, cartItems);
		if (applicableSubtotal <= 0) {
			return new DiscountResult(0.0, "Voucher does not apply to items in your cart", null);
		}

		double discountAmount = 0.0;
		if ("percentage".equalsIgnoreCase(v.getType())) {
			discountAmount = applicableSubtotal * (v.getValue() / 100.0);
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

	private boolean isVoucherApplicableToCart(Voucher voucher, List<Map<String, Object>> cartItems) {
		if (cartItems == null || cartItems.isEmpty()) {
			return "all".equalsIgnoreCase(voucher.getApplicableFor());
		}

		// If voucher applies to all, it's valid
		if ("all".equalsIgnoreCase(voucher.getApplicableFor())) {
			return true;
		}

		// Get applicable item IDs from voucher_applicable_items
		List<UUID> applicableIds = getApplicableItemIds(voucher.getId());
		if (applicableIds.isEmpty()) {
			// If no specific items are set, treat as 'all'
			return true;
		}

		// Check if any cart item matches
		for (Map<String, Object> item : cartItems) {
			String productId = item.get("productId") != null ? item.get("productId").toString() : null;
			String vendorId = item.get("vendorId") != null ? item.get("vendorId").toString() : null;
			
			if ("product".equalsIgnoreCase(voucher.getApplicableFor()) && productId != null) {
				try {
					UUID productUuid = UUID.fromString(productId);
					if (applicableIds.contains(productUuid)) {
						return true;
					}
				} catch (IllegalArgumentException e) {
					// Invalid UUID, skip
				}
			} else if ("vendor".equalsIgnoreCase(voucher.getApplicableFor()) && vendorId != null) {
				try {
					UUID vendorUuid = UUID.fromString(vendorId);
					if (applicableIds.contains(vendorUuid)) {
						return true;
					}
				} catch (IllegalArgumentException e) {
					// Invalid UUID, skip
				}
			}
		}

		return false;
	}

	private double calculateApplicableSubtotal(Voucher voucher, List<Map<String, Object>> cartItems) {
		if (cartItems == null || cartItems.isEmpty()) {
			return 0.0;
		}

		// If voucher applies to all, use full subtotal
		if ("all".equalsIgnoreCase(voucher.getApplicableFor())) {
			return cartItems.stream()
				.mapToDouble(item -> {
					Object price = item.get("price");
					Object quantity = item.get("quantity");
					if (price != null && quantity != null) {
						return Double.parseDouble(price.toString()) * Integer.parseInt(quantity.toString());
					}
					return 0.0;
				})
				.sum();
		}

		// Get applicable item IDs
		List<UUID> applicableIds = getApplicableItemIds(voucher.getId());
		if (applicableIds.isEmpty()) {
			// If no specific items, treat as 'all'
			return cartItems.stream()
				.mapToDouble(item -> {
					Object price = item.get("price");
					Object quantity = item.get("quantity");
					if (price != null && quantity != null) {
						return Double.parseDouble(price.toString()) * Integer.parseInt(quantity.toString());
					}
					return 0.0;
				})
				.sum();
		}

		// Calculate subtotal for applicable items only
		double applicableSubtotal = 0.0;
		for (Map<String, Object> item : cartItems) {
			String productId = item.get("productId") != null ? item.get("productId").toString() : null;
			String vendorId = item.get("vendorId") != null ? item.get("vendorId").toString() : null;
			Object price = item.get("price");
			Object quantity = item.get("quantity");

			boolean isApplicable = false;
			if ("product".equalsIgnoreCase(voucher.getApplicableFor()) && productId != null) {
				try {
					UUID productUuid = UUID.fromString(productId);
					isApplicable = applicableIds.contains(productUuid);
				} catch (IllegalArgumentException e) {
					// Invalid UUID, skip
				}
			} else if ("vendor".equalsIgnoreCase(voucher.getApplicableFor()) && vendorId != null) {
				try {
					UUID vendorUuid = UUID.fromString(vendorId);
					isApplicable = applicableIds.contains(vendorUuid);
				} catch (IllegalArgumentException e) {
					// Invalid UUID, skip
				}
			}

			if (isApplicable && price != null && quantity != null) {
				applicableSubtotal += Double.parseDouble(price.toString()) * Integer.parseInt(quantity.toString());
			}
		}

		return applicableSubtotal;
	}

	private List<UUID> getApplicableItemIds(String voucherId) {
		String sql = "SELECT applicable_id FROM voucher_applicable_items WHERE voucher_id = ?::uuid";
		try {
			return jdbcTemplate.query(sql, (rs, rowNum) -> {
				return UUID.fromString(rs.getString("applicable_id"));
			}, voucherId);
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	// Backwards-compatible overload without cart items
	public DiscountResult calculateDiscount(String userId, double subtotal, String voucherCode) {
		return calculateDiscount(userId, subtotal, voucherCode, null);
	}

	// Backwards-compatible overload without voucher
	public DiscountResult calculateDiscount(String userId, double subtotal) {
		return calculateDiscount(userId, subtotal, null, null);
	}
}

