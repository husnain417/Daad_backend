package com.Daad.ecommerce.service;

import com.Daad.ecommerce.dto.Order;
import com.Daad.ecommerce.repository.OrderRepository;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderEmailService {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;
    
    @Autowired
    private OrderRepository orderRepository;

    @Async("emailTaskExecutor")
    public void sendOrderConfirmationNotification(Order order, String customerEmail) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("customerEmail", customerEmail);
        variables.put("orderDate", order.getCreatedAt());
        variables.put("totalAmount", order.getTotal());
        variables.put("items", order.getItems());
        variables.put("shippingAddress", order.getShippingAddress());
        variables.put("paymentMethod", order.getPaymentMethod());

        emailTemplateService.sendHtmlEmail(
            customerEmail,
            "Order Confirmation #" + order.getId(),
            "order-confirmation",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendNewOrderNotification(Order order, String vendorEmail) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("vendorEmail", vendorEmail);
        variables.put("orderDate", order.getCreatedAt());
        variables.put("customerEmail", order.getCustomerEmail());
        variables.put("items", order.getItems().stream()
            .filter(item -> item.getVendorId() != null)
            .collect(Collectors.toList()));
        variables.put("totalAmount", order.getTotal());

        emailTemplateService.sendHtmlEmail(
            vendorEmail,
            "New Order Received #" + order.getId(),
            "new-order-vendor",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendOrderStatusUpdateNotification(Order order, String customerEmail, String newStatus) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("customerEmail", customerEmail);
        variables.put("newStatus", newStatus);
        variables.put("updateDate", java.time.Instant.now());
        variables.put("trackingNumber", order.getTrackingNumber());
        variables.put("estimatedDelivery", order.getEstimatedDelivery());

        emailTemplateService.sendHtmlEmail(
            customerEmail,
            "Order Status Update #" + order.getId() + " - " + newStatus,
            "order-status-update",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendOrderCancellationNotification(Order order, String customerEmail, String vendorEmail, String cancelledBy) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("customerEmail", customerEmail);
        variables.put("cancelledBy", cancelledBy);
        variables.put("cancellationDate", order.getCancelledAt());
        variables.put("cancellationReason", order.getCancellationReason());
        variables.put("refundAmount", order.getTotal());

        // Send to customer
        emailTemplateService.sendHtmlEmail(
            customerEmail,
            "Order Cancelled #" + order.getId(),
            "order-cancellation-customer",
            variables
        );

        // Send to vendor if cancelled by customer
        if ("customer".equals(cancelledBy) && vendorEmail != null) {
            variables.put("vendorEmail", vendorEmail);
            emailTemplateService.sendHtmlEmail(
                vendorEmail,
                "Order Cancelled by Customer #" + order.getId(),
                "order-cancellation-vendor",
                variables
            );
        }
    }

    @Async("emailTaskExecutor")
    public void sendPaymentConfirmationNotification(Order order, String customerEmail, String vendorEmail) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("customerEmail", customerEmail);
        variables.put("paymentAmount", order.getTotal());
        variables.put("paymentMethod", order.getPaymentMethod());
        variables.put("paymentDate", java.time.Instant.now());

        // Send to customer
        emailTemplateService.sendHtmlEmail(
            customerEmail,
            "Payment Confirmed #" + order.getId(),
            "payment-confirmation",
            variables
        );

        // Send to vendor
        if (vendorEmail != null) {
            variables.put("vendorEmail", vendorEmail);
            emailTemplateService.sendHtmlEmail(
                vendorEmail,
                "Payment Received for Order #" + order.getId(),
                "payment-received-vendor",
                variables
            );
        }
    }

    @Async("emailTaskExecutor")
    public void sendPaymentFailureNotification(Order order, String customerEmail) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("customerEmail", customerEmail);
        variables.put("paymentAmount", order.getTotal());
        variables.put("paymentMethod", order.getPaymentMethod());
        variables.put("failureDate", java.time.Instant.now());

        emailTemplateService.sendHtmlEmail(
            customerEmail,
            "Payment Failed #" + order.getId(),
            "payment-failure",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendRefundNotification(Order order, String customerEmail, String vendorEmail, double refundAmount) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("customerEmail", customerEmail);
        variables.put("refundAmount", refundAmount);
        variables.put("refundDate", java.time.Instant.now());
        variables.put("refundMethod", order.getPaymentMethod());

        // Send to customer
        emailTemplateService.sendHtmlEmail(
            customerEmail,
            "Refund Processed #" + order.getId(),
            "refund-notification",
            variables
        );

        // Send to vendor
        if (vendorEmail != null) {
            variables.put("vendorEmail", vendorEmail);
            emailTemplateService.sendHtmlEmail(
                vendorEmail,
                "Refund Processed for Order #" + order.getId(),
                "refund-vendor-notification",
                variables
            );
        }
    }

    /**
     * Send delivery tracking email to customer
     */
    public void sendDeliveryTrackingEmail(Map<String, Object> data) {
        try {
            String orderId = (String) data.get("order_id");
            String trackingNumber = (String) data.get("tracking_number");
            String trackUrl = (String) data.get("track_url");
            String fincartOrderId = (String) data.get("fincart_order_id");

            Map<String, Object> variables = new HashMap<>();
            variables.put("orderId", orderId);
            variables.put("trackingNumber", trackingNumber);
            variables.put("trackUrl", trackUrl);
            variables.put("fincartOrderId", fincartOrderId);

            // Get customer email from order
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null && order.getCustomerEmail() != null) {
                emailTemplateService.sendHtmlEmail(
                    order.getCustomerEmail(),
                    "Your Order #" + orderId + " is Ready for Delivery",
                    "delivery-tracking",
                    variables
                );
            }
        } catch (Exception e) {
            System.err.println("Error sending delivery tracking email: " + e.getMessage());
        }
    }

    /**
     * Send delivery status update email to customer
     */
    public void sendDeliveryStatusUpdateEmail(Map<String, Object> data) {
        try {
            String orderId = (String) data.get("order_id");
            String fincartOrderId = (String) data.get("fincart_order_id");
            String status = (String) data.get("status");
            String subStatus = (String) data.get("sub_status");

            Map<String, Object> variables = new HashMap<>();
            variables.put("orderId", orderId);
            variables.put("fincartOrderId", fincartOrderId);
            variables.put("status", status);
            variables.put("subStatus", subStatus);

            // Get customer email from order
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null && order.getCustomerEmail() != null) {
                String subject = getDeliveryStatusSubject(status, subStatus);
                emailTemplateService.sendHtmlEmail(
                    order.getCustomerEmail(),
                    subject,
                    "delivery-status-update",
                    variables
                );
            }
        } catch (Exception e) {
            System.err.println("Error sending delivery status update email: " + e.getMessage());
        }
    }

    private String getDeliveryStatusSubject(String status, String subStatus) {
        if ("successful".equals(status) && "delivered to customer".equals(subStatus)) {
            return "Your Order Has Been Delivered Successfully!";
        } else if ("processing".equals(status) && "out for delivery".equals(subStatus)) {
            return "Your Order is Out for Delivery";
        } else if ("processing".equals(status) && "picked up".equals(subStatus)) {
            return "Your Order Has Been Picked Up";
        } else if ("processing".equals(status) && "at courier hub".equals(subStatus)) {
            return "Your Order is at Courier Hub";
        } else if ("unsuccessful".equals(status)) {
            return "Delivery Attempt Failed - Action Required";
        } else {
            return "Order Delivery Status Update";
        }
    }
}