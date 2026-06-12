package com.instagram.infrastructure.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static String privateKeyB64;
    private static String publicKeyB64;

    private JwtTokenProvider tokenProvider;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        privateKeyB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        publicKeyB64  = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    }

    @BeforeEach
    void setUp() {
        tokenProvider = buildProvider(900_000L, 604_800_000L);
    }

    private static JwtTokenProvider buildProvider(long accessMs, long refreshMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "rsaPrivateKeyBase64",  privateKeyB64);
        ReflectionTestUtils.setField(p, "rsaPublicKeyBase64",   publicKeyB64);
        ReflectionTestUtils.setField(p, "accessTokenExpiryMs",  accessMs);
        ReflectionTestUtils.setField(p, "refreshTokenExpiryMs", refreshMs);
        p.init();
        return p;
    }

    @Test
    void generateAccessToken_returnsValidToken() {
        String token = tokenProvider.generateAccessToken(UUID.randomUUID(), "ROLE_USER");
        assertThat(token).isNotBlank();
    }

    @Test
    void validateAccessToken_returnsUserId_forValidToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, "ROLE_USER");

        Optional<UUID> result = tokenProvider.validateAccessToken(token);

        assertThat(result).isPresent().contains(userId);
    }

    @Test
    void validateAccessToken_returnsEmpty_forTamperedToken() {
        String token = tokenProvider.generateAccessToken(UUID.randomUUID(), "ROLE_USER");

        Optional<UUID> result = tokenProvider.validateAccessToken(token + "X");

        assertThat(result).isEmpty();
    }

    @Test
    void validateAccessToken_returnsEmpty_forCompletelyGarbageInput() {
        assertThat(tokenProvider.validateAccessToken("not.a.jwt")).isEmpty();
    }

    @Test
    void validateAccessToken_returnsEmpty_forExpiredToken() {
        JwtTokenProvider expiredProvider = buildProvider(0L, 0L);
        String expiredToken = expiredProvider.generateAccessToken(UUID.randomUUID(), "ROLE_USER");

        assertThat(tokenProvider.validateAccessToken(expiredToken)).isEmpty();
    }

    @Test
    void generateRefreshToken_returnsValidToken() {
        String token = tokenProvider.generateRefreshToken(UUID.randomUUID());
        assertThat(token).isNotBlank();
    }

    @Test
    void validateRefreshToken_returnsUserId_forValidToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateRefreshToken(userId);

        Optional<UUID> result = tokenProvider.validateRefreshToken(token);

        assertThat(result).isPresent().contains(userId);
    }
}
