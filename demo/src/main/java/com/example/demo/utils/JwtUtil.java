package com.example.demo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;
    /**
     * 获取用于签名的密钥对象
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    /**
     * 生成 JWT token（包含用户ID和用户名）
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .claim("userId", userId)
                .subject(username)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSigningKey())
                .compact();
    }
    /**
     * 从 token 中解析所有声明
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    /**
     * 从 token 中获取用户 ID
     */
    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", Long.class);
    }
    /**
     * 从 token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }
    /**
     * 获取 token 的过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return parseClaims(token).getExpiration();
    }
    /**
     * 检查 token 是否已过期
     */
    public Boolean isTokenExpired(String token) {
        return getExpirationDateFromToken(token).before(new Date());
    }
    /**
     * 验证 token 是否有效（仅签名和过期时间）
     */
    public Boolean validateToken(String token) {
        try {
            parseClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.warn("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }
    /**
     * 匹配 token 和 username
     */
    public Boolean validateToken(String token, String username) {
        String tokenUsername = getUsernameFromToken(token);
        return (tokenUsername.equals(username) && !isTokenExpired(token));
    }
}