package com.instagram.adapter.out.persistence.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostHashtagId {
    @Column(name = "post_id")
    private UUID postId;
    @Column(name = "hashtag_id")
    private UUID hashtagId;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PostHashtagId that = (PostHashtagId) o;
        return Objects.equals(postId, that.postId) &&
                Objects.equals(hashtagId, that.hashtagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, hashtagId);
    }
}
