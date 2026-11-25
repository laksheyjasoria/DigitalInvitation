package com.app.admin;

import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final com.app.audit.AuditLogService audit;
    public AdminCatalogController(JdbcTemplate jdbc,ObjectMapper mapper,com.app.audit.AuditLogService audit){this.jdbc=jdbc;this.mapper=mapper;this.audit=audit;}
    @PostMapping("/categories") public ApiResponse<?> createCategory(@RequestBody Map<String,String> b){
        String key=b.getOrDefault("key","").trim().toUpperCase(),name=b.getOrDefault("name","").trim();
        if(!key.matches("[A-Z0-9_-]{2,80}")||name.isBlank()||name.length()>120)throw new IllegalArgumentException("Valid category key and name are required");
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO event_category(id,category_key,display_name,description,sort_order) VALUES (?,?,?,?,?)",id,key,name,b.get("description"),Integer.parseInt(b.getOrDefault("sortOrder","0")));
        return ApiResponse.success(Map.of("id",id,"key",key));
    }
    @PostMapping("/templates") public ApiResponse<?> createTemplate(@RequestBody Map<String,Object> b,Authentication auth){
        UUID category=UUID.fromString(String.valueOf(b.get("categoryId")));String key=String.valueOf(b.get("key")).trim().toLowerCase(),name=String.valueOf(b.get("name")).trim();
        Object definition=b.get("definition");if(!key.matches("[a-z0-9_-]{2,100}")||name.isBlank()||!(definition instanceof Map))throw new IllegalArgumentException("Template key, name and JSON definition are required");
        UUID id=UUID.randomUUID(),version=UUID.randomUUID();
        try{jdbc.update("INSERT INTO invitation_template(id,category_id,template_key,display_name,description,status,current_version) VALUES (?,?,?,?,?,'PUBLISHED',1)",id,category,key,name,b.get("description"));
            jdbc.update("INSERT INTO invitation_template_version(id,template_id,version_number,definition,created_by) VALUES (?,?,1,?::jsonb,?)",version,id,mapper.writeValueAsString(definition),UUID.fromString(auth.getName()));
        }catch(Exception e){throw new IllegalArgumentException("Unable to create template");}
        audit.record(UUID.fromString(auth.getName()),"TEMPLATE_CREATED","TEMPLATE",id.toString(),"SUCCESS",Map.of("key",key));
        return ApiResponse.success(Map.of("id",id,"versionId",version));
    }
    @PostMapping("/templates/{id}/versions") public ApiResponse<?> addVersion(@PathVariable UUID id,@RequestBody Map<String,Object> b,Authentication auth){
        Object definition=b.get("definition");if(!(definition instanceof Map))throw new IllegalArgumentException("Template definition is required");
        Integer current=jdbc.queryForObject("SELECT current_version FROM invitation_template WHERE id=?",Integer.class,id);if(current==null)throw new IllegalArgumentException("Template not found");
        int next=current+1;UUID version=UUID.randomUUID();
        try{jdbc.update("INSERT INTO invitation_template_version(id,template_id,version_number,definition,created_by) VALUES (?,?,?,?::jsonb,?)",version,id,next,mapper.writeValueAsString(definition),UUID.fromString(auth.getName()));
            jdbc.update("UPDATE invitation_template SET current_version=?,updated_at=now() WHERE id=?",next,id);
        }catch(Exception e){throw new IllegalArgumentException("Unable to version template");}
        return ApiResponse.success(Map.of("versionId",version,"versionNumber",next));
    }
    @PatchMapping("/templates/{id}/status") public ApiResponse<?> templateStatus(@PathVariable UUID id,@RequestBody Map<String,String> b){
        String status=b.get("status");if(!java.util.Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(status))throw new IllegalArgumentException("Invalid template status");
        int changed=jdbc.update("UPDATE invitation_template SET status=?,updated_at=now() WHERE id=?",status,id);if(changed==0)throw new IllegalArgumentException("Template not found");
        return ApiResponse.success(Map.of("id",id,"status",status));
    }
}
