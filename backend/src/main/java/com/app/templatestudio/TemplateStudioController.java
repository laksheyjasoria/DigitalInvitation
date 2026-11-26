package com.app.templatestudio;

import com.app.audit.AuditLogService;
import com.app.common.api.ApiResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/template-studio")
public class TemplateStudioController {
    private static final int MAX_FILES = 150;
    private static final long MAX_EXPANDED_BYTES = 25L * 1024 * 1024;
    private static final int MAX_ARCHIVE_BYTES = 10 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final TemplatePackageValidator validator;
    private final AuditLogService audit;
    private final TemplateDefinitionMapper definitionMapper;

    public TemplateStudioController(JdbcTemplate jdbc, TemplatePackageValidator validator, AuditLogService audit, TemplateDefinitionMapper definitionMapper) {
        this.jdbc = jdbc;
        this.validator = validator;
        this.audit = audit;
        this.definitionMapper = definitionMapper;
    }

    @GetMapping("/packages")
    public ApiResponse<?> packages() {
        return ApiResponse.success(jdbc.queryForList(
                "SELECT id,template_id,template_name,package_version,status,file_count,expanded_bytes,sha256,uploaded_by,created_at,validated_at " +
                "FROM template_package_stage ORDER BY created_at DESC"));
    }

    @GetMapping("/packages/{id}/preview")
    public ApiResponse<?> preview(@PathVariable UUID id) {
        List<byte[]> rows = jdbc.query("SELECT archive_bytes FROM template_package_stage WHERE id=? AND status IN ('VALIDATED','QUEUED_FOR_RELEASE','RELEASED')", (rs,n) -> rs.getBytes(1), id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Validated staged template package not found");
        return ApiResponse.success(definitionMapper.map(rows.get(0)));
    }

    @PostMapping(value = "/packages/files", consumes = "multipart/form-data")
    @Transactional
    public ApiResponse<?> uploadFiles(@RequestParam("files") List<MultipartFile> files, Authentication authentication) throws Exception {
        if (files == null || files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Select between 1 and 150 template files");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        long expandedBytes = 0;
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (MultipartFile part : files) {
                if (part == null || part.isEmpty()) {
                    throw new IllegalArgumentException("Every selected file must have content");
                }
                String name = safeArchiveEntryName(part.getOriginalFilename());
                if (!names.add(name)) {
                    throw new IllegalArgumentException("Duplicate template file: " + name);
                }

                zip.putNextEntry(new ZipEntry(name));
                try (InputStream input = part.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        expandedBytes += read;
                        if (expandedBytes > MAX_EXPANDED_BYTES) {
                            throw new IllegalArgumentException("Combined template files must not exceed 25 MB");
                        }
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
                zip.flush();
                if (bytes.size() > MAX_ARCHIVE_BYTES) {
                    throw new IllegalArgumentException("Generated template ZIP exceeds 10 MB; optimize the assets or upload a ZIP directly");
                }
            }
        }

        byte[] archive = bytes.toByteArray();
        TemplatePackageValidator.ValidationResult result = validator.validate(archive);
        UUID id = UUID.randomUUID();
        UUID actor = UUID.fromString(authentication.getName());
        jdbc.update("INSERT INTO template_package_stage(id,template_id,template_name,package_version,archive_bytes,status,file_count,expanded_bytes,sha256,uploaded_by,validated_at) VALUES (?,?,?,?,?,'VALIDATED',?,?,?,?,now())",
                id, result.templateId(), result.templateName(), result.version(), archive, result.fileCount(), result.expandedBytes(), result.sha256(), actor);
        audit.record(actor, "TEMPLATE_FILES_STAGED", "TEMPLATE_PACKAGE", id.toString(), "SUCCESS",
                Map.of("templateId", result.templateId(), "version", result.version(), "sha256", result.sha256()));
        return ApiResponse.success(Map.of("id", id, "templateId", result.templateId(), "name", result.templateName(),
                "version", result.version(), "status", "VALIDATED", "fileCount", result.fileCount(), "sha256", result.sha256()));
    }

    static String safeArchiveEntryName(String originalName) {
        if (originalName == null || originalName.isBlank() || originalName.length() > 180
                || originalName.startsWith("/") || originalName.startsWith("\\")
                || originalName.contains("\\") || originalName.contains(":")
                || originalName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Template file has an unsafe path");
        }
        String[] segments = originalName.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Template file has an unsafe path: " + originalName);
            }
        }
        return originalName;
    }

    @PostMapping(value = "/packages", consumes = "multipart/form-data")
    @Transactional
    public ApiResponse<?> upload(@RequestPart("file") MultipartFile file, Authentication authentication) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > MAX_ARCHIVE_BYTES) {
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
            return ApiResponse.failure("TEMPLATE_VALIDATION_FAILED", safeMessage(ex));
        }
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null ? "Template validation failed" : message.substring(0, Math.min(message.length(), 500));
    }
}
