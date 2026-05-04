package com.instagram.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.instagram.domain.exception.AlreadySavedException;
import com.instagram.domain.exception.NotSavedException;
import com.instagram.domain.model.SavedPost;
import com.instagram.domain.port.in.save.GetSavedPostsUseCase;
import com.instagram.domain.port.in.save.SavePostUseCase;
import com.instagram.domain.port.in.save.UnsavePostUseCase;
import com.instagram.domain.port.out.SavedPostRepository;

@ExtendWith(MockitoExtension.class)
class SavedPostServiceTest {

    @Mock
    private SavedPostRepository savedPostRepository;

    @InjectMocks
    private SavedPostService savedPostService;

    private UUID postId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ── SavePostUseCase ───────────────────────────────────────────────────────

    @Test
    void save_notYetSaved_persistsSavedPost() {
        SavedPost expectedSavedPost = SavedPost.of(postId, userId);
        when(savedPostRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(savedPostRepository.save(any(SavedPost.class))).thenReturn(expectedSavedPost);

        SavedPost result = savedPostService.save(new SavePostUseCase.Command(postId, userId));

        assertThat(result.getPostId()).isEqualTo(postId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getSavedAt()).isNotNull();
        verify(savedPostRepository).save(any(SavedPost.class));
    }

    @Test
    void save_alreadySaved_throwsAlreadySavedException() {
        when(savedPostRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);

        assertThrows(AlreadySavedException.class,
                () -> savedPostService.save(new SavePostUseCase.Command(postId, userId)));

        verify(savedPostRepository, never()).save(any());
    }

    // ── UnsavePostUseCase ─────────────────────────────────────────────────────

    @Test
    void unsave_saved_deletesRecord() {
        when(savedPostRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(true);

        savedPostService.unsave(new UnsavePostUseCase.Command(postId, userId));

        verify(savedPostRepository).delete(postId, userId);
    }

    @Test
    void unsave_notSaved_throwsNotSavedException() {
        when(savedPostRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);

        assertThrows(NotSavedException.class,
                () -> savedPostService.unsave(new UnsavePostUseCase.Command(postId, userId)));

        verify(savedPostRepository, never()).delete(any(), any());
    }

    // ── GetSavedPostsUseCase ─────────────────────────────────────────────────

    @Test
    void getSavedPosts_returnsPagedResults() {
        SavedPost savedPost = SavedPost.of(postId, userId);
        Page<SavedPost> page = new PageImpl<>(List.of(savedPost));
        when(savedPostRepository.findByUserId(any(UUID.class), any(Pageable.class))).thenReturn(page);

        List<SavedPost> result = savedPostService.getSavedPosts(
                new GetSavedPostsUseCase.Query(userId, 0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPostId()).isEqualTo(postId);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        verify(savedPostRepository).findByUserId(any(UUID.class), any(Pageable.class));
    }
}
