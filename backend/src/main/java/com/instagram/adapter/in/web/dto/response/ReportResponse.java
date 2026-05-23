package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.Report;
import com.instagram.domain.model.User;

public record ReportResponse(
        String id,
        String reporterId,
        String reporterUsername,
        String entityType,
        String entityId,
        String reason,
        String details,
        String status,
        String reviewedById,
        String reviewedAt,
        String createdAt) {

    public static ReportResponse from(Report report, User reporter) {
        return new ReportResponse(
                report.getId().toString(),
                report.getReporterId().toString(),
                reporter.getUsername(),
                report.getEntityType().name(),
                report.getEntityId().toString(),
                report.getReason(),
                report.getDetails(),
                report.getStatus().name(),
                report.getReviewedById() != null ? report.getReviewedById().toString() : null,
                report.getReviewedAt() != null ? report.getReviewedAt().toString() : null,
                report.getCreatedAt().toString());
    }
}
