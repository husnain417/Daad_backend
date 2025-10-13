package com.Daad.ecommerce.dto;

public class PaymentDtos {
    public static class CreatePaymentSessionRequest {
        private String orderId;
        private String customerEmail;
        private String customerPhone;
        private String currency = "EGP";
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
        public String getCustomerPhone() { return customerPhone; }
        public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    public static class CreatePaymentSessionResponse {
        private String paymentKey;
        private String checkoutUrl;
        private String paymentReference;
        private Long expiresAt;
        public String getPaymentKey() { return paymentKey; }
        public void setPaymentKey(String paymentKey) { this.paymentKey = paymentKey; }
        public String getCheckoutUrl() { return checkoutUrl; }
        public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
        public String getPaymentReference() { return paymentReference; }
        public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
        public Long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class PaymobWebhookEvent {
        private String type;
        private String signature;
        private Object data;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }

    // Request DTOs
    public static class RefundRequest {
        private String orderId;
        private String reason;
        private Double amountCents; // Optional for partial refunds, if null = full refund
        
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Double getAmountCents() { return amountCents; }
        public void setAmountCents(Double amountCents) { this.amountCents = amountCents; }
    }
    
    public static class CancelOrderRequest {
        private String reason;
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    // Response DTOs
    public static class RefundResponse {
        private boolean success;
        private String refundType; // "VOID" or "REFUND"
        private String refundId; // Internal UUID
        private String paymobRefundId; // Paymob's refund reference
        private String status; // initiated, pending, completed, failed
        private String message;
        private Double amountRefunded;
        private String currency;
        private String transactionId;
        private java.time.LocalDateTime refundedAt;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getRefundType() { return refundType; }
        public void setRefundType(String refundType) { this.refundType = refundType; }
        public String getRefundId() { return refundId; }
        public void setRefundId(String refundId) { this.refundId = refundId; }
        public String getPaymobRefundId() { return paymobRefundId; }
        public void setPaymobRefundId(String paymobRefundId) { this.paymobRefundId = paymobRefundId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Double getAmountRefunded() { return amountRefunded; }
        public void setAmountRefunded(Double amountRefunded) { this.amountRefunded = amountRefunded; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public java.time.LocalDateTime getRefundedAt() { return refundedAt; }
        public void setRefundedAt(java.time.LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    }
    
    public static class VoidResponse {
        private boolean success;
        private String message;
        private String transactionId;
        private String status;
        private java.time.LocalDateTime voidedAt;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.time.LocalDateTime getVoidedAt() { return voidedAt; }
        public void setVoidedAt(java.time.LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
    }
    
    public static class CancellationResponse {
        private boolean success;
        private String orderId;
        private String orderStatus; // "cancelled"
        private RefundResponse refundDetails;
        private String message;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getOrderStatus() { return orderStatus; }
        public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
        public RefundResponse getRefundDetails() { return refundDetails; }
        public void setRefundDetails(RefundResponse refundDetails) { this.refundDetails = refundDetails; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}


