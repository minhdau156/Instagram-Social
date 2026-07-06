package com.instagram.adapter.out.storage;

import com.instagram.domain.exception.MediaUploadException;
import com.instagram.domain.port.out.MediaStoragePort.PartETag;
import com.instagram.domain.port.out.MediaStoragePort.PartInfo;
import com.instagram.infrastructure.config.MultipartMinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.messages.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioStorageAdapterTest {

    @Mock
    private MultipartMinioClient minioClient;

    private MinioStorageAdapter adapter;

    private static final String BUCKET = "media";
    private static final String ENDPOINT = "http://localhost:9000";
    private static final String CDN_BASE_URL = "https://cdn.example.com";

    @BeforeEach
    void setUp() {
        adapter = new MinioStorageAdapter(minioClient, BUCKET, ENDPOINT, CDN_BASE_URL);
    }

    // ── getPublicUrl ──────────────────────────────────────────────────────────

    @Test
    void getPublicUrl_returnsFormattedCdnUrl() {
        String url = adapter.getPublicUrl("images/photo.jpg");
        assertThat(url).isEqualTo("https://cdn.example.com/media/images/photo.jpg");
    }

    @Test
    void getPublicUrl_handlesNestedPath() {
        String url = adapter.getPublicUrl("users/abc/avatars/pic.jpg");
        assertThat(url).isEqualTo("https://cdn.example.com/media/users/abc/avatars/pic.jpg");
    }

    // ── uploadFile ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void uploadFile_returnsCdnUrlOnSuccess() throws Exception {
        doAnswer(inv -> CompletableFuture.completedFuture(null))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        String url = adapter.uploadFile("images/photo.jpg", new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(url).isEqualTo("https://cdn.example.com/media/images/photo.jpg");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadFile_throwsMediaUploadExceptionOnFailure() throws Exception {
        doAnswer(inv -> CompletableFuture.failedFuture(new RuntimeException("connection refused")))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> adapter.uploadFile("key", new byte[]{1}, "image/jpeg"))
                .isInstanceOf(MediaUploadException.class)
                .hasMessageContaining("Failed to upload");
    }

    // ── generatePresignedPutUrl ───────────────────────────────────────────────

    @Test
    void generatePresignedPutUrl_delegatesToMinioAndReturnsUrl() throws Exception {
        String expectedUrl = "https://minio.example.com/presigned?X-Amz-Signature=abc";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn(expectedUrl);

        String url = adapter.generatePresignedPutUrl("images/photo.jpg", Duration.ofMinutes(15));

        assertThat(url).isEqualTo(expectedUrl);
    }

    @Test
    void generatePresignedPutUrl_throwsMediaUploadExceptionOnFailure() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        assertThatThrownBy(() -> adapter.generatePresignedPutUrl("key", Duration.ofMinutes(5)))
                .isInstanceOf(MediaUploadException.class)
                .hasMessageContaining("presigned put URL");
    }

    // ── initiateMultipartUpload ───────────────────────────────────────────────

    @Test
    void initiateMultipartUpload_returnsUploadId() throws Exception {
        when(minioClient.createMultipartUpload(BUCKET, "video/clip.mp4", "video/mp4"))
                .thenReturn("upload-id-xyz");

        String uploadId = adapter.initiateMultipartUpload("video/clip.mp4", "video/mp4");

        assertThat(uploadId).isEqualTo("upload-id-xyz");
    }

    @Test
    void initiateMultipartUpload_throwsMediaUploadExceptionOnFailure() throws Exception {
        when(minioClient.createMultipartUpload(any(), any(), any()))
                .thenThrow(new RuntimeException("bucket not found"));

        assertThatThrownBy(() -> adapter.initiateMultipartUpload("key", "video/mp4"))
                .isInstanceOf(MediaUploadException.class)
                .hasMessageContaining("initiate multipart");
    }

    // ── generatePresignedPartUrl ──────────────────────────────────────────────

    @Test
    void generatePresignedPartUrl_returnsUrlFromMinio() throws Exception {
        String expectedUrl = "https://minio.example.com/presigned-part?partNumber=1";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn(expectedUrl);

        String url = adapter.generatePresignedPartUrl("video/clip.mp4", "upload-id-xyz", 1, Duration.ofMinutes(30));

        assertThat(url).isEqualTo(expectedUrl);
    }

    @Test
    void generatePresignedPartUrl_throwsMediaUploadExceptionOnFailure() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("network error"));

        assertThatThrownBy(() -> adapter.generatePresignedPartUrl("key", "uid", 1, Duration.ofMinutes(5)))
                .isInstanceOf(MediaUploadException.class)
                .hasMessageContaining("presigned part URL");
    }

    // ── completeMultipartUpload ───────────────────────────────────────────────

    @Test
    void completeMultipartUpload_returnsCdnUrl() throws Exception {
        List<PartETag> parts = List.of(new PartETag(1, "etag-1"), new PartETag(2, "etag-2"));
        doNothing().when(minioClient).completeMultipartUpload(any(), any(), any(), any());

        String url = adapter.completeMultipartUpload("video/clip.mp4", "upload-id-xyz", parts);

        assertThat(url).isEqualTo("https://cdn.example.com/media/video/clip.mp4");
    }

    @Test
    void completeMultipartUpload_passesCorrectPartsToMinio() throws Exception {
        List<PartETag> parts = List.of(new PartETag(1, "etag-1"), new PartETag(2, "etag-2"));
        ArgumentCaptor<Part[]> partsCaptor = ArgumentCaptor.forClass(Part[].class);
        doNothing().when(minioClient).completeMultipartUpload(
                eq(BUCKET), eq("video/clip.mp4"), eq("upload-id-xyz"), partsCaptor.capture());

        adapter.completeMultipartUpload("video/clip.mp4", "upload-id-xyz", parts);

        Part[] captured = partsCaptor.getValue();
        assertThat(captured).hasSize(2);
        assertThat(captured[0].partNumber()).isEqualTo(1);
        assertThat(captured[0].etag()).isEqualTo("etag-1");
        assertThat(captured[1].partNumber()).isEqualTo(2);
        assertThat(captured[1].etag()).isEqualTo("etag-2");
    }

    @Test
    void completeMultipartUpload_throwsMediaUploadExceptionOnFailure() throws Exception {
        doThrow(new RuntimeException("complete failed"))
                .when(minioClient).completeMultipartUpload(any(), any(), any(), any());

        assertThatThrownBy(() -> adapter.completeMultipartUpload("key", "uid", List.of(new PartETag(1, "e"))))
                .isInstanceOf(MediaUploadException.class)
                .hasMessageContaining("complete multipart");
    }

    // ── abortMultipartUpload ──────────────────────────────────────────────────

    @Test
    void abortMultipartUpload_doesNotThrowOnSuccess() throws Exception {
        doNothing().when(minioClient).abortMultipartUpload(BUCKET, "video/clip.mp4", "upload-id-xyz");

        // Should not throw
        adapter.abortMultipartUpload("video/clip.mp4", "upload-id-xyz");

        verify(minioClient).abortMultipartUpload(BUCKET, "video/clip.mp4", "upload-id-xyz");
    }

    @Test
    void abortMultipartUpload_silentlyIgnoresException() throws Exception {
        doThrow(new RuntimeException("abort failed"))
                .when(minioClient).abortMultipartUpload(any(), any(), any());

        // Should not throw
        adapter.abortMultipartUpload("key", "uid");
    }

    // ── listUploadedParts ─────────────────────────────────────────────────────

    @Test
    void listUploadedParts_returnsMappedPartInfoList() throws Exception {
        Part part1 = mock(Part.class);
        when(part1.partNumber()).thenReturn(1);
        when(part1.etag()).thenReturn("etag-1");
        when(part1.partSize()).thenReturn(1024L);

        Part part2 = mock(Part.class);
        when(part2.partNumber()).thenReturn(2);
        when(part2.etag()).thenReturn("etag-2");
        when(part2.partSize()).thenReturn(2048L);

        when(minioClient.listParts(BUCKET, "video/clip.mp4", "upload-id-xyz"))
                .thenReturn(List.of(part1, part2));

        List<PartInfo> parts = adapter.listUploadedParts("video/clip.mp4", "upload-id-xyz");

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).partNumber()).isEqualTo(1);
        assertThat(parts.get(0).etag()).isEqualTo("etag-1");
        assertThat(parts.get(0).sizeBytes()).isEqualTo(1024L);
        assertThat(parts.get(1).partNumber()).isEqualTo(2);
        assertThat(parts.get(1).etag()).isEqualTo("etag-2");
        assertThat(parts.get(1).sizeBytes()).isEqualTo(2048L);
    }

    @Test
    void listUploadedParts_throwsMediaUploadExceptionOnFailure() throws Exception {
        when(minioClient.listParts(any(), any(), any()))
                .thenThrow(new RuntimeException("list failed"));

        assertThatThrownBy(() -> adapter.listUploadedParts("key", "uid"))
                .isInstanceOf(MediaUploadException.class)
                .hasMessageContaining("list uploaded parts");
    }
}
