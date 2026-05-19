package com.instagram.application.service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class SearchService
        implements SearchUsersUseCase, SearchHashtagsUseCase, SearchPostsUseCase,
        GetPostsByHashtagUseCase, GetSearchHistoryUseCase, ClearSearchHistoryUseCase {

    private final SearchRepository searchRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    public SearchService(SearchRepository searchRepository, SearchHistoryRepository searchHistoryRepository) {
        this.searchRepository = searchRepository;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    @Override
    public List<User> searchUsers(SearchUsersUseCase.Query query) {
        if (query.q() == null || query.q().isBlank()) {
            return Collections.emptyList();
        }
        List<User> results = searchRepository.searchUsers(query.q(), PageRequest.of(query.page(), query.size()));
        saveHistoryAsync(query.currentUserId(), query.q());
        return results;
    }

    @Override
    public List<Hashtag> searchHashtags(SearchHashtagsUseCase.Query query) {
        if (query.q() == null || query.q().isBlank()) {
            return Collections.emptyList();
        }
        return searchRepository.searchHashtags(query.q(), PageRequest.of(query.page(), query.size()));
    }

    @Override
    public List<Post> searchPosts(SearchPostsUseCase.Query query) {
        if (query.q() == null || query.q().isBlank()) {
            return Collections.emptyList();
        }
        List<Post> results = searchRepository.searchPosts(query.q(), PageRequest.of(query.page(), query.size()));
        saveHistoryAsync(query.currentUserId(), query.q());
        return results;
    }

    @Override
    public List<Post> getPostsByHashtag(GetPostsByHashtagUseCase.Query query) {
        if (query.hashtagName() == null || query.hashtagName().isBlank()) {
            return Collections.emptyList();
        }
        return searchRepository.findPostsByHashtag(query.hashtagName(), PageRequest.of(query.page(), query.size()));
    }

    @Override
    public List<SearchHistory> getSearchHistory(GetSearchHistoryUseCase.Query query) {
        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(
                query.userId(), PageRequest.ofSize(query.limit()));
    }

    @Override
    @Transactional
    public void clearSearchHistory(ClearSearchHistoryUseCase.Command command) {
        searchHistoryRepository.deleteByUserId(command.userId());
    }

    @Async
    private void saveHistoryAsync(UUID userId, String q) {
        SearchHistory history = SearchHistory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .query(q)
                .searchedAt(OffsetDateTime.now())
                .build();
        searchHistoryRepository.save(history);
    }
}
