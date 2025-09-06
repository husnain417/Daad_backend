package com.Daad.ecommerce.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.Daad.ecommerce.model.User;

import java.security.SecureRandom;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

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
            "Best regards,\nYour E-commerce Team",
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

            mailSender.send(message);
            System.out.println("✅ Email sent successfully to: " + to);
        } catch (Exception e) {
            System.err.println("❌ Failed to send email to: " + to);
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendWelcomeEmail(User user) {
        String subject = "Welcome to Our E-commerce Platform!";
        String body = String.format(
            "Hello %s,\n\n" +
            "Welcome to our e-commerce platform! Your account has been created successfully.\n\n" +
            "You can now:\n" +
            "- Browse our products\n" +
            "- Add items to your cart\n" +
            "- Place orders\n" +
            "- Manage your profile\n\n" +
            "If you have any questions, feel free to contact our support team.\n\n" +
            "Best regards,\nYour E-commerce Team",
            user.getUsername()
        );
        
        sendEmail(user.getEmail(), subject, body);
    }

    public void sendPasswordChangeConfirmation(User user) {
        String subject = "Password Changed Successfully";
        String body = String.format(
            "Hello %s,\n\n" +
            "Your password has been changed successfully.\n\n" +
            "If you didn't make this change, please contact our support team immediately.\n\n" +
            "Best regards,\nYour E-commerce Team",
            user.getUsername()
        );
        
        sendEmail(user.getEmail(), subject, body);
    }
}


