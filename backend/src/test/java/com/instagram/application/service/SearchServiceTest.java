package com.instagram.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.SearchHistory;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.search.ClearSearchHistoryUseCase;
import com.instagram.domain.port.in.search.GetPostsByHashtagUseCase;
import com.instagram.domain.port.in.search.GetSearchHistoryUseCase;
import com.instagram.domain.port.in.search.SearchHashtagsUseCase;
import com.instagram.domain.port.in.search.SearchPostsUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.out.SearchHistoryRepository;
import com.instagram.domain.port.out.SearchRepository;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchRepository searchRepository;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @InjectMocks
    private SearchService searchService;

    // ── searchUsers ──────────────────────────────────────────────────────── //

    @Test
    void searchUsers_blankQuery_returnsEmptyWithNoDbCall() {
        List<User> result = searchService.searchUsers(
                new SearchUsersUseCase.Query("  ", UUID.randomUUID(), 0, 20));

        assertThat(result).isEmpty();
        verify(searchRepository, never()).searchUsers(any(), any(), any());
        verify(searchHistoryRepository, never()).save(any());
    }

    @Test
    void searchUsers_nullQuery_returnsEmptyWithNoDbCall() {
        List<User> result = searchService.searchUsers(
                new SearchUsersUseCase.Query(null, UUID.randomUUID(), 0, 20));

        assertThat(result).isEmpty();
        verify(searchRepository, never()).searchUsers(any(), any(), any());
    }

    @Test
    void searchUsers_nonBlankQuery_delegatesWithCorrectPageRequest() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(UUID.randomUUID()).username("john").build();
        when(searchRepository.searchUsers(eq(userId), eq("john"), eq(PageRequest.of(0, 20)))).thenReturn(List.of(user));

        List<User> result = searchService.searchUsers(new SearchUsersUseCase.Query("john", userId, 0, 20));

        assertThat(result).hasSize(1);
        verify(searchRepository).searchUsers(userId, "john", PageRequest.of(0, 20));
    }

    @Test
    void searchUsers_nonBlankQuery_savesHistoryWithCorrectUserIdAndQuery() {
        UUID userId = UUID.randomUUID();
        when(searchRepository.searchUsers(any(), any(), any())).thenReturn(List.of());
        ArgumentCaptor<SearchHistory> captor = ArgumentCaptor.forClass(SearchHistory.class);

        searchService.searchUsers(new SearchUsersUseCase.Query("travel", userId, 0, 20));

        verify(searchHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getQuery()).isEqualTo("travel");
        assertThat(captor.getValue().getSearchedAt()).isNotNull();
        assertThat(captor.getValue().getId()).isNotNull();
    }

    // ── searchHashtags ───────────────────────────────────────────────────── //

    @Test
    void searchHashtags_blankQuery_returnsEmptyWithNoDbCall() {
        List<Hashtag> result = searchService.searchHashtags(
                new SearchHashtagsUseCase.Query("", 0, 20));

        assertThat(result).isEmpty();
        verify(searchRepository, never()).searchHashtags(any(), any());
    }

    @Test
    void searchHashtags_nonBlankQuery_delegatesToRepositoryWithoutSavingHistory() {
        Hashtag hashtag = Hashtag.builder().id(UUID.randomUUID()).name("travel").build();
        when(searchRepository.searchHashtags(eq("travel"), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(hashtag));

        List<Hashtag> result = searchService.searchHashtags(
                new SearchHashtagsUseCase.Query("travel", 0, 20));

        assertThat(result).hasSize(1);
        verify(searchRepository).searchHashtags("travel", PageRequest.of(0, 20));
        verify(searchHistoryRepository, never()).save(any());
    }

    // ── searchPosts ──────────────────────────────────────────────────────── //

    @Test
    void searchPosts_blankQuery_returnsEmptyWithNoDbCallAndNoHistorySave() {
        List<Post> result = searchService.searchPosts(
                new SearchPostsUseCase.Query(" ", UUID.randomUUID(), 0, 20));

        assertThat(result).isEmpty();
        verify(searchRepository, never()).searchPosts(any(), any(), any());
        verify(searchHistoryRepository, never()).save(any());
    }

    @Test
    void searchPosts_nonBlankQuery_delegatesAndSavesHistory() {
        UUID userId = UUID.randomUUID();
        Post post = Post.builder().id(UUID.randomUUID()).caption("Sunset view").build();
        when(searchRepository.searchPosts(eq(userId), eq("sunset"), any())).thenReturn(List.of(post));
        ArgumentCaptor<SearchHistory> captor = ArgumentCaptor.forClass(SearchHistory.class);

        List<Post> result = searchService.searchPosts(
                new SearchPostsUseCase.Query("sunset", userId, 0, 20));

        assertThat(result).hasSize(1);
        verify(searchHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getQuery()).isEqualTo("sunset");
    }

    // ── getPostsByHashtag ────────────────────────────────────────────────── //

    @Test
    void getPostsByHashtag_blankName_returnsEmptyWithNoDbCall() {
        List<Post> result = searchService.getPostsByHashtag(
                new GetPostsByHashtagUseCase.Query(" ", UUID.randomUUID(), 0, 20));

        assertThat(result).isEmpty();
        verify(searchRepository, never()).findPostsByHashtag(any(), any(), any());
    }

    @Test
    void getPostsByHashtag_validName_delegatesWithCorrectArgsAndNoHistorySave() {
        Post post = Post.builder().id(UUID.randomUUID()).build();
        when(searchRepository.findPostsByHashtag(any(UUID.class), eq("travel"), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(post));

        List<Post> result = searchService.getPostsByHashtag(
                new GetPostsByHashtagUseCase.Query("travel", UUID.randomUUID(), 0, 20));

        assertThat(result).hasSize(1);
        verify(searchRepository).findPostsByHashtag(any(UUID.class), eq("travel"), eq(PageRequest.of(0, 20)));
        verify(searchHistoryRepository, never()).save(any());
    }

    // ── getSearchHistory ─────────────────────────────────────────────────── //

    @Test
    void getSearchHistory_delegatesWithCorrectUserIdAndLimit() {
        UUID userId = UUID.randomUUID();
        when(searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(eq(userId), any()))
                .thenReturn(List.of());

        searchService.getSearchHistory(new GetSearchHistoryUseCase.Query(userId, 10));

        verify(searchHistoryRepository).findByUserIdOrderBySearchedAtDesc(userId, PageRequest.ofSize(10));
    }

    // ── clearSearchHistory ───────────────────────────────────────────────── //

    @Test
    void clearSearchHistory_delegatesDeleteByUserIdWithCorrectUserId() {
        UUID userId = UUID.randomUUID();

        searchService.clearSearchHistory(new ClearSearchHistoryUseCase.Command(userId));

        verify(searchHistoryRepository).deleteByUserId(userId);
    }
}
