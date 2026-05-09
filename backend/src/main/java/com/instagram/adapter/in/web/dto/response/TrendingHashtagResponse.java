package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.Hashtag;

public record TrendingHashtagResponse(
        String id,
        String name,
        int postCount) {
    public static TrendingHashtagResponse from(Hashtag hashtag) {
        return new TrendingHashtagResponse(
                hashtag.getId().toString(),
                hashtag.getName(),
                hashtag.getPostCount());
    }
}
