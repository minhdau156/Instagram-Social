package com.instagram.domain.port.in.search;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.SearchHistory;

public interface GetSearchHistoryUseCase {
    List<SearchHistory> getSearchHistory(Query query);

    record Query(UUID userId, int limit) {
    }
}
