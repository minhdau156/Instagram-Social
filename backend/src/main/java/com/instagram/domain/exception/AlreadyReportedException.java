package com.instagram.domain.exception;

import java.util.UUID;

public class AlreadyReportedException extends RuntimeException {

    public AlreadyReportedException(UUID reporterId, UUID entityId) {
        super("User " + reporterId + " has already reported entity " + entityId);
    }
}
