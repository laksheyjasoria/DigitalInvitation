package com.app.admin;

import com.app.common.api.ApiResponse;
import com.app.audit.AuditLogService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
 private final JdbcTemplate jdbc;private final AuditLogService audit;
 public AdminOperationsController(JdbcTemplate jdbc,AuditLogService audit){this.jdbc=jdbc;this.audit=audit;}
 @GetMapping("/users")
 public ApiResponse<?> users(@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String search){
  int safe=Math.max(1,Math.min(limit,100));String term=search==null?"%":"%"+search.trim().toLowerCase()+"%";
  return ApiResponse.success(jdbc.queryForList("SELECT id,email,display_name,role,status,email_verified_at,created_at FROM app_user WHERE email LIKE ? OR lower(display_name) LIKE ? ORDER BY created_at DESC LIMIT ?",term,term,safe));
 }
 @PatchMapping("/users/{id}/status") @org.springframework.transaction.annotation.Transactional
 public ApiResponse<?> userStatus(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication auth){
  String status=body.get("status");if(status==null||!Set.of("ACTIVE","SUSPENDED").contains(status))throw new IllegalArgumentException("Status must be ACTIVE or SUSPENDED");
  int n=jdbc.update("UPDATE app_user SET status=?,auth_version=auth_version+1,updated_at=now() WHERE id=? AND role='CUSTOMER' AND status IN ('ACTIVE','SUSPENDED')",status,id);if(n==0)throw new IllegalArgumentException("Customer account not found or not eligible for status changes");
  audit.record(UUID.fromString(auth.getName()),"USER_STATUS_CHANGED","USER",id.toString(),"SUCCESS",Map.of("status",status));
  return ApiResponse.success(Map.of("userId",id,"status",status));
 }
 @PatchMapping("/users/{id}/role") @org.springframework.transaction.annotation.Transactional
 public ApiResponse<?> userRole(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication auth){
  requireSuperAdmin(auth);String role=body.get("role");if(role==null||!Set.of("CUSTOMER","ADMIN","SUPER_ADMIN","SUPPORT").contains(role))throw new IllegalArgumentException("Invalid role");
  int n=jdbc.update("UPDATE app_user SET role=?,auth_version=auth_version+1,updated_at=now() WHERE id=?",role,id);if(n==0)throw new IllegalArgumentException("User not found");
  audit.record(UUID.fromString(auth.getName()),"USER_ROLE_CHANGED","USER",id.toString(),"SUCCESS",Map.of("role",role));
  return ApiResponse.success(Map.of("userId",id,"role",role));
 }
 @GetMapping("/audit")
 public ApiResponse<?> auditEvents(@RequestParam(defaultValue="100")int limit){
  int safe=Math.max(1,Math.min(limit,250));
  return ApiResponse.success(jdbc.queryForList("SELECT id,actor_user_id,action,resource_type,resource_id,outcome,metadata,created_at FROM audit_log ORDER BY created_at DESC LIMIT ?",safe));
 }
 @GetMapping("/email-outbox")
 public ApiResponse<?> emailOutbox(@RequestParam(defaultValue="100")int limit,@RequestParam(required=false)String status){
  int safe=Math.max(1,Math.min(limit,250));
  if(status!=null&&!Set.of("PENDING","SENT","FAILED").contains(status))throw new IllegalArgumentException("Invalid email outbox status");
  if(status==null)return ApiResponse.success(jdbc.queryForList("SELECT id,recipient,template_key,status,attempts,next_attempt_at,created_at,sent_at FROM email_outbox ORDER BY created_at DESC LIMIT ?",safe));
  return ApiResponse.success(jdbc.queryForList("SELECT id,recipient,template_key,status,attempts,next_attempt_at,created_at,sent_at FROM email_outbox WHERE status=? ORDER BY created_at DESC LIMIT ?",status,safe));
 }
 @PostMapping("/email-outbox/{id}/retry") @org.springframework.transaction.annotation.Transactional
 public ApiResponse<?> retryEmail(@PathVariable UUID id,Authentication auth){
  int changed=jdbc.update("UPDATE email_outbox SET status='PENDING',attempts=0,next_attempt_at=now() WHERE id=? AND status='FAILED'",id);
  if(changed!=1)throw new IllegalArgumentException("Failed email item not found or is not eligible for retry");
  audit.record(UUID.fromString(auth.getName()),"EMAIL_OUTBOX_RETRY","EMAIL_OUTBOX",id.toString(),"SUCCESS",Map.of());
  return ApiResponse.success(Map.of("id",id,"status","PENDING"));
 }
 @GetMapping("/orders")
 public ApiResponse<?> orders(@RequestParam(defaultValue="100")int limit){
  int safe=Math.max(1,Math.min(limit,250));
  return ApiResponse.success(jdbc.queryForList("SELECT o.id,o.order_number,o.currency,o.amount_minor,o.status,o.created_at,u.email,i.title FROM customer_order o JOIN app_user u ON u.id=o.user_id JOIN invitation i ON i.id=o.invitation_id ORDER BY o.created_at DESC LIMIT ?",safe));
 }
 @PostMapping("/invitations/{id}/revoke") @org.springframework.transaction.annotation.Transactional
 public ApiResponse<?> revoke(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication auth){
  String reason=body.getOrDefault("reason","Administrative revocation");
  int n=jdbc.update("UPDATE invitation_publication SET revoked_at=now() WHERE invitation_id=? AND revoked_at IS NULL",id);
  int updated=jdbc.update("UPDATE invitation SET status='SUSPENDED',updated_at=now() WHERE id=?",id);
  if(updated==0)throw new IllegalArgumentException("Invitation not found");
  audit.record(UUID.fromString(auth.getName()),"INVITATION_REVOKED","INVITATION",id.toString(),"SUCCESS",Map.of("reason",reason,"publicationsRevoked",n));
  return ApiResponse.success(Map.of("invitationId",id,"revokedPublications",n));
 }
 private void requireSuperAdmin(Authentication auth){
  if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_SUPER_ADMIN")))
   throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"SUPER_ADMIN role required");
 }
}
