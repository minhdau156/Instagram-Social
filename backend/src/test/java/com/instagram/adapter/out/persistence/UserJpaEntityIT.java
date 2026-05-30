package com.instagram.adapter.out.persistence;

import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(com.instagram.infrastructure.config.JpaConfig.class)
class UserJpaEntityIT {

    @Autowired
    private TestEntityManager entityManager;

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserJpaEntity minimalUser() {
        return UserJpaEntity.builder()
                .username("john_doe")
                .email("john@example.com")
                .fullName("John Doe")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();
    }

    // ── persist & find ───────────────────────────────────────────────────────

    @Test
    void shouldPersistAndRetrieveUserWithRequiredFields() {
        UserJpaEntity saved = entityManager.persistFlushFind(minimalUser());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("john_doe");
        assertThat(saved.getEmail()).isEqualTo("john@example.com");
        assertThat(saved.getFullName()).isEqualTo("John Doe");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getPrivacyLevel()).isEqualTo(PrivacyLevel.PUBLIC);
        assertThat(saved.isVerified()).isFalse();
    }

    @Test
    void shouldInheritAuditFieldsFromBaseJpaEntity() {
        UserJpaEntity saved = entityManager.persistFlushFind(minimalUser());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    // ── nullable fields ───────────────────────────────────────────────────────

    @Test
    void shouldAllowNullableFieldsToBeNull() {
        UserJpaEntity user = minimalUser();
        // bio, passwordHash, phoneNumber, profilePictureUrl, websiteUrl, lastLoginAt
        // all nullable
        assertThat(user.getBio()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getProfilePictureUrl()).isNull();
        assertThat(user.getWebsiteUrl()).isNull();
        assertThat(user.getLastLoginAt()).isNull();

        UserJpaEntity saved = entityManager.persistFlushFind(user);
        assertThat(saved.getBio()).isNull();
    }

    @Test
    void shouldPersistAllOptionalFields() {
        UserJpaEntity user = UserJpaEntity.builder()
                .username("jane_doe")
                .email("jane@example.com")
                .phoneNumber("+84912345678")
                .passwordHash("$2a$10$hashedpassword")
                .fullName("Jane Doe")
                .bio("Living my best life 🌟")
                .profilePictureUrl("https://cdn.example.com/jane.jpg")
                .websiteUrl("https://janedoe.com")
                .status(UserStatus.PENDING_VERIFICATION)
                .privacyLevel(PrivacyLevel.FOLLOWERS_ONLY)
                .isVerified(true)
                .build();

        UserJpaEntity saved = entityManager.persistFlushFind(user);

        assertThat(saved.getPhoneNumber()).isEqualTo("+84912345678");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$hashedpassword");
        assertThat(saved.getBio()).isEqualTo("Living my best life 🌟");
        assertThat(saved.getProfilePictureUrl()).isEqualTo("https://cdn.example.com/jane.jpg");
        assertThat(saved.getWebsiteUrl()).isEqualTo("https://janedoe.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getPrivacyLevel()).isEqualTo(PrivacyLevel.FOLLOWERS_ONLY);
        assertThat(saved.isVerified()).isTrue();
    }

    // ── enum values ──────────────────────────────────────────────────────────

    @Test
    void shouldPersistAndReadAllUserStatusValues() {
        for (UserStatus status : UserStatus.values()) {
            // Each user needs a unique username to avoid unique constraint violation
            UserJpaEntity user = UserJpaEntity.builder()
                    .username("user_" + status.name().toLowerCase())
                    .email(status.name().toLowerCase() + "@example.com")
                    .fullName("Test " + status.name())
                    .status(status)
                    .privacyLevel(PrivacyLevel.PUBLIC)
                    .isVerified(false)
                    .build();

            UserJpaEntity saved = entityManager.persistFlushFind(user);
            assertThat(saved.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void shouldPersistAndReadAllPrivacyLevelValues() {
        for (PrivacyLevel level : PrivacyLevel.values()) {
            UserJpaEntity user = UserJpaEntity.builder()
                    .username("priv_" + level.name().toLowerCase())
                    .email(level.name().toLowerCase() + "@privacy.com")
                    .fullName("Privacy " + level.name())
                    .status(UserStatus.ACTIVE)
                    .privacyLevel(level)
                    .isVerified(false)
                    .build();

            UserJpaEntity saved = entityManager.persistFlushFind(user);
            assertThat(saved.getPrivacyLevel()).isEqualTo(level);
        }
    }

    // ── identity ─────────────────────────────────────────────────────────────

    @Test
    void shouldGenerateUniqueIdPerUser() {
        UserJpaEntity user1 = minimalUser();
        UserJpaEntity user2 = UserJpaEntity.builder()
                .username("other_user")
                .email("other@example.com")
                .fullName("Other User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();

        UUID id1 = entityManager.persistFlushFind(user1).getId();
        UUID id2 = entityManager.persistFlushFind(user2).getId();

        assertThat(id1).isNotNull().isNotEqualTo(id2);
    }
}
