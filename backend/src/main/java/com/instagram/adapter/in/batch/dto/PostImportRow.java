package com.instagram.adapter.in.batch.dto;

public record PostImportRow(
        String caption,
        String location,
        String mediaUrl,
        String createdAt
) {}
