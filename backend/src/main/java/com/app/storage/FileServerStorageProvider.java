package com.app.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Server-to-server adapter for FileServer's Master Drive API.
 *
 * The FileServer master key is sent only in the X-Master-Key header. It must
 * never be returned to a browser or persisted in invitation/media metadata.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.storage.fileserver.enabled", havingValue = "true")
public class FileServerStorageProvider implements StorageProvider {

    private final RestClient client;
    private final String driveId;
    private final String driveName;
    private final String uploadPath;
    private final String statusPath;
    private final String downloadPath;
    private final String deletePath;
    private final long uploadTimeoutMillis;
    private final long pollIntervalMillis;

    public FileServerStorageProvider(
            @Value("${app.storage.fileserver.base-url}") String baseUrl,
            @Value("${app.storage.fileserver.master-key}") String masterKey,
            @Value("${app.storage.fileserver.master-drive-id:}") String driveId,
            @Value("${app.storage.fileserver.master-drive-name:DigitalInvitation}") String driveName,
            @Value("${app.storage.fileserver.upload-path:/master-drive/{driveId}/files/upload}") String uploadPath,
            @Value("${app.storage.fileserver.status-path:/master-drive/uploads/{uploadId}/status}") String statusPath,
            @Value("${app.storage.fileserver.download-path:/master-drive/files/{fileId}/download}") String downloadPath,
            @Value("${app.storage.fileserver.delete-path:/master-drive/files/{fileId}}") String deletePath,
            @Value("${app.storage.fileserver.upload-timeout-seconds:300}") long uploadTimeoutSeconds,
            @Value("${app.storage.fileserver.poll-interval-millis:1000}") long pollIntervalMillis) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("FileServer Master Drive is enabled but APP_FILESERVER_BASE_URL is missing");
        }
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("FileServer Master Drive is enabled but APP_FILESERVER_MASTER_KEY is missing");
        }
        if ((driveId == null || driveId.isBlank()) && (driveName == null || driveName.isBlank())) {
            throw new IllegalStateException("Configure APP_FILESERVER_MASTER_DRIVE_ID or APP_FILESERVER_MASTER_DRIVE_NAME");
        }
        if (uploadTimeoutSeconds < 1 || pollIntervalMillis < 100) {
            throw new IllegalStateException("FileServer upload timeout and polling interval must be positive");
        }

        this.driveId = driveId == null ? "" : driveId.trim();
        this.driveName = driveName == null ? "" : driveName.trim();
        this.uploadPath = uploadPath;
        this.statusPath = statusPath;
        this.downloadPath = downloadPath;
        this.deletePath = deletePath;
        this.uploadTimeoutMillis = Duration.ofSeconds(uploadTimeoutSeconds).toMillis();
        this.pollIntervalMillis = pollIntervalMillis;
        this.client = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader("X-Master-Key", masterKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String name() {
        return "FILESERVER_MASTER_DRIVE";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StoredObject upload(String objectName, String contentType, long size, InputStream data) {
        if (size < 0 || data == null) {
            throw new IllegalArgumentException("A valid file size and input stream are required");
        }

        String resolvedDriveId = resolveDriveId();
        InputStreamResource resource = new InputStreamResource(data) {
            @Override
            public long contentLength() {
                return size;
            }

            @Override
            public String getFilename() {
                return objectName;
            }
        };

        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);

        Map<String, Object> accepted = client.post()
                .uri(uploadPath, resolvedDriveId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(Map.class);

        if (accepted == null) {
            throw new IllegalStateException("FileServer returned an empty Master Drive upload response");
        }

        String uploadId = stringValue(accepted.get("uploadId"));
        String status = stringValue(accepted.get("status"));
        String fileId = stringValue(accepted.get("fileId"));

        if (fileId != null && "COMPLETED".equalsIgnoreCase(status)) {
            return new StoredObject(fileId, objectName, contentType, size, name());
        }
        if (uploadId == null) {
            throw new IllegalStateException("FileServer upload response did not include an uploadId");
        }

        long deadline = System.nanoTime() + Duration.ofMillis(uploadTimeoutMillis).toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> progress = getUploadStatus(uploadId);
            String currentStatus = stringValue(progress.get("status"));
            if ("COMPLETED".equalsIgnoreCase(currentStatus)) {
                String completedFileId = stringValue(progress.get("fileId"));
                if (completedFileId == null) {
                    throw new IllegalStateException("FileServer marked the upload completed without returning fileId");
                }
                return new StoredObject(completedFileId, objectName, contentType, size, name());
            }
            if ("FAILED".equalsIgnoreCase(currentStatus)
                    || "CANCELLED".equalsIgnoreCase(currentStatus)
                    || "CANCELED".equalsIgnoreCase(currentStatus)) {
                String message = stringValue(progress.get("message"));
                throw new IllegalStateException(message == null
                        ? "FileServer Master Drive upload ended with status " + currentStatus
                        : "FileServer Master Drive upload failed: " + message);
            }
            sleepBeforeNextPoll();
        }

        throw new IllegalStateException("Timed out waiting for FileServer Master Drive upload completion; uploadId="
                + uploadId);
    }

    @Override
    public InputStream download(String storageKey) {
        byte[] content = client.get()
                .uri(downloadPath, storageKey)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .body(byte[].class);
        if (content == null) {
            throw new IllegalStateException("FileServer returned an empty Master Drive file response");
        }
        return new java.io.ByteArrayInputStream(content);
    }

    @Override
    public void delete(String storageKey) {
        client.delete().uri(deletePath, storageKey).retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getUploadStatus(String uploadId) {
        Map<String, Object> response = client.get()
                .uri(statusPath, uploadId)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("FileServer returned an empty upload status response");
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private String resolveDriveId() {
        if (!driveId.isBlank()) {
            return driveId;
        }
        Map<String, Object> drive = client.get()
                .uri("/master-drive/name/{driveName}", driveName)
                .retrieve()
                .body(Map.class);
        if (drive == null || stringValue(drive.get("id")) == null) {
            throw new IllegalStateException("Configured FileServer Master Drive was not found: " + driveName);
        }
        return stringValue(drive.get("id"));
    }

    private void sleepBeforeNextPoll() {
        try {
            Thread.sleep(pollIntervalMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for FileServer upload completion", ex);
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }
}
