package com.instagram.domain.port.in.moderation;

import java.util.UUID;

import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;

public interface ReportContentUseCase {
    Report reportContent(Command command);

    record Command(
            UUID reporterId,
            ReportEntityType entityType,
            UUID entityId,
            String reason,
            String details) {
    }
}
