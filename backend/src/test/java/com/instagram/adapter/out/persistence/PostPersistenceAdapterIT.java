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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
public class PostPersistenceAdapterIT {
    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    UserJpaRepository userJpaRepository;

    PostPersistenceAdapter postPersistenceAdapter;
    UserJpaEntity testUser;

    @BeforeEach
    void setUp() {
        postPersistenceAdapter = new PostPersistenceAdapter(postJpaRepository);
        testUser = userJpaRepository.save(UserJpaEntity.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .role("USER")
                .build());
    }

    @AfterEach
    void tearDown() {
        postJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    @Test
    void save_success_returnsSavedPost() {
        // GIVEN
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .userId(testUser.getId())
                .caption("test caption")
                .build();

        // ACT
        Post savedPost = postPersistenceAdapter.save(post);

        // ASSERT
        assertNotNull(savedPost.getId());
        assertEquals(post.getCaption(), savedPost.getCaption());
    }

    @Test
    void findById_success_returnsUser() {
        // GIVEN
        var post = Post.builder()
                .id(UUID.randomUUID())
                .userId(testUser.getId())
                .caption("test caption")
                .build();

        // ACT
        Post savedPost = postPersistenceAdapter.save(post);
        Post foundPost = postPersistenceAdapter.findById(savedPost.getId()).get();

        assertEquals(savedPost.getId(), foundPost.getId());
        assertEquals(savedPost.getCaption(), foundPost.getCaption());
    }

    @Test
    void findByUserId_success_returnsUser() {
        // GIVEN
        var post = Post.builder()
                .id(UUID.randomUUID())
                .userId(testUser.getId())
                .caption("test caption")
                .build();

        // ACT
        Post savedPost = postPersistenceAdapter.save(post);
        Post foundPost = postPersistenceAdapter.findByUserId(savedPost.getUserId(), PageRequest.of(0, 10)).getContent()
                .get(0);

        assertEquals(savedPost.getId(), foundPost.getId());
        assertEquals(savedPost.getCaption(), foundPost.getCaption());
    }

}
