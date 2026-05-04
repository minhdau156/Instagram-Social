package com.instagram.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedPostTest {

    @Test
    void of_createsSavedPost_withCorrectFields() {
        // Arrange
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Act
        SavedPost savedPost = SavedPost.of(postId, userId);

        // Assert
        assertThat(savedPost).isNotNull();
        assertThat(savedPost.getId()).isNotNull();
        assertThat(savedPost.getPostId()).isEqualTo(postId);
        assertThat(savedPost.getUserId()).isEqualTo(userId);
        assertThat(savedPost.getSavedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void builder_throwsException_whenPostIdIsNull() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> SavedPost.of(null, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postId cannot be null");
    }

    @Test
    void builder_throwsException_whenUserIdIsNull() {
        // Arrange
        UUID postId = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> SavedPost.of(postId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId cannot be null");
    }
}
