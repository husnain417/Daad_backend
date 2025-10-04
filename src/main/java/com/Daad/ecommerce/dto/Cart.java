package com.Daad.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Cart {
	private String userId; // For logged-in users
	private String cartId; // For guest users
	private boolean isGuest = false; // Flag to differentiate
	private List<CartItem> items = new ArrayList<>();
	private double subtotal = 0.0;
	private double tax = 0.0;
	private double shipping = 0.0;
	private double discount = 0.0;
	private double total = 0.0;
	private List<String> appliedVouchers = new ArrayList<>();
	private ShippingAddress shippingAddress;
	private EstimatedDelivery estimatedDelivery;
	private LocalDateTime lastUpdated = LocalDateTime.now();
	private LocalDateTime createdAt = LocalDateTime.now();
	private LocalDateTime updatedAt = LocalDateTime.now();

	public static class CartItem {
		private String productId;
		private String vendorId;
		private String color;
		private String size;
		private int quantity;
		private double price;
		private Double discountedPrice; // nullable
		private double totalPrice;
		
		// Product details for display
		private String productName;
		private String productDescription;
		private String defaultImageUrl; // First default image URL
		public String getProductId() { return productId; }
		public void setProductId(String productId) { this.productId = productId; }
		public String getVendorId() { return vendorId; }
		public void setVendorId(String vendorId) { this.vendorId = vendorId; }
		public String getColor() { return color; }
		public void setColor(String color) { this.color = color; }
		public String getSize() { return size; }
		public void setSize(String size) { this.size = size; }
		public int getQuantity() { return quantity; }
		public void setQuantity(int quantity) { this.quantity = quantity; }
		public double getPrice() { return price; }
		public void setPrice(double price) { this.price = price; }
		public Double getDiscountedPrice() { return discountedPrice; }
		public void setDiscountedPrice(Double discountedPrice) { this.discountedPrice = discountedPrice; }
		public double getTotalPrice() { return totalPrice; }
		public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
		
		// Product details getters and setters
		public String getProductName() { return productName; }
		public void setProductName(String productName) { this.productName = productName; }
		public String getProductDescription() { return productDescription; }
		public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
		public String getDefaultImageUrl() { return defaultImageUrl; }
		public void setDefaultImageUrl(String defaultImageUrl) { this.defaultImageUrl = defaultImageUrl; }
	}

	public static class ShippingAddress {
		private String type = "home"; // home, work, other
		private String fullName;
		private String addressLine1;
		private String addressLine2;
		private String city;
		private String state;
		private String postalCode;
		private String country;
		private String phoneNumber;
		private String email; // not in Node cart, useful for checkout
		public String getType() { return type; }
		public void setType(String type) { this.type = type; }
		public String getFullName() { return fullName; }
		public void setFullName(String fullName) { this.fullName = fullName; }
		public String getAddressLine1() { return addressLine1; }
		public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
		public String getAddressLine2() { return addressLine2; }
		public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
		public String getCity() { return city; }
		public void setCity(String city) { this.city = city; }
		public String getState() { return state; }
		public void setState(String state) { this.state = state; }
		public String getPostalCode() { return postalCode; }
		public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
		public String getCountry() { return country; }
		public void setCountry(String country) { this.country = country; }
		public String getPhoneNumber() { return phoneNumber; }
		public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
		public String getEmail() { return email; }
		public void setEmail(String email) { this.email = email; }
	}

	public static class EstimatedDelivery {
		private int minDays;
		private int maxDays;
		public EstimatedDelivery() {}
		public EstimatedDelivery(int minDays, int maxDays) { this.minDays = minDays; this.maxDays = maxDays; }
		public int getMinDays() { return minDays; }
		public void setMinDays(int minDays) { this.minDays = minDays; }
		public int getMaxDays() { return maxDays; }
		public void setMaxDays(int maxDays) { this.maxDays = maxDays; }
	}

	// Helper method to get the identifier (userId or cartId)
	public String getIdentifier() {
		return isGuest ? cartId : userId;
	}

	public void calculateTotals() {
		this.subtotal = items.stream().mapToDouble(it -> (it.getDiscountedPrice() != null ? it.getDiscountedPrice() : it.getPrice()) * it.getQuantity()).sum();
		this.total = subtotal + tax + shipping - discount;
		this.lastUpdated = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	public void addItem(String productId, String vendorId, String color, String size, int quantity, double priceToUse) {
		addItem(productId, vendorId, color, size, quantity, priceToUse, null, null, null);
	}
	
	public void addItem(String productId, String vendorId, String color, String size, int quantity, double priceToUse, 
			String productName, String productDescription, String defaultImageUrl) {
		CartItem existing = items.stream().filter(i -> i.getProductId().equals(productId)
				&& Objects.equals(i.getVendorId(), vendorId)
				&& i.getColor().equals(color) && i.getSize().equals(size)).findFirst().orElse(null);
		if (existing != null) {
			existing.setQuantity(existing.getQuantity() + quantity);
			existing.setPrice(priceToUse);
			existing.setTotalPrice(existing.getPrice() * existing.getQuantity());
		} else {
			CartItem it = new CartItem();
			it.setProductId(productId);
			it.setVendorId(vendorId);
			it.setColor(color);
			it.setSize(size);
			it.setQuantity(quantity);
			it.setPrice(priceToUse);
			it.setTotalPrice(priceToUse * quantity);
			it.setProductName(productName);
			it.setProductDescription(productDescription);
			it.setDefaultImageUrl(defaultImageUrl);
			items.add(it);
		}
		calculateTotals();
	}

	public void removeItem(String productId, String vendorId, String color, String size) {
		items.removeIf(i -> i.getProductId().equals(productId) && Objects.equals(i.getVendorId(), vendorId)
				&& i.getColor().equals(color) && i.getSize().equals(size));
		calculateTotals();
	}

	public void updateItemQuantity(String productId, String vendorId, String color, String size, int quantity) {
		for (CartItem i : items) {
			if (i.getProductId().equals(productId) && Objects.equals(i.getVendorId(), vendorId)
					&& i.getColor().equals(color) && i.getSize().equals(size)) {
				i.setQuantity(Math.max(1, quantity));
				i.setTotalPrice(i.getPrice() * i.getQuantity());
				break;
			}
		}
		calculateTotals();
	}

	public void clearCart() {
		items.clear();
		subtotal = tax = shipping = discount = total = 0.0;
		appliedVouchers.clear();
		lastUpdated = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	public Map<String, Object> getSummary() {
		Map<String, Object> m = new HashMap<>();
		m.put("itemCount", items.size());
		m.put("totalItems", items.stream().mapToInt(CartItem::getQuantity).sum());
		m.put("subtotal", subtotal);
		m.put("tax", tax);
		m.put("shipping", shipping);
		m.put("discount", discount);
		m.put("total", total);
		m.put("estimatedDelivery", estimatedDelivery);
		m.put("isGuest", isGuest);
		if (isGuest) {
			m.put("cartId", cartId);
		}
		return m;
	}

	// Getters and Setters
	public String getUserId() { return userId; }
	public void setUserId(String userId) { 
		this.userId = userId; 
		this.isGuest = false; // If userId is set, it's not a guest
	}
	
	public String getCartId() { return cartId; }
	public void setCartId(String cartId) { 
		this.cartId = cartId; 
		this.isGuest = true; // If cartId is set, it's a guest
	}
	
	public boolean isGuest() { return isGuest; }
	public void setGuest(boolean guest) { this.isGuest = guest; }
	
	public List<CartItem> getItems() { return items; }
	public void setItems(List<CartItem> items) { this.items = items; }
	public double getSubtotal() { return subtotal; }
	public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
	public double getTax() { return tax; }
	public void setTax(double tax) { this.tax = tax; }
	public double getShipping() { return shipping; }
	public void setShipping(double shipping) { this.shipping = shipping; }
	public double getDiscount() { return discount; }
	public void setDiscount(double discount) { this.discount = discount; }
	public double getTotal() { return total; }
	public void setTotal(double total) { this.total = total; }
	public List<String> getAppliedVouchers() { return appliedVouchers; }
	public void setAppliedVouchers(List<String> appliedVouchers) { this.appliedVouchers = appliedVouchers; }
	public ShippingAddress getShippingAddress() { return shippingAddress; }
	public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }
	public EstimatedDelivery getEstimatedDelivery() { return estimatedDelivery; }
	public void setEstimatedDelivery(EstimatedDelivery estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
	public LocalDateTime getLastUpdated() { return lastUpdated; }
	public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}