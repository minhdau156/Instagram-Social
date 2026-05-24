package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.instagram.domain.exception.AlreadyBlockedException;
import com.instagram.domain.exception.AlreadyReportedException;
import com.instagram.domain.exception.NotBlockedException;
import com.instagram.domain.exception.SelfBlockException;
import com.instagram.domain.exception.UserNotFoundException;

import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserBlock;
import com.instagram.domain.port.in.moderation.BlockUserUseCase;
import com.instagram.domain.port.in.moderation.GetBlockedUsersUseCase;
import com.instagram.domain.port.in.moderation.ReportContentUseCase;
import com.instagram.domain.port.in.moderation.UnblockUserUseCase;
import com.instagram.domain.port.out.AuditLogRepository;
import com.instagram.domain.port.out.ModerationRepository;
import com.instagram.domain.port.out.UserRepository;

@ExtendWith(MockitoExtension.class)
public class ModerationServiceTest {

    @Mock
    private ModerationRepository moderationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ModerationService moderationService;

    User user;
    UserBlock userBlock;
    Report report;
    UUID reportId;

    @BeforeEach
    void setup() {
        this.user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("[EMAIL_ADDRESS]")
                .passwordHash("passwordHash")
                .fullName("Full Name")
                .bio("Bio")
                .build();
        this.userBlock = UserBlock.builder()
                .blockerId(UUID.randomUUID())
                .blockedId(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .build();
        this.reportId = UUID.randomUUID();
        this.report = Report.builder()
                .id(UUID.randomUUID())
                .reporterId(this.reportId)
                .entityId(UUID.randomUUID())
                .entityType(ReportEntityType.POST)
                .reason("Test reason")
                .details("Test details")
                .status(ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

    }

    @Test
    void reportContent_whenValid_saveReportAndLog() {
        when(moderationRepository.existsByReporterIdAndEntityId(any(), any()))
                .thenReturn(false);
        UUID entityId = UUID.randomUUID();

        this.moderationService.reportContent(
                new ReportContentUseCase.Command(
                        this.user.getId(),
                        ReportEntityType.POST,
                        entityId,
                        "Test reason",
                        "Test details"));

        verify(moderationRepository)
                .saveReport(any(Report.class));
        verify(auditLogRepository)
                .log(this.user.getId(), AuditLogRepository.REPORT_SUBMIT,
                        "POST", entityId, null, null);

    }

    @Test
    void reportContent_whenReasonIsBlank_throwIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> this.moderationService.reportContent(
                new ReportContentUseCase.Command(
                        this.user.getId(),
                        ReportEntityType.POST,
                        UUID.randomUUID(),
                        "",
                        "Test details")));

    }

    @Test
    void reportContent_whenAlreadyReported_throwAlreadyReportedException() {
        when(moderationRepository.existsByReporterIdAndEntityId(any(), any()))
                .thenReturn(true);
        assertThrows(AlreadyReportedException.class, () -> this.moderationService.reportContent(
                new ReportContentUseCase.Command(
                        this.user.getId(),
                        ReportEntityType.POST,
                        UUID.randomUUID(),
                        "Test reason",
                        "Test details")));
        verify(moderationRepository, never()).saveReport(any());
    }

    @Test
    void blockUser_whenValid_saveAndLog() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.of(this.user));
        when(moderationRepository.isBlocked(any(), any()))
                .thenReturn(false);
        this.moderationService.blockUser(
                new BlockUserUseCase.Command(
                        this.reportId,
                        "testuser"));
        verify(moderationRepository)
                .saveBlock(any(UserBlock.class));
        verify(auditLogRepository)
                .log(this.reportId, AuditLogRepository.USER_BLOCK,
                        "USER", this.user.getId(), null, null);
    }

    @Test
    void blockUser_whenUserNotFound_throwUserNotFoundException() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> this.moderationService.blockUser(
                new BlockUserUseCase.Command(
                        this.user.getId(),
                        "testuser")));
    }

    @Test
    void blockUser_whenSelfBlock_throwSelfBlockException() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.of(this.user));
        assertThrows(SelfBlockException.class, () -> this.moderationService.blockUser(
                new BlockUserUseCase.Command(
                        this.user.getId(),
                        "testuser")));
    }

    @Test
    void blockUser_whenAlreadyBlocked_throwAlreadyBlockedException() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.of(this.user));
        when(moderationRepository.isBlocked(any(), any()))
                .thenReturn(true);
        assertThrows(AlreadyBlockedException.class, () -> this.moderationService.blockUser(
                new BlockUserUseCase.Command(
                        this.reportId,
                        "testuser")));
    }

    @Test
    void unblockUser_whenValid_deleteAndLog() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.of(this.user));
        when(moderationRepository.isBlocked(any(), any()))
                .thenReturn(true);
        this.moderationService.unblockUser(
                new UnblockUserUseCase.Command(
                        this.user.getId(),
                        "testuser"));
        verify(moderationRepository)
                .deleteBlock(this.user.getId(), this.user.getId());
        verify(auditLogRepository)
                .log(this.user.getId(), AuditLogRepository.USER_UNBLOCK,
                        "USER", this.user.getId(), null, null);
    }

    @Test
    void unblockUser_whenUserNotFound_throwUserNotFoundException() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> this.moderationService.unblockUser(
                new UnblockUserUseCase.Command(
                        this.user.getId(),
                        "testuser")));
    }

    @Test
    void unblockUser_whenNotBlocked_throwNotBlockedException() {
        when(userRepository.findByUsername(any()))
                .thenReturn(Optional.of(this.user));
        when(moderationRepository.isBlocked(any(), any()))
                .thenReturn(false);
        assertThrows(NotBlockedException.class, () -> this.moderationService.unblockUser(
                new UnblockUserUseCase.Command(
                        this.user.getId(),
                        "testuser")));
    }

    @Test
    void getBlockedUsers_whenValid_returnBlockedUsers() {
        when(moderationRepository.findBlocksByBlockerId(any(), any()))
                .thenReturn(List.of(this.userBlock));
        List<UserBlock> result = this.moderationService.getBlockedUsers(
                new GetBlockedUsersUseCase.Query(
                        this.user.getId(),
                        0,
                        10));
        assertEquals(1, result.size());
        verifyNoInteractions(auditLogRepository);
    }

}
