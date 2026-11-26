package com.app.admin;

import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final com.app.audit.AuditLogService audit;
    public AdminSettingsController(JdbcTemplate jdbc,ObjectMapper mapper,com.app.audit.AuditLogService audit){this.jdbc=jdbc;this.mapper=mapper;this.audit=audit;}
    public record SettingRequest(String valueType,Object value,String description){}
    @GetMapping public ApiResponse<?> list(){return ApiResponse.success(jdbc.queryForList("SELECT setting_key,setting_value::text AS setting_value,value_type,description,updated_at FROM system_setting WHERE is_sensitive=false ORDER BY setting_key"));}
    @PutMapping("/{key}") public ApiResponse<?> update(@PathVariable String key,@RequestBody SettingRequest req,Authentication auth){
        if(key.isBlank()||key.length()>160||!key.matches("[a-zA-Z0-9._-]+"))throw new IllegalArgumentException("Invalid setting key");
        if(req==null||req.value()==null||req.valueType()==null||!java.util.Set.of("STRING","INTEGER","LONG","DECIMAL","BOOLEAN","JSON").contains(req.valueType().toUpperCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("A supported setting value and type are required");
        Boolean sensitive=jdbc.query("SELECT is_sensitive FROM system_setting WHERE setting_key=?",rs->rs.next()?rs.getBoolean(1):null,key);
        if(Boolean.TRUE.equals(sensitive))throw new IllegalArgumentException("Sensitive settings must be changed through the secret-management workflow");
        String normalizedType=req.valueType().toUpperCase(java.util.Locale.ROOT);
        try {
            switch(normalizedType) {
                case "STRING" -> { if(!(req.value() instanceof String))throw new IllegalArgumentException(); }
                case "INTEGER" -> { Integer.parseInt(String.valueOf(req.value())); }
                case "LONG" -> { Long.parseLong(String.valueOf(req.value())); }
                case "DECIMAL" -> { new java.math.BigDecimal(String.valueOf(req.value())); }
                case "BOOLEAN" -> { if(!(req.value() instanceof Boolean))throw new IllegalArgumentException(); }
                case "JSON" -> { mapper.valueToTree(req.value()); }
                default -> throw new IllegalArgumentException();
            }
        } catch(Exception invalid) { throw new IllegalArgumentException("Setting value does not match its declared type"); }
        if(key.endsWith(".grace-days")){int days=Integer.parseInt(String.valueOf(req.value()));if(days<0||days>365)throw new IllegalArgumentException("Grace days must be between 0 and 365");}
        UUID actor=UUID.fromString(auth.getName());
        try{String json=mapper.writeValueAsString(req.value());
            jdbc.update("INSERT INTO system_setting(setting_key,setting_value,value_type,description,updated_by) VALUES (?,?::jsonb,?,?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value,value_type=EXCLUDED.value_type,description=EXCLUDED.description,updated_by=EXCLUDED.updated_by,updated_at=now(),version=system_setting.version+1",key,json,normalizedType,req.description(),actor);
            jdbc.update("INSERT INTO system_setting_version(setting_id,setting_key,setting_value,changed_by,change_reason) SELECT id,setting_key,setting_value,?,? FROM system_setting WHERE setting_key=?",actor,"Admin configuration update",key);
        }catch(Exception e){throw new IllegalArgumentException("Unable to save configuration value");}
        audit.record(actor,"SETTING_UPDATED","SYSTEM_SETTING",key,"SUCCESS",Map.of("valueType",req.valueType()));
        return ApiResponse.success(Map.of("key",key,"updated",true));
    }
}
