package com.Daad.ecommerce.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import com.Daad.ecommerce.model.User;

import java.security.SecureRandom;

@Service
public class EmailService {

    @Autowired
    private AccountEmailService accountEmailService;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    EmailTemplateService emailTemplateService;

    private final SecureRandom random = new SecureRandom();

    public void sendOtp(User user) {
        String otp = String.valueOf(100000 + random.nextInt(900000));
        user.setOtp(otp);
        user.setOtpExpires(java.time.Instant.now().plusSeconds(2 * 60));
        
        String subject = "Password Reset OTP";
        String body = String.format(
            "Hello %s,\n\n" +
            "Your password reset OTP is: %s\n\n" +
            "This OTP will expire in 2 minutes.\n\n" +
            "If you didn't request this, please ignore this email.\n\n" +
            "Best regards,\nYour Daad E-commerce Team",
            user.getUsername(), otp
        );
        
        sendEmail(user.getEmail(), subject, body);
    }

    public void reSendOtp(User user) {
        sendOtp(user);
    }

    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            emailTemplateService.sendTextEmail(to, subject, body);
        } catch (Exception e) {
            System.err.println("❌ Failed to send email to: " + to);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendWelcomeEmail(User user) {
        // Use the new template-based service
        accountEmailService.sendWelcomeNotification(user);
    }

    public void sendPasswordChangeConfirmation(User user) {
        String subject = "Password Changed Successfully";
        String body = String.format(
            "Hello %s,\n\n" +
            "Your password has been changed successfully.\n\n" +
            "If you didn't make this change, please contact our support team immediately.\n\n" +
            "Best regards,\nYour Daad E-commerce Team",
            user.getUsername()
        );
        
        sendEmail(user.getEmail(), subject, body);
    }
}


