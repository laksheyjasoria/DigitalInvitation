package com.app.templatestudio;

import com.app.audit.AuditLogService;
import com.app.common.api.ApiResponse;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/template-studio")
public class TemplateStudioController {
    private final JdbcTemplate jdbc;
    private final TemplatePackageValidator validator;
    private final AuditLogService audit;

    public TemplateStudioController(JdbcTemplate jdbc, TemplatePackageValidator validator, AuditLogService audit) {
        this.jdbc = jdbc;
        this.validator = validator;
        this.audit = audit;
    }

    @GetMapping("/packages")
    public ApiResponse<?> packages() {
        return ApiResponse.success(jdbc.queryForList(
                "SELECT id,template_id,template_name,package_version,status,file_count,expanded_bytes,sha256,uploaded_by,created_at,validated_at " +
                "FROM template_package_stage ORDER BY created_at DESC"));
    }

    @PostMapping(value = "/packages", consumes = "multipart/form-data")
    @Transactional
    public ApiResponse<?> upload(@RequestPart("file") MultipartFile file, Authentication authentication) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("Choose a template ZIP smaller than 10 MB");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP template packages are accepted");
        }
        byte[] bytes = file.getBytes();
        TemplatePackageValidator.ValidationResult result = validator.validate(bytes);
        UUID id = UUID.randomUUID();
        UUID actor = UUID.fromString(authentication.getName());
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO template_package_stage(id,template_id,template_name,package_version,archive_bytes,status,file_count,expanded_bytes,sha256,uploaded_by,validated_at) " +
                    "VALUES (?,?,?,?,?,'VALIDATED',?,?,?,?,now())");
            statement.setObject(1, id);
            statement.setString(2, result.templateId());
            statement.setString(3, result.templateName());
            statement.setString(4, result.version());
            statement.setBytes(5, bytes);
            statement.setInt(6, result.fileCount());
            statement.setInt(7, result.expandedBytes());
            statement.setString(8, result.sha256());
            statement.setObject(9, actor);
            return statement;
        });
        audit.record(actor, "TEMPLATE_PACKAGE_STAGED", "TEMPLATE_PACKAGE", id.toString(), "SUCCESS",
                Map.of("templateId", result.templateId(), "version", result.version(), "sha256", result.sha256()));
        return ApiResponse.success(Map.of("id", id, "templateId", result.templateId(), "name", result.templateName(),
                "version", result.version(), "status", "VALIDATED", "fileCount", result.fileCount(),
                "expandedBytes", result.expandedBytes(), "sha256", result.sha256()));
    }

    @PostMapping("/packages/{id}/revalidate")
    @Transactional
    public ApiResponse<?> revalidate(@PathVariable UUID id, Authentication authentication) {
        List<byte[]> rows = jdbc.query("SELECT archive_bytes FROM template_package_stage WHERE id=?",
                (rs, n) -> rs.getBytes(1), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Staged template package not found");
        try {
            var result = validator.validate(rows.get(0));
            jdbc.update("UPDATE template_package_stage SET status='VALIDATED',validated_at=now(),validation_error=NULL WHERE id=?", id);
            audit.record(UUID.fromString(authentication.getName()), "TEMPLATE_PACKAGE_REVALIDATED", "TEMPLATE_PACKAGE",
                    id.toString(), "SUCCESS", Map.of("sha256", result.sha256()));
            return ApiResponse.success(Map.of("id", id, "status", "VALIDATED", "templateId", result.templateId(),
                    "version", result.version(), "sha256", result.sha256()));
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE template_package_stage SET status='VALIDATION_FAILED',validation_error=?,validated_at=now() WHERE id=?",
                    safeMessage(ex), id);
            throw ex;
        }
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null ? "Template validation failed" : message.substring(0, Math.min(message.length(), 500));
    }
}
