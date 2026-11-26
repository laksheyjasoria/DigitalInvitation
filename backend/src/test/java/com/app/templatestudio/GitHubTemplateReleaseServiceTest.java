package com.app.templatestudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitHubTemplateReleaseServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsReleaseBranchAndCommitsThePackageBlob() throws Exception {
        try (StubGitHub github = new StubGitHub(false)) {
            GitHubTemplateReleaseService service = service(github);
            byte[] zip = new byte[] {1, 2, 3, 4};
            var result = service.commitPackage("TEMP0000001", "1.0.0", "a".repeat(64), zip);

            assertEquals("commit-sha", result.commitSha());
            assertEquals("template-releases", result.branch());
            assertEquals("backend/src/main/resources/templates/packages/TEMP0000001/1.0.0.zip", result.resourcePath());
            assertTrue(github.requests.stream().anyMatch(r -> r.contains("POST /repos/test/repo/git/refs")));
            assertTrue(github.requests.stream().anyMatch(r -> r.contains(Base64.getEncoder().encodeToString(zip))));
            assertTrue(github.requests.stream().anyMatch(r -> r.contains("backend/src/main/resources/templates/packages/TEMP0000001/1.0.0.zip")));
        }
    }

    @Test
    void synchronizesExistingReleaseBranchWithMainBeforeCommitting() throws Exception {
        try (StubGitHub github = new StubGitHub(true)) {
            GitHubTemplateReleaseService service = service(github);
            var result = service.commitPackage("TEMP0000002", "2.0.0", "b".repeat(64), new byte[] {9});

            assertEquals("commit-sha", result.commitSha());
            assertTrue(github.requests.stream().anyMatch(r -> r.contains("POST /repos/test/repo/merges")));
            assertTrue(github.requests.stream().anyMatch(r -> r.contains("GET /repos/test/repo/git/commits/synced-sha")));
        }
    }

    @Test
    void refusesPublishingWhenGitHubCredentialsAreNotConfigured() {
        GitHubTemplateReleaseService service = new GitHubTemplateReleaseService(
                mapper, "", "test/repo", "template-releases", "main", "http://localhost:9999", true);
        assertThrows(IllegalStateException.class,
                () -> service.commitPackage("TEMP0000001", "1.0.0", "a".repeat(64), new byte[] {1}));
    }

    private GitHubTemplateReleaseService service(StubGitHub github) {
        return new GitHubTemplateReleaseService(mapper, "test-token", "test/repo",
                "template-releases", "main", github.baseUrl(), true);
    }

    private static final class StubGitHub implements AutoCloseable {
        private final HttpServer server;
        private final List<String> requests = new ArrayList<>();
        private final AtomicInteger releaseRefCalls = new AtomicInteger();
        private final boolean branchExists;

        private StubGitHub(boolean branchExists) throws Exception {
            this.branchExists = branchExists;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(method + " " + path + " " + body);
                int status = 200;
                String response = "{}";

                if (path.endsWith("/git/ref/heads/template-releases") && method.equals("GET")) {
                    int call = releaseRefCalls.incrementAndGet();
                    if (!branchExists) {
                        status = 404;
                        response = "{\"message\":\"Not Found\"}";
                    } else {
                        String sha = call == 1 ? "release-sha" : "synced-sha";
                        response = "{\"object\":{\"sha\":\"" + sha + "\"}}";
                    }
                } else if (path.endsWith("/git/ref/heads/main") && method.equals("GET")) {
                    response = "{\"object\":{\"sha\":\"base-sha\"}}";
                } else if (path.endsWith("/git/refs") && method.equals("POST")) {
                    status = 201;
                    response = "{\"object\":{\"sha\":\"base-sha\"}}";
                } else if (path.endsWith("/merges") && method.equals("POST")) {
                    status = 204;
                    response = "";
                } else if (path.endsWith("/git/commits/base-sha") || path.endsWith("/git/commits/synced-sha")) {
                    response = "{\"tree\":{\"sha\":\"base-tree\"}}";
                } else if (path.endsWith("/git/blobs") && method.equals("POST")) {
                    status = 201;
                    response = "{\"sha\":\"blob-sha\"}";
                } else if (path.endsWith("/git/trees") && method.equals("POST")) {
                    status = 201;
                    response = "{\"sha\":\"tree-sha\"}";
                } else if (path.endsWith("/git/commits") && method.equals("POST")) {
                    status = 201;
                    response = "{\"sha\":\"commit-sha\"}";
                } else if (path.endsWith("/git/refs/heads/template-releases") && method.equals("PATCH")) {
                    response = "{\"object\":{\"sha\":\"commit-sha\"}}";
                } else {
                    status = 404;
                    response = "{\"message\":\"Unexpected test request: " + method + " " + path + "\"}";
                }

                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                if (status == 204) {
                    exchange.sendResponseHeaders(status, -1);
                } else {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
                exchange.close();
            });
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
