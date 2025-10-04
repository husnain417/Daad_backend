package com.Daad.ecommerce.service;

import com.Daad.ecommerce.dto.Order;
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
}