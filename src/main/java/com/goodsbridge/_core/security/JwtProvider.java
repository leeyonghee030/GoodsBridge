
package com.goodsbridge._core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessExpiration;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // Access Token 생성
    public String createAccessToken(Long userId, String role) {
        return createToken(userId, role, "ACCESS", accessExpiration);
    }

    // Refresh Token 생성
    public String createRefreshToken(Long userId) {
        return createToken(userId, null, "REFRESH", refreshExpiration);
    }

    // 공통 토큰 생성
    private String createToken(
        Long userId,
        String role,
        String type,
        long expiration
    ) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("type", type)
            .issuedAt(now)
            .expiration(expiry);

        if (role != null) {
            builder.claim("role", role);
        }

        return builder
            .signWith(secretKey)
            .compact();
    }

    // 토큰 검증 및 Claims 반환
    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    // Access Token 검증
    public Claims validateAccessToken(String token) {
        Claims claims = validateToken(token);

        if (!"ACCESS".equals(claims.get("type", String.class))) {
            throw new JwtException("Access Token이 아닙니다.");
        }

        return claims;
    }

    // Refresh Token 검증
    public Claims validateRefreshToken(String token) {
        Claims claims = validateToken(token);

        if (!"REFRESH".equals(claims.get("type", String.class))) {
            throw new JwtException("Refresh Token이 아닙니다.");
        }

        return claims;
    }

    // 사용자 ID 추출
    public Long getUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    // 사용자 권한 추출
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
