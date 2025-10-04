package com.Daad.ecommerce.dto;

public class AuthDtos {
    public static class RegisterRequest {
        public String username;
        public String email;
        public String password;
        public String role;
        public VendorDetails vendorDetails;
    }

    public static class VendorDetails {
        public String businessName;
        public String businessType;
        public String phoneNumber;
        public BusinessAddress businessAddress;
        public String description;
        public BankDetails bankDetails;
    }

    public static class BusinessAddress {
        public String addressLine1;
        public String addressLine2;
        public String city;
        public String state;
        public String postalCode;
        public String country;
    }

    public static class BankDetails {
        public String bankName;
        public String accountNumber;
        public String accountHolderName;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class GoogleAuthRequest {
        public String id_token;
    }

    public static class PasswordUpdateRequest {
        public String oldPassword;
        public String newPassword;
        public String currentPassword; // for updateAccount
        public String username; // for updateAccount
    }

    public static class EmailRequest {
        public String email;
    }
}


