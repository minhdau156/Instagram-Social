export type ReportEntityType = 'USER' | 'POST' | 'COMMENT' | 'MESSAGE';

export type ReportStatus = 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED';

export type ReportReason = 'SPAM' | 'HATE_SPEECH' | 'NUDITY' | 'VIOLENCE' | 'HARASSMENT' | 'FALSE_INFORMATION' | 'SELF_HARM' | 'OTHER';

export type ReviewAction = 'RESOLVE' | 'DISMISS' | 'MARK_REVIEWED';

export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED' | 'PENDING_VERIFICATION';

export interface Report {
    id: string;
    reporterId: string;
    reporterUsername: string;
    entityType: ReportEntityType;
    entityId: string;
    reason: string;
    details: string | null;
    status: ReportStatus;
    reviewedById: string | null;
    reviewedAt: string | null;
    createdAt: string;
}

export interface UserBlock {
    blockedUserId: string;
    username: string;
    fullName: string | null;
    avatarUrl: string | null;
    blockedAt: string;
}

export interface AdminUser {
    id: string;
    username: string;
    email: string;
    fullName: string | null;
    accountStatus: AccountStatus;
    isVerified: boolean;
    createdAt: string;
    lastLoginAt: string | null;
}

export interface SubmitReportPayload {
    entityType: ReportEntityType;
    entityId: string;
    reason: ReportReason;
    details?: string;
}

export interface ReviewReportPayload {
    action: ReviewAction;
}

export interface SuspendUserPayload {
    reason: string;
}

export interface AuditLog {
    id: number;
    userId: string | null;
    action: string;
    entityType: string | null;
    entityId: string | null;
    metadata: string | null;
    ipAddress: string | null;
    createdAt: string;
}