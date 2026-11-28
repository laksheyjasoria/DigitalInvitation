package com.app.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public AuditLogService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public void record(UUID actor,String action,String resourceType,String resourceId,String outcome,Map<String,Object> metadata){
  try{jdbc.update("INSERT INTO audit_log(actor_user_id,action,resource_type,resource_id,outcome,metadata) VALUES (?,?,?,?,?,?::jsonb)",
   actor,action,resourceType,resourceId,outcome,mapper.writeValueAsString(metadata==null?Map.of():metadata));}
  catch(Exception e){throw new IllegalStateException("Unable to record required audit event",e);}
 }
}
