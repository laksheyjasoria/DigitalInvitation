package com.app.policy;

import com.app.common.api.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PolicyController {
 private final JdbcTemplate jdbc;
 public PolicyController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping("/api/v1/public/policies/current")
 public ApiResponse<?> current(){
  return ApiResponse.success(jdbc.queryForList("SELECT policy_type,version_number,title,body_markdown,published_at FROM policy_document WHERE status='PUBLISHED' AND (policy_type,version_number) IN (SELECT policy_type,max(version_number) FROM policy_document WHERE status='PUBLISHED' GROUP BY policy_type) ORDER BY policy_type"));
 }
 @PostMapping("/api/v1/admin/policies/{type}")
 public ApiResponse<?> publish(@PathVariable String type,@RequestBody Map<String,String> body,Authentication auth){
  String kind=type.toUpperCase();if(!java.util.Set.of("TERMS","PRIVACY").contains(kind))throw new IllegalArgumentException("Policy type must be TERMS or PRIVACY");
  String title=body.get("title"),text=body.get("bodyMarkdown");if(title==null||title.isBlank()||text==null||text.isBlank())throw new IllegalArgumentException("Policy title and content are required");
  Integer latest=jdbc.queryForObject("SELECT COALESCE(max(version_number),0) FROM policy_document WHERE policy_type=?",Integer.class,kind);
  int next=latest+1;UUID id=UUID.randomUUID();
  jdbc.update("UPDATE policy_document SET status='RETIRED' WHERE policy_type=? AND status='PUBLISHED'",kind);
  jdbc.update("INSERT INTO policy_document(id,policy_type,version_number,title,body_markdown,status,published_at,created_by) VALUES (?,?,?,?,?,'PUBLISHED',now(),?)",id,kind,next,title,text,UUID.fromString(auth.getName()));
  return ApiResponse.success(Map.of("id",id,"type",kind,"version",next));
 }
}
