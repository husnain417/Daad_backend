package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Order;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.CartRepository;
import com.Daad.ecommerce.repository.OrderRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import com.Daad.ecommerce.service.LocalUploadService;
import com.Daad.ecommerce.service.PaymentService;
import com.Daad.ecommerce.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

	@Autowired private OrderRepository orderRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private VendorRepository vendorRepository;
	@Autowired private LocalUploadService localUploadService;
	@Autowired private NotificationService notificationService;
	@Autowired private PaymentService paymentService;
	@Autowired private CartRepository cartRepository;

	private int calculatePoints(double amount) { return (int) Math.floor(amount / 100.0); }

	@PostMapping("/create")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> createOrderAuth(
			@RequestPart(value = "receipt", required = false) MultipartFile receipt,
			@RequestPart(value = "orderData", required = false) String orderDataJson,
			@RequestBody(required = false) Map<String, Object> jsonBody
	) {
		return createOrderInternal(receipt, orderDataJson, jsonBody, true);
	}

	@PostMapping("/create-guest")
	public ResponseEntity<Map<String, Object>> createOrderGuest(
			@RequestPart(value = "receipt", required = false) MultipartFile receipt,
			@RequestPart(value = "orderData", required = false) String orderDataJson,
			@RequestBody(required = false) Map<String, Object> jsonBody
	) {
		return createOrderInternal(receipt, orderDataJson, jsonBody, false);
	}

	private ResponseEntity<Map<String, Object>> createOrderInternal(MultipartFile receipt, String orderDataJson, Map<String, Object> jsonBody, boolean authenticated) {
		try {
			Map<String, Object> orderData;
			if (receipt != null && orderDataJson != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> parsedData = new com.fasterxml.jackson.databind.ObjectMapper().readValue(orderDataJson, Map.class);
				orderData = parsedData;
			} else {
				orderData = jsonBody != null ? jsonBody : new HashMap<>();
			}
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
			@SuppressWarnings("unchecked")
			Map<String, Object> shippingAddressMap = (Map<String, Object>) orderData.get("shippingAddress");
			@SuppressWarnings("unchecked")
			Map<String, Object> customerInfoMap = (Map<String, Object>) orderData.get("customerInfo");
			double subtotal = Double.parseDouble(orderData.get("subtotal").toString());
			double shippingCharges = orderData.get("shippingCharges") != null ? Double.parseDouble(orderData.get("shippingCharges").toString()) : 0.0;
			double total = Double.parseDouble(orderData.get("total").toString());
			double discount = orderData.get("discount") != null ? Double.parseDouble(orderData.get("discount").toString()) : 0.0;
			@SuppressWarnings("unchecked")
			Map<String, Object> discountInfo = (Map<String, Object>) orderData.getOrDefault("discountInfo", Map.of("amount", 0, "reasons", List.of(), "pointsUsed", 0));
			String paymentMethod = orderData.get("paymentMethod").toString();

			String userId = authenticated ? SecurityUtils.currentUserId() : null;

			if (items == null || items.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No items in order"));
			}

			int pointsToUse = Integer.parseInt(String.valueOf(discountInfo.getOrDefault("pointsUsed", 0)));
			if (userId != null) {
				var userOpt = userRepository.findById(userId);
				if (userOpt.isPresent()) {
					var user = userOpt.get();
					if (pointsToUse > Optional.ofNullable(user.getRewardPoints()).orElse(0)) {
						return ResponseEntity.status(400).body(Map.of("success", false, "message", "Cannot use more points than available. You have " + user.getRewardPoints() + " points."));
					}
				}
			}

			List<Order.Item> processedItems = new ArrayList<>();
			for (Map<String, Object> item : items) {
				String productId = item.get("product").toString();
				var productOpt = productRepository.findById(productId);
				if (productOpt.isEmpty()) {
					return ResponseEntity.status(404).body(Map.of("success", false, "message", "Product " + productId + " not found"));
				}
				Product product = productOpt.get();

				// find inventory
				String color = item.get("color").toString();
				String size = item.get("size").toString();
				int quantity = Integer.parseInt(item.get("quantity").toString());
				var invItem = product.getColorInventories().stream()
						.filter(colorInv -> color.equals(colorInv.getColor()))
						.flatMap(colorInv -> colorInv.getSizes().stream())
						.filter(sizeInv -> size.equals(sizeInv.getSize()))
						.findFirst();
				if (invItem.isEmpty() || invItem.get().getStock() < quantity) {
					return ResponseEntity.status(400).body(Map.of("success", false, "message", product.getName() + " is out of stock or has insufficient quantity"));
				}

				Order.Item oi = new Order.Item();
				oi.setProduct(productId);
				oi.setVendorId(product.getVendor() != null ? product.getVendor().getId() : null);
				oi.setProductName(product.getName());
				oi.setColor(color);
				oi.setSize(size);
				oi.setQuantity(quantity);
				oi.setPrice(Double.valueOf(item.get("price").toString()));
				processedItems.add(oi);

				// deduct stock using repository method
				productRepository.decrementStock(productId, color, size, quantity);
			}

			int pointsEarned = calculatePoints(total);

			boolean isFirstOrder = false;
			if (userId != null) {
				isFirstOrder = orderRepository.findByUserId(userId).isEmpty();
			}

			Order.PaymentReceipt receiptData = null;
			// For Paymob bank-transfer, we no longer require manual receipt upload

			Order order = new Order();
			order.setItems(processedItems);
			Order.ShippingAddress sa = new Order.ShippingAddress();
			sa.setFullName(Objects.toString(shippingAddressMap.get("fullName"), null));
			sa.setAddressLine1(Objects.toString(shippingAddressMap.get("addressLine1"), null));
			sa.setAddressLine2(Objects.toString(shippingAddressMap.get("addressLine2"), null));
			sa.setCity(Objects.toString(shippingAddressMap.get("city"), null));
			sa.setState(Objects.toString(shippingAddressMap.get("state"), null));
			sa.setPostalCode(Objects.toString(shippingAddressMap.get("postalCode"), null));
			sa.setCountry(Objects.toString(shippingAddressMap.get("country"), null));
			sa.setPhoneNumber(Objects.toString(shippingAddressMap.get("phoneNumber"), null));
			
			// Get email from customerInfo (for notifications)
			String email = Objects.toString(customerInfoMap.get("email"), null);
			sa.setEmail(email);
			order.setShippingAddress(sa);
			order.setCustomerEmail(sa.getEmail());
			order.setSubtotal(subtotal);
			order.setShippingCharges(shippingCharges);
			order.setDiscount(discount);
			order.setDiscountCode(discountInfo.get("reasons") instanceof List ? String.join(", ", ((List<?>) discountInfo.get("reasons")).stream().map(String::valueOf).collect(Collectors.toList())) : "");
			order.setTotal(total);
			order.setPointsUsed(pointsToUse);
			order.setPointsEarned(pointsEarned);
			order.setPaymentMethod(paymentMethod);
			order.setIsFirstOrder(isFirstOrder);
			order.setPaymentReceipt(receiptData);
			if (userId != null) order.setUserId(userId);

			Order saved = orderRepository.save(order);
			// persist order items for vendor queries
			orderRepository.insertOrderItems(saved.getId(), processedItems);

			if (userId != null) {
				var userOpt = userRepository.findById(userId);
				userOpt.ifPresent(u -> {
					int current = Optional.ofNullable(u.getRewardPoints()).orElse(0);
					u.setRewardPoints(current - pointsToUse + pointsEarned);
					userRepository.save(u);
				});
				
				// Clear the user's cart after successful order creation
				cartRepository.clearCartAfterOrder(userId);
			}


			// Send customer email notification for all payment methods
			try {
				if (email != null && !email.isBlank()) {
					notificationService.notifyOrderPlaced(saved);
				} else {
					System.out.println("Warning: No email address provided for order notification. Order ID: " + saved.getId());
				}
			} catch (Exception e) {
				System.err.println("Failed to send order notification: " + e.getMessage());
			}

			if ("bank-transfer".equalsIgnoreCase(paymentMethod)) {
				try {
					var req = new com.Daad.ecommerce.dto.PaymentDtos.CreatePaymentSessionRequest();
					req.setOrderId(saved.getId());
					req.setCustomerEmail(email);
					req.setCustomerPhone(sa.getPhoneNumber());
					var payResp = paymentService.createPaymentSession(req, Math.round(total * 100), saved.getId());
					return ResponseEntity.status(201).body(Map.of(
						"success", true,
						"message", "Order created. Proceed to Paymob checkout",
						"order", saved,
						"payment", payResp
					));
				} catch (Exception paymentError) {
					System.err.println("Payment session creation failed: " + paymentError.getMessage());
					// Return order created but with payment error
					return ResponseEntity.status(201).body(Map.of(
						"success", true,
						"message", "Order created but payment setup failed. Please contact support.",
						"order", saved,
						"paymentError", paymentError.getMessage(),
						"paymentMethod", "bank-transfer"
					));
				}
			} else {
				return ResponseEntity.status(201).body(Map.of("success", true, "message", "Order created successfully", "order", saved));
			}

		} catch (Exception e) {
			return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to create order", "error", e.getMessage()));
		}
	}

	// Admin: Get all
	@GetMapping("/all")
	public ResponseEntity<Map<String, Object>> getAllOrders(
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "10") Integer limit,
			@RequestParam(required = false) String status
	) {
		List<Order> all = orderRepository.findAllByStatusOptional(status);
		int total = all.size();
		int start = Math.max(0, (page - 1) * limit);
		int end = Math.min(total, start + limit);
		List<Order> paginated = start < end ? all.subList(start, end) : new ArrayList<>();

		List<Map<String, Object>> processed = paginated.stream().map(o -> {
			Map<String, Object> m = new HashMap<>();
			m.put("id", o.getId());
			m.put("userId", o.getUserId());
			m.put("customerEmail", o.getCustomerEmail());
			m.put("email", o.getCustomerEmail());
			m.put("orderStatus", o.getOrderStatus());
			m.put("createdAt", o.getCreatedAt());
			m.put("total", o.getTotal());
			m.put("subtotal", o.getSubtotal());
			m.put("shippingCharges", o.getShippingCharges());
			m.put("paymentMethod", o.getPaymentMethod());
			m.put("paymentStatus", o.getPaymentStatus());
			m.put("trackingNumber", o.getTrackingNumber());
			m.put("estimatedDelivery", o.getEstimatedDelivery());
			m.put("deliveredAt", o.getDeliveredAt());
			m.put("cancelledAt", o.getCancelledAt());
			m.put("cancellationReason", o.getCancellationReason());
			m.put("items", o.getItems());
			
			// Get vendor information from order items
			Set<String> vendorIds = o.getItems().stream()
				.map(item -> item.getVendorId())
				.filter(vendorId -> vendorId != null)
				.collect(Collectors.toSet());
			
			List<Map<String, Object>> vendors = new ArrayList<>();
			for (String vendorId : vendorIds) {
				vendorRepository.findById(vendorId).ifPresent(vendor -> {
					Map<String, Object> vendorInfo = new HashMap<>();
					vendorInfo.put("id", vendor.getId());
					vendorInfo.put("businessName", vendor.getBusinessName());
					vendorInfo.put("businessType", vendor.getBusinessType());
					vendorInfo.put("status", vendor.getStatus());
					vendorInfo.put("rating", vendor.getRating());
					// Ensure vendor email from users table
					try {
						if (vendor.getUser() != null && vendor.getUser().getId() != null) {
							userRepository.findById(vendor.getUser().getId()).ifPresent(u -> vendorInfo.put("email", u.getEmail()));
						}
					} catch (Exception ignored) {}
					if (!vendorInfo.containsKey("email") && vendor.getUser() != null) {
						vendorInfo.put("email", vendor.getUser().getEmail());
					}
					vendorInfo.put("phone", vendor.getPhoneNumber());
					vendors.add(vendorInfo);
				});
			}
			m.put("vendors", vendors);
			
			return m;
		}).collect(Collectors.toList());

		return ResponseEntity.ok(Map.of(
			"success", true,
			"count", processed.size(),
			"total", total,
			"totalPages", (int) Math.ceil((double) total / limit),
			"currentPage", page,
			"orders", processed
		));
	}

	// User's orders - Optimized with vendor details (Single Query)
	@GetMapping("/my-orders")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> getUserOrders() {
		String userId = SecurityUtils.currentUserId();
		
		// Use optimized single-query method to get orders with vendor details
		List<Map<String, Object>> orders = orderRepository.findUserOrdersWithVendorDetails(userId);
		
		return ResponseEntity.ok(Map.of(
			"success", true, 
			"count", orders.size(), 
			"orders", orders
		));
	}

	// Get by id
	@GetMapping("/details/{orderId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> getOrderById(@PathVariable String orderId) {
		String userId = SecurityUtils.currentUserId();
		var orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
		Order order = orderOpt.get();
		boolean isAdmin = SecurityUtils.hasRole("ADMIN");
		if (!isAdmin && !Objects.equals(order.getUserId(), userId)) {
			return ResponseEntity.status(403).body(Map.of("success", false, "message", "Not authorized to view this order"));
		}
		return ResponseEntity.ok(Map.of("success", true, "order", order));
	}

	// Update status (admin)
	@PutMapping("/update-status/{orderId}")
	public ResponseEntity<Map<String, Object>> updateOrderStatus(@PathVariable String orderId, @RequestBody Map<String, Object> body) {
		String status = body.get("status") != null ? body.get("status").toString() : null;
		if (status == null || status.isBlank()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Status is required"));

		var orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
		Order order = orderOpt.get();

		if (body.containsKey("trackingNumber")) order.setTrackingNumber(Objects.toString(body.get("trackingNumber"), null));
		boolean statusChanged = !Objects.equals(order.getOrderStatus(), status);
		order.setOrderStatus(status);
		Order saved = orderRepository.save(order);

		if (statusChanged) {
			try {
				notificationService.notifyOrderStatusUpdate(saved, status);
			} catch (Exception ignored) {}
		}
		return ResponseEntity.ok(Map.of("success", true, "message", "Order status updated successfully", "order", saved));
	}

	// Get tracking info
	@GetMapping("/{orderId}/tracking")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> getOrderTracking(@PathVariable String orderId) {
		String userId = SecurityUtils.currentUserId();
		var orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
		Order order = orderOpt.get();
		boolean isAdmin = SecurityUtils.hasRole("ADMIN");
		if (!isAdmin && !Objects.equals(order.getUserId(), userId)) {
			return ResponseEntity.status(403).body(Map.of("success", false, "message", "Not authorized to view this order"));
		}
		Map<String, Object> tracking = new HashMap<>();
		tracking.put("trackingNumber", order.getTrackingNumber());
		tracking.put("orderStatus", order.getOrderStatus());
		tracking.put("estimatedDelivery", order.getEstimatedDelivery());
		tracking.put("deliveredAt", order.getDeliveredAt());
		return ResponseEntity.ok(Map.of("success", true, "tracking", tracking));
	}

	// Cancel order
	@PostMapping("/{orderId}/cancel")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable String orderId, @RequestBody(required = false) Map<String, Object> body) {
		String userId = SecurityUtils.currentUserId();
		var orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
		Order order = orderOpt.get();
		boolean isAdmin = SecurityUtils.hasRole("ADMIN");
		if (!isAdmin && !Objects.equals(order.getUserId(), userId)) {
			return ResponseEntity.status(403).body(Map.of("success", false, "message", "Not authorized to cancel this order"));
		}
		if ("cancelled".equalsIgnoreCase(order.getOrderStatus()) || "delivered".equalsIgnoreCase(order.getOrderStatus())) {
			return ResponseEntity.status(400).body(Map.of("success", false, "message", "Order cannot be cancelled in its current status"));
		}
		String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
		orderRepository.cancelOrder(orderId, reason);
		var updated = orderRepository.findById(orderId).get();
		try {
			notificationService.notifyOrderCancellation(updated, "customer");
		} catch (Exception ignored) {}
		return ResponseEntity.ok(Map.of("success", true, "message", "Order cancelled successfully", "order", updated));
	}

	// Vendor: list vendor orders
	@GetMapping("/vendor")
	@PreAuthorize("hasRole('VENDOR')")
	public ResponseEntity<Map<String, Object>> getVendorOrders() {
		String userId = SecurityUtils.currentUserId();
		var vendorOpt = vendorRepository.findByUserId(userId);
		if (vendorOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vendor profile not found"));
		String vendorId = vendorOpt.get().getId();
		
		// Get all orders for the vendor
		List<Order> orders = orderRepository.findByVendorId(vendorId);
		
		// Get order counts by status
		Map<String, Integer> statusCounts = orderRepository.getVendorOrderCountsByStatus(vendorId);
		
		// Calculate total orders
		int totalOrders = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
		
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("totalOrders", totalOrders);
		response.put("count", orders.size());
		response.put("orders", orders);
		
		// Add status counts
		Map<String, Object> statusSummary = new HashMap<>();
		statusSummary.put("pending", statusCounts.getOrDefault("pending", 0));
		statusSummary.put("confirmed", statusCounts.getOrDefault("confirmed", 0));
		statusSummary.put("processing", statusCounts.getOrDefault("processing", 0));
		statusSummary.put("shipped", statusCounts.getOrDefault("shipped", 0));
		statusSummary.put("delivered", statusCounts.getOrDefault("delivered", 0));
		statusSummary.put("cancelled", statusCounts.getOrDefault("cancelled", 0));
		
		response.put("statusCounts", statusSummary);
		
		return ResponseEntity.ok(response);
	}
	
	// Vendor: get order counts by status only
	@GetMapping("/vendor/counts")
	@PreAuthorize("hasRole('VENDOR')")
	public ResponseEntity<Map<String, Object>> getVendorOrderCounts() {
		String userId = SecurityUtils.currentUserId();
		var vendorOpt = vendorRepository.findByUserId(userId);
		if (vendorOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vendor profile not found"));
		String vendorId = vendorOpt.get().getId();
		
		// Get order counts by status
		Map<String, Integer> statusCounts = orderRepository.getVendorOrderCountsByStatus(vendorId);
		
		// Calculate total orders
		int totalOrders = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
		
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("totalOrders", totalOrders);
		
		// Add status counts
		Map<String, Object> statusSummary = new HashMap<>();
		statusSummary.put("pending", statusCounts.getOrDefault("pending", 0));
		statusSummary.put("confirmed", statusCounts.getOrDefault("confirmed", 0));
		statusSummary.put("processing", statusCounts.getOrDefault("processing", 0));
		statusSummary.put("shipped", statusCounts.getOrDefault("shipped", 0));
		statusSummary.put("delivered", statusCounts.getOrDefault("delivered", 0));
		statusSummary.put("cancelled", statusCounts.getOrDefault("cancelled", 0));
		
		response.put("statusCounts", statusSummary);
		
		return ResponseEntity.ok(response);
	}

	// Vendor: update order status for their items' order
	@PutMapping("/vendor/{orderId}/status")
	@PreAuthorize("hasRole('VENDOR')")
	public ResponseEntity<Map<String, Object>> updateVendorOrderStatus(@PathVariable String orderId, @RequestBody Map<String, Object> body) {
		String status = body.get("status") != null ? body.get("status").toString() : null;
		if (status == null || status.isBlank()) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Status is required"));

		String userId = SecurityUtils.currentUserId();
		var vendorOpt = vendorRepository.findByUserId(userId);
		if (vendorOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Vendor profile not found"));
		String vendorId = vendorOpt.get().getId();
		boolean owns = orderRepository.vendorOwnsOrder(orderId, vendorId);
		if (!owns) return ResponseEntity.status(403).body(Map.of("success", false, "message", "Not authorized to update this order"));

		var orderOpt = orderRepository.findById(orderId);
		if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Order not found"));
		Order order = orderOpt.get();
		boolean statusChanged = !Objects.equals(order.getOrderStatus(), status);
		order.setOrderStatus(status);
		Order saved = orderRepository.save(order);
		if (statusChanged) {
			try {
				notificationService.notifyOrderStatusUpdate(saved, status);
			} catch (Exception ignored) {}
		}
		return ResponseEntity.ok(Map.of("success", true, "message", "Order status updated successfully", "order", saved));
	}
}
