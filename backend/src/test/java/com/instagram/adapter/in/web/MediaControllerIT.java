package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.CompleteUploadRequest;
import com.instagram.adapter.in.web.dto.request.InitiateUploadRequest;
import com.instagram.adapter.in.web.dto.request.ProcessMediaRequest;
import com.instagram.adapter.in.web.dto.request.UploadUrlRequest;
import com.instagram.adapter.out.persistence.entity.UploadSessionJpaEntity;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.adapter.out.persistence.repository.UploadSessionJpaRepository;
import com.instagram.application.service.PostService;
import com.instagram.domain.port.in.GenerateUploadUrlUseCase;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.infrastructure.security.HtmlSanitizer;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;
import com.instagram.infrastructure.storage.MediaValidator;

@WebMvcTest(MediaController.class)
@Import(SecurityConfig.class)
public class MediaControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    @MockBean
    OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockBean
    HtmlSanitizer htmlSanitizer;

    @MockBean
    IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    // PostService implements GenerateUploadUrlUseCase — mock only PostService to
    // avoid an ambiguous-bean error; stub generateUploadUrl() on this mock directly.
    @MockBean
    PostService postService;

    @MockBean
    MediaStoragePort mediaStoragePort;

    @MockBean
    UploadSessionJpaRepository uploadSessionRepository;

    @MockBean
    MediaValidator mediaValidator;

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getUploadUrl_returns200_onSuccess() throws Exception {
        var request = new UploadUrlRequest("photo.jpg", "image/jpeg");
        var uploadUrl = new GenerateUploadUrlUseCase.UploadUrl("https://minio/presigned-url", "uploads/photo.jpg");

        when(postService.generateUploadUrl(any(GenerateUploadUrlUseCase.Command.class))).thenReturn(uploadUrl);

        mockMvc.perform(post("/api/v1/media/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value("https://minio/presigned-url"))
                .andExpect(jsonPath("$.data.mediaKey").value("uploads/photo.jpg"));
    }

    @Test
    void getUploadUrl_unauthenticated_returns401() throws Exception {
        var request = new UploadUrlRequest("photo.jpg", "image/jpeg");

        mockMvc.perform(post("/api/v1/media/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getUploadUrl_invalidContentType_returns400() throws Exception {
        var request = new UploadUrlRequest("photo.bmp", "image/bmp");

        mockMvc.perform(post("/api/v1/media/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void serveImage_returns204() throws Exception {
        mockMvc.perform(get("/api/v1/media/images/{key}", "test-image.jpg")
                .header("Accept", "image/webp,image/*"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void processMedia_returns202_onSuccess() throws Exception {
        var request = new ProcessMediaRequest("https://minio/photo.jpg", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/media/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("accepted"))
                .andExpect(jsonPath("$.data.jobId").isNotEmpty());
    }

    @Test
    void processMedia_unauthenticated_returns401() throws Exception {
        var request = new ProcessMediaRequest("https://minio/photo.jpg", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/media/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void initiateUpload_returns201_onSuccess() throws Exception {
        long partSize = 5L * 1024 * 1024; // 5 MB — minimum valid part size
        var request = new InitiateUploadRequest("video.mp4", "video/mp4", 1, partSize, partSize);

        when(mediaStoragePort.initiateMultipartUpload(anyString(), anyString())).thenReturn("upload-id-123");
        when(mediaStoragePort.generatePresignedPartUrl(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn("https://minio/presigned-part-url");
        when(uploadSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/media/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploadId").value("upload-id-123"))
                .andExpect(jsonPath("$.data.partUrls").isArray());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void initiateUpload_fileTooLarge_returns400() throws Exception {
        long oversizeBytes = 3L * 1024 * 1024 * 1024; // 3 GB — over the 2 GB cap
        long partSize = 5L * 1024 * 1024;
        var request = new InitiateUploadRequest("big.mp4", "video/mp4", 1, oversizeBytes, partSize);

        mockMvc.perform(post("/api/v1/media/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void initiateUpload_partSizeTooSmall_returns400() throws Exception {
        long partSize = 1024L; // 1 KB — under the 5 MB minimum
        var request = new InitiateUploadRequest("video.mp4", "video/mp4", 1, partSize, partSize);

        mockMvc.perform(post("/api/v1/media/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiateUpload_unauthenticated_returns401() throws Exception {
        long partSize = 5L * 1024 * 1024;
        var request = new InitiateUploadRequest("video.mp4", "video/mp4", 1, partSize, partSize);

        mockMvc.perform(post("/api/v1/media/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void listUploadedParts_returns200_onSuccess() throws Exception {
        String uploadId = "upload-id-123";
        UploadSessionJpaEntity session = UploadSessionJpaEntity.builder()
                .uploadId(uploadId)
                .objectKey("uploads/" + USER_ID + "/file.mp4")
                .userId(USER_ID)
                .totalParts(2)
                .build();

        when(uploadSessionRepository.findByUploadId(uploadId)).thenReturn(Optional.of(session));
        when(mediaStoragePort.listUploadedParts(anyString(), anyString()))
                .thenReturn(List.of(
                        new MediaStoragePort.PartInfo(1, "etag1", 5L * 1024 * 1024),
                        new MediaStoragePort.PartInfo(2, "etag2", 5L * 1024 * 1024)));

        mockMvc.perform(get("/api/v1/media/uploads/{uploadId}/parts", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value(uploadId))
                .andExpect(jsonPath("$.data.uploadedParts").isArray())
                .andExpect(jsonPath("$.data.uploadedParts[0].partNumber").value(1));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void completeUpload_returns200_withPublicUrl() throws Exception {
        String uploadId = "upload-id-123";
        UploadSessionJpaEntity session = UploadSessionJpaEntity.builder()
                .uploadId(uploadId)
                .objectKey("uploads/" + USER_ID + "/file.mp4")
                .userId(USER_ID)
                .totalParts(1)
                .build();

        var request = new CompleteUploadRequest(
                List.of(new CompleteUploadRequest.PartRequest(1, "etag1")));

        when(uploadSessionRepository.findByUploadId(uploadId)).thenReturn(Optional.of(session));
        when(mediaStoragePort.completeMultipartUpload(anyString(), anyString(), any()))
                .thenReturn("https://minio/uploads/file.mp4");
        when(uploadSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/media/uploads/{uploadId}/complete", uploadId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://minio/uploads/file.mp4"));
    }
}
