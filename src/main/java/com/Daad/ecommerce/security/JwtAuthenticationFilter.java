package com.Daad.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey accessSecretKey;

    public JwtAuthenticationFilter(@Value("${jwt.access.secret}") String accessSecret) {
        this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parserBuilder().setSigningKey(accessSecretKey).build().parseClaimsJws(token).getBody();
                String userId = claims.get("id", String.class);
                String role = claims.get("role", String.class);
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, Collections.singletonList(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
            }
        }
        filterChain.doFilter(request, response);
    }
}


