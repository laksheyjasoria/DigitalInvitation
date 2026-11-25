package com.app.catalog;

import com.app.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final JdbcTemplate jdbc;
    public CatalogController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/categories") public ApiResponse<?> categories(){return ApiResponse.success(jdbc.queryForList("SELECT id,category_key,display_name,description FROM event_category WHERE enabled=true ORDER BY sort_order,display_name"));}
    @GetMapping("/categories/{categoryId}/templates") public ApiResponse<?> templates(@PathVariable UUID categoryId){return ApiResponse.success(jdbc.queryForList("SELECT t.id,t.template_key,t.display_name,t.description,t.current_version,v.id AS template_version_id,v.version_number FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version WHERE t.category_id=? AND t.status='PUBLISHED' ORDER BY t.display_name",categoryId));}
    @GetMapping("/templates/{templateId}") public ApiResponse<?> template(@PathVariable UUID templateId){return ApiResponse.success(jdbc.queryForMap("SELECT t.id,t.display_name,t.description,v.id AS template_version_id,v.version_number,v.definition FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version WHERE t.id=? AND t.status='PUBLISHED'",templateId));}
}
