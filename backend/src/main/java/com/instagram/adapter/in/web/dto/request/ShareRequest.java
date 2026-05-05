package com.instagram.adapter.in.web.dto.request;

import java.util.UUID;

import com.instagram.domain.model.ShareType;

import jakarta.validation.constraints.NotNull;

public record ShareRequest(
        @NotNull ShareType shareType,
        UUID recipientId // optional — required only for DM
) {
}
