package com.instagram.adapter.in.web.dto.response;

import java.util.List;

public record InitiateUploadResponse(
        String uploadId,
        String objectKey,
        List<String> partUrls // one presigned PUT URL per part, index 0 = part 1
) {
}
