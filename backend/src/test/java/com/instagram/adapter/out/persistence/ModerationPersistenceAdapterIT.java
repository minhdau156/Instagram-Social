package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import com.instagram.adapter.out.persistence.entity.ReportJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserBlockId;
import com.instagram.adapter.out.persistence.entity.UserBlockJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.ReportJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserBlockJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.UserBlock;
import com.instagram.domain.model.UserStatus;

public class ModerationPersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private UserBlockJpaRepository userBlockJpaRepository;
    @Autowired
    private ReportJpaRepository reportJpaRepository;

    @Autowired
    UserJpaRepository userJpaRepository;

    ModerationPersistenceAdapter moderationPersistenceAdapter;

    UserJpaEntity testUser;

    private UserJpaEntity buildUserJpaEntity() {
        return UserJpaEntity.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();
    }

    // reports.reporter_id / reviewed_by_id and user_blocks.blocker_id /
    // blocked_id are real FKs to users.id — every row we persist here needs an
    // actually-persisted parent user rather than a bare UUID.randomUUID().
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("mod_" + suffix)
                .email("mod_" + suffix + "@example.com")
                .fullName("Moderation Test User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private Report buildReportDomain(UUID reporterId, String reason, ReportStatus status) {
        return Report.builder()
                .id(UUID.randomUUID())
                .reporterId(reporterId)
                .entityType(ReportEntityType.POST)
                .entityId(UUID.randomUUID())
                .reason(reason)
                .details("Spam post")
                .status(status)
                .reviewedById(persistUser())
                .reviewedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private UserBlock buildUserBlock(UUID blockerId, UUID blockedId) {
        return UserBlock.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        moderationPersistenceAdapter = new ModerationPersistenceAdapter(userBlockJpaRepository, reportJpaRepository);
        testUser = userJpaRepository.save(buildUserJpaEntity());
    }

    @Test
    void saveReport_whenValid_returnSavedReport() {
        Report report = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        Report savedReport = moderationPersistenceAdapter.saveReport(report);
        assertNotNull(savedReport.getId());
        assertEquals(report.getReporterId(), savedReport.getReporterId());
    }

    @Test
    void findReportById_whenExists_returnReport() {
        Report report = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        Report savedReport = moderationPersistenceAdapter.saveReport(report);
        Report foundReport = moderationPersistenceAdapter.findReportById(savedReport.getId()).get();
        assertNotNull(foundReport);
        assertEquals(savedReport.getId(), foundReport.getId());
    }

    @Test
    void findAllReports_whenStatusProvided_returnFilteredReports() {
        Report report1 = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        Report report2 = buildReportDomain(persistUser(), "Spam", ReportStatus.PENDING);
        moderationPersistenceAdapter.saveReport(report1);
        moderationPersistenceAdapter.saveReport(report2);
        List<Report> foundReports = moderationPersistenceAdapter.findAllReports(ReportStatus.PENDING,
                Pageable.unpaged());
        assertNotNull(foundReports);
        assertEquals(2, foundReports.size());
    }

    @Test
    void saveReport_whenResolved_returnResolvedReport() {
        UUID adminId = persistUser();
        Report report = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        Report saved = moderationPersistenceAdapter.saveReport(report);

        moderationPersistenceAdapter.saveReport(saved.withResolved(adminId));

        Report found = moderationPersistenceAdapter.findReportById(saved.getId()).get();
        assertEquals(ReportStatus.RESOLVED, found.getStatus());
        assertEquals(adminId, found.getReviewedById());
    }

    @Test
    void findPendingReports_whenPendingReportsExist_returnOnlyPendingReports() {
        Report report1 = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        Report report2 = buildReportDomain(persistUser(), "Spam", ReportStatus.PENDING);
        Report report3 = buildReportDomain(persistUser(), "Spam", ReportStatus.RESOLVED);
        moderationPersistenceAdapter.saveReport(report1);
        moderationPersistenceAdapter.saveReport(report2);
        moderationPersistenceAdapter.saveReport(report3);
        List<Report> foundReports = moderationPersistenceAdapter.findPendingReports(Pageable.unpaged());
        assertNotNull(foundReports);
        assertEquals(2, foundReports.size());
    }

    @Test
    void countByStatus_whenReportsExist_returnCorrectCount() {
        Report report1 = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        Report report2 = buildReportDomain(persistUser(), "Spam", ReportStatus.PENDING);
        Report report3 = buildReportDomain(persistUser(), "Spam", ReportStatus.RESOLVED);
        moderationPersistenceAdapter.saveReport(report1);
        moderationPersistenceAdapter.saveReport(report2);
        moderationPersistenceAdapter.saveReport(report3);
        long count = moderationPersistenceAdapter.countByStatus(ReportStatus.PENDING);
        assertEquals(2, count);
    }

    @Test
    void existsByReporterIdAndEntityId_whenExists_returnTrue() {
        Report report = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        moderationPersistenceAdapter.saveReport(report);
        boolean exists = moderationPersistenceAdapter.existsByReporterIdAndEntityId(report.getReporterId(),
                report.getEntityId());
        assertTrue(exists);
    }

    @Test
    void existsByReporterIdAndEntityId_whenNotExists_returnFalse() {
        Report report = buildReportDomain(testUser.getId(), "Spam", ReportStatus.PENDING);
        moderationPersistenceAdapter.saveReport(report);
        boolean exists = moderationPersistenceAdapter.existsByReporterIdAndEntityId(UUID.randomUUID(),
                report.getEntityId());
        assertFalse(exists);
    }

    // BLOCK

    @Test
    void saveBlock_whenValid_returnSavedBlock() {
        UserBlock userBlock = buildUserBlock(persistUser(), persistUser());
        UserBlock savedUserBlock = moderationPersistenceAdapter.saveBlock(userBlock);
        assertNotNull(savedUserBlock.getBlockerId());
        assertEquals(userBlock.getBlockerId(), savedUserBlock.getBlockerId());
    }

    @Test
    void deleteBlock_whenBlockExists_blockIsRemoved() {
        UserBlock userBlock = buildUserBlock(persistUser(), persistUser());
        moderationPersistenceAdapter.saveBlock(userBlock);
        moderationPersistenceAdapter.deleteBlock(userBlock.getBlockerId(), userBlock.getBlockedId());
    }

    @Test
    void isBlocked_whenBlockExists_returnTrue() {
        UserBlock userBlock = buildUserBlock(persistUser(), persistUser());
        moderationPersistenceAdapter.saveBlock(userBlock);
        boolean exists = moderationPersistenceAdapter.isBlocked(userBlock.getBlockerId(),
                userBlock.getBlockedId());
        assertTrue(exists);
    }

    @Test
    void isBlocked_whenBlockNotExists_returnFalse() {
        UserBlock userBlock = buildUserBlock(persistUser(), persistUser());
        moderationPersistenceAdapter.saveBlock(userBlock);
        boolean exists = moderationPersistenceAdapter.isBlocked(UUID.randomUUID(),
                userBlock.getBlockedId());
        assertFalse(exists);
    }

    @Test
    void isBlocked_whenReversedDirection_returnFalse() {
        UserBlock userBlock = buildUserBlock(persistUser(), persistUser());
        moderationPersistenceAdapter.saveBlock(userBlock);
        assertFalse(moderationPersistenceAdapter.isBlocked(userBlock.getBlockedId(), userBlock.getBlockerId()));
    }

    @Test
    void isEitherBlocked_whenBlockExists_returnTrue() {
        UUID blockerId = persistUser();
        UUID blockedId = persistUser();
        moderationPersistenceAdapter.saveBlock(buildUserBlock(blockerId, blockedId));
        assertTrue(moderationPersistenceAdapter.isEitherBlocked(blockerId, blockedId));
        assertTrue(moderationPersistenceAdapter.isEitherBlocked(blockedId, blockerId));
    }

    @Test
    void findBlocksByBlockerId_whenBlocksExist_returnUserBlocks() {
        UUID blockerId = persistUser();

        UserBlock userBlock1 = buildUserBlock(blockerId, persistUser());
        UserBlock userBlock2 = buildUserBlock(blockerId, persistUser());

        moderationPersistenceAdapter.saveBlock(userBlock1);
        moderationPersistenceAdapter.saveBlock(userBlock2);

        List<UserBlock> foundUserBlocks = moderationPersistenceAdapter.findBlocksByBlockerId(blockerId,
                Pageable.unpaged());
        assertNotNull(foundUserBlocks);
        assertEquals(2, foundUserBlocks.size());
    }

    @Test
    void findBlocksByBlockerId_whenNoBlocksExist_returnEmptyList() {
        UUID blockerId = UUID.randomUUID();

        List<UserBlock> foundUserBlocks = moderationPersistenceAdapter.findBlocksByBlockerId(blockerId,
                Pageable.unpaged());
        assertNotNull(foundUserBlocks);
        assertEquals(0, foundUserBlocks.size());
    }

    @Test
    void findBlockedUserIdsByBlockerId_whenBlocksExist_returnBlockedUserIds() {
        UUID blockerId = persistUser();

        UserBlock userBlock1 = buildUserBlock(blockerId, persistUser());
        UserBlock userBlock2 = buildUserBlock(blockerId, persistUser());

        moderationPersistenceAdapter.saveBlock(userBlock1);
        moderationPersistenceAdapter.saveBlock(userBlock2);

        List<UUID> foundBlockedUserIds = moderationPersistenceAdapter.findBlockedUserIdsByBlockerId(blockerId);
        assertNotNull(foundBlockedUserIds);
        assertEquals(2, foundBlockedUserIds.size());
    }

    @Test
    void selfBlockGuard_whenSameBlockerAndBlockedId_throwIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> UserBlock.builder().blockerId(id).blockedId(id).createdAt(OffsetDateTime.now()).build());
    }

    @Test
    void findBlockerIdsByBlockedId_whenBlocksExist_returnBlockerIds() {
        UUID blockedId = persistUser();

        UserBlock userBlock1 = buildUserBlock(persistUser(), blockedId);
        UserBlock userBlock2 = buildUserBlock(persistUser(), blockedId);

        moderationPersistenceAdapter.saveBlock(userBlock1);
        moderationPersistenceAdapter.saveBlock(userBlock2);

        List<UUID> foundBlockerIds = moderationPersistenceAdapter.findBlockerIdsByBlockedId(blockedId);
        assertNotNull(foundBlockerIds);
        assertEquals(2, foundBlockerIds.size());
    }

}
