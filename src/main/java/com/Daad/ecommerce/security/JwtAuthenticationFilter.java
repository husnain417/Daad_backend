package com.Daad.ecommerce.security;

import com.Daad.ecommerce.model.Permission;
import com.Daad.ecommerce.repository.RoleRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey accessSecretKey;
    
    @Autowired(required = false)
    private RoleRepository roleRepository;

    public JwtAuthenticationFilter(@Value("${jwt.access.secret}") String accessSecret) {
        this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String path = request.getRequestURI();
        
        // Log authentication attempts for discount endpoints
        if (path != null && path.contains("/discount")) {
            System.out.println("🔐 JWT Filter - Processing request to: " + path);
            System.out.println("🔐 JWT Filter - Authorization header present: " + (authHeader != null));
            if (authHeader != null) {
                System.out.println("🔐 JWT Filter - Authorization header value: " + (authHeader.length() > 20 ? authHeader.substring(0, 20) + "..." : authHeader));
                System.out.println("🔐 JWT Filter - Starts with 'Bearer ': " + authHeader.startsWith("Bearer "));
            }
        }
        
        if (authHeader != null && authHeader.trim().startsWith("Bearer ")) {
            String token = authHeader.trim().substring(7).trim();
            try {
                Claims claims = Jwts.parserBuilder().setSigningKey(accessSecretKey).build().parseClaimsJws(token).getBody();
                String userId = claims.get("id", String.class);
                String role = claims.get("role", String.class);
                
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                
                // Add permissions as authorities if role is admin and repository is available
                if ("admin".equalsIgnoreCase(role) && roleRepository != null) {
                    try {
                        List<Permission> permissions = roleRepository.getPermissionsByUserId(userId);
                        for (Permission permission : permissions) {
                            authorities.add(new SimpleGrantedAuthority("PERMISSION_" + permission.getName()));
                        }
                    } catch (Exception e) {
                        // If permission loading fails, continue with role-based auth only
                        // This ensures backward compatibility
                    }
                }
                
                Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                
                // Log successful authentication for discount endpoints
                if (path != null && path.contains("/discount")) {
                    System.out.println("✅ JWT Filter - Authentication successful for user: " + userId + ", role: " + role);
                    System.out.println("✅ JWT Filter - Authorities: " + authorities);
                }
            } catch (Exception e) {
                // Log JWT parsing errors for discount endpoints
                if (path != null && path.contains("/discount")) {
                    System.out.println("❌ JWT Filter - Token validation failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            // Log missing token for discount endpoints
            if (path != null && path.contains("/discount")) {
                System.out.println("❌ JWT Filter - No Authorization header or invalid format");
            }
        }
        filterChain.doFilter(request, response);
    }
}


