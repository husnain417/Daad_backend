package com.Daad.ecommerce.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import io.jsonwebtoken.Claims;

@Service
public class JwtService {

    private final SecretKey accessSecretKey;
    private final SecretKey resetSecretKey;
    private final int expirationHours;

    public JwtService(
            @Value("${jwt.access.secret}") String accessSecret,
            @Value("${jwt.reset.secret}") String resetSecret,
            @Value("${jwt.expiration-hours:24}") int expirationHours
    ) {
        this.accessSecretKey = Keys.hmacShaKeyFor(accessSecret.getBytes());
        this.resetSecretKey = Keys.hmacShaKeyFor(resetSecret.getBytes());
        this.expirationHours = expirationHours;
    }

    public String generateAccessToken(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(accessSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateResetToken(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .signWith(resetSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parserBuilder().setSigningKey(accessSecretKey).build().parseClaimsJws(token).getBody();
    }

    public Claims parseResetToken(String token) {
        return Jwts.parserBuilder().setSigningKey(resetSecretKey).build().parseClaimsJws(token).getBody();
    }

    public String extractUserIdFromResetToken(String bearerTokenHeader) {
        if (bearerTokenHeader == null || !bearerTokenHeader.startsWith("Bearer ")) return null;
        String token = bearerTokenHeader.substring(7);
        Claims claims = parseResetToken(token);
        Object id = claims.get("id");
        return id == null ? null : id.toString();
    }
}


