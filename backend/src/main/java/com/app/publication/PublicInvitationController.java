package com.app.publication;

import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public")
public class PublicInvitationController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final com.app.templatestudio.TemplatePackageResourceService packages;

    public PublicInvitationController(JdbcTemplate jdbc, ObjectMapper mapper,
                                      com.app.templatestudio.TemplatePackageResourceService packages) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.packages = packages;
    }

    @GetMapping("/invitations/{slug}")
    public org.springframework.http.ResponseEntity<ApiResponse<?>> invitation(@PathVariable String slug) {
        var rows = jdbc.query(
                "SELECT p.published_snapshot::text AS published_snapshot,p.expires_at,p.revoked_at,v.package_resource_path " +
                "FROM invitation_publication p JOIN invitation_template_version v ON v.id=p.template_version_id WHERE p.public_slug=?",
                (rs,n) -> new java.util.HashMap<String,Object>() {{
                    put("snapshot", rs.getString("published_snapshot"));
                    put("expires", rs.getTimestamp("expires_at").toInstant());
                    put("revoked", rs.getTimestamp("revoked_at")==null?"":rs.getTimestamp("revoked_at").toInstant().toString());
                    put("packagePath", rs.getString("package_resource_path"));
                }}, slug);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        var row = rows.get(0);
        if (!row.get("revoked").equals("") || !((Instant) row.get("expires")).isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation expired or unavailable");
        }
        try {
            ObjectNode snapshot = (ObjectNode) mapper.readTree(String.valueOf(row.get("snapshot")));
            snapshot.put("publicSlug", slug);
            snapshot.put("locationQrUrl", "/api/v1/public/invitations/" + slug + "/qr");
            String locationUrl=snapshot.path("content").path("locationUrl").asText("");
            if(!locationUrl.isBlank())snapshot.put("locationQrDataUrl", InvitationQrController.dataUrl(locationUrl));
            String packagePath = (String) row.get("packagePath");
            if (packagePath != null) snapshot.set("templatePackagePreview", mapper.valueToTree(packages.previewFiles(packagePath)));
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store, private")
                    .body(ApiResponse.success(snapshot));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load this invitation's template", ex);
        }
    }
}
