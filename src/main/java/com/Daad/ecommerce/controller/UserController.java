package com.Daad.ecommerce.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.Daad.ecommerce.dto.AuthDtos;
import com.Daad.ecommerce.model.User;
import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import com.Daad.ecommerce.service.EmailService;
import com.Daad.ecommerce.service.JwtService;
import com.Daad.ecommerce.service.LocalUploadService;

import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final LocalUploadService localUploadService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserController(UserRepository userRepository,
                          VendorRepository vendorRepository,
                          JwtService jwtService,
                          EmailService emailService,
                          LocalUploadService localUploadService) {
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.localUploadService = localUploadService;
    }

    @PostMapping("/google-auth")
    public ResponseEntity<?> googleAuth(@RequestBody AuthDtos.GoogleAuthRequest request,
                                        @Value("${google.client-id:}") String googleClientId) {
        if (request == null || request.id_token == null || request.id_token.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "No ID token provided"
            ));
        }

        try {
            // TODO: Verify Google ID token using Google libraries; for now, accept input as placeholder
            String email = "user" + System.currentTimeMillis() + "@example.com";
            String name = "google_user";
            String googleId = UUID.randomUUID().toString();

            Optional<User> existing = userRepository.findByEmail(email);
            User user = existing.orElseGet(() -> {
                User u = new User();
                u.setUsername(name);
                u.setEmail(email);
                u.setPassword("");
                u.setAuthProvider("google");
                u.setGoogleId(googleId);
                u.setIsVerified(true);
                return userRepository.save(u);
            });

            if (!"google".equals(user.getAuthProvider())) {
                user.setAuthProvider("google");
                user.setGoogleId(googleId);
                user.setIsVerified(true);
                userRepository.save(user);
            }

            if ("vendor".equals(user.getRole())) {
                Optional<Vendor> v = vendorRepository.findByUser(user);
                if (v.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                            "success", false,
                            "message", "Vendor profile not found. Please contact support."
                    ));
                }
                String status = v.get().getStatus();
                if ("rejected".equals(status)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Your vendor account has been rejected. Please contact support for more information.", "vendorStatus", "rejected"));
                if ("suspended".equals(status)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Your vendor account has been suspended. Please contact support to resolve this issue.", "vendorStatus", "suspended"));
                if ("pending".equals(status)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Your vendor account is pending approval. You will be notified once your account is reviewed.", "vendorStatus", "pending"));
            }

            Map<String, Object> tokenPayload = new HashMap<>();
            tokenPayload.put("id", user.getId());
            tokenPayload.put("email", user.getEmail());
            tokenPayload.put("role", user.getRole());

            String accessToken = jwtService.generateAccessToken(tokenPayload);

            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());
            userData.put("isVerified", user.getIsVerified());
            userData.put("profilePicUrl", user.getProfilePicUrl());
            userData.put("rewardPoints", user.getRewardPoints());
            userData.put("firstOrderPlaced", user.getFirstOrderPlaced());
            userData.put("authProvider", user.getAuthProvider());

            if ("vendor".equals(user.getRole())) {
                vendorRepository.findByUser(user).ifPresent(v -> {
                    Map<String, Object> vendor = new LinkedHashMap<>();
                    vendor.put("businessName", v.getBusinessName());
                    vendor.put("businessType", v.getBusinessType());
                    vendor.put("status", v.getStatus());
                    vendor.put("profileCompleted", v.getProfileCompleted());
                    vendor.put("rating", v.getRating());
                    userData.put("vendor", vendor);
                });
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Google login successful",
                    "user", userData,
                    "token", accessToken
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Invalid Google token"
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthDtos.RegisterRequest body) {
        if (body.username == null || body.email == null || body.password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Fill all fields"));
        }
        if (body.role != null && !(body.role.equals("customer") || body.role.equals("vendor"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role specified"));
        }

        if (userRepository.findByUsername(body.username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }
        if (userRepository.findByEmail(body.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already exists"));
        }

        boolean isPasswordValid = body.password.matches("^(?=.*\\d)(?=.*[\\W_]).{8,}$");
        if (!isPasswordValid) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 8 characters long, include at least one special character, and contain at least one number."));
        }

        boolean isEmailValid = body.email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        if (!isEmailValid) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a valid email format"));
        }

        User user = new User();
        user.setUsername(body.username);
        user.setEmail(body.email.toLowerCase());
        user.setPassword(passwordEncoder.encode(body.password));
        user.setRole(body.role == null ? "customer" : body.role);

        user = userRepository.save(user);

        if ("vendor".equals(user.getRole()) && body.vendorDetails != null) {
            try {
                Vendor vendor = new Vendor();
                vendor.setUser(user);
                vendor.setBusinessName(body.vendorDetails.businessName);
                vendor.setBusinessType(body.vendorDetails.businessType);
                vendor.setPhoneNumber(body.vendorDetails.phoneNumber);
                Vendor.BusinessAddress addr = new Vendor.BusinessAddress();
                addr.setAddressLine1(body.vendorDetails.businessAddress.addressLine1);
                addr.setAddressLine2(body.vendorDetails.businessAddress.addressLine2);
                addr.setCity(body.vendorDetails.businessAddress.city);
                addr.setState(body.vendorDetails.businessAddress.state);
                addr.setPostalCode(body.vendorDetails.businessAddress.postalCode);
                addr.setCountry(body.vendorDetails.businessAddress.country == null ? "Pakistan" : body.vendorDetails.businessAddress.country);
                vendor.setBusinessAddress(addr);
                vendor.setDescription(body.vendorDetails.description);
                vendor.setStatus("pending");
                vendorRepository.save(vendor);
            } catch (Exception ex) {
                ex.printStackTrace();
                userRepository.deleteById(user.getId());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "message", "Failed to create vendor profile. Please try again.",
                        "error", ex.getMessage()
                ));
            }
        }

        String responseMessage = "vendor".equals(user.getRole()) ?
                "Vendor account created successfully. Your account is pending approval." :
                "Customer account created successfully";

        // Send welcome email
        try {
            emailService.sendWelcomeEmail(user);
        } catch (Exception e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
            // Don't fail registration if email fails
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", responseMessage);
        resp.put("role", user.getRole());
        resp.put("userId", user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDtos.LoginRequest body) {
        if (body.email == null || body.password == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email and password are required"));
        }
        Optional<User> userOpt = userRepository.findByEmail(body.email.toLowerCase());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Invalid credentials"));
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(body.password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Invalid credentials"));
        }

        if ("vendor".equals(user.getRole())) {
            Optional<Vendor> v = vendorRepository.findByUser(user);
            if (v.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor profile not found. Please contact support."));
            }
            String status = v.get().getStatus();
            if ("rejected".equals(status)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Your vendor account has been rejected. Please contact support for more information.", "vendorStatus", "rejected"));
            if ("suspended".equals(status)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Your vendor account has been suspended. Please contact support to resolve this issue.", "vendorStatus", "suspended"));
            if ("pending".equals(status)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Your vendor account is pending approval. You will be notified once your account is reviewed.", "vendorStatus", "pending"));
        }

        Map<String, Object> tokenPayload = new HashMap<>();
        tokenPayload.put("id", user.getId());
        tokenPayload.put("email", user.getEmail());
        tokenPayload.put("role", user.getRole());
        String token = jwtService.generateAccessToken(tokenPayload);

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole());
        userData.put("profilePicUrl", user.getProfilePicUrl());

        if ("vendor".equals(user.getRole())) {
            vendorRepository.findByUser(user).ifPresent(v -> {
                Map<String, Object> vendor = new LinkedHashMap<>();
                vendor.put("businessName", v.getBusinessName());
                vendor.put("businessType", v.getBusinessType());
                vendor.put("status", v.getStatus());
                vendor.put("profileCompleted", v.getProfileCompleted());
                vendor.put("rating", v.getRating());
                userData.put("vendor", vendor);
            });
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful",
                "user", userData,
                "token", token
        ));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile(org.springframework.security.core.Authentication authentication) {
        String id = (String) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
        }
        return ResponseEntity.ok(Map.of("message", "Profile", "user", userOpt.get()));
    }

    @PostMapping("/password-forgot")
    public ResponseEntity<?> forgotPass(@RequestBody AuthDtos.EmailRequest body) {
        if (body.email == null) return ResponseEntity.badRequest().body(Map.of("message", "Enter a valid email format"));
        Optional<User> u = userRepository.findByEmail(body.email);
        if (u.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "User does not exist"));
        emailService.sendOtp(u.get());
        userRepository.save(u.get());
        return ResponseEntity.ok(Map.of("message", "OTP sent to your email", "email", u.get().getEmail()));
    }

    @PostMapping("/set-new-password")
    public ResponseEntity<?> setNewPassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestBody AuthDtos.PasswordUpdateRequest body) {
        String id;
        try {
            id = jwtService.extractUserIdFromResetToken(authorization);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid token"));
        }
        if (id == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No token provided"));
        if (body.newPassword == null) return ResponseEntity.badRequest().body(Map.of("message", "New password required"));
        Optional<User> u = userRepository.findById(id);
        if (u.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
        boolean isPasswordValid = body.newPassword.matches("^(?=.*\\d)(?=.*[\\W_]).{8,}$");
        if (!isPasswordValid) return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 8 characters long, include at least one special character, and contain at least one number."));
        u.get().setPassword(passwordEncoder.encode(body.newPassword));
        u.get().setOtp(null);
        u.get().setOtpExpires(null);
        if (!Boolean.TRUE.equals(u.get().getIsVerified())) u.get().setIsVerified(true);
        userRepository.save(u.get());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PostMapping("/password-update")
    public ResponseEntity<?> changePass(org.springframework.security.core.Authentication authentication, @RequestBody AuthDtos.PasswordUpdateRequest body) {
        String id = (String) authentication.getPrincipal();
        if (Objects.equals(body.oldPassword, body.newPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "You entered the same password please change: "));
        }
        Optional<User> u = userRepository.findById(id);
        if (u.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "No user found"));
        if (!passwordEncoder.matches(body.oldPassword, u.get().getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid old password"));
        }
        u.get().setPassword(passwordEncoder.encode(body.newPassword));
        userRepository.save(u.get());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    @PostMapping("/upload-pic")
    public ResponseEntity<?> uploadPicture(org.springframework.security.core.Authentication authentication, @RequestParam("profilePicture") MultipartFile file) {
        String id = (String) authentication.getPrincipal();
        Optional<User> u = userRepository.findById(id);
        if (u.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "No file uploaded"));
        try {
            LocalUploadService.UploadResult result = localUploadService.uploadMultipart(file, "user/" + u.get().getId());
            u.get().setProfilePicUrl(result.url);
            userRepository.save(u.get());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Profile picture updated successfully",
                    "data", Map.of(
                            "profilePicUrl", result.url,
                            "imageUrl", result.url,
                            "filename", result.filename
                    )
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Server error"));
        }
    }

    @PutMapping("/update-account")
    public ResponseEntity<?> updateAccount(org.springframework.security.core.Authentication authentication, @RequestBody AuthDtos.PasswordUpdateRequest body) {
        String id = (String) authentication.getPrincipal();
        Optional<User> u = userRepository.findById(id);
        if (u.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        User user = u.get();
        if (body.username != null && !body.username.equals(user.getUsername())) {
            if (userRepository.findByUsername(body.username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username already taken"));
            }
            user.setUsername(body.username);
        }
        if (body.currentPassword != null && body.newPassword != null) {
            if (!passwordEncoder.matches(body.currentPassword, user.getPassword())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Current password is incorrect"));
            }
            boolean isPasswordValid = body.newPassword.matches("^(?=.*\\d)(?=.*[\\W_]).{8,}$");
            if (!isPasswordValid) return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 8 characters long, include at least one special character, and contain at least one number."));
            user.setPassword(passwordEncoder.encode(body.newPassword));
        }
        userRepository.save(user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", Map.of("username", user.getUsername(), "email", user.getEmail(), "profilePicUrl", user.getProfilePicUrl()));
        return ResponseEntity.ok(Map.of("success", true, "message", "Account updated successfully", "data", data));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody AuthDtos.EmailRequest body) {
        if (body.email == null) return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        Optional<User> u = userRepository.findByEmail(body.email);
        if (u.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "User not found"));

        // enforce 30s window
        java.time.Instant thirtySecondsAgo = java.time.Instant.now().minusSeconds(30);
        if (u.get().getOtpExpires() != null && u.get().getOtpExpires().minusSeconds(120).isAfter(thirtySecondsAgo)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Please wait before requesting a new OTP", "retryAfter", 30));
        }
        emailService.reSendOtp(u.get());
        userRepository.save(u.get());
        return ResponseEntity.ok(Map.of("message", "New OTP sent to your email", "email", u.get().getEmail()));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/vendors")
    public ResponseEntity<?> getAllVendors() {
        List<Vendor> vendors = vendorRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> transformed = new ArrayList<>();
        for (Vendor vendor : vendors) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("vendorId", vendor.getId());
            v.put("businessName", vendor.getBusinessName());
            v.put("businessType", vendor.getBusinessType());
            v.put("phoneNumber", vendor.getPhoneNumber());
            v.put("businessAddress", vendor.getBusinessAddress());
            v.put("description", vendor.getDescription());
            v.put("logo", vendor.getLogo());
            v.put("status", vendor.getStatus());
            v.put("approvedBy", vendor.getApprovedBy());
            v.put("approvedAt", vendor.getApprovedAt());
            v.put("bankDetails", vendor.getBankDetails());
            v.put("taxId", vendor.getTaxId());
            v.put("policies", vendor.getPolicies());
            v.put("commission", vendor.getCommission());
            v.put("rating", vendor.getRating());
            v.put("profileCompleted", vendor.getProfileCompleted());
            if (vendor.getUser() != null) {
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("userId", vendor.getUser().getId());
                user.put("username", vendor.getUser().getUsername());
                user.put("email", vendor.getUser().getEmail());
                user.put("role", vendor.getUser().getRole());
                user.put("profilePicUrl", vendor.getUser().getProfilePicUrl());
                user.put("isVerified", vendor.getUser().getIsVerified());
                v.put("user", user);
            }
            v.put("vendorCreatedAt", vendor.getCreatedAt());
            v.put("vendorUpdatedAt", vendor.getUpdatedAt());
            transformed.add(v);
        }
        return ResponseEntity.ok(Map.of("success", true, "count", transformed.size(), "data", transformed));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/vendors/{vendorId}/status")
    public ResponseEntity<?> updateVendorStatus(@PathVariable String vendorId, @RequestBody Map<String, String> body, org.springframework.security.core.Authentication authentication) {
        String status = body.get("status");
        if (vendorId == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Vendor ID is required"));
        if (status == null || !(status.equals("pending") || status.equals("approved") || status.equals("rejected") || status.equals("suspended"))) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Valid status is required: pending, approved, rejected, or suspended"));
        }
        Optional<Vendor> vOpt = vendorRepository.findById(vendorId);
        if (vOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor not found"));
        Vendor v = vOpt.get();
        v.setStatus(status);
        if ("approved".equals(status)) {
            String adminId = (String) authentication.getPrincipal();
            userRepository.findById(adminId).ifPresent(v::setApprovedBy);
            v.setApprovedAt(java.time.Instant.now());
            if (v.getUser() != null) {
                userRepository.updateRole(v.getUser().getId(), "vendor");
            }
        }
        if ("rejected".equals(status) || "suspended".equals(status)) {
            if (v.getUser() != null) {
                userRepository.updateRole(v.getUser().getId(), "customer");
            }
        }
        vendorRepository.save(v);
        return ResponseEntity.ok(Map.of("success", true, "message", "Vendor status updated to " + status));
    }
}


