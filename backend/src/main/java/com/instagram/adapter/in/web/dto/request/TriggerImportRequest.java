package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TriggerImportRequest(@NotBlank String filePath) {}
