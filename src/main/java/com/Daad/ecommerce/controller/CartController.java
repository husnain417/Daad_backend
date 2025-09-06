package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Cart;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.CartRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
@PreAuthorize("isAuthenticated()")
public class CartController {

	@Autowired private CartRepository cartRepository;
	@Autowired private ProductRepository productRepository;

	// Get user's cart
	@GetMapping("/")
	public ResponseEntity<Map<String, Object>> getCart() {
		String userId = SecurityUtils.currentUserId();
		Cart cart = cartRepository.getOrCreate(userId);

		// Filter out inactive/unapproved products
		cart.setItems(new ArrayList<>(cart.getItems()));
		cart.getItems().removeIf(item -> {
			Optional<Product> p = productRepository.findById(item.getProductId());
			return p.isEmpty() || !Boolean.TRUE.equals(p.get().getIsActive()) || !"approved".equalsIgnoreCase(p.get().getStatus());
		});
		cart.calculateTotals();
		cartRepository.save(cart);

		return ResponseEntity.ok(Map.of(
			"success", true,
			"data", cart,
			"summary", cart.getSummary()
		));
	}

	// Add item to cart
	@PostMapping("/add")
	public ResponseEntity<Map<String, Object>> addToCart(@RequestBody Map<String, Object> body) {
		String userId = SecurityUtils.currentUserId();
		Object productIdObj = body.get("productId");
		String color = Objects.toString(body.get("color"), null);
		String size = Objects.toString(body.get("size"), null);
		int quantity = Integer.parseInt(Objects.toString(body.getOrDefault("quantity", 1)));

		if (productIdObj == null || color == null || size == null) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Product ID, color, and size are required"));
		}
		if (quantity < 1) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Quantity must be at least 1"));
		}

		String productId = productIdObj.toString();
		Optional<Product> productOpt = productRepository.findById(productId);
		if (productOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found"));
		Product product = productOpt.get();
		if (!Boolean.TRUE.equals(product.getIsActive()) || !"approved".equalsIgnoreCase(product.getStatus())) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Product is not available for purchase"));
		}
		if (product.getVendor() == null || !"approved".equalsIgnoreCase(product.getVendor().getStatus())) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Product vendor is not approved"));
		}

		var inv = product.getColorInventories().stream()
				.filter(colorInv -> color.equals(colorInv.getColor()))
				.flatMap(colorInv -> colorInv.getSizes().stream())
				.filter(sizeInv -> size.equals(sizeInv.getSize()))
				.findFirst();
		if (inv.isEmpty() || inv.get().getStock() < quantity) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Insufficient stock for " + color + " " + size + ". Available: " + (inv.isPresent() ? inv.get().getStock() : 0)));
		}

		Cart cart = cartRepository.getOrCreate(userId);

		// price with discount
		double basePrice = product.getPrice().doubleValue();
		double finalPrice = basePrice;
		if (product.getDiscount() != null && product.getDiscount().getDiscountValue() != null && product.getDiscount().getDiscountType() != null) {
			// Support percentage discount type "percentage"
			if ("percentage".equalsIgnoreCase(product.getDiscount().getDiscountType())) {
				finalPrice = basePrice * (1.0 - product.getDiscount().getDiscountValue().doubleValue() / 100.0);
			}
		}

		cart.addItem(productId, product.getVendor().getId(), color, size, quantity, finalPrice);
		cartRepository.save(cart);

		return ResponseEntity.ok(Map.of("success", true, "message", "Item added to cart successfully", "data", cart, "summary", cart.getSummary()));
	}

	// Update cart item quantity
	@PutMapping("/update")
	public ResponseEntity<Map<String, Object>> updateCartItem(@RequestBody Map<String, Object> body) {
		String userId = SecurityUtils.currentUserId();
		String productId = body.get("productId").toString();
		String color = body.get("color").toString();
		String size = body.get("size").toString();
		int quantity = Integer.parseInt(body.get("quantity").toString());

		if (quantity < 1) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Quantity must be at least 1"));

		Optional<Product> productOpt = productRepository.findById(productId);
		if (productOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found"));
		Product product = productOpt.get();
		var inv = product.getColorInventories().stream()
				.filter(colorInv -> color.equals(colorInv.getColor()))
				.flatMap(colorInv -> colorInv.getSizes().stream())
				.filter(sizeInv -> size.equals(sizeInv.getSize()))
				.findFirst();
		if (inv.isEmpty() || inv.get().getStock() < quantity) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Insufficient stock for " + color + " " + size + ". Available: " + (inv.isPresent() ? inv.get().getStock() : 0)));
		}

		Cart cart = cartRepository.findByUser(userId).orElse(null);
		if (cart == null) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));

		cart.updateItemQuantity(productId, product.getVendor() != null ? product.getVendor().getId() : null, color, size, quantity);
		cartRepository.save(cart);

		return ResponseEntity.ok(Map.of("success", true, "message", "Cart item updated successfully", "data", cart, "summary", cart.getSummary()));
	}

	// Remove item
	@DeleteMapping("/remove")
	public ResponseEntity<Map<String, Object>> removeFromCart(@RequestBody Map<String, Object> body) {
		String userId = SecurityUtils.currentUserId();
		String productId = body.get("productId").toString();
		String color = body.get("color").toString();
		String size = body.get("size").toString();

		Cart cart = cartRepository.findByUser(userId).orElse(null);
		if (cart == null) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));

		String vendorId = productRepository.findById(productId).map(p -> p.getVendor() != null ? p.getVendor().getId() : null).orElse(null);
		cart.removeItem(productId, vendorId, color, size);
		cartRepository.save(cart);

		return ResponseEntity.ok(Map.of("success", true, "message", "Item removed from cart successfully", "data", cart, "summary", cart.getSummary()));
	}

	// Clear cart
	@DeleteMapping("/clear")
	public ResponseEntity<Map<String, Object>> clearCart() {
		String userId = SecurityUtils.currentUserId();
		Cart cart = cartRepository.findByUser(userId).orElse(null);
		if (cart == null) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		cart.clearCart();
		cartRepository.save(cart);
		return ResponseEntity.ok(Map.of("success", true, "message", "Cart cleared successfully", "data", cart, "summary", cart.getSummary()));
	}

	// Update shipping address
	@PutMapping("/shipping-address")
	public ResponseEntity<Map<String, Object>> updateShippingAddress(@RequestBody Cart.ShippingAddress shippingAddress) {
		String userId = SecurityUtils.currentUserId();
		Cart cart = cartRepository.findByUser(userId).orElse(null);
		if (cart == null) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		cart.setShippingAddress(shippingAddress);
		cartRepository.save(cart);
		return ResponseEntity.ok(Map.of("success", true, "message", "Shipping address updated successfully", "data", cart.getShippingAddress()));
	}

	// Calculate shipping and tax
	@PostMapping("/calculate-shipping")
	public ResponseEntity<Map<String, Object>> calculateShipping(@RequestBody Map<String, Object> body) {
		String userId = SecurityUtils.currentUserId();
		Cart cart = cartRepository.findByUser(userId).orElse(null);
		if (cart == null) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));

		Map<String, Object> shippingAddress = (Map<String, Object>) body.get("shippingAddress");

		double shippingCost = 0.0; int minDays = 3, maxDays = 7;
		if (shippingAddress != null) {
			String country = Objects.toString(shippingAddress.get("country"), "");
			if ("Pakistan".equalsIgnoreCase(country)) {
				shippingCost = cart.getSubtotal() > 5000 ? 0.0 : 200.0;
				minDays = 2; maxDays = 5;
			} else {
				shippingCost = 500.0; minDays = 7; maxDays = 14;
			}
		}

		double taxRate = (shippingAddress != null && "Pakistan".equalsIgnoreCase(Objects.toString(shippingAddress.get("country"), ""))) ? 0.05 : 0.0;
		double taxAmount = cart.getSubtotal() * taxRate;

		cart.setShipping(shippingCost);
		cart.setTax(taxAmount);
		cart.setEstimatedDelivery(new Cart.EstimatedDelivery(minDays, maxDays));
		if (shippingAddress != null) {
			Cart.ShippingAddress sa = new Cart.ShippingAddress();
			sa.setFullName(Objects.toString(shippingAddress.get("fullName"), null));
			sa.setAddressLine1(Objects.toString(shippingAddress.get("addressLine1"), null));
			sa.setAddressLine2(Objects.toString(shippingAddress.get("addressLine2"), null));
			sa.setCity(Objects.toString(shippingAddress.get("city"), null));
			sa.setState(Objects.toString(shippingAddress.get("state"), null));
			sa.setPostalCode(Objects.toString(shippingAddress.get("postalCode"), null));
			sa.setCountry(Objects.toString(shippingAddress.get("country"), null));
			sa.setPhoneNumber(Objects.toString(shippingAddress.get("phoneNumber"), null));
			cart.setShippingAddress(sa);
		}

		cart.calculateTotals();
		cartRepository.save(cart);

		return ResponseEntity.ok(Map.of(
			"success", true,
			"data", Map.of(
				"shipping", shippingCost,
				"tax", taxAmount,
				"estimatedDelivery", cart.getEstimatedDelivery(),
				"subtotal", cart.getSubtotal(),
				"total", cart.getTotal()
			)
		));
	}

	// Get cart summary
	@GetMapping("/summary")
	public ResponseEntity<Map<String, Object>> getCartSummary() {
		String userId = SecurityUtils.currentUserId();
		Cart cart = cartRepository.findByUser(userId).orElse(null);
		if (cart == null) {
			return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
				"itemCount", 0,
				"totalItems", 0,
				"subtotal", 0,
				"tax", 0,
				"shipping", 0,
				"discount", 0,
				"total", 0
			)));
		}
		return ResponseEntity.ok(Map.of("success", true, "data", cart.getSummary()));
	}
}
