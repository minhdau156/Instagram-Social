package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.SearchHistory;

public record SearchHistoryResponse(
        String id,
        String query,
        String searchedAt) {

    public static SearchHistoryResponse from(SearchHistory searchHistory) {
        return new SearchHistoryResponse(
                searchHistory.getId().toString(),
                searchHistory.getQuery(),
                searchHistory.getSearchedAt().toString());
    }
}
