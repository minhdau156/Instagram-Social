package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class SearchHistory {
    private UUID id;
    private UUID userId;
    private String query;
    private OffsetDateTime searchedAt;

    private SearchHistory() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getQuery() {
        return query;
    }

    public OffsetDateTime getSearchedAt() {
        return searchedAt;
    }

    public static final class Builder {
        private final SearchHistory searchHistory = new SearchHistory();

        private Builder() {
        }

        public Builder id(UUID id) {
            searchHistory.id = id;
            return this;
        }

        public Builder userId(UUID userId) {
            searchHistory.userId = userId;
            return this;
        }

        public Builder query(String query) {
            searchHistory.query = query;
            return this;
        }

        public Builder searchedAt(OffsetDateTime searchedAt) {
            searchHistory.searchedAt = searchedAt;
            return this;
        }

        public SearchHistory build() {
            if (searchHistory.id == null) {
                throw new IllegalArgumentException("id cannot be null");
            }
            if (searchHistory.userId == null) {
                throw new IllegalArgumentException("userId cannot be null");
            }
            if (searchHistory.query == null) {
                throw new IllegalArgumentException("query cannot be null");
            }
            if (searchHistory.searchedAt == null) {
                throw new IllegalArgumentException("searchedAt cannot be null");
            }
            return searchHistory;
        }
    }

}
