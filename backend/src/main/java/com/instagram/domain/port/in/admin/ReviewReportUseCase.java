package com.instagram.domain.port.in.admin;

import java.util.UUID;

import com.instagram.domain.model.Report;

public interface ReviewReportUseCase {
    Report reviewReport(Command command);

    record Command(
            UUID adminId,
            UUID reportId,
            ReviewAction action) {
    }

    enum ReviewAction {
        RESOLVE,
        DISMISS,
        MARK_REVIEWED
    }
}
