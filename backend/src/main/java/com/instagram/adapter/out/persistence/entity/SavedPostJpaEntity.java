package com.instagram.adapter.out.persistence.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;

@Entity
@Table(name = "saved_posts")
@Builder
public class SavedPostJpaEntity {

    @EmbeddedId
    private SavePostId id;

    @Column(name = "saved_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant savedAt;

    public SavedPostJpaEntity(SavePostId id, Instant savedAt) {
        this.id = id;
        this.savedAt = savedAt;
    }

    public SavedPostJpaEntity() {
    }

    public SavePostId getId() {
        return id;
    }

    public void setId(SavePostId id) {
        this.id = id;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }

}
