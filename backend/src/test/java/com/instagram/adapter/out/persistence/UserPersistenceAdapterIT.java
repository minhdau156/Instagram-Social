package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.infrastructure.config.JpaConfig;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserRole;
import com.instagram.domain.model.UserStatus;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
public class UserPersistenceAdapterIT {
    @Autowired
    UserJpaRepository userJpaRepository;

    UserPersistenceAdapter userPersistenceAdapter;

    @BeforeEach
    void setUp() {
        userPersistenceAdapter = new UserPersistenceAdapter(userJpaRepository);
    }

    @AfterEach
    void tearDown() {
        userJpaRepository.deleteAll();
    }

    @Test
    void save_success_returnsSavedUser() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);

        assertNotNull(savedUser.getId());
        assertEquals(user.getUsername(), savedUser.getUsername());
        assertEquals(user.getEmail(), savedUser.getEmail());
        assertEquals(user.getFullName(), savedUser.getFullName());
        assertEquals(user.getStatus(), savedUser.getStatus());
        assertEquals(user.getPrivacyLevel(), savedUser.getPrivacyLevel());
    }

    @Test
    void findById_success_returnsUser() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);
        User foundUser = userPersistenceAdapter.findById(savedUser.getId()).get();

        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals(savedUser.getUsername(), foundUser.getUsername());
        assertEquals(savedUser.getEmail(), foundUser.getEmail());
        assertEquals(savedUser.getFullName(), foundUser.getFullName());
        assertEquals(savedUser.getStatus(), foundUser.getStatus());
        assertEquals(savedUser.getPrivacyLevel(), foundUser.getPrivacyLevel());
    }

    @Test
    void findByUsername_success_returnsUser() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);
        User foundUser = userPersistenceAdapter.findByUsername("testuser").get();

        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals(savedUser.getUsername(), foundUser.getUsername());
        assertEquals(savedUser.getEmail(), foundUser.getEmail());
        assertEquals(savedUser.getFullName(), foundUser.getFullName());
        assertEquals(savedUser.getStatus(), foundUser.getStatus());
        assertEquals(savedUser.getPrivacyLevel(), foundUser.getPrivacyLevel());
    }

    @Test
    void findByEmail_success_returnsUser() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);
        User foundUser = userPersistenceAdapter.findByEmail("[EMAIL_ADDRESS]").get();

        assertEquals(savedUser.getId(), foundUser.getId());
        assertEquals(savedUser.getUsername(), foundUser.getUsername());
        assertEquals(savedUser.getEmail(), foundUser.getEmail());
        assertEquals(savedUser.getFullName(), foundUser.getFullName());
        assertEquals(savedUser.getStatus(), foundUser.getStatus());
        assertEquals(savedUser.getPrivacyLevel(), foundUser.getPrivacyLevel());
    }

    @Test
    void existsByUsername_success_returnsTrue() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);
        boolean exists = userPersistenceAdapter.existsByUsername("testuser");

        assertEquals(true, exists);
    }

    @Test
    void existsByEmail_success_returnsTrue() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);
        boolean exists = userPersistenceAdapter.existsByEmail("[EMAIL_ADDRESS]");

        assertEquals(true, exists);
    }

    @Test
    void updateProfile_success_returnsUpdatedUser() {
        // GIVEN
        var user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("TestUser")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();

        // ACT
        User savedUser = userPersistenceAdapter.save(user);

        var updatedUser = User.builder()
                .id(savedUser.getId())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("hashedPassword")
                .fullName("NewFullName")
                .bio("NewBio")
                .websiteUrl("NewWebsite")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role(UserRole.USER)
                .build();
        User updatingUser = userPersistenceAdapter.save(updatedUser);

        assertEquals(savedUser.getUsername(), updatingUser.getUsername());
        assertEquals(savedUser.getEmail(), updatingUser.getEmail());
        assertEquals("NewFullName", updatingUser.getFullName());
        assertEquals("NewBio", updatingUser.getBio());
        assertEquals("NewWebsite", updatingUser.getWebsiteUrl());
        assertEquals(savedUser.getStatus(), updatingUser.getStatus());
        assertEquals(savedUser.getPrivacyLevel(), updatingUser.getPrivacyLevel());
    }
}
