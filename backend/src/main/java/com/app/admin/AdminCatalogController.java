package com.app.admin;

import com.app.audit.AuditLogService;
import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogService audit;

    public AdminCatalogController(JdbcTemplate jdbc, ObjectMapper mapper, AuditLogService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories() {
        return ApiResponse.success(jdbc.queryForList(
                "SELECT id,category_key,display_name,description,enabled,sort_order,created_at,updated_at " +
                "FROM event_category ORDER BY sort_order,display_name"));
    }

    @PostMapping("/categories")
    @Transactional
    public ApiResponse<?> createCategory(@RequestBody Map<String, Object> body, Authentication auth) {
        String key = string(body.get("key")).toUpperCase(java.util.Locale.ROOT);
        String name = string(body.get("name"));
        if (!key.matches("[A-Z0-9_-]{2,80}") || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("Valid category key and name are required");
        }
        int sortOrder = integer(body.getOrDefault("sortOrder", 0), "Sort order");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO event_category(id,category_key,display_name,description,sort_order) VALUES (?,?,?,?,?)",
                id, key, name, nullableString(body.get("description")), sortOrder);
        UUID actor = UUID.fromString(auth.getName());
        audit.record(actor, "CATEGORY_CREATED", "EVENT_CATEGORY", id.toString(), "SUCCESS", Map.of("key", key));
        return ApiResponse.success(Map.of("id", id, "key", key));
    }

    @PatchMapping("/categories/{id}")
    @Transactional
    public ApiResponse<?> updateCategory(@PathVariable UUID id, @RequestBody Map<String, Object> body,
                                         Authentication auth) {
        var current = jdbc.query("SELECT category_key,display_name,description,enabled,sort_order FROM event_category WHERE id=? FOR UPDATE",
                (rs, n) -> Map.of(
                        "key", rs.getString("category_key"),
                        "name", rs.getString("display_name"),
                        "description", rs.getString("description") == null ? "" : rs.getString("description"),
                        "enabled", rs.getBoolean("enabled"),
                        "sortOrder", rs.getInt("sort_order")), id);
        if (current.isEmpty()) throw new IllegalArgumentException("Category not found");
        Map<String, Object> old = current.get(0);
        String name = body.containsKey("name") ? string(body.get("name")) : (String) old.get("name");
        String description = body.containsKey("description") ? nullableString(body.get("description")) : (String) old.get("description");
        boolean enabled = body.containsKey("enabled") ? Boolean.parseBoolean(String.valueOf(body.get("enabled"))) : (Boolean) old.get("enabled");
        int sortOrder = body.containsKey("sortOrder") ? integer(body.get("sortOrder"), "Sort order") : (Integer) old.get("sortOrder");
        if (name.isBlank() || name.length() > 120) throw new IllegalArgumentException("Category name is required and must be at most 120 characters");
        jdbc.update("UPDATE event_category SET display_name=?,description=?,enabled=?,sort_order=?,updated_at=now() WHERE id=?",
                name, description, enabled, sortOrder, id);
        audit.record(UUID.fromString(auth.getName()), "CATEGORY_UPDATED", "EVENT_CATEGORY", id.toString(), "SUCCESS",
                Map.of("enabled", enabled, "sortOrder", sortOrder));
        return ApiResponse.success(Map.of("id", id, "enabled", enabled, "sortOrder", sortOrder));
    }

    @GetMapping("/templates")
    public ApiResponse<?> templates() {
        return ApiResponse.success(jdbc.queryForList(
                "SELECT t.id,t.category_id,c.display_name AS category_name,t.template_key,t.display_name," +
                "t.description,t.status,t.current_version,v.id AS template_version_id,v.definition::text AS definition," +
                "t.updated_at FROM invitation_template t JOIN event_category c ON c.id=t.category_id " +
                "LEFT JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version " +
                "ORDER BY c.sort_order,t.display_name"));
    }

    @PostMapping("/templates")
    @Transactional
    public ApiResponse<?> createTemplate(@RequestBody Map<String, Object> body, Authentication auth) {
        UUID category = UUID.fromString(string(body.get("categoryId")));
        String key = string(body.get("key")).toLowerCase(java.util.Locale.ROOT);
        String name = string(body.get("name"));
        Object definition = body.get("definition");
        if (!key.matches("[a-z0-9_-]{2,100}") || name.isBlank() || name.length() > 160 || !(definition instanceof Map)) {
            throw new IllegalArgumentException("Template key, name and JSON definition are required");
        }
        Integer categoryExists = jdbc.queryForObject("SELECT count(*) FROM event_category WHERE id=? AND enabled=true",
                Integer.class, category);
        if (categoryExists == null || categoryExists != 1) throw new IllegalArgumentException("Choose an enabled category");
        UUID id = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID actor = UUID.fromString(auth.getName());
        try {
            jdbc.update("INSERT INTO invitation_template(id,category_id,template_key,display_name,description,status,current_version) VALUES (?,?,?,?,?,'PUBLISHED',1)",
                    id, category, key, name, nullableString(body.get("description")));
            jdbc.update("INSERT INTO invitation_template_version(id,template_id,version_number,definition,created_by) VALUES (?,?,1,?::jsonb,?)",
                    version, id, mapper.writeValueAsString(definition), actor);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to create template. Check its key and JSON definition");
        }
        audit.record(actor, "TEMPLATE_CREATED", "TEMPLATE", id.toString(), "SUCCESS", Map.of("key", key));
        return ApiResponse.success(Map.of("id", id, "versionId", version, "versionNumber", 1));
    }

    @PostMapping("/templates/{id}/versions")
    @Transactional
    public ApiResponse<?> addVersion(@PathVariable UUID id, @RequestBody Map<String, Object> body,
                                     Authentication auth) {
        Object definition = body.get("definition");
        if (!(definition instanceof Map)) throw new IllegalArgumentException("Template definition must be a JSON object");
        var rows = jdbc.query("SELECT current_version FROM invitation_template WHERE id=? FOR UPDATE",
                (rs, n) -> rs.getInt(1), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Template not found");
        int next = rows.get(0) + 1;
        UUID version = UUID.randomUUID();
        UUID actor = UUID.fromString(auth.getName());
        try {
            jdbc.update("INSERT INTO invitation_template_version(id,template_id,version_number,definition,created_by) VALUES (?,?,?,?::jsonb,?)",
                    version, id, next, mapper.writeValueAsString(definition), actor);
            jdbc.update("UPDATE invitation_template SET current_version=?,updated_at=now() WHERE id=?", next, id);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to create template version");
        }
        audit.record(actor, "TEMPLATE_VERSION_CREATED", "TEMPLATE", id.toString(), "SUCCESS",
                Map.of("versionNumber", next));
        return ApiResponse.success(Map.of("versionId", version, "versionNumber", next));
    }

    @PatchMapping("/templates/{id}/status")
    @Transactional
    public ApiResponse<?> templateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body,
                                         Authentication auth) {
        String status = body.get("status");
        if (!Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw new IllegalArgumentException("Invalid template status");
        }
        int changed = jdbc.update("UPDATE invitation_template SET status=?,updated_at=now() WHERE id=?", status, id);
        if (changed == 0) throw new IllegalArgumentException("Template not found");
        audit.record(UUID.fromString(auth.getName()), "TEMPLATE_STATUS_CHANGED", "TEMPLATE", id.toString(),
                "SUCCESS", Map.of("status", status));
        return ApiResponse.success(Map.of("id", id, "status", status));
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullableString(Object value) {
        String result = string(value);
        return result.isBlank() ? null : result;
    }

    private static int integer(Object value, String label) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " must be a whole number");
        }
    }
}
