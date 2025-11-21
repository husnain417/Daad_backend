package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Cart;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.CartRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
@Slf4j
public class CartController {

	@Autowired private CartRepository cartRepository;
	@Autowired private ProductRepository productRepository;

	// Helper method to get user ID or cart ID
	private String getCartIdentifier(String guestCartId) {
		try {
			String userId = SecurityUtils.currentUserId();
			if (userId != null && !userId.isBlank() && !"anonymousUser".equalsIgnoreCase(userId)) {
				return userId; // Return user ID for logged-in users
			}
		} catch (Exception e) {
			// ignore and fall back to guest logic
		}
		
		// For guests, use provided cart ID or generate new one
		if (guestCartId == null || guestCartId.trim().isEmpty()) {
			return UUID.randomUUID().toString();
		}
		return guestCartId;
	}

	private boolean isUserLoggedIn() {
		try {
			String userId = SecurityUtils.currentUserId();
			return userId != null && !userId.isBlank() && !"anonymousUser".equalsIgnoreCase(userId);
		} catch (Exception e) {
			return false;
		}
	}

	// Separate method to enrich cart with products without mixing JPA and JDBC transactions
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<Product> enrichCartWithProducts(List<String> productIds) {
		try {
			return productRepository.findByIds(productIds);
		} catch (Exception e) {
			System.err.println("Error in separate transaction for product enrichment: " + e.getMessage());
			return new ArrayList<>();
		}
	}

	// Separate method to validate product without mixing JPA and JDBC transactions
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Product validateProductForCart(String productId) {
		try {
			Optional<Product> productOpt = productRepository.findById(productId);
			if (productOpt.isEmpty()) return null;
			
			Product product = productOpt.get();
			if (!Boolean.TRUE.equals(product.getIsActive()) || !"approved".equalsIgnoreCase(product.getStatus())) {
				return null;
			}
			if (product.getVendor() == null || !"approved".equalsIgnoreCase(product.getVendor().getStatus())) {
				return null;
			}
			
			return product;
		} catch (Exception e) {
			System.err.println("Error in separate transaction for product validation: " + e.getMessage());
			return null;
		}
	}

	@GetMapping("/")
	public ResponseEntity<Map<String, Object>> getCart(@RequestParam(value = "cartId", required = false) String guestCartId) {
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		Cart cart = cartRepository.getOrCreate(cartIdentifier, isLoggedIn);
		
		// Filter out inactive/unapproved products and enrich with product details
		cart.setItems(new ArrayList<>(cart.getItems()));
		
		// Get all product IDs from cart items
		List<String> productIds = cart.getItems().stream()
			.map(Cart.CartItem::getProductId)
			.distinct()
			.collect(Collectors.toList());
		
		// Fetch all products to enrich cart items - do this in a separate transaction
		final Map<String, Product> productMap = new HashMap<>();
		if (!productIds.isEmpty()) {
			try {
				// Use a separate method that doesn't mix with JDBC transactions
				List<Product> products = enrichCartWithProducts(productIds);
				productMap.putAll(products.stream()
					.collect(Collectors.toMap(Product::getId, p -> p)));
			} catch (Exception e) {
				System.err.println("Error fetching products for cart enrichment: " + e.getMessage());
			}
		}
		
		cart.getItems().removeIf(item -> {
			Product product = productMap.get(item.getProductId());
			if (product == null) {
				return true; // Remove if product no longer exists
			}
			
			// Enrich cart item with product details
			item.setProductName(product.getName());
			item.setProductDescription(product.getDescription());
			
			// Set the first default image URL
			if (product.getDefaultImages() != null && !product.getDefaultImages().isEmpty()) {
				item.setDefaultImageUrl(product.getDefaultImages().get(0).getUrl());
			}
			
			return false; // Keep the item
		});
		
		cart.calculateTotals();
		
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("data", cart);
		response.put("summary", cart.getSummary());
		
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}
	
		return ResponseEntity.ok(response);
	}

	// Add item to cart
	@PostMapping("/add")
	public ResponseEntity<Map<String, Object>> addToCart(@RequestBody Map<String, Object> body) {
		String guestCartId = Objects.toString(body.get("cartId"), null);
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		Object productIdObj = body.get("productId");
		String color = Objects.toString(body.get("color"), null);
		String size = Objects.toString(body.get("size"), null);
		int quantity = Integer.parseInt(Objects.toString(body.getOrDefault("quantity", 1)));

		if (productIdObj == null || color == null || size == null) {
			log.error("Add to cart validation failed: Product ID, color, and size are required");
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Product ID, color, and size are required"));
		}
		if (quantity < 1) {
			log.error("Add to cart validation failed: Quantity must be at least 1");
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Quantity must be at least 1"));
		}

		String productId = productIdObj.toString();
		Product product = validateProductForCart(productId);
		if (product == null) {
			log.error("Add to cart failed: Product {} not found or not available for purchase", productId);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found or not available for purchase"));
		}

		var inv = product.getColorInventories().stream()
				.filter(colorInv -> color.equals(colorInv.getColor()))
				.flatMap(colorInv -> colorInv.getSizes().stream())
				.filter(sizeInv -> size.equals(sizeInv.getSize()))
				.findFirst();
		if (inv.isEmpty() || inv.get().getStock() < quantity) {
			log.error("Add to cart failed: Insufficient stock for product {} color {} size {}", productId, color, size);
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Insufficient stock for " + color + " " + size + ". Available: " + (inv.isPresent() ? inv.get().getStock() : 0)));
		}

		Cart cart = cartRepository.getOrCreate(cartIdentifier, isLoggedIn);

		// price with discount
		double basePrice = product.getPrice().doubleValue();
		double finalPrice = basePrice;
		if (product.getDiscount() != null && product.getDiscount().getDiscountValue() != null && product.getDiscount().getDiscountType() != null) {
			// Support percentage discount type "percentage"
			if ("percentage".equalsIgnoreCase(product.getDiscount().getDiscountType())) {
				finalPrice = basePrice * (1.0 - product.getDiscount().getDiscountValue().doubleValue() / 100.0);
			}
		}

		// Get the first default image URL
		String defaultImageUrl = null;
		if (product.getDefaultImages() != null && !product.getDefaultImages().isEmpty()) {
			defaultImageUrl = product.getDefaultImages().get(0).getUrl();
		}
		
		cart.addItem(productId, product.getVendor().getId(), color, size, quantity, finalPrice, 
				product.getName(), product.getDescription(), defaultImageUrl);
		cartRepository.save(cart);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("message", "Item added to cart successfully");
		response.put("data", cart);
		response.put("summary", cart.getSummary());
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Update cart item quantity
	@PutMapping("/update")
	public ResponseEntity<Map<String, Object>> updateCartItem(@RequestBody Map<String, Object> body) {
		String guestCartId = Objects.toString(body.get("cartId"), null);
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		String productId = body.get("productId").toString();
		String color = body.get("color").toString();
		String size = body.get("size").toString();
		int quantity = Integer.parseInt(body.get("quantity").toString());

		if (quantity < 1) {
			log.error("Update cart validation failed: Quantity must be at least 1");
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Quantity must be at least 1"));
		}

		Product product = validateProductForCart(productId);
		if (product == null) {
			log.error("Update cart failed: Product {} not found or not available for purchase", productId);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product not found or not available for purchase"));
		}

		var inv = product.getColorInventories().stream()
				.filter(colorInv -> color.equals(colorInv.getColor()))
				.flatMap(colorInv -> colorInv.getSizes().stream())
				.filter(sizeInv -> size.equals(sizeInv.getSize()))
				.findFirst();
		if (inv.isEmpty() || inv.get().getStock() < quantity) {
			log.error("Update cart failed: Insufficient stock for product {} color {} size {}", productId, color, size);
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Insufficient stock for " + color + " " + size + ". Available: " + (inv.isPresent() ? inv.get().getStock() : 0)));
		}

		Cart cart = cartRepository.findByIdentifier(cartIdentifier, isLoggedIn).orElse(null);
		if (cart == null) {
			log.error("Update cart failed: Cart {} not found", cartIdentifier);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		}

		cart.updateItemQuantity(productId, product.getVendor() != null ? product.getVendor().getId() : null, color, size, quantity);
		
		// Enrich the updated cart item with product details
		cart.getItems().stream()
			.filter(item -> item.getProductId().equals(productId) && item.getColor().equals(color) && item.getSize().equals(size))
			.findFirst()
			.ifPresent(item -> {
				item.setProductName(product.getName());
				item.setProductDescription(product.getDescription());
				if (product.getDefaultImages() != null && !product.getDefaultImages().isEmpty()) {
					item.setDefaultImageUrl(product.getDefaultImages().get(0).getUrl());
				}
			});
		
		cartRepository.save(cart);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("message", "Cart item updated successfully");
		response.put("data", cart);
		response.put("summary", cart.getSummary());
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Remove item
	@DeleteMapping("/remove")
	public ResponseEntity<Map<String, Object>> removeFromCart(@RequestBody Map<String, Object> body) {
		String guestCartId = Objects.toString(body.get("cartId"), null);
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		String productId = body.get("productId").toString();
		String color = body.get("color").toString();
		String size = body.get("size").toString();

		Cart cart = cartRepository.findByIdentifier(cartIdentifier, isLoggedIn).orElse(null);
		if (cart == null) {
			log.error("Remove from cart failed: Cart {} not found", cartIdentifier);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		}

		Product product = validateProductForCart(productId);
		String vendorId = product != null && product.getVendor() != null ? product.getVendor().getId() : null;
		cart.removeItem(productId, vendorId, color, size);
		cartRepository.save(cart);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("message", "Item removed from cart successfully");
		response.put("data", cart);
		response.put("summary", cart.getSummary());
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Clear cart
	@DeleteMapping("/clear")
	public ResponseEntity<Map<String, Object>> clearCart(@RequestParam(value = "cartId", required = false) String guestCartId) {
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		Cart cart = cartRepository.findByIdentifier(cartIdentifier, isLoggedIn).orElse(null);
		if (cart == null) {
			log.error("Clear cart failed: Cart {} not found", cartIdentifier);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		}
		cart.clearCart();
		cartRepository.save(cart);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("message", "Cart cleared successfully");
		response.put("data", cart);
		response.put("summary", cart.getSummary());
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Update shipping address
	@PutMapping("/shipping-address")
	public ResponseEntity<Map<String, Object>> updateShippingAddress(
			@RequestBody Map<String, Object> body) {
		
		String guestCartId = Objects.toString(body.get("cartId"), null);
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		// Extract shipping address from body
		Cart.ShippingAddress shippingAddress = new Cart.ShippingAddress();
		if (body.get("shippingAddress") instanceof Map) {
			Map<String, Object> addressMap = (Map<String, Object>) body.get("shippingAddress");
			shippingAddress.setType(Objects.toString(addressMap.get("type"), "home"));
			shippingAddress.setFullName(Objects.toString(addressMap.get("fullName"), null));
			shippingAddress.setAddressLine1(Objects.toString(addressMap.get("addressLine1"), null));
			shippingAddress.setAddressLine2(Objects.toString(addressMap.get("addressLine2"), null));
			shippingAddress.setCity(Objects.toString(addressMap.get("city"), null));
			shippingAddress.setState(Objects.toString(addressMap.get("state"), null));
			shippingAddress.setPostalCode(Objects.toString(addressMap.get("postalCode"), null));
			shippingAddress.setCountry(Objects.toString(addressMap.get("country"), null));
			shippingAddress.setPhoneNumber(Objects.toString(addressMap.get("phoneNumber"), null));
			shippingAddress.setEmail(Objects.toString(addressMap.get("email"), null));
		}
		
		Cart cart = cartRepository.findByIdentifier(cartIdentifier, isLoggedIn).orElse(null);
		if (cart == null) {
			log.error("Update shipping address failed: Cart {} not found", cartIdentifier);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		}
		cart.setShippingAddress(shippingAddress);
		cartRepository.save(cart);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("message", "Shipping address updated successfully");
		response.put("data", cart.getShippingAddress());
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Calculate shipping and tax
	@PostMapping("/calculate-shipping")
	public ResponseEntity<Map<String, Object>> calculateShipping(@RequestBody Map<String, Object> body) {
		String guestCartId = Objects.toString(body.get("cartId"), null);
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		Cart cart = cartRepository.findByIdentifier(cartIdentifier, isLoggedIn).orElse(null);
		if (cart == null) {
			log.error("Calculate shipping failed: Cart {} not found", cartIdentifier);
			return ResponseEntity.status(404).body(Map.of("success", false, "message", "Cart not found"));
		}

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
			sa.setType(Objects.toString(shippingAddress.get("type"), "home"));
			sa.setFullName(Objects.toString(shippingAddress.get("fullName"), null));
			sa.setAddressLine1(Objects.toString(shippingAddress.get("addressLine1"), null));
			sa.setAddressLine2(Objects.toString(shippingAddress.get("addressLine2"), null));
			sa.setCity(Objects.toString(shippingAddress.get("city"), null));
			sa.setState(Objects.toString(shippingAddress.get("state"), null));
			sa.setPostalCode(Objects.toString(shippingAddress.get("postalCode"), null));
			sa.setCountry(Objects.toString(shippingAddress.get("country"), null));
			sa.setPhoneNumber(Objects.toString(shippingAddress.get("phoneNumber"), null));
			sa.setEmail(Objects.toString(shippingAddress.get("email"), null));
			cart.setShippingAddress(sa);
		}

		cart.calculateTotals();
		cartRepository.save(cart);

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("data", Map.of(
			"shipping", shippingCost,
			"tax", taxAmount,
			"estimatedDelivery", cart.getEstimatedDelivery(),
			"subtotal", cart.getSubtotal(),
			"total", cart.getTotal()
		));
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Get cart summary
	@GetMapping("/summary")
	public ResponseEntity<Map<String, Object>> getCartSummary(@RequestParam(value = "cartId", required = false) String guestCartId) {
		String cartIdentifier = getCartIdentifier(guestCartId);
		boolean isLoggedIn = isUserLoggedIn();
		
		Cart cart = cartRepository.findByIdentifier(cartIdentifier, isLoggedIn).orElse(null);
		if (cart == null) {
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("data", Map.of(
				"itemCount", 0,
				"totalItems", 0,
				"subtotal", 0,
				"tax", 0,
				"shipping", 0,
				"discount", 0,
				"total", 0
			));
			
			// Return cart ID for guests even when cart is empty
			if (!isLoggedIn) {
				response.put("cartId", cartIdentifier);
			}
			
			return ResponseEntity.ok(response);
		}

		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("data", cart.getSummary());
		
		// Return cart ID for guests
		if (!isLoggedIn) {
			response.put("cartId", cartIdentifier);
		}

		return ResponseEntity.ok(response);
	}

	// Merge guest cart with user cart (called when user logs in)
	@PostMapping("/merge")
	public ResponseEntity<Map<String, Object>> mergeGuestCart(@RequestBody Map<String, Object> body) {
		if (!isUserLoggedIn()) {
			log.error("Merge cart failed: User must be logged in");
			return ResponseEntity.status(401).body(Map.of("success", false, "message", "User must be logged in to merge cart"));
		}

		String guestCartId = Objects.toString(body.get("guestCartId"), null);
		if (guestCartId == null || guestCartId.trim().isEmpty()) {
			log.error("Merge cart validation failed: Guest cart ID is required");
			return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Guest cart ID is required"));
		}
		
		String userId = SecurityUtils.currentUserId();
		
		// Get or create user cart
		Cart userCart = cartRepository.getOrCreate(userId, true);
		
		// Find guest cart
		Optional<Cart> guestCartOpt = cartRepository.findByIdentifier(guestCartId, false);
		if (guestCartOpt.isEmpty()) {
			return ResponseEntity.ok(Map.of(
				"success", true, 
				"message", "No guest cart found to merge",
				"data", userCart,
				"summary", userCart.getSummary()
			));
		}
		
		Cart guestCart = guestCartOpt.get();
		
		// Merge items from guest cart to user cart
		for (Cart.CartItem guestItem : guestCart.getItems()) {
			userCart.addItem(
				guestItem.getProductId(),
				guestItem.getVendorId(),
				guestItem.getColor(),
				guestItem.getSize(),
				guestItem.getQuantity(),
				guestItem.getPrice(),
				guestItem.getProductName(),
				guestItem.getProductDescription(),
				guestItem.getDefaultImageUrl()
			);
		}
		
		// Copy shipping details if user cart doesn't have them
		if (userCart.getShippingAddress() == null && guestCart.getShippingAddress() != null) {
			userCart.setShippingAddress(guestCart.getShippingAddress());
		}
		
		userCart.calculateTotals();
		cartRepository.save(userCart);
		
		// Delete guest cart
		cartRepository.deleteByCartId(guestCartId);
		
		return ResponseEntity.ok(Map.of(
			"success", true,
			"message", "Guest cart merged successfully",
			"data", userCart,
			"summary", userCart.getSummary()
		));
	}
}