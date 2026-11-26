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
    private final com.app.templatestudio.TemplatePackageResourceService packages;
    public CatalogController(JdbcTemplate jdbc, com.app.templatestudio.TemplatePackageResourceService packages){this.jdbc=jdbc;this.packages=packages;}
    @GetMapping("/categories") public ApiResponse<?> categories(){return ApiResponse.success(jdbc.queryForList("SELECT id,category_key,display_name,description FROM event_category WHERE enabled=true ORDER BY sort_order,display_name"));}
    @GetMapping("/categories/{categoryId}/templates") public ApiResponse<?> templates(@PathVariable("categoryId") UUID categoryId){
        var rows=jdbc.queryForList("SELECT t.id,t.category_id,t.template_key,t.display_name,t.description,t.current_version,v.id AS template_version_id,v.version_number,v.package_resource_path FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version WHERE t.category_id=? AND t.status='PUBLISHED' ORDER BY t.display_name",categoryId);
        rows.removeIf(row->{String path=(String)row.get("package_resource_path");return path!=null&&!packages.isAvailable(path);});
        rows.forEach(row->row.remove("package_resource_path"));
        return ApiResponse.success(rows);
    }
    @GetMapping("/template-versions/{versionId}") public ApiResponse<?> templateVersion(@PathVariable("versionId") UUID versionId){
        var rows=jdbc.query("SELECT t.id,t.category_id,t.display_name,t.description,v.id AS template_version_id,v.version_number,v.definition::text AS definition,v.package_resource_path FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id WHERE v.id=?",
            (rs,n)->{java.util.Map<String,Object> row=new java.util.LinkedHashMap<>();row.put("id",rs.getObject("id"));row.put("category_id",rs.getObject("category_id"));row.put("display_name",rs.getString("display_name"));row.put("description",rs.getString("description"));row.put("template_version_id",rs.getObject("template_version_id"));row.put("version_number",rs.getInt("version_number"));row.put("definition",rs.getString("definition"));row.put("package_resource_path",rs.getString("package_resource_path"));return row;},versionId);
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Template version not found");
        var row=rows.get(0);String path=(String)row.remove("package_resource_path");
        if(path!=null){row.put("packagePreview",packages.previewFiles(path));row.put("packageSampleContent",packages.sampleContent(path));}
        return ApiResponse.success(row);
    }
    @GetMapping("/templates/{templateId}") public ApiResponse<?> template(@PathVariable("templateId") UUID templateId){
        var rows=jdbc.query("SELECT t.id,t.display_name,t.description,v.id AS template_version_id,v.version_number,v.definition::text AS definition,v.package_resource_path FROM invitation_template t JOIN invitation_template_version v ON v.template_id=t.id AND v.version_number=t.current_version WHERE t.id=? AND t.status='PUBLISHED'",
            (rs,n)->{java.util.Map<String,Object> row=new java.util.LinkedHashMap<>();row.put("id",rs.getObject("id"));row.put("display_name",rs.getString("display_name"));row.put("description",rs.getString("description"));row.put("template_version_id",rs.getObject("template_version_id"));row.put("version_number",rs.getInt("version_number"));row.put("definition",rs.getString("definition"));row.put("package_resource_path",rs.getString("package_resource_path"));return row;},templateId);
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Template not found");
        var row=rows.get(0);String path=(String)row.remove("package_resource_path");
        if(path!=null)row.put("packagePreview",packages.previewFiles(path));
        return ApiResponse.success(row);
    }
}
