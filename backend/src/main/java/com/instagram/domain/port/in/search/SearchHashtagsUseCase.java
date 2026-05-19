package com.instagram.domain.port.in.search;

import java.util.List;

import com.instagram.domain.model.Hashtag;

public interface SearchHashtagsUseCase {
    List<Hashtag> searchHashtags(Query query);

    record Query(String q, int page, int size) {
    }
}
