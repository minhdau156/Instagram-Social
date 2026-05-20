package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.Hashtag;

public record HashtagSearchResponse(
        String id,
        String name,
        long postCount) {
    public static HashtagSearchResponse from(Hashtag hashtag) {
        return new HashtagSearchResponse(
                hashtag.getId().toString(),
                hashtag.getName(),
                hashtag.getPostCount());
    }
}
