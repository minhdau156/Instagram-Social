package com.instagram.domain.port.out;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.SearchHistory;

public interface SearchHistoryRepository {
    SearchHistory save(SearchHistory searchHistory);

    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable);

    void deleteByUserId(UUID userId);
}
