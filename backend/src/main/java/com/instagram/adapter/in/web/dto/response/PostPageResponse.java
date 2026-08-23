package com.instagram.adapter.in.web.dto.response;

import java.util.List;

public record PostPageResponse(
        List<PostResponse> content,
        int page,
        int size,
        boolean last) {

    /** {@code last} is inferred from a short page — no separate count query. */
    public static PostPageResponse of(List<PostResponse> content, int page, int size) {
        return new PostPageResponse(content, page, size, content.size() < size);
    }
}
