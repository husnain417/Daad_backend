package com.Daad.ecommerce.service;

import com.Daad.ecommerce.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AccountEmailService {

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Async("emailTaskExecutor")
    public void sendWelcomeNotification(User user) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getUsername());
        variables.put("userEmail", user.getEmail());
        variables.put("role", user.getRole());
        variables.put("registrationDate", user.getCreatedAt());

        emailTemplateService.sendHtmlEmail(
            user.getEmail(),
            "Welcome to Daad!",
            "welcome-email",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendEmailVerificationNotification(User user, String verificationToken) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getUsername());
        variables.put("userEmail", user.getEmail());
        variables.put("verificationToken", verificationToken);
        variables.put("verificationLink", "https://daadfashion.com/verify-email?token=" + verificationToken);

        emailTemplateService.sendHtmlEmail(
            user.getEmail(),
            "Verify Your Email Address",
            "email-verification",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetNotification(User user, String resetToken) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getUsername());
        variables.put("userEmail", user.getEmail());
        variables.put("resetToken", resetToken);
        variables.put("resetLink", "https://daadfashion.com/reset-password?token=" + resetToken);
        variables.put("expiryTime", "24 hours");

        emailTemplateService.sendHtmlEmail(
            user.getEmail(),
            "Password Reset Request",
            "password-reset",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendAccountStatusNotification(User user, String newStatus) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getUsername());
        variables.put("userEmail", user.getEmail());
        variables.put("newStatus", newStatus);
        variables.put("statusChangeDate", java.time.Instant.now());

        String subject = "Account Status Update - " + newStatus;

        emailTemplateService.sendHtmlEmail(
            user.getEmail(),
            subject,
            "account-status-update",
            variables
        );
    }

    @Async("emailTaskExecutor")
    public void sendOtpNotification(User user, String otp) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getUsername());
        variables.put("userEmail", user.getEmail());
        variables.put("otp", otp);
        variables.put("otpExpiry", "5 minutes");

        emailTemplateService.sendHtmlEmail(
            user.getEmail(),
            "Your OTP Code",
            "otp-notification",
            variables
        );
    }
}
