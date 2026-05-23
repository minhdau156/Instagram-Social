package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Report {
    private UUID id;
    private UUID reporterId;
    private ReportEntityType entityType;
    private UUID entityId;
    private String reason;
    private String details;
    private ReportStatus status;
    private UUID reviewedById;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;

    private Report() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Report report = new Report();

        public Builder id(UUID id) {
            report.id = id;
            return this;
        }

        public Builder reporterId(UUID reporterId) {
            report.reporterId = reporterId;
            return this;
        }

        public Builder entityType(ReportEntityType entityType) {
            report.entityType = entityType;
            return this;
        }

        public Builder entityId(UUID entityId) {
            report.entityId = entityId;
            return this;
        }

        public Builder reason(String reason) {
            report.reason = reason;
            return this;
        }

        public Builder details(String details) {
            report.details = details;
            return this;
        }

        public Builder status(ReportStatus status) {
            report.status = status;
            return this;
        }

        public Builder reviewedById(UUID reviewedById) {
            report.reviewedById = reviewedById;
            return this;
        }

        public Builder reviewedAt(OffsetDateTime reviewedAt) {
            report.reviewedAt = reviewedAt;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            report.createdAt = createdAt;
            return this;
        }

        public Report build() {
            if (report.status == null) {
                report.status = ReportStatus.PENDING;
            }
            if (report.reporterId == null || report.entityType == null || report.entityId == null
                    || report.reason == null || report.createdAt == null) {
                throw new IllegalStateException("Missing required fields");
            }
            return report;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public ReportEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public UUID getReviewedById() {
        return reviewedById;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    private Builder copy() {
        return new Builder().id(id).reporterId(reporterId).entityType(entityType).entityId(entityId)
                .reason(reason).details(details).status(status).reviewedById(reviewedById).reviewedAt(reviewedAt)
                .createdAt(createdAt);
    }

    public Report withResolved(UUID reviewerId) {
        if (this.status == ReportStatus.RESOLVED || this.status == ReportStatus.DISMISSED) {
            throw new IllegalStateException("Cannot transition from terminal state: " + this.status);
        }
        return this.copy()
                .status(ReportStatus.RESOLVED)
                .reviewedById(reviewerId)
                .reviewedAt(OffsetDateTime.now())
                .build();
    }

    public Report withDismissed(UUID reviewerId) {
        if (this.status == ReportStatus.RESOLVED || this.status == ReportStatus.DISMISSED) {
            throw new IllegalStateException("Cannot transition from terminal state: " + this.status);
        }
        return this.copy()
                .status(ReportStatus.DISMISSED)
                .reviewedById(reviewerId)
                .reviewedAt(OffsetDateTime.now())
                .build();
    }

    public Report withReviewed(UUID reviewerId) {
        if (this.status == ReportStatus.RESOLVED || this.status == ReportStatus.DISMISSED) {
            throw new IllegalStateException("Cannot transition from terminal state: " + this.status);
        }
        return this.copy()
                .status(ReportStatus.REVIEWED)
                .reviewedById(reviewerId)
                .reviewedAt(OffsetDateTime.now())
                .build();
    }
}
