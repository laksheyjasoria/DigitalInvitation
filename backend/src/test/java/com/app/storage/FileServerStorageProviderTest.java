package com.app.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FileServerStorageProviderTest {

    private static final String BASE_URL = "https://fileserver.test";
    private static final String MASTER_KEY = "test-master-key";

    @Test
    void uploadResolvesDriveByNameAndPollsUntilFileIdIsAvailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FileServerStorageProvider provider = provider(builder, "", "DigitalInvitation");

        server.expect(requestTo(BASE_URL + "/master-drive/name/DigitalInvitation"))
                .andExpect(method(GET))
                .andExpect(header("X-Master-Key", MASTER_KEY))
                .andRespond(withSuccess("{\"id\":\"drive-123\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + "/master-drive/drive-123/files/upload"))
                .andExpect(method(POST))
                .andExpect(header("X-Master-Key", MASTER_KEY))
                .andRespond(withAccepted()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"uploadId\":\"upload-123\",\"status\":\"IN_PROGRESS\"}"));

        server.expect(requestTo(BASE_URL + "/master-drive/uploads/upload-123/status"))
                .andExpect(method(GET))
                .andExpect(header("X-Master-Key", MASTER_KEY))
                .andRespond(withSuccess(
                        "{\"uploadId\":\"upload-123\",\"fileId\":\"file-456\",\"status\":\"COMPLETED\"}",
                        MediaType.APPLICATION_JSON));

        StoredObject stored = provider.upload(
                "invitation-photo.png",
                "image/png",
                4,
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

        assertEquals("file-456", stored.storageKey());
        assertEquals("invitation-photo.png", stored.originalFilename());
        assertEquals("image/png", stored.contentType());
        assertEquals(4, stored.sizeBytes());
        assertEquals("FILESERVER_MASTER_DRIVE", stored.provider());
        server.verify();
    }

    @Test
    void uploadUsesConfiguredDriveIdWithoutNameLookup() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FileServerStorageProvider provider = provider(builder, "drive-fixed", "DigitalInvitation");

        server.expect(requestTo(BASE_URL + "/master-drive/drive-fixed/files/upload"))
                .andExpect(method(POST))
                .andExpect(header("X-Master-Key", MASTER_KEY))
                .andRespond(withSuccess(
                        "{\"uploadId\":\"upload-fixed\",\"status\":\"IN_PROGRESS\"}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + "/master-drive/uploads/upload-fixed/status"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"uploadId\":\"upload-fixed\",\"fileId\":\"file-fixed\",\"status\":\"COMPLETED\"}",
                        MediaType.APPLICATION_JSON));

        StoredObject stored = provider.upload(
                "photo.webp", "image/webp", 1, new ByteArrayInputStream(new byte[] {9}));

        assertEquals("file-fixed", stored.storageKey());
        server.verify();
    }

    @Test
    void uploadFailsWhenFileServerReportsFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FileServerStorageProvider provider = provider(builder, "drive-fixed", "DigitalInvitation");

        server.expect(requestTo(BASE_URL + "/master-drive/drive-fixed/files/upload"))
                .andExpect(method(POST))
                .andRespond(withAccepted()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"uploadId\":\"upload-failed\",\"status\":\"IN_PROGRESS\"}"));

        server.expect(requestTo(BASE_URL + "/master-drive/uploads/upload-failed/status"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"uploadId\":\"upload-failed\",\"status\":\"FAILED\",\"message\":\"Telegram unavailable\"}",
                        MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> provider.upload(
                "photo.jpg", "image/jpeg", 1, new ByteArrayInputStream(new byte[] {1})));

        assertEquals("FileServer Master Drive upload failed: Telegram unavailable", error.getMessage());
        server.verify();
    }

    @Test
    void downloadAndDeleteUseTheConfiguredMasterDriveRoutes() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FileServerStorageProvider provider = provider(builder, "drive-fixed", "DigitalInvitation");
        byte[] expected = "image-content".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo(BASE_URL + "/master-drive/files/file-789/download"))
                .andExpect(method(GET))
                .andExpect(header("X-Master-Key", MASTER_KEY))
                .andRespond(withSuccess(expected, MediaType.APPLICATION_OCTET_STREAM));

        server.expect(requestTo(BASE_URL + "/master-drive/files/file-789"))
                .andExpect(method(DELETE))
                .andExpect(header("X-Master-Key", MASTER_KEY))
                .andRespond(withNoContent());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var input = provider.download("file-789")) {
            input.transferTo(output);
        }
        assertArrayEquals(expected, output.toByteArray());

        provider.delete("file-789");
        server.verify();
    }

    @Test
    void rejectsMissingMasterKeyAndInvalidApiPaths() {
        assertThrows(IllegalStateException.class, () -> new FileServerStorageProvider(
                RestClient.builder(), BASE_URL, " ", "drive", "DigitalInvitation",
                "/master-drive/{driveId}/files/upload",
                "/master-drive/uploads/{uploadId}/status",
                "/master-drive/files/{fileId}/download",
                "/master-drive/files/{fileId}", 30, 100));

        assertThrows(IllegalStateException.class, () -> new FileServerStorageProvider(
                RestClient.builder(), BASE_URL, MASTER_KEY, "drive", "DigitalInvitation",
                "https://attacker.test/upload",
                "/master-drive/uploads/{uploadId}/status",
                "/master-drive/files/{fileId}/download",
                "/master-drive/files/{fileId}", 30, 100));

        assertThrows(IllegalStateException.class, () -> new FileServerStorageProvider(
                RestClient.builder(), BASE_URL, MASTER_KEY, "drive", "DigitalInvitation",
                "//attacker.test/upload",
                "/master-drive/uploads/{uploadId}/status",
                "/master-drive/files/{fileId}/download",
                "/master-drive/files/{fileId}", 30, 100));
    }

    private FileServerStorageProvider provider(
            RestClient.Builder builder, String driveId, String driveName) {
        return new FileServerStorageProvider(
                builder,
                BASE_URL,
                MASTER_KEY,
                driveId,
                driveName,
                "/master-drive/{driveId}/files/upload",
                "/master-drive/uploads/{uploadId}/status",
                "/master-drive/files/{fileId}/download",
                "/master-drive/files/{fileId}",
                5,
                100);
    }
}
