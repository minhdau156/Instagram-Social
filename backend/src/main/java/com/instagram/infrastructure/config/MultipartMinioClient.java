package com.instagram.infrastructure.config;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.MinioAsyncClient;
import io.minio.messages.Part;

import java.util.List;

public class MultipartMinioClient extends MinioAsyncClient {

    protected MultipartMinioClient(MinioAsyncClient client) {
        super(client);
    }

    public String createMultipartUpload(String bucket, String object, String contentType) throws Exception {
        Multimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", contentType);
        return createMultipartUpload(bucket, null, object, headers, null).result().uploadId();
    }

    public void completeMultipartUpload(String bucket, String object, String uploadId, Part[] parts) throws Exception {
        completeMultipartUpload(bucket, null, object, uploadId, parts, null, null);
    }

    public void abortMultipartUpload(String bucket, String object, String uploadId) throws Exception {
        abortMultipartUpload(bucket, null, object, uploadId, null, null);
    }

    public List<Part> listParts(String bucket, String object, String uploadId) throws Exception {
        return listParts(bucket, null, object, null, null, uploadId, null, null).result().partList();
    }
}
