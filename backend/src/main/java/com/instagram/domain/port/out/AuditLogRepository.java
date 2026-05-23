package com.instagram.domain.port.out;

import java.util.UUID;

public interface AuditLogRepository {

    String REPORT_SUBMIT  = "report_submit";
    String USER_BLOCK     = "user_block";
    String USER_UNBLOCK   = "user_unblock";
    String REPORT_RESOLVE = "report_resolve";
    String REPORT_DISMISS = "report_dismiss";
    String REPORT_REVIEW  = "report_review";
    String USER_SUSPEND   = "user_suspend";
    String USER_UNSUSPEND = "user_unsuspend";

    /**
     * Appends a single audit log entry. This method is fire-and-forget:
     * implementations must never throw — on failure they must catch,
     * log to SLF4J at WARN level, and return silently.
     *
     * @param actorId    the user performing the action; maps to {@code user_id}
     * @param action     one of the constants defined in this interface
     * @param entityType nullable entity type label; maps to {@code entity_type}
     * @param entityId   nullable UUID of the affected entity; maps to {@code entity_id}
     * @param metadata   nullable JSON string; maps to {@code metadata} JSONB
     * @param ipAddress  nullable IP address string; maps to {@code ip_address} INET
     */
    void log(UUID actorId, String action, String entityType, UUID entityId, String metadata, String ipAddress);
}
