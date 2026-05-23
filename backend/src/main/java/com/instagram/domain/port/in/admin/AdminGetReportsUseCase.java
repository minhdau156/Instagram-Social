package com.instagram.domain.port.in.admin;

import java.util.List;

import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportStatus;

public interface AdminGetReportsUseCase {
    List<Report> getReports(Query query);

    record Query(
            ReportStatus status,
            int page,
            int size) {
    }
}
