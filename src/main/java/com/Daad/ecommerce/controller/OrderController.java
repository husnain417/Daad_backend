package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.Order;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.OrderRepository;
import com.Daad.ecommerce.repository.ProductRepository;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.security.SecurityUtils;
import com.Daad.ecommerce.service.LocalUploadService;
import com.Daad.ecommerce.service.OrderEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

	@Autowired private OrderRepository orderRepository;
	@Autowired private ProductRepository productRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private LocalUploadService localUploadService;
	@Autowired private OrderEmailService orderEmailService;

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
				orderData = new com.fasterxml.jackson.databind.ObjectMapper().readValue(orderDataJson, Map.class);
			} else {
				orderData = jsonBody != null ? jsonBody : new HashMap<>();
			}

			Map<String, Object> customerInfo = (Map<String, Object>) orderData.get("customerInfo");
			List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
			Map<String, Object> shippingAddressMap = (Map<String, Object>) orderData.get("shippingAddress");
			double subtotal = Double.parseDouble(orderData.get("subtotal").toString());
			double shippingCharges = orderData.get("shippingCharges") != null ? Double.parseDouble(orderData.get("shippingCharges").toString()) : 0.0;
			double total = Double.parseDouble(orderData.get("total").toString());
			double discount = orderData.get("discount") != null ? Double.parseDouble(orderData.get("discount").toString()) : 0.0;
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
			if ("bank-transfer".equalsIgnoreCase(paymentMethod)) {
				if (receipt == null) {
					return ResponseEntity.status(400).body(Map.of("success", false, "message", "Payment receipt is required for bank transfers"));
				}
				var uploaded = localUploadService.uploadMultipart(receipt, "order-payment/" + (userId != null ? userId : "guest"));
				receiptData = new Order.PaymentReceipt();
				receiptData.setUrl(uploaded.url);
				receiptData.setPublicId(uploaded.filename);
				receiptData.setUploaded(true);
			}

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
			sa.setEmail(Objects.toString(shippingAddressMap.get("email"), null));
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

			if (userId != null) {
				var userOpt = userRepository.findById(userId);
				userOpt.ifPresent(u -> {
					int current = Optional.ofNullable(u.getRewardPoints()).orElse(0);
					u.setRewardPoints(current - pointsToUse + pointsEarned);
					userRepository.save(u);
				});
			}

			try {
				String email = sa.getEmail();
				if (email != null && !email.isBlank()) {
					orderEmailService.sendOrderConfirmationToCustomer(saved, email);
					orderEmailService.sendNewOrderEmailToAdmin(saved, email);
				}
			} catch (Exception ignored) {}

			return ResponseEntity.status(201).body(Map.of("success", true, "message", "Order created successfully", "order", saved));

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

	// User's orders
	@GetMapping("/my-orders")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Map<String, Object>> getUserOrders() {
		String userId = SecurityUtils.currentUserId();
		List<Order> orders = orderRepository.findByUserId(userId);
		return ResponseEntity.ok(Map.of("success", true, "count", orders.size(), "orders", orders));
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
			String emailToSend = order.getCustomerEmail();
			if (emailToSend != null && !emailToSend.isBlank()) {
				try { orderEmailService.sendOrderStatusUpdateToCustomer(saved, emailToSend); } catch (Exception ignored) {}
			}
		}
		return ResponseEntity.ok(Map.of("success", true, "message", "Order status updated successfully", "order", saved));
	}
}
