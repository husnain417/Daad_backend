package com.Daad.ecommerce.model;

import lombok.Data;

import java.time.Instant;

@Data
public class Vendor {

    private String id; // UUID string

    private User user;

    private String businessName;
    private String businessType;
    private String phoneNumber;

    private BusinessAddress businessAddress;

    private String description;

    private String logo;

    private String status = "pending"; // pending, approved, rejected, suspended

    private User approvedBy;

    private Instant approvedAt;

    private String bankDetails;
    private String taxId;

    private String policies;

    private Double commission;
    private Double rating;
    private Boolean profileCompleted = false;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    String websiteSyncUrl;
    String requestToken;

    @Data
    public static class BusinessAddress {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;

    }

}


