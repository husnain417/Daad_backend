package com.Daad.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class EcommerceApplication {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public static void main(String[] args) {
        // Load .env file FIRST, before Spring Boot starts
        loadEnvironmentVariables();
        
        // Create necessary directories
        createDirectories();
        
        // Start Spring Boot application
        SpringApplication.run(EcommerceApplication.class, args);
    }

    private static void loadEnvironmentVariables() {
        System.out.println("🔧 Loading .env file...");
        
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            System.out.println("⚠️  .env file not found at: " + envPath.toAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envPath.toFile()))) {
            String line;
            int loadedCount = 0;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse key=value pairs
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();
                    
                    // Remove quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    System.setProperty(key, value);
                    loadedCount++;
                    
                    // Log loaded variables (hide sensitive ones)
                    if (key.contains("PASSWORD") || key.contains("SECRET")) {
                        System.out.println("✅ Loaded: " + key + "=***HIDDEN***");
                    } else {
                        System.out.println("✅ Loaded: " + key + "=" + value);
                    }
                }
            }
            
            System.out.println("🎉 Successfully loaded " + loadedCount + " environment variables from .env");
            
        } catch (IOException e) {
            System.err.println("❌ Error loading .env file: " + e.getMessage());
        }
    }

    private static void createDirectories() {
        // Create uploads directory
        File uploadsDir = new File("uploads");
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }

        // Create temp directory
        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                    .allowedHeaders("*")
                    .allowCredentials(false);
            }
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void testDatabaseConnection() {
        System.out.println("🌐 Database connection test after startup...");
        
        if (jdbcTemplate != null) {
            try {
                // Test database connection
                String result = jdbcTemplate.queryForObject("SELECT 'Database connected successfully!' as message", String.class);
                System.out.println("✅ " + result);
            } catch (Exception e) {
                System.err.println("❌ Database connection failed: " + e.getMessage());
                System.err.println("💡 This might be due to:");
                System.err.println("   - Network connectivity issues");
                System.err.println("   - Firewall restrictions");
                System.err.println("   - Database server being down");
                System.err.println("   - IP whitelist restrictions on Supabase");
                System.err.println("🔧 Application will continue running with in-memory data");
            }
        } else {
            System.out.println("⚠️  JdbcTemplate not available - database connection test skipped");
        }
        
        System.out.println("🚀 Spring Boot Application Started Successfully!");
        System.out.println("🌍 Backend is running on port 5000");
    }
}