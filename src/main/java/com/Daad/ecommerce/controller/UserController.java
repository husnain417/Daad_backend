package com.Daad.ecommerce.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.Daad.ecommerce.security.SecurityUtils;
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
import com.Daad.ecommerce.service.NotificationService;

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

    @Autowired
    private NotificationService notificationService;

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

    // Vendor: Get own full details (vendor + linked user + bank details) from JWT
    @GetMapping("/vendor/details")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Map<String, Object>> getMyVendorDetails() {
        try {
            String userId = SecurityUtils.currentUserId();
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
            }
            Optional<Vendor> vendOpt = vendorRepository.findByUserId(userId);
            if (vendOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor profile not found"));
            }
            Vendor vendor = vendOpt.get();

            // Ensure user is fully loaded to get email/username/etc
            if (vendor.getUser() != null && vendor.getUser().getId() != null) {
                userRepository.findById(vendor.getUser().getId()).ifPresent(vendor::setUser);
            }

            Map<String, Object> userInfo = new LinkedHashMap<>();
            if (vendor.getUser() != null) {
                userInfo.put("id", vendor.getUser().getId());
                userInfo.put("username", vendor.getUser().getUsername());
                userInfo.put("email", vendor.getUser().getEmail());
                userInfo.put("role", vendor.getUser().getRole());
                userInfo.put("profilePicUrl", vendor.getUser().getProfilePicUrl());
                userInfo.put("isVerified", vendor.getUser().getIsVerified());
                userInfo.put("createdAt", vendor.getUser().getCreatedAt());
            }

            Map<String, Object> bankDetails = new LinkedHashMap<>();
            try {
                String bd = vendor.getBankDetails();
                if (bd != null && !bd.isBlank()) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked") Map<String, Object> parsed = om.readValue(bd, Map.class);
                    bankDetails.putAll(parsed);
                }
            } catch (Exception ignored) {}

            Map<String, Object> vendorInfo = new LinkedHashMap<>();
            vendorInfo.put("id", vendor.getId());
            vendorInfo.put("businessName", vendor.getBusinessName());
            vendorInfo.put("businessType", vendor.getBusinessType());
            vendorInfo.put("status", vendor.getStatus());
            vendorInfo.put("phoneNumber", vendor.getPhoneNumber());
            vendorInfo.put("commission", vendor.getCommission());
            vendorInfo.put("ratingAverage", vendor.getRating());
            if (vendor.getBusinessAddress() != null) {
                Map<String, Object> addr = new LinkedHashMap<>();
                addr.put("line1", vendor.getBusinessAddress().getAddressLine1());
                addr.put("line2", vendor.getBusinessAddress().getAddressLine2());
                addr.put("city", vendor.getBusinessAddress().getCity());
                addr.put("state", vendor.getBusinessAddress().getState());
                addr.put("postalCode", vendor.getBusinessAddress().getPostalCode());
                addr.put("country", vendor.getBusinessAddress().getCountry());
                vendorInfo.put("address", addr);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "vendor", vendorInfo,
                    "user", userInfo,
                    "bankDetails", bankDetails
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error fetching vendor details: " + e.getMessage()
            ));
        }
    }

    // Vendor: Update basic profile info
    @PutMapping("/vendor/profile")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<?> updateMyVendorProfile(@RequestBody Map<String, Object> body) {
        try {
            String userId = SecurityUtils.currentUserId();
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
            }
            Optional<Vendor> vendOpt = vendorRepository.findByUserId(userId);
            if (vendOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor profile not found"));
            }
            Vendor vendor = vendOpt.get();

            // Allowed updatable fields
            if (body.containsKey("businessName")) vendor.setBusinessName(Objects.toString(body.get("businessName"), vendor.getBusinessName()));
            if (body.containsKey("businessType")) vendor.setBusinessType(Objects.toString(body.get("businessType"), vendor.getBusinessType()));
            if (body.containsKey("phoneNumber")) vendor.setPhoneNumber(Objects.toString(body.get("phoneNumber"), vendor.getPhoneNumber()));
            if (body.containsKey("description")) vendor.setDescription(Objects.toString(body.get("description"), vendor.getDescription()));
            if (body.containsKey("logo")) vendor.setLogo(Objects.toString(body.get("logo"), vendor.getLogo()));

            vendorRepository.save(vendor);

            return ResponseEntity.ok(Map.of("success", true, "message", "Vendor profile updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", Objects.toString(e.getMessage(), "Failed to update profile")));
        }
    }

    // Vendor: Update business address
    @PutMapping("/vendor/address")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<?> updateMyVendorAddress(@RequestBody Map<String, Object> body) {
        try {
            String userId = SecurityUtils.currentUserId();
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
            }
            Optional<Vendor> vendOpt = vendorRepository.findByUserId(userId);
            if (vendOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor profile not found"));
            }
            Vendor vendor = vendOpt.get();

            Vendor.BusinessAddress addr = vendor.getBusinessAddress() != null ? vendor.getBusinessAddress() : new Vendor.BusinessAddress();
            if (body.containsKey("addressLine1")) addr.setAddressLine1(Objects.toString(body.get("addressLine1"), addr.getAddressLine1()));
            if (body.containsKey("addressLine2")) addr.setAddressLine2(Objects.toString(body.get("addressLine2"), addr.getAddressLine2()));
            if (body.containsKey("city")) addr.setCity(Objects.toString(body.get("city"), addr.getCity()));
            if (body.containsKey("state")) addr.setState(Objects.toString(body.get("state"), addr.getState()));
            if (body.containsKey("postalCode")) addr.setPostalCode(Objects.toString(body.get("postalCode"), addr.getPostalCode()));
            if (body.containsKey("country")) addr.setCountry(Objects.toString(body.get("country"), addr.getCountry()));
            vendor.setBusinessAddress(addr);

            vendorRepository.save(vendor);

            return ResponseEntity.ok(Map.of("success", true, "message", "Business address updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", Objects.toString(e.getMessage(), "Failed to update address")));
        }
    }

    // Vendor: Update bank details
    @PutMapping("/vendor/bank-details")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<?> updateMyVendorBankDetails(@RequestBody Map<String, Object> body) {
        try {
            String userId = SecurityUtils.currentUserId();
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
            }
            Optional<Vendor> vendOpt = vendorRepository.findByUserId(userId);
            if (vendOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor profile not found"));
            }
            Vendor vendor = vendOpt.get();

            String bankName = body.get("bankName") != null ? Objects.toString(body.get("bankName")) : null;
            String accountNumber = body.get("accountNumber") != null ? Objects.toString(body.get("accountNumber")) : null;
            String accountHolderName = body.get("accountHolderName") != null ? Objects.toString(body.get("accountHolderName")) : null;
            String routingNumber = body.get("routingNumber") != null ? Objects.toString(body.get("routingNumber")) : null;

            vendorRepository.updateBankDetails(vendor.getId(), bankName, accountNumber, accountHolderName, routingNumber);

            // No need to persist JSON field; build response snapshot
            Map<String, Object> bank = new LinkedHashMap<>();
            bank.put("bankName", Objects.toString(bankName, ""));
            bank.put("accountNumber", Objects.toString(accountNumber, ""));
            bank.put("accountHolderName", Objects.toString(accountHolderName, ""));

            return ResponseEntity.ok(Map.of("success", true, "message", "Bank details updated successfully", "bankDetails", bank));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", Objects.toString(e.getMessage(), "Failed to update bank details")));
        }
    }

    // Admin: Get full vendor details by ID (vendor + linked user + bank details)
    @GetMapping("/vendors/{vendorId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getVendorDetails(@PathVariable String vendorId) {
        try {
            Optional<Vendor> vendOpt = vendorRepository.findById(vendorId);
            if (vendOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Vendor not found"));
            }
            Vendor vendor = vendOpt.get();

            // Ensure user is fully loaded to get email/username/etc
            if (vendor.getUser() != null && vendor.getUser().getId() != null) {
                userRepository.findById(vendor.getUser().getId()).ifPresent(vendor::setUser);
            }

            Map<String, Object> userInfo = new LinkedHashMap<>();
            if (vendor.getUser() != null) {
                userInfo.put("id", vendor.getUser().getId());
                userInfo.put("username", vendor.getUser().getUsername());
                userInfo.put("email", vendor.getUser().getEmail());
                userInfo.put("role", vendor.getUser().getRole());
                userInfo.put("profilePicUrl", vendor.getUser().getProfilePicUrl());
                userInfo.put("isVerified", vendor.getUser().getIsVerified());
                userInfo.put("createdAt", vendor.getUser().getCreatedAt());
            }

            Map<String, Object> bankDetails = new LinkedHashMap<>();
            try {
                String bd = vendor.getBankDetails();
                if (bd != null && !bd.isBlank()) {
                    // naive parse since it is simple JSON we wrote
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked") Map<String, Object> parsed = om.readValue(bd, Map.class);
                    bankDetails.putAll(parsed);
                }
            } catch (Exception ignored) {}

            Map<String, Object> vendorInfo = new LinkedHashMap<>();
            vendorInfo.put("id", vendor.getId());
            vendorInfo.put("businessName", vendor.getBusinessName());
            vendorInfo.put("businessType", vendor.getBusinessType());
            vendorInfo.put("status", vendor.getStatus());
            vendorInfo.put("phoneNumber", vendor.getPhoneNumber());
            vendorInfo.put("commission", vendor.getCommission());
            vendorInfo.put("ratingAverage", vendor.getRating());
            if (vendor.getBusinessAddress() != null) {
                Map<String, Object> addr = new LinkedHashMap<>();
                addr.put("line1", vendor.getBusinessAddress().getAddressLine1());
                addr.put("line2", vendor.getBusinessAddress().getAddressLine2());
                addr.put("city", vendor.getBusinessAddress().getCity());
                addr.put("state", vendor.getBusinessAddress().getState());
                addr.put("postalCode", vendor.getBusinessAddress().getPostalCode());
                addr.put("country", vendor.getBusinessAddress().getCountry());
                vendorInfo.put("address", addr);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "vendor", vendorInfo,
                    "user", userInfo,
                    "bankDetails", bankDetails
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error fetching vendor details: " + e.getMessage()
            ));
        }
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
                
                // Handle bank details
                if (body.vendorDetails.bankDetails != null) {
                    String bankDetailsJson = buildBankDetailsJson(
                        body.vendorDetails.bankDetails.bankName,
                        body.vendorDetails.bankDetails.accountNumber,
                        body.vendorDetails.bankDetails.accountHolderName
                    );
                    vendor.setBankDetails(bankDetailsJson);
                }
                
                vendor.setStatus("pending");
                // Persist vendor and bank columns
                vendor = vendorRepository.save(vendor);
                if (body.vendorDetails.bankDetails != null) {
                    vendorRepository.updateBankDetails(
                        vendor.getId(),
                        body.vendorDetails.bankDetails.bankName,
                        body.vendorDetails.bankDetails.accountNumber,
                        body.vendorDetails.bankDetails.accountHolderName,
                        null
                    );
                }
                
                // Send vendor registration notifications
                try {
                    notificationService.notifyVendorRegistration(vendor);
                } catch (Exception e) {
                    System.err.println("Failed to send vendor registration notifications: " + e.getMessage());
                }
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

        // Send welcome email only for customers (not vendors)
        if (!"vendor".equals(user.getRole())) {
            try {
                notificationService.notifyUserRegistration(user);
            } catch (Exception e) {
                System.err.println("Failed to send welcome email: " + e.getMessage());
                // Don't fail registration if email fails
            }
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

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        
        if (email == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email and OTP are required"));
        }
        
        Optional<User> u = userRepository.findByEmail(email);
        if (u.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not found"));
        }
        
        User user = u.get();
        
        // Check if OTP exists and is not expired
        if (user.getOtp() == null || user.getOtpExpires() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No OTP found. Please request a new one."));
        }
        
        if (user.getOtpExpires().isBefore(java.time.Instant.now())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "OTP has expired. Please request a new one."));
        }
        
        // Verify OTP
        if (!user.getOtp().equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid OTP"));
        }
        
        // Generate reset token
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("email", user.getEmail());
        String resetToken = jwtService.generateResetToken(claims);
        
        // Clear OTP
        user.setOtp(null);
        user.setOtpExpires(null);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "OTP verified successfully",
            "resetToken", resetToken,
            "expiresIn", "15 minutes"
        ));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/vendors")
    public ResponseEntity<?> getAllVendors(@RequestParam(required = false) String status) {
        System.out.println("getAllVendors called with status: " + status);
        List<Vendor> vendors;
        if (status != null && !status.trim().isEmpty()) {
            // Filter by status
            System.out.println("Calling findByStatus with: " + status);
            vendors = vendorRepository.findByStatus(status);
        } else {
            // Get all vendors
            System.out.println("Calling findAllByOrderByCreatedAtDesc");
            vendors = vendorRepository.findAllByOrderByCreatedAtDesc();
        }
        
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

    // Admin: pending vendor approvals
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/vendors/pending-approval")
    public ResponseEntity<?> getPendingVendors() {
        List<Vendor> vendors = vendorRepository.findByStatus("pending");
        return ResponseEntity.ok(Map.of("success", true, "count", vendors.size(), "vendors", vendors));
    }

    // Admin: approve vendor
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/vendors/{vendorId}/approve")
    public ResponseEntity<?> approveVendor(@PathVariable String vendorId, org.springframework.security.core.Authentication authentication) {
        String adminId = (String) authentication.getPrincipal();
        vendorRepository.updateStatus(vendorId, "approved", adminId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Vendor approved"));
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
        
        // Load full user details for the vendor
        if (v.getUser() != null && v.getUser().getId() != null) {
            userRepository.findById(v.getUser().getId()).ifPresent(user -> {
                v.getUser().setUsername(user.getUsername());
                v.getUser().setEmail(user.getEmail());
                v.getUser().setRole(user.getRole());
                v.getUser().setProfilePicUrl(user.getProfilePicUrl());
                v.getUser().setIsVerified(user.getIsVerified());
            });
        }
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
        
        // Send vendor approval notifications
        try {
            boolean isApproved = "approved".equals(status);
            String reason = isApproved ? "Your application has been approved" : "Your application has been rejected";
            
            // Debug: Check if vendor has user details
            System.out.println("Sending vendor approval notification for vendor: " + v.getId());
            if (v.getUser() != null) {
                System.out.println("Vendor user ID: " + v.getUser().getId());
                System.out.println("Vendor user email: " + v.getUser().getEmail());
                System.out.println("Vendor user username: " + v.getUser().getUsername());
            } else {
                System.out.println("Vendor has no user details!");
            }
            
            notificationService.notifyVendorApproval(v, isApproved, reason);
        } catch (Exception e) {
            System.err.println("Failed to send vendor approval notifications: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(Map.of("success", true, "message", "Vendor status updated to " + status));
    }
    
    private String buildBankDetailsJson(String bankName, String accountNumber, String accountHolderName) {
        return String.format(
            "{\"bankName\":\"%s\",\"accountNumber\":\"%s\",\"accountHolderName\":\"%s\"}",
            bankName != null ? bankName : "",
            accountNumber != null ? accountNumber : "",
            accountHolderName != null ? accountHolderName : ""
        );
    }
}


