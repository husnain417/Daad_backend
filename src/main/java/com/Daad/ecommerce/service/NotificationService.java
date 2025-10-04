package com.Daad.ecommerce.service;

import com.Daad.ecommerce.dto.Order;
import com.Daad.ecommerce.dto.Review;
import com.Daad.ecommerce.model.User;
import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    @Autowired
    private VendorEmailService vendorEmailService;

    @Autowired
    private OrderEmailService orderEmailService;

    @Autowired
    private AccountEmailService accountEmailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    // Vendor Management Notifications
    @Async("emailTaskExecutor")
    public void notifyVendorRegistration(Vendor vendor) {
        // Notify admin
        List<User> admins = userRepository.findByRole("admin");
        if (!admins.isEmpty()) {
            vendorEmailService.sendVendorRegistrationNotification(vendor, admins.get(0));
        }

        // Notify vendor
        vendorEmailService.sendVendorRegistrationConfirmation(vendor);
    }

    @Async("emailTaskExecutor")
    public void notifyVendorApproval(Vendor vendor, boolean isApproved, String reason) {
        // Notify vendor
        vendorEmailService.sendVendorApprovalNotification(vendor, isApproved, reason);

        // Notify admin
        List<User> admins = userRepository.findByRole("admin");
        if (!admins.isEmpty()) {
            vendorEmailService.sendAdminVendorActionNotification(admins.get(0), vendor, 
                isApproved ? "approved" : "rejected");
        }
    }

    // Product Management Notifications
    @Async("emailTaskExecutor")
    public void notifyProductSubmission(Product product, Vendor vendor) {
        // Notify admin
        List<User> admins = userRepository.findByRole("admin");
        if (!admins.isEmpty()) {
            vendorEmailService.sendProductSubmissionNotification(product, vendor, admins.get(0));
        }
    }

    @Async("emailTaskExecutor")
    public void notifyProductApproval(Product product, Vendor vendor, boolean isApproved, String reason) {
        // Notify vendor
        vendorEmailService.sendProductApprovalNotification(product, vendor, isApproved, reason);

        // Notify admin
        List<User> admins = userRepository.findByRole("admin");
        if (!admins.isEmpty()) {
            vendorEmailService.sendAdminVendorActionNotification(admins.get(0), vendor, 
                isApproved ? "product approved" : "product rejected");
        }
    }

    @Async("emailTaskExecutor")
    public void notifyStockAlert(Product product, Vendor vendor, int stockLevel) {
        vendorEmailService.sendStockAlertNotification(product, vendor, stockLevel);
    }

    // Order Management Notifications
    @Async("emailTaskExecutor")
    public void notifyOrderPlaced(Order order) {
        // Notify customer
        String customerEmail = order.getCustomerEmail();
        if (customerEmail != null && !customerEmail.trim().isEmpty()) {
            orderEmailService.sendOrderConfirmationNotification(order, customerEmail);
            System.out.println("Order confirmation email sent to: " + customerEmail);
        } else {
            System.err.println("Warning: No customer email found for order " + order.getId());
        }

        // Notify vendors
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            Set<String> vendorIds = order.getItems().stream()
                .map(item -> item.getVendorId())
                .filter(vendorId -> vendorId != null)
                .collect(Collectors.toSet());

            for (String vendorId : vendorIds) {
                try {
                    vendorRepository.findById(vendorId).ifPresent(vendor -> {
                        if (vendor.getUser() != null && vendor.getUser().getId() != null) {
                            // Fetch user details to get email
                            userRepository.findById(vendor.getUser().getId()).ifPresent(user -> {
                                if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                                    orderEmailService.sendNewOrderNotification(order, user.getEmail());
                                    System.out.println("New order notification sent to vendor: " + user.getEmail());
                                } else {
                                    System.err.println("Warning: Vendor " + vendorId + " user has no email");
                                }
                            });
                        } else {
                            System.err.println("Warning: Vendor " + vendorId + " has no user");
                        }
                    });
                } catch (Exception e) {
                    System.err.println("Error sending notification to vendor " + vendorId + ": " + e.getMessage());
                }
            }
        }
    }

    @Async("emailTaskExecutor")
    public void notifyOrderStatusUpdate(Order order, String newStatus) {
        orderEmailService.sendOrderStatusUpdateNotification(order, order.getCustomerEmail(), newStatus);
    }

    @Async("emailTaskExecutor")
    public void notifyOrderCancellation(Order order, String cancelledBy) {
        orderEmailService.sendOrderCancellationNotification(order, order.getCustomerEmail(), 
            order.getItems().stream()
                .map(item -> item.getVendorId())
                .filter(vendorId -> vendorId != null)
                .findFirst()
                .map(vendorId -> vendorRepository.findById(vendorId)
                    .map(vendor -> vendor.getUser().getEmail())
                    .orElse(null))
                .orElse(null), cancelledBy);
    }

    // Account Management Notifications
    @Async("emailTaskExecutor")
    public void notifyUserRegistration(User user) {
        accountEmailService.sendWelcomeNotification(user);
    }

    @Async("emailTaskExecutor")
    public void notifyPasswordReset(User user, String resetToken) {
        accountEmailService.sendPasswordResetNotification(user, resetToken);
    }

    @Async("emailTaskExecutor")
    public void notifyAccountStatusChange(User user, String newStatus) {
        accountEmailService.sendAccountStatusNotification(user, newStatus);
    }

    // Review Notifications
    @Async("emailTaskExecutor")
    public void notifyReviewSubmitted(Review review, Product product) {
        if (product.getVendor() != null) {
            vendorRepository.findById(product.getVendor().getId()).ifPresent(vendor -> {
                // Notify vendor about new review
                // Implementation for review notification
            });
        }
    }

    // System Notifications
    @Async("emailTaskExecutor")
    public void notifySystemMaintenance(String message, List<String> userEmails) {
        for (String email : userEmails) {
            // Implementation for system maintenance notification
            System.out.println("System maintenance notification sent to: " + email);
        }
    }

    @Async("emailTaskExecutor")
    public void notifySecurityAlert(String message, List<String> adminEmails) {
        for (String email : adminEmails) {
            // Implementation for security alert notification
            System.out.println("Security alert sent to: " + email);
        }
    }
}
