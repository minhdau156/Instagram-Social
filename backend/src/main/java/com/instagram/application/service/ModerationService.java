package com.instagram.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.domain.exception.AlreadyBlockedException;
import com.instagram.domain.exception.AlreadyReportedException;
import com.instagram.domain.exception.NotBlockedException;
import com.instagram.domain.exception.SelfBlockException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.Report;
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

@Service
public class ModerationService implements ReportContentUseCase,
        BlockUserUseCase,
        UnblockUserUseCase,
        GetBlockedUsersUseCase {

    private final ModerationRepository moderationRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    public ModerationService(ModerationRepository moderationRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository) {
        this.moderationRepository = moderationRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public List<UserBlock> getBlockedUsers(GetBlockedUsersUseCase.Query query) {
        return moderationRepository.findBlocksByBlockerId(query.userId(),
                PageRequest.of(query.page(), query.size()));
    }

    @Override
    @Transactional
    public void unblockUser(UnblockUserUseCase.Command command) {
        User targetUser = userRepository.findByUsername(command.targetUsername())
                .orElseThrow(() -> UserNotFoundException.withUsername(command.targetUsername()));
        UUID targetId = targetUser.getId();

        if (!moderationRepository.isBlocked(command.blockerId(), targetId)) {
            throw new NotBlockedException(command.blockerId(), targetId);
        }

        moderationRepository.deleteBlock(command.blockerId(), targetId);
        auditLogRepository.log(command.blockerId(), AuditLogRepository.USER_UNBLOCK,
                "user",
                targetId,
                null,
                null);
    }

    @Override
    @Transactional
    public UserBlock blockUser(BlockUserUseCase.Command command) {
        User blockedUser = userRepository.findByUsername(command.targetUsername())
                .orElseThrow(() -> UserNotFoundException.withUsername(command.targetUsername()));
        UUID blockedUserId = blockedUser.getId();
        if (blockedUserId.equals(command.blockerId())) {
            throw new SelfBlockException(blockedUser.getId());
        }

        if (moderationRepository.isBlocked(command.blockerId(), blockedUserId)) {
            throw new AlreadyBlockedException(command.blockerId(), blockedUserId);
        }

        UserBlock block = UserBlock.builder()
                .blockerId(command.blockerId())
                .blockedId(blockedUserId)
                .createdAt(OffsetDateTime.now())
                .build();

        UserBlock saved = moderationRepository.saveBlock(block);

        auditLogRepository.log(command.blockerId(), AuditLogRepository.USER_BLOCK,
                "user",
                blockedUserId,
                null,
                null);

        return saved;
    }

    @Override
    public Report reportContent(ReportContentUseCase.Command command) {
        if (command.reason().isBlank()) {
            throw new IllegalArgumentException("Reason cannot be blank");
        }

        if (moderationRepository.existsByReporterIdAndEntityId(command.reporterId(), command.entityId())) {
            throw new AlreadyReportedException(command.reporterId(), command.entityId());
        }

        Report newReport = Report.builder()
                .reporterId(command.reporterId())
                .entityId(command.entityId())
                .entityType(command.entityType())
                .reason(command.reason())
                .details(command.details())
                .status(ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        Report saved = moderationRepository.saveReport(newReport);

        auditLogRepository.log(command.reporterId(), AuditLogRepository.REPORT_SUBMIT,
                command.entityType().name(),
                command.entityId(),
                null,
                null);

        return saved;
    }

}
