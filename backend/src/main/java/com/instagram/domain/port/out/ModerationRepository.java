package com.instagram.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.UserBlock;
import org.springframework.data.domain.Pageable;

public interface ModerationRepository {
    // REPORT
    Report saveReport(Report report);

    Optional<Report> findReportById(UUID reportId);

    List<Report> findAllReports(ReportStatus status, Pageable pageable);

    List<Report> findPendingReports(Pageable pageable);

    long countByStatus(ReportStatus status);

    boolean existsByReporterIdAndEntityId(UUID reporterId, UUID entityId);

    // BLOCK
    UserBlock saveBlock(UserBlock block);

    void deleteBlock(UUID blockerId, UUID blockedId);

    boolean isBlocked(UUID blockerId, UUID blockedId);

    boolean isEitherBlocked(UUID userA, UUID userB);

    List<UserBlock> findBlocksByBlockerId(UUID blockerId, Pageable pageable);

    List<UUID> findBlockedUserIdsByBlockerId(UUID blockerId);

    List<UUID> findBlockerIdsByBlockedId(UUID blockedId);
}
