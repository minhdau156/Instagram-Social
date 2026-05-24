package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.exception.ReportNotFoundException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.admin.AdminGetReportsUseCase;
import com.instagram.domain.port.in.admin.ReviewReportUseCase;
import com.instagram.domain.port.in.admin.SuspendUserUseCase;
import com.instagram.domain.port.in.admin.UnsuspendUserUseCase;
import com.instagram.domain.port.out.AuditLogRepository;
import com.instagram.domain.port.out.ModerationRepository;
import com.instagram.domain.port.out.UserRepository;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {
    @Mock
    private ModerationRepository moderationRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminService adminService;

    User user;
    Report report;
    UUID userId;
    UUID reportId;
    UUID adminId;

    @BeforeEach
    void setup() {
        this.adminId = UUID.randomUUID();
        this.userId = UUID.randomUUID();
        this.reportId = UUID.randomUUID();
        this.user = User.builder()
                .id(this.userId)
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("passwordHash")
                .fullName("Full Name")
                .bio("Bio")
                .build();
        this.report = Report.builder()
                .id(this.reportId)
                .reporterId(UUID.randomUUID())
                .entityId(UUID.randomUUID())
                .entityType(ReportEntityType.POST)
                .reason("Test reason")
                .details("Test details")
                .status(ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void reviewReport_whenValid_saveReportAndLog() {
        when(moderationRepository.findReportById(this.reportId)).thenReturn(Optional.of(this.report));

        this.adminService
                .reviewReport(new ReviewReportUseCase.Command(this.adminId, this.reportId,
                        ReviewReportUseCase.ReviewAction.RESOLVE));

        verify(moderationRepository).saveReport(any(Report.class));
        verify(auditLogRepository).log(
                this.adminId,
                "report_resolve",
                "REPORT",
                this.reportId,
                null,
                null);

    }

    @Test
    void reviewReport_whenReportNotFound_throwReportNotFoundException() {
        when(moderationRepository.findReportById(this.reportId))
                .thenReturn(Optional.empty());

        assertThrows(ReportNotFoundException.class, () -> this.adminService.reviewReport(
                new ReviewReportUseCase.Command(this.adminId, this.reportId,
                        ReviewReportUseCase.ReviewAction.RESOLVE)));
    }

    @Test
    void reviewReport_whenDismiss_saveReportAndLog() {
        when(moderationRepository.findReportById(this.reportId)).thenReturn(Optional.of(this.report));

        this.adminService
                .reviewReport(new ReviewReportUseCase.Command(this.adminId, this.reportId,
                        ReviewReportUseCase.ReviewAction.DISMISS));

        verify(moderationRepository).saveReport(any(Report.class));
        verify(auditLogRepository).log(
                this.adminId,
                "report_dismiss",
                "REPORT",
                this.reportId,
                null,
                null);
    }

    @Test
    void reviewReport_whenAlreadyResolved_throwIllegalStateException() {
        Report resolvedReport = Report.builder()
                .id(this.reportId)
                .reporterId(UUID.randomUUID())
                .entityId(UUID.randomUUID())
                .entityType(ReportEntityType.POST)
                .reason("Test reason")
                .status(ReportStatus.RESOLVED)
                .reviewedById(UUID.randomUUID())
                .reviewedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
        when(moderationRepository.findReportById(this.reportId)).thenReturn(Optional.of(resolvedReport));

        assertThrows(IllegalStateException.class, () -> this.adminService.reviewReport(
                new ReviewReportUseCase.Command(this.adminId, this.reportId,
                        ReviewReportUseCase.ReviewAction.RESOLVE)));
    }

    @Test
    void suspendUser_whenValid_saveUserAndLog() {
        User activeUser = this.user.withUnsuspend();
        when(userRepository.findById(this.userId)).thenReturn(Optional.of(activeUser));

        this.adminService.suspendUser(new SuspendUserUseCase.Command(this.adminId, this.userId, "Test reason"));

        verify(userRepository).save(any(User.class));
        verify(auditLogRepository).log(
                this.adminId,
                "user_suspend",
                "USER",
                this.userId,
                "{\"reason\": \"Test reason\"}",
                null);
    }

    @Test
    void suspendUser_whenUserNotFound_throwUserNotFoundException() {
        when(userRepository.findById(this.userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> this.adminService.suspendUser(
                new SuspendUserUseCase.Command(this.adminId, this.userId, "Test reason")));
    }

    @Test
    void suspendUser_whenAlreadySuspended_throwIllegalStateException() {
        User suspendedUser = this.user.withSuspend();
        when(userRepository.findById(this.userId)).thenReturn(Optional.of(suspendedUser));

        assertThrows(IllegalStateException.class, () -> this.adminService.suspendUser(
                new SuspendUserUseCase.Command(this.adminId, this.userId, "Test reason")));
    }

    @Test
    void unsuspendUser_whenValid_saveUserAndLog() {
        User activeUser = this.user.withSuspend();
        when(userRepository.findById(this.userId)).thenReturn(Optional.of(activeUser));

        this.adminService.unsuspendUser(new UnsuspendUserUseCase.Command(this.adminId, this.userId));

        verify(userRepository).save(any(User.class));
        verify(auditLogRepository).log(
                this.adminId,
                "user_unsuspend",
                "USER",
                this.userId,
                null,
                null);
    }

    @Test
    void unsuspendUser_whenUserNotFound_throwUserNotFoundException() {
        when(userRepository.findById(this.userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> this.adminService.unsuspendUser(
                new UnsuspendUserUseCase.Command(this.adminId, this.userId)));
    }

    @Test
    void unsuspendUser_whenAlreadyActive_throwIllegalStateException() {
        User activeUser = this.user.withUnsuspend();
        when(userRepository.findById(this.userId)).thenReturn(Optional.of(activeUser));

        assertThrows(IllegalStateException.class, () -> this.adminService.unsuspendUser(
                new UnsuspendUserUseCase.Command(this.adminId, this.userId)));
    }

    @Test
    void getReports_whenValid_returnReports() {
        when(moderationRepository.findAllReports(any(), any())).thenReturn(List.of(this.report));
        List<Report> reports = this.adminService
                .getReports(new AdminGetReportsUseCase.Query(ReportStatus.PENDING, 0, 10));
        assertNotNull(reports);
        assertEquals(1, reports.size());
    }
}
