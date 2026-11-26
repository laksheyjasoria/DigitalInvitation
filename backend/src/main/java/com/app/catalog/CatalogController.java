package com.app.catalog;

import com.app.common.api.ApiResponse;
import com.app.template.TemplatePackageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TemplatePackageService packages;

    public CatalogController(JdbcTemplate jdbc,ObjectMapper mapper,TemplatePackageService packages) {
        this.jdbc=jdbc; this.mapper=mapper; this.packages=packages;
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories() {
        return ApiResponse.success(jdbc.queryForList("SELECT id,category_key,display_name,description FROM event_category WHERE enabled=true ORDER BY sort_order,display_name"));
    }

    @GetMapping("/categories/{categoryId}/templates")
    public ApiResponse<?> templates(@PathVariable("categoryId") UUID categoryId) {
        return ApiResponse.success(jdbc.queryForList(
            "SELECT t.id,t.category_id,t.template_key,t.display_name,t.description,t.current_version,"+
            "v.id AS template_version_id,v.version_number,v.definition_format,v.package_path "+
            "FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version "+
            "WHERE t.category_id=? AND t.status='PUBLISHED' ORDER BY t.display_name",categoryId));
    }

    @GetMapping("/templates/{templateId}")
    public ApiResponse<?> template(@PathVariable("templateId") UUID templateId) {
        Map<String,Object> row=jdbc.queryForMap(
            "SELECT t.id,t.display_name,t.description,v.id AS template_version_id,v.version_number,"+
            "v.definition::text AS definition,v.definition_format,v.package_path "+
            "FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version "+
            "WHERE t.id=? AND t.status='PUBLISHED'",templateId);
        if("XML_PACKAGE".equals(row.get("definition_format"))) {
            String path=String.valueOf(row.get("package_path"));
            String packageId=path.substring(path.lastIndexOf('/')+1);
            Map<String,Object> definition=packages.getTemplate(packageId);
            try {
                Map<String,Object> legacy=mapper.readValue(String.valueOf(row.get("definition")),new TypeReference<Map<String,Object>>() {});
                definition.put("design",legacy.get("design"));
            } catch(Exception ex) {
                throw new IllegalStateException("Unable to load template design metadata",ex);
            }
            row.put("definition",definition);
            row.put("preview_url",definition.get("previewUrl"));
        }
        return ApiResponse.success(row);
    }
}