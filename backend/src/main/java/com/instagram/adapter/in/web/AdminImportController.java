package com.instagram.adapter.in.web;

import com.instagram.adapter.in.web.dto.request.TriggerImportRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.ImportJobResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/imports")
@RequiredArgsConstructor
@Tag(name = "Admin — Imports", description = "Bulk import operations")
public class AdminImportController {

    private static final Logger log = LoggerFactory.getLogger(AdminImportController.class);

    private final JobLauncher jobLauncher;
    private final Job importPostsJob;
    private final JobExplorer jobExplorer;

    @PostMapping("/posts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> triggerImport(
            @Valid @RequestBody TriggerImportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        JobParameters params = new JobParametersBuilder()
                .addString("userId", userId.toString())
                .addString("filePath", request.filePath())
                .toJobParameters();
        log.info("Import job triggered userId={} filePath={}", userId, request.filePath());

        try {
            JobExecution execution = jobLauncher.run(importPostsJob, params);
            return ResponseEntity.accepted()
                    .body(ApiResponse.ok(new ImportJobResponse(
                            execution.getId(),
                            execution.getStatus().name())));
        } catch (Exception e) {
            log.error("Failed to launch import job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to launch import job: " + e.getMessage()));
        }
    }

    @GetMapping("/posts/{executionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> getStatus(@PathVariable Long executionId) {
        log.debug("getStatus executionId={}", executionId);
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Job execution not found: " + executionId));
        }
        return ResponseEntity.ok(ApiResponse.ok(new ImportJobResponse(
                execution.getId(),
                execution.getStatus().name())));
    }
}
