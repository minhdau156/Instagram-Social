package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateConversationRequest(
        @NotEmpty @Size(min = 1, max = 20) List<UUID> participantIds,

        @Size(max = 100) String name, // optional for group chats

        @NotNull boolean isGroup) {

}
