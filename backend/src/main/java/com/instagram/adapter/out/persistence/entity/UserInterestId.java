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
@AllArgsConstructor
@NoArgsConstructor
public class UserInterestId {
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "hashtag_id")
    private UUID hashtagId;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserInterestId that = (UserInterestId) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(hashtagId, that.hashtagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, hashtagId);
    }
}
