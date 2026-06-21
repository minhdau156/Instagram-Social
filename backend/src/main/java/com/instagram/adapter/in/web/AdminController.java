package com.instagram.adapter.in.web;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.ReviewReportRequest;
import com.instagram.adapter.in.web.dto.request.SuspendUserRequest;
import com.instagram.adapter.in.web.dto.response.AdminUserResponse;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.ReportResponse;
import com.instagram.adapter.in.web.dto.response.UserResponse;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.admin.AdminGetReportsUseCase;
import com.instagram.domain.port.in.admin.ReviewReportUseCase;
import com.instagram.domain.port.in.admin.SuspendUserUseCase;
import com.instagram.domain.port.in.admin.UnsuspendUserUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@AllArgsConstructor
@Tag(name = "Admin", description = "Admin endpoints")
public class AdminController {

    private final ReviewReportUseCase reviewReportUseCase;
    private final SuspendUserUseCase suspendUserUseCase;
    private final UnsuspendUserUseCase unsuspendUserUseCase;
    private final AdminGetReportsUseCase adminGetReportsUseCase;
    private final FindAllUserUseCase findUserUseCase;
    private final GetUserUseCase getUserUseCase;
    private final SearchUsersUseCase searchUsersUseCase;

    @Nullable
    private UUID currentUserIdOrNull() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }

    private UUID currentUserId() {
        UUID userId = currentUserIdOrNull();
        if (userId == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userId;
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("getReports status={} page={} size={}", status, page, size);
        List<Report> reports = adminGetReportsUseCase.getReports(new AdminGetReportsUseCase.Query(status, page, size));
        if (CollectionUtils.isEmpty(reports)) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
        Collection<UUID> ids = reports.stream()
                .map(Report::getReporterId)
                .collect(Collectors.toList());
        List<User> users = findUserUseCase.findAllByIds(ids);
        Map<UUID, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<ReportResponse> reportResponses = reports.stream()
                .map(report -> ReportResponse.from(report, userMap.get(report.getReporterId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(reportResponses));
    }

    @PutMapping("/reports/{id}")
    public ResponseEntity<ApiResponse<ReportResponse>> reviewReport(@PathVariable UUID id,
            @Valid @RequestBody ReviewReportRequest request) {
        Report report = reviewReportUseCase
                .reviewReport(new ReviewReportUseCase.Command(currentUserId(), id, request.action()));
        log.info("Report reviewed id={} action={}", id, request.action());
        User reporter = getUserUseCase.getUser(new GetUserUseCase.Query(report.getReporterId()));
        return ResponseEntity.ok(ApiResponse.ok(ReportResponse.from(report, reporter)));
    }

    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<ApiResponse<UserResponse>> suspendUser(@PathVariable UUID id,
            @Valid @RequestBody SuspendUserRequest request) {
        User user = suspendUserUseCase
                .suspendUser(new SuspendUserUseCase.Command(currentUserId(), id, request.reason()));
        log.info("User suspended userId={} reason={}", id, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @PutMapping("/users/{id}/unsuspend")
    public ResponseEntity<ApiResponse<UserResponse>> unsuspendUser(@PathVariable UUID id) {
        User user = unsuspendUserUseCase.unsuspendUser(new UnsuspendUserUseCase.Command(currentUserId(), id));
        log.info("User unsuspended userId={}", id);
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("searchUsers username={} page={} size={}", username, page, size);
        List<User> users = searchUsersUseCase
                .searchUsers(new SearchUsersUseCase.Query(username, currentUserId(), page, size));
        if (CollectionUtils.isEmpty(users)) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }
        List<AdminUserResponse> userResponses = users.stream()
                .map(AdminUserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(userResponses));
    }

}
