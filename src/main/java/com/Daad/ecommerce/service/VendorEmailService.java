package com.Daad.ecommerce.service;

import com.Daad.ecommerce.model.User;
import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.dto.Product;
import com.Daad.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class VendorEmailService {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private UserRepository userRepository;

    @Async("emailTaskExecutor")
    public void sendVendorRegistrationNotification(Vendor vendor, User admin) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("vendorEmail", vendor.getUser().getEmail());
        variables.put("businessType", vendor.getBusinessType());
        variables.put("phoneNumber", vendor.getPhoneNumber());
        variables.put("adminName", admin.getUsername());
        variables.put("registrationDate", vendor.getCreatedAt());

        emailTemplateService.sendHtmlEmail(
            admin.getEmail(),
            "New Vendor Registration - " + vendor.getBusinessName(),
            "vendor-registration-admin",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendVendorRegistrationConfirmation(Vendor vendor) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("vendorEmail", vendor.getUser().getEmail());
        variables.put("businessType", vendor.getBusinessType());

        emailTemplateService.sendHtmlEmail(
            vendor.getUser().getEmail(),
            "Vendor Registration Received - Daad",
            "vendor-registration-confirmation",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendVendorApprovalNotification(Vendor vendor, boolean isApproved, String reason) {
        System.out.println("sendVendorApprovalNotification called for vendor: " + vendor.getId());
        System.out.println("Vendor business name: " + vendor.getBusinessName());
        
        if (vendor.getUser() == null) {
            System.err.println("ERROR: Vendor has no user details!");
            return;
        }
        
        System.out.println("Vendor user email: " + vendor.getUser().getEmail());
        System.out.println("Vendor user username: " + vendor.getUser().getUsername());
        
        if (vendor.getUser().getEmail() == null || vendor.getUser().getEmail().trim().isEmpty()) {
            System.err.println("ERROR: Vendor user has no email address!");
            return;
        }
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("vendorEmail", vendor.getUser().getEmail());
        variables.put("isApproved", isApproved);
        variables.put("reason", reason);
        variables.put("approvalDate", vendor.getApprovedAt());

        String subject = isApproved ? 
            "Congratulations! Your Vendor Account Has Been Approved" : 
            "Vendor Account Application Update";

        System.out.println("Sending email to: " + vendor.getUser().getEmail());
        System.out.println("Subject: " + subject);
        
        try {
            emailTemplateService.sendHtmlEmail(
                vendor.getUser().getEmail(),
                subject,
                "vendor-approval-notification",
                variables
            );
            System.out.println("Vendor approval email sent successfully!");
        } catch (Exception e) {
            System.err.println("Failed to send vendor approval email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Async("emailTaskExecutor")
    public void sendAdminVendorActionNotification(User admin, Vendor vendor, String action) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("adminName", admin.getUsername());
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("action", action);
        variables.put("actionDate", java.time.Instant.now());

        emailTemplateService.sendHtmlEmail(
            admin.getEmail(),
            "Vendor Action Completed - " + action,
            "admin-vendor-action",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendProductSubmissionNotification(Product product, Vendor vendor, User admin) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("productName", product.getName());
        variables.put("productPrice", product.getPrice());
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("adminName", admin.getUsername());
        variables.put("submissionDate", product.getCreatedAt());

        emailTemplateService.sendHtmlEmail(
            admin.getEmail(),
            "New Product Submitted for Approval - " + product.getName(),
            "product-submission-admin",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendProductApprovalNotification(Product product, Vendor vendor, boolean isApproved, String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("productName", product.getName());
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("isApproved", isApproved);
        variables.put("reason", reason);
        variables.put("approvalDate", java.time.Instant.now());

        String subject = isApproved ? 
            "Product Approved: " + product.getName() : 
            "Product Rejected: " + product.getName();

        emailTemplateService.sendHtmlEmail(
            vendor.getUser().getEmail(),
            subject,
            "product-approval-notification",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendStockAlertNotification(Product product, Vendor vendor, int stockLevel) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("productName", product.getName());
        variables.put("vendorName", vendor.getBusinessName());
        variables.put("stockLevel", stockLevel);
        variables.put("alertDate", java.time.Instant.now());

        String subject = stockLevel == 0 ? 
            "URGENT: Product Out of Stock - " + product.getName() : 
            "Low Stock Alert - " + product.getName();

        emailTemplateService.sendHtmlEmail(
            vendor.getUser().getEmail(),
            subject,
            "stock-alert",
            variables
        );
    }
}
