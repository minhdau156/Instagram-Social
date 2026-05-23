package com.instagram.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.repository.UserBlockJpaRepository;
import com.instagram.adapter.out.persistence.entity.ReportJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserBlockId;
import com.instagram.adapter.out.persistence.entity.UserBlockJpaEntity;
import com.instagram.adapter.out.persistence.repository.ReportJpaRepository;
import com.instagram.domain.model.UserBlock;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.port.out.ModerationRepository;

@Component
public class ModerationPersistenceAdapter implements ModerationRepository {
    private final UserBlockJpaRepository userBlockJpaRepository;
    private final ReportJpaRepository reportJpaRepository;

    public ModerationPersistenceAdapter(UserBlockJpaRepository userBlockJpaRepository,
            ReportJpaRepository reportJpaRepository) {
        this.userBlockJpaRepository = userBlockJpaRepository;
        this.reportJpaRepository = reportJpaRepository;
    }

    @Override
    public Report saveReport(Report report) {
        ReportJpaEntity entity = toReportEntity(report);
        ReportJpaEntity saved = reportJpaRepository.save(entity);
        return toReportDomain(saved);
    }

    @Override
    public Optional<Report> findReportById(UUID reportId) {
        return reportJpaRepository.findById(reportId).map(this::toReportDomain);
    }

    @Override
    public List<Report> findAllReports(ReportStatus status, Pageable pageable) {
        if (status == null) {
            return this.reportJpaRepository.findAllByOrderByCreatedAtDesc(pageable).stream()
                    .map(this::toReportDomain).collect(Collectors.toList());
        }
        return this.reportJpaRepository.findByStatusOrderByCreatedAtDesc(status, pageable).stream()
                .map(this::toReportDomain).collect(Collectors.toList());

    }

    @Override
    public List<Report> findPendingReports(Pageable pageable) {
        return this.reportJpaRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING, pageable)
                .stream().map(this::toReportDomain).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(ReportStatus status) {
        return this.reportJpaRepository.countByStatus(status);
    }

    @Override
    public boolean existsByReporterIdAndEntityId(UUID reporterId, UUID entityId) {
        return this.reportJpaRepository.existsByReporterIdAndEntityId(reporterId, entityId);
    }

    @Override
    public UserBlock saveBlock(UserBlock block) {
        UserBlockJpaEntity entity = toBlockEntity(block);
        UserBlockJpaEntity saved = userBlockJpaRepository.save(entity);
        return toBlockDomain(saved);
    }

    @Override
    @Transactional
    public void deleteBlock(UUID blockerId, UUID blockedId) {
        userBlockJpaRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Override
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return this.userBlockJpaRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Override
    public boolean isEitherBlocked(UUID userA, UUID userB) {
        return (isBlocked(userA, userB) || isBlocked(userB, userA));
    }

    @Override
    public List<UserBlock> findBlocksByBlockerId(UUID blockerId, Pageable pageable) {
        return this.userBlockJpaRepository.findByIdBlockerIdOrderByCreatedAtDesc(blockerId, pageable)
                .stream().map(this::toBlockDomain).collect(Collectors.toList());
    }

    @Override
    public List<UUID> findBlockedUserIdsByBlockerId(UUID blockerId) {
        return this.userBlockJpaRepository.findBlockedIdsByBlockerId(blockerId);
    }

    @Override
    public List<UUID> findBlockerIdsByBlockedId(UUID blockedId) {
        return this.userBlockJpaRepository.findBlockerIdsByBlockedId(blockedId);
    }

    private Report toReportDomain(ReportJpaEntity report) {
        return Report.builder()
                .id(report.getId())
                .reporterId(report.getReporterId())
                .entityType(report.getEntityType())
                .entityId(report.getEntityId())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .reviewedById(report.getReviewedById())
                .reviewedAt(report.getReviewedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private ReportJpaEntity toReportEntity(Report report) {
        return ReportJpaEntity.builder()
                .id(report.getId())
                .reporterId(report.getReporterId())
                .entityType(report.getEntityType())
                .entityId(report.getEntityId())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .reviewedById(report.getReviewedById())
                .reviewedAt(report.getReviewedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private UserBlock toBlockDomain(UserBlockJpaEntity block) {
        return UserBlock.builder()
                .blockerId(block.getId().getBlockerId())
                .blockedId(block.getId().getBlockedId())
                .createdAt(block.getCreatedAt())
                .build();
    }

    private UserBlockJpaEntity toBlockEntity(UserBlock block) {
        UserBlockId id = new UserBlockId(block.getBlockerId(), block.getBlockedId());
        return UserBlockJpaEntity.builder()
                .id(id)
                .createdAt(block.getCreatedAt())
                .build();
    }

}
