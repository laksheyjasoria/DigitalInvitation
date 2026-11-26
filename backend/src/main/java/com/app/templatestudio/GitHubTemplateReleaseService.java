package com.app.templatestudio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GitHubTemplateReleaseService {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String token;
    private final String repository;
    private final String branch;
    private final String baseBranch;
    private final String apiBase;
    private final boolean enabled;

    public GitHubTemplateReleaseService(
            ObjectMapper mapper,
            @Value("${APP_GITHUB_TOKEN:}") String token,
            @Value("${APP_GITHUB_REPOSITORY:laksheyjasoria/DigitalInvitation}") String repository,
            @Value("${APP_GITHUB_TEMPLATE_RELEASE_BRANCH:template-releases}") String branch,
            @Value("${APP_GITHUB_TEMPLATE_BASE_BRANCH:feature/platform-implementation}") String baseBranch,
            @Value("${APP_GITHUB_API_BASE:https://api.github.com}") String apiBase,
            @Value("${APP_GITHUB_TEMPLATE_RELEASES_ENABLED:false}") boolean enabled) {
        this.mapper = mapper;
        this.token = token == null ? "" : token.trim();
        this.repository = repository;
        this.branch = branch;
        this.baseBranch = baseBranch;
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.enabled = enabled;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public ReleaseCommit commitPackage(String templateId, String version, String sha256, byte[] archive) {
        if (!enabled || token.isBlank()) {
            throw new IllegalStateException("GitHub template publishing is not configured. Set APP_GITHUB_TEMPLATE_RELEASES_ENABLED=true and provide APP_GITHUB_TOKEN with Contents read/write access.");
        }
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                || !branch.matches("[A-Za-z0-9._/-]{1,100}")
                || !baseBranch.matches("[A-Za-z0-9._/-]{1,100}")) {
            throw new IllegalStateException("GitHub template release repository or branch configuration is invalid");
        }
        String path = "backend/src/main/resources/templates/packages/" + templateId + "/" + version + ".zip";
        try {
            JsonNode ref = ensureReleaseBranch();
            String parentSha = ref.path("object").path("sha").asText();
            JsonNode parentCommit = request("GET", "/repos/" + repository + "/git/commits/" + parentSha, null, 200);
            String baseTree = parentCommit.path("tree").path("sha").asText();
            if (parentSha.isBlank() || baseTree.isBlank()) throw new IllegalStateException("GitHub returned an incomplete release branch reference");

            JsonNode blob = request("POST", "/repos/" + repository + "/git/blobs",
                    Map.of("content", Base64.getEncoder().encodeToString(archive), "encoding", "base64"), 201);
            String blobSha = blob.path("sha").asText();
            JsonNode tree = request("POST", "/repos/" + repository + "/git/trees",
                    Map.of("base_tree", baseTree, "tree", List.of(Map.of(
                            "path", path, "mode", "100644", "type", "blob", "sha", blobSha))), 201);
            String treeSha = tree.path("sha").asText();
            JsonNode commit = request("POST", "/repos/" + repository + "/git/commits",
                    Map.of("message", "Publish template " + templateId + " version " + version + " (" + sha256 + ")",
                            "tree", treeSha, "parents", List.of(parentSha)), 201);
            String commitSha = commit.path("sha").asText();
            request("PATCH", "/repos/" + repository + "/git/refs/heads/" + branch,
                    Map.of("sha", commitSha, "force", false), 200);
            return new ReleaseCommit(commitSha, path, branch);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to commit the template package to the GitHub release branch: " + safeMessage(ex), ex);
        }
    }

    private JsonNode ensureReleaseBranch() throws Exception {
        String refPath = "/repos/" + repository + "/git/ref/heads/" + branch;
        HttpResponse<String> existing = send("GET", refPath, null);
        if (existing.statusCode() == 200) {
            HttpResponse<String> sync = send("POST", "/repos/" + repository + "/merges",
                    Map.of("base", branch, "head", baseBranch,
                            "commit_message", "Sync application changes before publishing a template"));
            if (sync.statusCode() != 201 && sync.statusCode() != 204) throw responseError(sync);
            HttpResponse<String> refreshed = send("GET", refPath, null);
            if (refreshed.statusCode() != 200) throw responseError(refreshed);
            return mapper.readTree(refreshed.body());
        }
        if (existing.statusCode() != 404) throw responseError(existing);

        JsonNode base = request("GET", "/repos/" + repository + "/git/ref/heads/" + baseBranch, null, 200);
        String baseSha = base.path("object").path("sha").asText();
        if (baseSha.isBlank()) throw new IllegalStateException("Configured GitHub base branch does not exist");
        JsonNode created = request("POST", "/repos/" + repository + "/git/refs",
                Map.of("ref", "refs/heads/" + branch, "sha", baseSha), 201);
        return created;
    }

    private JsonNode request(String method, String path, Object body, int expectedStatus) throws Exception {
        HttpResponse<String> response = send(method, path, body);
        if (response.statusCode() != expectedStatus) throw responseError(response);
        return response.body() == null || response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(apiBase + path))
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private IllegalStateException responseError(HttpResponse<String> response) {
        String detail = "GitHub API returned HTTP " + response.statusCode();
        try {
            JsonNode body = mapper.readTree(response.body());
            String message = body.path("message").asText("");
            if (!message.isBlank()) detail += ": " + message;
        } catch (Exception ignored) { }
        return new IllegalStateException(detail);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    public record ReleaseCommit(String commitSha, String resourcePath, String branch) { }
}
