package com.Daad.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Order {
	private String id;
	private String userId; // nullable for guests
	private List<Item> items = new ArrayList<>();
	private ShippingAddress shippingAddress;
	private Double subtotal;
	private Double discount;
	private String discountCode;
	private Double shippingCharges;
	private Double total;
	private Integer pointsUsed;
	private Integer pointsEarned;
	private String paymentMethod; // cash-on-delivery, bank-transfer, card
	private String paymentStatus = "pending"; // pending, paid, failed
	private String paymentProvider; // e.g., paymob
	private String transactionId;
	private String paymentReference;
	private PaymentReceipt paymentReceipt;
	private String orderStatus = "pending"; // pending, confirmed, processing, shipped, delivered, cancelled
	private Boolean isFirstOrder = false;
	private String trackingNumber;
	private String estimatedDelivery;
	private String deliveredAt;
	private String cancelledAt;
	private String cancellationReason;
	private String customerEmail;
	private LocalDateTime createdAt = LocalDateTime.now();
	private LocalDateTime updatedAt = LocalDateTime.now();
	private Map<String, Object> userInfo; // Customer user information

	public static class Item {
		private String product; // productId
		private String vendorId; // added for vendor analytics
		private String productName;
		private String color;
		private String size;
		private Integer quantity;
		private Double price;
		public String getProduct() { return product; }
		public void setProduct(String product) { this.product = product; }
		public String getVendorId() { return vendorId; }
		public void setVendorId(String vendorId) { this.vendorId = vendorId; }
		public String getProductName() { return productName; }
		public void setProductName(String productName) { this.productName = productName; }
		public String getColor() { return color; }
		public void setColor(String color) { this.color = color; }
		public String getSize() { return size; }
		public void setSize(String size) { this.size = size; }
		public Integer getQuantity() { return quantity; }
		public void setQuantity(Integer quantity) { this.quantity = quantity; }
		public Double getPrice() { return price; }
		public void setPrice(Double price) { this.price = price; }
	}

	public static class ShippingAddress {
		private String fullName;
		private String addressLine1;
		private String addressLine2;
		private String city;
		private String state;
		private String postalCode;
		private String country;
		private String phoneNumber;
		private String email;
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

	public static class PaymentReceipt {
		private String url;
		private String publicId;
		private Boolean uploaded;
		public String getUrl() { return url; }
		public void setUrl(String url) { this.url = url; }
		public String getPublicId() { return publicId; }
		public void setPublicId(String publicId) { this.publicId = publicId; }
		public Boolean getUploaded() { return uploaded; }
		public void setUploaded(Boolean uploaded) { this.uploaded = uploaded; }
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public List<Item> getItems() { return items; }
	public void setItems(List<Item> items) { this.items = items; }
	public ShippingAddress getShippingAddress() { return shippingAddress; }
	public void setShippingAddress(ShippingAddress shippingAddress) { this.shippingAddress = shippingAddress; }
	public Double getSubtotal() { return subtotal; }
	public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }
	public Double getDiscount() { return discount; }
	public void setDiscount(Double discount) { this.discount = discount; }
	public String getDiscountCode() { return discountCode; }
	public void setDiscountCode(String discountCode) { this.discountCode = discountCode; }
	public Double getShippingCharges() { return shippingCharges; }
	public void setShippingCharges(Double shippingCharges) { this.shippingCharges = shippingCharges; }
	public Double getTotal() { return total; }
	public void setTotal(Double total) { this.total = total; }
	public Integer getPointsUsed() { return pointsUsed; }
	public void setPointsUsed(Integer pointsUsed) { this.pointsUsed = pointsUsed; }
	public Integer getPointsEarned() { return pointsEarned; }
	public void setPointsEarned(Integer pointsEarned) { this.pointsEarned = pointsEarned; }
	public String getPaymentMethod() { return paymentMethod; }
	public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
	public String getPaymentStatus() { return paymentStatus; }
	public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
	public String getPaymentProvider() { return paymentProvider; }
	public void setPaymentProvider(String paymentProvider) { this.paymentProvider = paymentProvider; }
	public String getTransactionId() { return transactionId; }
	public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
	public String getPaymentReference() { return paymentReference; }
	public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
	public PaymentReceipt getPaymentReceipt() { return paymentReceipt; }
	public void setPaymentReceipt(PaymentReceipt paymentReceipt) { this.paymentReceipt = paymentReceipt; }
	public String getOrderStatus() { return orderStatus; }
	public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
	public Boolean getIsFirstOrder() { return isFirstOrder; }
	public void setIsFirstOrder(Boolean isFirstOrder) { this.isFirstOrder = isFirstOrder; }
	public String getTrackingNumber() { return trackingNumber; }
	public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
	public String getEstimatedDelivery() { return estimatedDelivery; }
	public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
	public String getDeliveredAt() { return deliveredAt; }
	public void setDeliveredAt(String deliveredAt) { this.deliveredAt = deliveredAt; }
	public String getCancelledAt() { return cancelledAt; }
	public void setCancelledAt(String cancelledAt) { this.cancelledAt = cancelledAt; }
	public String getCancellationReason() { return cancellationReason; }
	public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
	public String getCustomerEmail() { return customerEmail; }
	public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
	public Map<String, Object> getUserInfo() { return userInfo; }
	public void setUserInfo(Map<String, Object> userInfo) { this.userInfo = userInfo; }
}
