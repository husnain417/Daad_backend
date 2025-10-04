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
}


