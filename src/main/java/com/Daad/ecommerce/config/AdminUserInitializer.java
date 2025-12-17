package com.Daad.ecommerce.config;

import com.Daad.ecommerce.model.User;
import com.Daad.ecommerce.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AdminUserInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminUserInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void ensureAdminUserExists() {
        final String email = "admin@daad.com";
        final String rawPassword = "L 123456789";

        Optional<User> existing = userRepository.findByEmail(email.toLowerCase());
        if (existing.isPresent()) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail(email.toLowerCase());
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setRole("admin");
        admin.setIsVerified(true);

        userRepository.save(admin);
    }
}


