package com.Daad.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
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
@EnableScheduling
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
                    
                    // Map environment variables to Spring Boot property names
                    // Database
                    if ("DB_URL".equals(key)) {
                        System.setProperty("spring.datasource.url", value);
                    } else if ("DB_USERNAME".equals(key)) {
                        System.setProperty("spring.datasource.username", value);
                    } else if ("DB_PASSWORD".equals(key)) {
                        System.setProperty("spring.datasource.password", value);
                    } else if ("DB_DRIVER".equals(key)) {
                        System.setProperty("spring.datasource.driver-class-name", value);
                    }
                    // Server
                    else if ("SERVER_PORT".equals(key)) {
                        System.setProperty("server.port", value);
                    }
                    // JWT
                    else if ("JWT_ACCESS_SECRET".equals(key)) {
                        System.setProperty("jwt.access.secret", value);
                    } else if ("JWT_RESET_SECRET".equals(key)) {
                        System.setProperty("jwt.reset.secret", value);
                    }
                    // Email
                    else if ("SMTP_HOST".equals(key)) {
                        System.setProperty("spring.mail.host", value);
                    } else if ("SMTP_PORT".equals(key)) {
                        System.setProperty("spring.mail.port", value);
                    } else if ("SMTP_USERNAME".equals(key)) {
                        System.setProperty("spring.mail.username", value);
                    } else if ("SMTP_PASSWORD".equals(key)) {
                        System.setProperty("spring.mail.password", value);
                    }
                    // Backblaze
                    else if ("BACKBLAZE_B2_KEY_ID".equals(key)) {
                        System.setProperty("backblaze.b2.key-id", value);
                    } else if ("BACKBLAZE_B2_APPLICATION_KEY".equals(key)) {
                        System.setProperty("backblaze.b2.application-key", value);
                    } else if ("BACKBLAZE_B2_BUCKET_NAME".equals(key)) {
                        System.setProperty("backblaze.b2.bucket-name", value);
                    } else if ("BACKBLAZE_B2_ENDPOINT".equals(key)) {
                        System.setProperty("backblaze.b2.endpoint", value);
                    } else if ("BACKBLAZE_B2_REGION".equals(key)) {
                        System.setProperty("backblaze.b2.region", value);
                    }
                    // Paymob
                    else if ("PAYMOB_API_KEY".equals(key)) {
                        System.setProperty("payments.paymob.api-key", value);
                    } else if ("PAYMOB_SECRET_KEY".equals(key)) {
                        System.setProperty("payments.paymob.secret-key", value);
                    } else if ("PAYMOB_PUBLIC_KEY".equals(key)) {
                        System.setProperty("payments.paymob.public-key", value);
                    } else if ("PAYMOB_INTEGRATION_ID".equals(key)) {
                        System.setProperty("payments.paymob.integration-id", value);
                    } else if ("PAYMOB_IFRAME_ID".equals(key)) {
                        System.setProperty("payments.paymob.iframe-id", value);
                    } else if ("PAYMOB_WEBHOOK_URL".equals(key)) {
                        System.setProperty("payments.paymob.webhook-url", value);
                    } else if ("PAYMOB_SUCCESS_URL".equals(key)) {
                        System.setProperty("payments.paymob.success-url", value);
                    } else if ("PAYMOB_FAILURE_URL".equals(key)) {
                        System.setProperty("payments.paymob.failure-url", value);
                    } else if ("PAYMOB_API_BASE_URL".equals(key)) {
                        System.setProperty("payments.paymob.api-base-url", value);
                    }
                    // Google
                    else if ("GOOGLE_CLIENT_ID".equals(key)) {
                        System.setProperty("google.client.id", value);
                    } else if ("GOOGLE_CLIENT_SECRET".equals(key)) {
                        System.setProperty("google.client.secret", value);
                    }
                    // Microsoft Graph
                    else if ("MICROSOFT_GRAPH_CLIENT_ID".equals(key)) {
                        System.setProperty("microsoft.graph.client-id", value);
                    } else if ("MICROSOFT_GRAPH_TENANT_ID".equals(key)) {
                        System.setProperty("microsoft.graph.tenant-id", value);
                    } else if ("MICROSOFT_GRAPH_SECRET".equals(key)) {
                        System.setProperty("microsoft.graph.secret", value);
                    } else if ("MICROSOFT_GRAPH_SENDER_EMAIL".equals(key)) {
                        System.setProperty("microsoft.graph.sender-email", value);
                    }
                    
                    loadedCount++;
                    
                    // Log loaded variables (hide sensitive ones)
                    if (key.contains("PASSWORD") || key.contains("SECRET")) {
                        System.out.println("✅ Loaded: " + key + "=***HIDDEN***");
                    } else {
                        System.out.println("✅ Loaded: " + key + "=" + value);
                    }
                }
            }
            
            // Set defaults for required properties if not provided
            if (System.getProperty("payments.paymob.api-base-url") == null) {
                System.setProperty("payments.paymob.api-base-url", "https://accept.paymob.com/api");
            }
            // Set empty defaults for Microsoft Graph (optional - only needed if using Microsoft Graph email)
            if (System.getProperty("microsoft.graph.client-id") == null) {
                System.setProperty("microsoft.graph.client-id", "");
            }
            if (System.getProperty("microsoft.graph.tenant-id") == null) {
                System.setProperty("microsoft.graph.tenant-id", "");
            }
            if (System.getProperty("microsoft.graph.secret") == null) {
                System.setProperty("microsoft.graph.secret", "");
            }
            if (System.getProperty("microsoft.graph.sender-email") == null) {
                System.setProperty("microsoft.graph.sender-email", "");
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
                registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                    .allowedHeaders("*")
                    .allowCredentials(false)
                    .maxAge(3600);
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
        System.out.println("🌍 Backend is running on port 5205");
    }
}