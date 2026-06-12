package com.instagram.infrastructure.security;

import com.instagram.domain.port.out.TokenPort;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

@Component
public class JwtTokenProvider implements TokenPort {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.rsa-private-key}")
    private String rsaPrivateKeyBase64;

    @Value("${app.jwt.rsa-public-key}")
    private String rsaPublicKeyBase64;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            byte[] privateBytes = Decoders.BASE64.decode(rsaPrivateKeyBase64.trim());
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

            byte[] publicBytes = Decoders.BASE64.decode(rsaPublicKeyBase64.trim());
            publicKey = kf.generatePublic(new X509EncodedKeySpec(publicBytes));

        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair for JWT signing", e);
        }
    }

    // generate access token
    @Override
    public String generateAccessToken(UUID userId, String role) {
        String accessToken = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return accessToken;
    }

    // generate refresh token
    @Override
    public String generateRefreshToken(UUID userId) {
        String refreshToken = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return refreshToken;
    }

    @Override
    public Optional<UUID> validateAccessToken(String token) {
        return parseSubject(token);
    }

    @Override
    public Optional<UUID> validateRefreshToken(String token) {
        return parseSubject(token);
    }

    private Optional<UUID> parseSubject(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("role", String.class);
    }

}