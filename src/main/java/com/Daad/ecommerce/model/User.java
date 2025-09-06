package com.Daad.ecommerce.model;

import java.time.Instant;

public class User {

    private String id; // UUID as string

    private String username;

    private String email;

    private String password;

    private String role = "customer"; 

    private Boolean isVerified = false;

    private String profilePicUrl;

    private Integer rewardPoints = 0;

    private Boolean firstOrderPlaced = false;

    private String authProvider = "local";

    private String googleId;

    private String otp;

    private Instant otpExpires;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public String getProfilePicUrl() {
        return profilePicUrl;
    }

    public void setProfilePicUrl(String profilePicUrl) {
        this.profilePicUrl = profilePicUrl;
    }

    public Integer getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(Integer rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public Boolean getFirstOrderPlaced() {
        return firstOrderPlaced;
    }

    public void setFirstOrderPlaced(Boolean firstOrderPlaced) {
        this.firstOrderPlaced = firstOrderPlaced;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public Instant getOtpExpires() {
        return otpExpires;
    }

    public void setOtpExpires(Instant otpExpires) {
        this.otpExpires = otpExpires;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}