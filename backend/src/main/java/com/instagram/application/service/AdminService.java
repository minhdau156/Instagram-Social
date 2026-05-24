package com.instagram.application.service;

import com.instagram.domain.port.in.admin.SuspendUserUseCase;
import com.instagram.domain.port.in.admin.UnsuspendUserUseCase;
import com.instagram.domain.port.out.AuditLogRepository;
import com.instagram.domain.port.out.ModerationRepository;
import com.instagram.domain.port.out.UserRepository;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.domain.exception.ReportNotFoundException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.admin.AdminGetReportsUseCase;
import com.instagram.domain.port.in.admin.ReviewReportUseCase;

@Service
public class AdminService implements ReviewReportUseCase,
        SuspendUserUseCase,
        UnsuspendUserUseCase,
        AdminGetReportsUseCase {
    private final ModerationRepository moderationRepository;
    private final AuditLogRepository auditLogRepository;

    private final UserRepository userRepository;

    public AdminService(ModerationRepository moderationRepository, AuditLogRepository auditLogRepository,
            UserRepository userRepository) {
        this.moderationRepository = moderationRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<Report> getReports(AdminGetReportsUseCase.Query query) {
        return moderationRepository.findAllReports(query.status(), PageRequest.of(query.page(), query.size()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public User unsuspendUser(UnsuspendUserUseCase.Command command) {
        User targetUser = userRepository.findById(command.targetUserId())
                .orElseThrow(() -> UserNotFoundException.withId(command.targetUserId()));

        if (targetUser.getStatus() != UserStatus.SUSPENDED) {
            throw new IllegalStateException("User is not suspended");
        }

        User unsuspend = targetUser.withUnsuspend();
        User updatedUser = userRepository.save(unsuspend);
        auditLogRepository.log(command.adminId(),
                AuditLogRepository.USER_UNSUSPEND,
                "USER",
                targetUser.getId(),
                null,
                null);

        return updatedUser;

    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public User suspendUser(SuspendUserUseCase.Command command) {
        User targetUser = userRepository.findById(command.targetUserId())
                .orElseThrow(() -> UserNotFoundException.withId(command.targetUserId()));

        if (targetUser.getStatus() == UserStatus.SUSPENDED) {
            throw new IllegalStateException("User is already suspended");
        }

        User suspend = targetUser.withSuspend();
        User updatedUser = userRepository.save(suspend);
        auditLogRepository.log(command.adminId(),
                AuditLogRepository.USER_SUSPEND,
                "USER",
                targetUser.getId(),
                "{\"reason\": \"" + command.reason() + "\"}",
                null);

        return updatedUser;

    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Report reviewReport(ReviewReportUseCase.Command command) {
        Report report = moderationRepository.findReportById(command.reportId())
                .orElseThrow(() -> new ReportNotFoundException(command.reportId()));

        Report updatedReport = switch (command.action()) {
            case DISMISS -> report.withDismissed(command.adminId());
            case RESOLVE -> report.withResolved(command.adminId());
            case MARK_REVIEWED -> report.withReviewed(command.adminId());
        };

        Report savedReport = moderationRepository.saveReport(updatedReport);

        String auditAction = switch (command.action()) {
            case DISMISS -> AuditLogRepository.REPORT_DISMISS;
            case RESOLVE -> AuditLogRepository.REPORT_RESOLVE;
            case MARK_REVIEWED -> AuditLogRepository.REPORT_REVIEW;
        };
        auditLogRepository.log(command.adminId(), auditAction, "REPORT", command.reportId(), null, null);

        return savedReport;

    }

}
