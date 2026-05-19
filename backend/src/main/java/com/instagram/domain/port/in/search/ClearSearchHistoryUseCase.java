package com.instagram.domain.port.in.search;

import java.util.UUID;

public interface ClearSearchHistoryUseCase {
    void clearSearchHistory(Command command);

    record Command(UUID userId) {
    }
}
