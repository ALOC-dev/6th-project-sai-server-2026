package com.aloc.sai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private SecretKey key;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationTime;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshAccessExpirationTime;

    @PostConstruct
    protected void init() {
        byte[] secretKeyBytes = Decoders.BASE64.decode(secretKey);
        key = Keys.hmacShaKeyFor(secretKeyBytes);
    }

    // 접근 토큰: API 요청 인증에 사용한다.
    public String createToken(Long userId) {
        return buildToken(userId, "access", accessExpirationTime);
    }

    // 재발급용 토큰: 접근 토큰 재발급에만 사용한다.
    public String createRefreshToken(Long userId) {
        return buildToken(userId, "refresh", refreshAccessExpirationTime);
    }

    private String buildToken(Long userId, String type, long expirationTime) {
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationTime))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);

        UserDetails userDetails = User.builder()
                .username(claims.getSubject())
                .password("")
                .authorities(Collections.emptyList())
                .build();

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public LocalDateTime getExpiresAt(String token) {
        return parseClaims(token).getExpiration().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
