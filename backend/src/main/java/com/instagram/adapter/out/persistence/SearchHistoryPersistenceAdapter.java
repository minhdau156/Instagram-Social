package com.instagram.adapter.out.persistence;

import com.instagram.adapter.out.persistence.entity.SearchHistoryJpaEntity;
import com.instagram.adapter.out.persistence.repository.SearchHistoryJpaRepository;
import com.instagram.domain.model.SearchHistory;
import com.instagram.domain.port.out.SearchHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SearchHistoryPersistenceAdapter implements SearchHistoryRepository {

    private final SearchHistoryJpaRepository jpaRepo;

    public SearchHistoryPersistenceAdapter(SearchHistoryJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    // ── SearchHistoryRepository (output port) ─────────────────────────────── //

    @Override
    public SearchHistory save(SearchHistory searchHistory) {
        SearchHistoryJpaEntity entity = toEntity(searchHistory);
        SearchHistoryJpaEntity saved = jpaRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<SearchHistory> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable) {
        return jpaRepo.findByUserIdOrderBySearchedAtDesc(userId, pageable)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRepo.deleteByUserId(userId);
    }

    // ── Mapping ──────────────────────────────────────────────────────────── //

    private SearchHistoryJpaEntity toEntity(SearchHistory s) {
        return SearchHistoryJpaEntity.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .query(s.getQuery())
                .searchedAt(s.getSearchedAt())
                .build();
    }

    private SearchHistory toDomain(SearchHistoryJpaEntity e) {
        return SearchHistory.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .query(e.getQuery())
                .searchedAt(e.getSearchedAt())
                .build();
    }
}
