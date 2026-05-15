package com.instagram.adapter.in.web.dto.request;

public record NotificationSettingsRequest(
        boolean likesEnabled,
        boolean commentsEnabled,
        boolean followsEnabled,
        boolean messagesEnabled,
        boolean pushEnabled) {
}
