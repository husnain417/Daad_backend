package com.Daad.ecommerce.controller;

import com.Daad.ecommerce.dto.AuthDtos;
import com.Daad.ecommerce.model.User;
import com.Daad.ecommerce.model.Vendor;
import com.Daad.ecommerce.repository.UserRepository;
import com.Daad.ecommerce.repository.VendorRepository;
import com.Daad.ecommerce.service.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository, VendorRepository vendorRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.jwtService = jwtService;
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
}
